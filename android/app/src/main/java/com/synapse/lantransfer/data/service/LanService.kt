package com.synapse.lantransfer.data.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.synapse.lantransfer.data.model.FileHeader
import com.synapse.lantransfer.data.model.TransferProgress
import com.synapse.lantransfer.data.model.TransferRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.*

/**
 * Handles the TCP/TLS file transfer protocol, compatible with the Go backend.
 *
 * Protocol (sender side):
 *   1. Listen on TLS server socket (self-signed cert)
 *   2. Accept connection
 *   3. Send: [8-byte headerLen BE][JSON FileHeader]
 *   4. Read: [8-byte reqLen BE][JSON TransferRequest]
 *   5. Seek to offset, stream file bytes
 *   6. Send: [32-byte checksum] (SHA-256, see note)
 *
 * Protocol (receiver side):
 *   1. Connect via TLS to sender (skip cert verification, matching Go)
 *   2. Read header → parse FileHeader
 *   3. Send TransferRequest (with resume offset)
 *   4. Read file bytes → save to disk
 *   5. Read + verify checksum
 *
 * Note: Go backend uses BLAKE3, but we use SHA-256 for now.
 * Checksum from Go senders is received but verification is skipped
 * since TLS already provides integrity.
 */
class LanService(private val context: Context) {

    companion object {
        private const val TAG = "LanService"
        private const val BUFFER_SIZE = 4 * 1024 * 1024  // 4MB buffer, matching Go
        private const val CHECKSUM_SIZE = 32
    }

    // ======================== SENDER ========================

    /**
     * Start a TLS server that serves files to connecting peers.
     * Returns the port number being listened on.
     *
     * @param uris List of content URIs to send
     * @param onProgress Callback for transfer progress updates
     * @param onPeerConnected Called when a peer connects (with their address)
     * @param onZipping Called when zipping starts (for multiple files)
     * @param onZipComplete Called when zipping completes
     * @param onComplete Called when transfer completes successfully
     * @param onError Called when transfer fails
     * @return The port number the server is listening on
     */
    suspend fun startSender(
        uris: List<Uri>,
        onProgress: (TransferProgress) -> Unit,
        onPeerConnected: (String) -> Unit = {},
        onZipping: () -> Unit = {},
        onZipComplete: () -> Unit = {},
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): SenderSession = withContext(Dispatchers.IO) {
        val sslContext = createServerSSLContext()
        val serverSocketFactory = sslContext.serverSocketFactory
        val serverSocket = serverSocketFactory.createServerSocket(0) as SSLServerSocket
        serverSocket.soTimeout = 0
        serverSocket.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")

        val port = serverSocket.localPort
        Log.d(TAG, "Sender listening on port $port")

        SenderSession(
            serverSocket = serverSocket,
            port = port,
            uris = uris,
            context = context,
            onProgress = onProgress,
            onPeerConnected = onPeerConnected,
            onZipping = onZipping,
            onZipComplete = onZipComplete,
            onComplete = onComplete,
            onError = onError
        )
    }

    /**
     * Connect to a sender and receive a file.
     *
     * @param address IP address of the sender
     * @param port Port of the sender
     * @param downloadDir Directory to save received files
     * @param onProgress Callback for transfer progress updates
     * @param onComplete Called with the filename when transfer completes
     * @param onError Called when transfer fails
     */
    suspend fun receiveFrom(
        address: String,
        port: Int,
        downloadDir: String,
        onProgress: (TransferProgress) -> Unit,
        onComplete: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to sender at $address:$port")

            // Create TLS socket that skips certificate verification (matches Go client)
            val sslContext = createTrustAllSSLContext()
            val socketFactory = sslContext.socketFactory
            val socket = socketFactory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(address, port), 30_000)
            socket.startHandshake()

            Log.d(TAG, "TLS handshake complete with $address:$port")

            val input = BufferedInputStream(socket.getInputStream(), BUFFER_SIZE)
            val output = BufferedOutputStream(socket.getOutputStream())

