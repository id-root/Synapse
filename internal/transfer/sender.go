package transfer

import (
	"archive/zip"
	"context"
	"crypto/tls"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/example/landrop/internal/discovery"
	"github.com/example/landrop/pkg/ui"
	"github.com/klauspost/compress/zstd"
	"github.com/schollz/progressbar/v3"
	"github.com/zeebo/blake3"
)

// StartSender starts the file transfer process as a sender.
// It listens on a random TCP port, announces itself via mDNS,
// and waits for receivers to connect.
// allowConn is a callback that returns true if the connection should be accepted.
// portChan is an optional channel to receive the bound port number.
func StartSender(inputPath string, allowConn func(string) bool, portChan chan<- int) error {
	fileInfo, err := os.Stat(inputPath)
	if err != nil {
		return fmt.Errorf("failed to get file info: %w", err)
	}

	isDir := fileInfo.IsDir()
	var fileSize int64
	var sourcePath string // If it's a file, original path. If dir, path to temp zip.
	var cleanup func()

	if isDir {
		// Create a temporary file for the zip archive
		tmpFile, err := os.CreateTemp("", "landrop-*.zip")
		if err != nil {
			return fmt.Errorf("failed to create temp file: %w", err)
		}
		
		ui.Info("Archiving directory '%s'...", inputPath)
		
		// Walk and zip
		if err := zipDirectory(inputPath, tmpFile); err != nil {
			tmpFile.Close()
			os.Remove(tmpFile.Name())
			return fmt.Errorf("failed to zip directory: %w", err)
		}
		
		// Get zip size
		stat, err := tmpFile.Stat()
		if err != nil {
			tmpFile.Close()
			os.Remove(tmpFile.Name())
			return err
		}
		fileSize = stat.Size()
		sourcePath = tmpFile.Name()
		tmpFile.Close() // Close it, we'll open it fresh for each transfer

		cleanup = func() {
			os.Remove(sourcePath)
		}
	} else {
		fileSize = fileInfo.Size()
		sourcePath = inputPath
		cleanup = func() {}
	}
	defer cleanup()

	// 1. Generate TLS Config
	cert, err := GenerateTLSCertificate()
	if err != nil {
		return fmt.Errorf("failed to generate TLS certificate: %w", err)
	}
	tlsConfig := &tls.Config{Certificates: []tls.Certificate{cert}}

	// 2. Start TCP listener
	listener, err := tls.Listen("tcp", ":0", tlsConfig)
	if err != nil {
		return fmt.Errorf("failed to listen on TCP: %w", err)
	}
	defer listener.Close()

	port := listener.Addr().(*net.TCPAddr).Port
	ui.Info("Listening on port %d...", port)
	
	if portChan != nil {
		portChan <- port
	}

	// 3. Announce service
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	shutdownDiscovery, err := discovery.Announce(ctx, port)
	if err != nil {
		return fmt.Errorf("failed to announce service: %w", err)
	}
	defer shutdownDiscovery()

	ui.Info("Waiting for receivers to connect... (Press Ctrl+C to stop)")

	// Mutex for UI prompts to avoid interleaving
	var promptMu sync.Mutex

	// 4. Accept loop
	for {
		conn, err := listener.Accept()
		if err != nil {
			// If listener closed, exit
			return nil
		}
		
		// Handle each connection in a goroutine
		go func(c net.Conn) {
			defer c.Close()
			
			// Connection Approval
			promptMu.Lock()
			approved := allowConn(c.RemoteAddr().String())
			promptMu.Unlock()

			if !approved {
				ui.Info("Connection rejected.")
				return
			}
			
			ui.Success("Starting transfer to %s", c.RemoteAddr())
			if err := handleTransfer(c, inputPath, sourcePath, fileSize, isDir); err != nil {
				ui.Error("Transfer to %s failed: %v", c.RemoteAddr(), err)
			} else {
				ui.Success("Transfer to %s completed", c.RemoteAddr())
			}
		}(conn)
	}
}