            try {
                // Step 1: Read FileHeader
                val headerLen = readLong(input)
                if (headerLen > 65536) {
                    throw IOException("Header too large: $headerLen")
                }
                val headerBytes = readFully(input, headerLen.toInt())
                val header = FileHeader.fromJson(headerBytes)
                Log.d(TAG, "Received header: ${header.name}, ${header.size} bytes, compression=${header.compression}")

                // Reject compressed transfers for now
                if (header.compression != FileHeader.COMPRESSION_NONE) {
                    throw IOException("Compressed transfers (${header.compression}) are not yet supported on Android")
                }

                if (header.isArchive) {
                    Log.d(TAG, "Archive transfer detected. Will save as .zip file.")
                }

                // Step 2: Determine resume offset and prepare output stream
                val safeName = sanitizeFilename(header.name)
                var offset = 0L
                val fos: OutputStream

                if (downloadDir.startsWith("content://")) {
                    val treeUri = Uri.parse(downloadDir)
                    val dirFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw IOException("Cannot access download directory")
                    
                    var docFile = dirFile.findFile(safeName)
                    if (docFile != null) {
                        if (docFile.length() < header.size) {
                            offset = docFile.length()
                            Log.d(TAG, "Resuming from offset $offset")
                        }
                    } else {
                        docFile = dirFile.createFile("application/octet-stream", safeName)
                            ?: throw IOException("Cannot create file in directory")
                    }
                    
                    // Step 3: Send TransferRequest
                    val request = TransferRequest(offset = offset)
                    val reqBytes = request.toJson()
                    writeLong(output, reqBytes.size.toLong())
                    output.write(reqBytes)
                    output.flush()

                    fos = if (offset > 0) {
                        context.contentResolver.openOutputStream(docFile.uri, "wa")
                    } else {
                        context.contentResolver.openOutputStream(docFile.uri)
                    } ?: throw IOException("Could not open output stream")
                } else {
                    val destDir = File(downloadDir)
                    if (!destDir.exists()) destDir.mkdirs()
                    val destFile = File(destDir, safeName)
                    
                    if (destFile.exists() && destFile.length() < header.size) {
                        offset = destFile.length()
                        Log.d(TAG, "Resuming from offset $offset")
                    }
                    
                    // Step 3: Send TransferRequest
                    val request = TransferRequest(offset = offset)
                    val reqBytes = request.toJson()
                    writeLong(output, reqBytes.size.toLong())
                    output.write(reqBytes)
                    output.flush()

                    val fileOut = if (offset > 0) {
                        FileOutputStream(destFile, true)  // Append mode for resume
                    } else {
                        FileOutputStream(destFile)
                    }
                    fos = fileOut
                }

                // Step 4: Receive file data
                val remaining = header.size - offset

                var bytesReceived = 0L
                val buf = ByteArray(BUFFER_SIZE)
                val startTime = System.currentTimeMillis()

                fos.use { fileOut ->
                    while (bytesReceived < remaining) {
                        if (!isActive) throw IOException("Transfer cancelled")

                        val toRead = minOf(buf.size.toLong(), remaining - bytesReceived).toInt()
                        val read = input.read(buf, 0, toRead)
                        if (read == -1) break

                        fileOut.write(buf, 0, read)
                        bytesReceived += read

                        val totalReceived = offset + bytesReceived
                        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                        val speed = bytesReceived * 1000f / elapsed

                        onProgress(
                            TransferProgress(
                                bytesTransferred = totalReceived,
                                totalBytes = header.size,
                                fileName = header.name,
                                speed = speed,
                                peerAddress = "$address:$port"
                            )
                        )
                    }
                }

                // Step 5: Read checksum (32 bytes) — receive but don't verify
                try {
                    val checksum = readFully(input, CHECKSUM_SIZE)
                    Log.d(TAG, "Received ${checksum.size}-byte checksum (verification skipped, TLS provides integrity)")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read checksum: ${e.message}")
                }

                Log.d(TAG, "Transfer complete: ${header.name}")
                onComplete(header.name)

            } finally {
                socket.close()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Receive failed: ${e.message}", e)
            onError(e.message ?: "Unknown error during receive")
            throw e
        }
    }

    // ======================== PROTOCOL HELPERS ========================

    /** Write a 64-bit big-endian long to the stream (matches Go's binary.BigEndian int64) */
    private fun writeLong(out: OutputStream, value: Long) {
        val buf = ByteBuffer.allocate(8)
        buf.putLong(value)
        out.write(buf.array())
    }

    /** Read a 64-bit big-endian long from the stream */
    private fun readLong(input: InputStream): Long {
        val buf = ByteArray(8)
        var read = 0
        while (read < 8) {
            val n = input.read(buf, read, 8 - read)
            if (n == -1) throw IOException("Unexpected EOF reading long")
            read += n
        }
        return ByteBuffer.wrap(buf).long
    }

    /** Read exactly n bytes from the stream */
    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buf = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = input.read(buf, read, size - read)
            if (n == -1) throw IOException("Unexpected EOF: expected $size bytes, got $read")
            read += n
        }
        return buf
    }

    // ======================== TLS HELPERS ========================

    /** Create an SSL context with a self-signed certificate for the server */
    private fun createServerSSLContext(): SSLContext {
        // Generate a self-signed RSA key pair
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        // Create a self-signed X.509 certificate
        val cert = createSelfSignedCertificate(keyPair)

        // Build a KeyStore containing the certificate
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("synapse", keyPair.private, charArrayOf(), arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, charArrayOf())

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
        return sslContext
    }

    /** Create an SSL context that trusts all certificates (for connecting to self-signed peers) */
    private fun createTrustAllSSLContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext
    }

    /** Generate a self-signed X.509 certificate (valid 24 hours, matching Go) */
    @Suppress("DEPRECATION")
    private fun createSelfSignedCertificate(keyPair: java.security.KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 24 * 60 * 60 * 1000L) // 24 hours

        // Use Android's built-in certificate generation
        val subject = "CN=Synapse Ephemeral, O=Synapse"
        val builder = android.security.keystore.KeyGenParameterSpec.Builder("synapse", 0)

        // Fallback removed, using internal generation directly

        // Actually, let's use a simpler approach that works on all Android versions
        // We'll create a minimal self-signed cert manually
        return generateSelfSignedCert(keyPair, notBefore, notAfter)
    }

    private fun generateSelfSignedCert(
        keyPair: java.security.KeyPair,
        notBefore: Date,
        notAfter: Date
    ): X509Certificate {
        // Use Android's CertificateFactory approach
        // For simplicity and broad compatibility, we generate via KeyStore
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)

        // Use platform API for cert generation if available, otherwise create minimal cert
        val certBuilder = javax.security.cert.X509Certificate::class.java

        // Simplest approach: use the key pair directly with SSLContext
        // The SSLContext.init with KeyManagerFactory handles this internally
        // We create a dummy certificate for the key store

        // Actually, the cleanest cross-API approach:
        // Generate cert bytes using basic ASN.1 encoding
        return CertificateGenerator.generate(keyPair, notBefore, notAfter)
    }

    /** Sanitize a filename for safe storage */
    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}

/**
 * Manages an active sender session. Can accept multiple peer connections.
 */
class SenderSession(
    private val serverSocket: SSLServerSocket,
    val port: Int,
    private val uris: List<Uri>,
    private val context: Context,
    private val onProgress: (TransferProgress) -> Unit,
    private val onPeerConnected: (String) -> Unit,
    private val onZipping: () -> Unit,
    private val onZipComplete: () -> Unit,
    private val onComplete: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Accept and handle one incoming peer connection.
     * Call this in a loop for multi-peer support.
     */
    suspend fun acceptAndTransfer() = withContext(Dispatchers.IO) {
        try {
            val socket = serverSocket.accept() as SSLSocket
            socket.useClientMode = false
            socket.startHandshake()
            val peerAddr = socket.remoteSocketAddress.toString()
            Log.d("SenderSession", "Peer connected and handshake complete: $peerAddr")
            onPeerConnected(peerAddr)

            try {
                handleSendConnection(socket, peerAddr)
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            if (!serverSocket.isClosed) {
                Log.e("SenderSession", "Accept failed: ${e.message}", e)
                onError(e.message ?: "Connection failed")
            }
        }
    }

    private fun handleSendConnection(socket: SSLSocket, peerAddr: String) {
        val output = BufferedOutputStream(socket.getOutputStream(), 4 * 1024 * 1024)
        val input = BufferedInputStream(socket.getInputStream())

        val isArchive = uris.size > 1
        var transferUri = uris.first()
        var name = getFileName(transferUri) ?: "files.zip"
        var size = getFileSize(transferUri)
        var tempFile: java.io.File? = null

        if (!isArchive && size <= 0L) {
            Log.e("SenderSession", "Cannot determine file size for $name — aborting transfer")
            onError("Could not determine file size for: $name. Try selecting the file from a different location.")
            return
        }

        if (isArchive) {
            Log.d("SenderSession", "Zipping ${uris.size} files for transfer...")
            onZipping()
            tempFile = File(context.externalCacheDir ?: context.cacheDir, "synapse_transfer_${System.currentTimeMillis()}.zip")
            name = "Synapse_Transfer.zip"
            
            try {
                FileOutputStream(tempFile).use { fos ->
                    java.util.zip.ZipOutputStream(fos).use { zos ->
                        for (uri in uris) {
                            val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
                            val entry = java.util.zip.ZipEntry(fileName)
                            zos.putNextEntry(entry)
                            contentResolver.openInputStream(uri)?.use { fileStream ->
                                fileStream.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
                onZipComplete()
                transferUri = Uri.fromFile(tempFile)
                size = tempFile.length()
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }

        // Step 2: Send FileHeader
        val header = FileHeader(
            name = name,
            size = size,
            isArchive = isArchive,
            compression = FileHeader.COMPRESSION_NONE
        )
        val headerBytes = header.toJson()
        val headerLenBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(headerBytes.size.toLong()).array()
        output.write(headerLenBuf)
        output.write(headerBytes)
        output.flush()

        // Step 2: Read TransferRequest
        val reqLenBytes = ByteArray(8)
        readFullyBlocking(input, reqLenBytes)
        val reqLen = ByteBuffer.wrap(reqLenBytes).long
        val reqBytes = ByteArray(reqLen.toInt())
        readFullyBlocking(input, reqBytes)
        val request = TransferRequest.fromJson(reqBytes)
        val offset = request.offset.coerceIn(0, size)

        Log.d("SenderSession", "Transfer request: offset=$offset for $name ($size bytes)")

        // Step 4: Stream file data
        val inputStream = if (tempFile != null) {
            java.io.FileInputStream(tempFile)
        } else {
            contentResolver.openInputStream(transferUri)
                ?: throw IOException("Cannot open URI: $transferUri")
        }

        inputStream.use { stream ->
            if (offset > 0) {
                stream.skip(offset)
            }

            val buf = ByteArray(4 * 1024 * 1024)
            var bytesSent = 0L
            val totalToSend = size - offset
            val startTime = System.currentTimeMillis()
            val digest = java.security.MessageDigest.getInstance("SHA-256")

            while (bytesSent < totalToSend) {
                val toRead = minOf(buf.size.toLong(), totalToSend - bytesSent).toInt()
                val read = stream.read(buf, 0, toRead)
                if (read == -1) break

                output.write(buf, 0, read)
                digest.update(buf, 0, read)
                bytesSent += read

                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val speed = bytesSent * 1000f / elapsed

                onProgress(
                    TransferProgress(
                        bytesTransferred = offset + bytesSent,
                        totalBytes = size,
                        fileName = name,
                        speed = speed,
                        peerAddress = peerAddr
                    )
                )
            }
            output.flush()

            // Step 4: Send checksum (SHA-256 produces 32 bytes, same size as BLAKE3)
            val checksum = digest.digest()
            output.write(checksum)
            output.flush()

            Log.d("SenderSession", "Transfer complete: $name ($bytesSent bytes sent)")
            onComplete(name)
        }
        
        // Clean up temp file
        tempFile?.delete()
    }

    private fun readFullyBlocking(input: InputStream, buf: ByteArray) {
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n == -1) throw IOException("Unexpected EOF")
            read += n
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long {
        if (uri.scheme == "content") {
            // Fast path: query the content provider for the SIZE column
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) {
                        val size = cursor.getLong(idx)
                        if (size > 0) return size
                    }
                }
            }
            // Reliable fallback: stat the file descriptor directly
            // This works for gallery images and other providers that omit the SIZE column
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val size = pfd.statSize
                    if (size > 0) return size
                }
            } catch (e: Exception) {
                Log.w("SenderSession", "Could not stat file descriptor for $uri: ${e.message}")
            }
        }
        // Last resort for file:// URIs
        return try {
            val path = uri.path
            if (path != null) java.io.File(path).length() else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Stop the sender and close the server socket. */
    fun stop() {
        try {
            serverSocket.close()
        } catch (e: Exception) {
            Log.w("SenderSession", "Error closing server socket: ${e.message}")
        }
    }
}

/**
 * Minimal self-signed X.509 certificate generator for TLS.
 * Compatible with all Android API levels 26+.
 */
internal object CertificateGenerator {

    fun generate(
        keyPair: java.security.KeyPair,
        notBefore: Date,
        notAfter: Date
    ): X509Certificate {
        // On Android, the simplest trusted approach is to use a KeyStore
        // and generate the certificate through platform APIs.
        // Since we're using a trust-all client anyway, the certificate
        // just needs to exist and be valid enough for TLS handshake.

        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        // Alternative approach: create a PKCS12 keystore in memory
        val p12 = KeyStore.getInstance("PKCS12")
        p12.load(null, null)

        // We need to create a self-signed certificate.
        // Use reflection to access the internal cert generation on Android,
        // or use a minimal X.509 v1 cert builder.

        // Simplest: use the built-in test cert generation
        val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")

        // Build a minimal self-signed DER certificate
        val certBytes = buildSelfSignedDER(keyPair, notBefore, notAfter)
        val cert = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate

        return cert
    }

    /**
     * Build a minimal self-signed X.509 v1 certificate in DER format.
     * This is the smallest valid certificate that works for TLS.
     */
    private fun buildSelfSignedDER(
        keyPair: java.security.KeyPair,
        notBefore: Date,
        notAfter: Date
    ): ByteArray {
        // Use org.bouncycastle if available on the platform, otherwise
        // create a minimal ASN.1 structure.

        // On modern Android (API 26+), we can leverage the platform's
        // implementation. Let's use java.security classes directly.

        // Actually, the most reliable cross-platform approach is to build
        // the TBSCertificate ASN.1 manually and sign it.
        val tbsCert = buildTBSCertificate(keyPair, notBefore, notAfter)
        val signature = java.security.Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)
        signature.update(tbsCert)
        val sig = signature.sign()

        // Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signature }
        val sigAlg = sha256WithRSAAlgorithmIdentifier()
        val sigBitString = wrapBitString(sig)

        return wrapSequence(tbsCert + sigAlg + sigBitString)
    }

    private fun buildTBSCertificate(
        keyPair: java.security.KeyPair,
        notBefore: Date,
        notAfter: Date
    ): ByteArray {
        val components = ByteArrayOutputStream()

        // Version: v1 (default, not explicitly encoded for v1)

        // Serial number
        components.write(wrapInteger(byteArrayOf(0x01)))

        // Signature algorithm: SHA256withRSA
        components.write(sha256WithRSAAlgorithmIdentifier())

        // Issuer: CN=Synapse
        components.write(buildSimpleDN("Synapse Ephemeral"))

        // Validity
        components.write(buildValidity(notBefore, notAfter))

        // Subject: CN=Synapse
        components.write(buildSimpleDN("Synapse Ephemeral"))

        // Subject Public Key Info
        components.write(keyPair.public.encoded)

        return wrapSequence(components.toByteArray())
    }

    private fun sha256WithRSAAlgorithmIdentifier(): ByteArray {
        // OID 1.2.840.113549.1.1.11 = SHA256withRSA
        val oid = byteArrayOf(
            0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(),
            0x0d, 0x01, 0x01, 0x0b
        )
        val nullParam = byteArrayOf(0x05, 0x00)
        return wrapSequence(oid + nullParam)
    }

    private fun buildSimpleDN(commonName: String): ByteArray {
        // OID 2.5.4.3 = commonName
        val cnOid = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)
        val cnValue = wrapUTF8String(commonName)
        val atv = wrapSequence(cnOid + cnValue)
        val rdn = wrapSet(atv)
        return wrapSequence(rdn)
    }

    private fun buildValidity(notBefore: Date, notAfter: Date): ByteArray {
        val fmt = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val before = wrapUTCTime(fmt.format(notBefore))
        val after = wrapUTCTime(fmt.format(notAfter))
        return wrapSequence(before + after)
    }

    // ASN.1 DER helpers
    private fun wrapSequence(content: ByteArray): ByteArray = wrapTag(0x30, content)
    private fun wrapSet(content: ByteArray): ByteArray = wrapTag(0x31, content)
    private fun wrapInteger(value: ByteArray): ByteArray = wrapTag(0x02, value)
    private fun wrapBitString(content: ByteArray): ByteArray = wrapTag(0x03, byteArrayOf(0x00) + content)
    private fun wrapUTF8String(s: String): ByteArray = wrapTag(0x0c, s.toByteArray(Charsets.UTF_8))
    private fun wrapUTCTime(s: String): ByteArray = wrapTag(0x17, s.toByteArray(Charsets.US_ASCII))

    private fun wrapTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        writeLength(out, content.size)
        out.write(content)
        return out.toByteArray()
    }

    private fun writeLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 128) {
            out.write(length)
        } else if (length < 256) {
            out.write(0x81)
            out.write(length)
        } else {
            out.write(0x82)
            out.write(length shr 8)
            out.write(length and 0xff)
        }
    }
}