func handleTransfer(conn net.Conn, originalName string, sourcePath string, fileSize int64, isDir bool) error {
	// Determine compression
	compression := getCompressionMethod(originalName, isDir)

	// 1. Send Header
	header := FileHeader{
		Name:        filepath.Base(originalName),
		Size:        fileSize,
		IsArchive:   isDir,
		Compression: compression,
	}

	headerBytes, err := json.Marshal(header)
	if err != nil {
		return fmt.Errorf("failed to marshal header: %w", err)
	}

	var headerLen int64 = int64(len(headerBytes))
	if err := binary.Write(conn, binary.BigEndian, headerLen); err != nil {
		return fmt.Errorf("failed to write header length: %w", err)
	}

	if _, err := conn.Write(headerBytes); err != nil {
		return fmt.Errorf("failed to send header: %w", err)
	}

	// 2. Receive Transfer Request (Offset)
	var reqLen int64
	if err := binary.Read(conn, binary.BigEndian, &reqLen); err != nil {
		return fmt.Errorf("failed to read request length: %w", err)
	}

	reqBytes := make([]byte, reqLen)
	if _, err := io.ReadFull(conn, reqBytes); err != nil {
		return fmt.Errorf("failed to read request JSON: %w", err)
	}

	var req TransferRequest
	if err := json.Unmarshal(reqBytes, &req); err != nil {
		return fmt.Errorf("failed to unmarshal request: %w", err)
	}

	offset := req.Offset
	if offset > fileSize {
		offset = 0 // Invalid offset, start from 0
	}
	
	if offset > 0 {
		ui.Info("Resuming transfer from offset %d...", offset)
	}

	// 3. Prepare Source
	file, err := os.Open(sourcePath)
	if err != nil {
		return fmt.Errorf("failed to open source file: %w", err)
	}
	defer file.Close()

	if _, err := file.Seek(offset, 0); err != nil {
		return fmt.Errorf("failed to seek file: %w", err)
	}

	// 4. Send Content
	bar := progressbar.DefaultBytes(
		fileSize-offset,
		"sending",
	)
	
	// Hasher - Replace sha256 with BLAKE3
	hasher := blake3.New()
	
	var destination io.Writer = conn
	
	// MultiWriter to update hash as we write to conn
	hashedDestination := io.MultiWriter(destination, hasher)
	
	var contentWriter io.Writer
	var closer io.Closer
	
	if compression == CompressionZstd {
		chunked := NewChunkedWriter(hashedDestination)
		// Use default compression level for Zstd
		zstdWriter, err := zstd.NewWriter(chunked)
		if err != nil {
			return fmt.Errorf("failed to create zstd writer: %w", err)
		}
		
		contentWriter = zstdWriter
		closer = &compositeCloser{zstdWriter, chunked}
	} else {
		contentWriter = hashedDestination
		closer = nil
	}
	
	// Wrap source with progress bar
	pbReader := io.TeeReader(file, bar)
	
	// CopyBuffer with 1MB-4MB buffer size
	buf := make([]byte, 4*1024*1024) // 4MB buffer
	if _, err := io.CopyBuffer(contentWriter, pbReader, buf); err != nil {
		return fmt.Errorf("failed to send file content: %w", err)
	}

	// Close wrappers to flush and write EOF marker
	if closer != nil {
		if err := closer.Close(); err != nil {
			return fmt.Errorf("failed to close writers: %w", err)
		}
	}

	// 5. Send Footer (Checksum)
	checksum := hasher.Sum(nil) 
	
	if _, err := conn.Write(checksum); err != nil {
		return fmt.Errorf("failed to send checksum: %w", err)
	}

	fmt.Println() // Newline after progress bar
	return nil
}

func getCompressionMethod(filename string, isDir bool) string {
	if isDir {
		return CompressionZstd // Compress zip archives? Actually zip might already be compressed if we used Deflate. 
		// `zipDirectory` uses `zip.Deflate`. So the directory is already compressed.
		// Re-compressing a zip file is usually not useful.
		// However, `zipDirectory` creates a single file which is then transferred.
		// If we use Store method in zip, we should compress here.
		// Current implementation of zipDirectory uses zip.Deflate.
		return CompressionNone
	}

	ext := strings.ToLower(filepath.Ext(filename))
	switch ext {
	case ".jpg", ".png", ".mp4", ".zip", ".iso", ".dmg", ".gz", ".zst", ".7z", ".rar":
		return CompressionNone
	case ".txt", ".log", ".json", ".md", ".go":
		return CompressionZstd
	default:
		// Default to compression for unknown types? 
		// The prompt says "Compress: .txt, .log, .json, .md, .go". 
		// It doesn't explicitly say what to do for others. 
		// But usually text-based is safe. Binaries might not compress well.
		// I'll stick to Zstd for anything not explicitly skipped, or maybe just the whitelist?
		// Requirement: "Implement a check based on file extension or MIME type."
		// - Skip: ...
		// - Compress: ...
		// I will assume whitelist for compression to be safe and avoid CPU overhead on random binaries.
		// But wait, "Smart 'Adaptive' Compression".
		// I'll err on the side of compressing common text types and skipping known binaries.
		// For unknown types, I'll default to None to save CPU, as high throughput is a goal.
		return CompressionNone
	}
}

type compositeCloser struct {
	a io.Closer
	b io.Closer
}

func (c *compositeCloser) Close() error {
	if err := c.a.Close(); err != nil {
		c.b.Close() // Try to close b anyway
		return err
	}
	return c.b.Close()
}

func zipDirectory(source string, target io.Writer) error {
	archive := zip.NewWriter(target)
	defer archive.Close()

	info, err := os.Stat(source)
	if err != nil {
		return nil
	}

	var baseDir string
	if info.IsDir() {
		baseDir = filepath.Base(source)
	}

	return filepath.Walk(source, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		header, err := zip.FileInfoHeader(info)
		if err != nil {
			return err
		}

		if baseDir != "" {
			relPath, err := filepath.Rel(source, path)
			if err != nil {
				return err
			}
			header.Name = filepath.Join(baseDir, relPath)
		} else {
			header.Name = filepath.Base(path)
		}

		if info.IsDir() {
			header.Name += "/"
		} else {
			// Check if file should be compressed using the same logic as single files
			method := getCompressionMethod(info.Name(), false)
			if method == CompressionZstd {
				// zip.Deflate is DEFLATE, not Zstd. But we are inside a zip file.
				// The requirement says "Smart 'Adaptive' Compression... Do NOT blindly compress all files".
				// Since we are creating a zip stream, we can choose Store or Deflate per file.
				// If `getCompressionMethod` returns CompressionZstd (which means "compressible"), we use Deflate.
				// If it returns CompressionNone (already compressed), we use Store.
				header.Method = zip.Deflate
			} else {
				header.Method = zip.Store
			}
		}

		writer, err := archive.CreateHeader(header)
		if err != nil {
			return err
		}

		if info.IsDir() {
			return nil
		}

		file, err := os.Open(path)
		if err != nil {
			return err
		}
		defer file.Close()
		_, err = io.Copy(writer, file)
		return err
	})
}
