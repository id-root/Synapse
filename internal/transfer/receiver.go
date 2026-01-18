package transfer

import (
	"archive/zip"
	"bytes"
	"compress/gzip"
	"crypto/tls"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/example/landrop/pkg/ui"
	"github.com/example/landrop/pkg/utils"
	"github.com/klauspost/compress/zstd"
	"github.com/schollz/progressbar/v3"
	"github.com/zeebo/blake3"
)

// ReceiveConnect connects to a specific peer and downloads the file/directory
func ReceiveConnect(address string) error {
	ui.Info("Connecting to %s...", address)

	// 1. Connect with TLS
	tlsConfig := &tls.Config{
		InsecureSkipVerify: true,
	}

	conn, err := tls.Dial("tcp", address, tlsConfig)
	if err != nil {
		return fmt.Errorf("failed to connect to sender: %w", err)
	}
	defer conn.Close()

	ui.Info("Waiting for sender approval...")

	// 2. Read Header
	var headerLen int64
	if err := binary.Read(conn, binary.BigEndian, &headerLen); err != nil {
		return fmt.Errorf("failed to read header length: %w", err)
	}

	if headerLen > 65536 {
		return fmt.Errorf("header length too large: %d", headerLen)
	}

	headerBytes := make([]byte, headerLen)
	if _, err := io.ReadFull(conn, headerBytes); err != nil {
		return fmt.Errorf("failed to read header JSON: %w", err)
	}

	var header FileHeader
	if err := json.Unmarshal(headerBytes, &header); err != nil {
		return fmt.Errorf("failed to unmarshal header: %w", err)
	}

	// Sanitize filename
	safeName := utils.SanitizeFilename(header.Name)

	// Create directory for received files
	const downloadDir = "received_files"
	if err := os.MkdirAll(downloadDir, 0755); err != nil {
		return fmt.Errorf("failed to create download directory: %w", err)
	}

	if header.IsArchive {
		ui.Info("Receiving directory: %s (%s)", safeName, byteCountDecimal(header.Size))
	} else {
		ui.Info("Receiving file: %s (%s)", safeName, byteCountDecimal(header.Size))
	}

	// 3. Check for existing file and determine Offset
	var offset int64 = 0
	var outPath string
	var destFile *os.File

	if header.IsArchive {
		destFile, err = os.CreateTemp("", "landrop-recv-*.zip")
		if err != nil {
			return fmt.Errorf("failed to create destination file: %w", err)
		}
		offset = 0
	} else {
		// Construct the full path inside the download directory
		finalPath := filepath.Join(downloadDir, safeName)

		// Check if file exists
		if info, err := os.Stat(finalPath); err == nil && !info.IsDir() {
			if info.Size() < header.Size {
				offset = info.Size()
				ui.Info("Found partial file. Resuming from %s...", byteCountDecimal(offset))
				destFile, err = os.OpenFile(finalPath, os.O_WRONLY|os.O_APPEND, 0644)
			} else {
				destFile, err = os.Create(finalPath)
			}
		} else {
			destFile, err = os.Create(finalPath)
		}
	}

	if err != nil {
		return fmt.Errorf("failed to open destination file: %w", err)
	}
	defer destFile.Close()
	outPath = destFile.Name()

	// 4. Send Transfer Request (Offset)
	req := TransferRequest{
		Offset: offset,
	}
	reqBytes, err := json.Marshal(req)
	if err != nil {
		return fmt.Errorf("failed to marshal request: %w", err)
	}

	var reqLen int64 = int64(len(reqBytes))
	if err := binary.Write(conn, binary.BigEndian, reqLen); err != nil {
		return fmt.Errorf("failed to write request length: %w", err)
	}
	if _, err := conn.Write(reqBytes); err != nil {
		return fmt.Errorf("failed to send request: %w", err)
	}

	// 5. Download Content

	// Hasher - Replace sha256 with BLAKE3
	hasher := blake3.New()

	var contentReader io.Reader

	if header.Compression == CompressionZstd {
		hashedReader := io.TeeReader(conn, hasher)
		chunked := NewChunkedReader(hashedReader)
		zstdReader, err := zstd.NewReader(chunked)
		if err != nil {
			return fmt.Errorf("failed to create zstd reader: %w", err)
		}
		defer zstdReader.Close()
		contentReader = zstdReader
	} else if header.Compression == CompressionGzip {
		hashedReader := io.TeeReader(conn, hasher)
		chunked := NewChunkedReader(hashedReader)
		gzipReader, err := gzip.NewReader(chunked)
		if err != nil {
			return fmt.Errorf("failed to create gzip reader: %w", err)
		}
		defer gzipReader.Close()
		contentReader = gzipReader
	} else {
		// No compression. Read exactly (Size - Offset) bytes.
		remaining := header.Size - offset
		limitedReader := io.LimitReader(conn, remaining)
		hashedReader := io.TeeReader(limitedReader, hasher)
		contentReader = hashedReader
	}

	// Progress bar
	bar := progressbar.DefaultBytes(
		header.Size-offset,
		"receiving",
	)

	// Use io.CopyBuffer
	buf := make([]byte, 4*1024*1024) // 4MB buffer
	if _, err := io.CopyBuffer(io.MultiWriter(destFile, bar), contentReader, buf); err != nil {
		return fmt.Errorf("failed to write file content: %w", err)
	}

	fmt.Println() // Newline after progress bar

	// 6. Verify Footer (Checksum)
	// Read 32 bytes from conn (BLAKE3 also produces 32 bytes by default)
	receivedChecksum := make([]byte, 32)
	if _, err := io.ReadFull(conn, receivedChecksum); err != nil {
		return fmt.Errorf("failed to read checksum: %w", err)
	}

	calculatedChecksum := hasher.Sum(nil)

	// Verify
	if !bytes.Equal(calculatedChecksum, receivedChecksum) {
		return fmt.Errorf("checksum mismatch! File may be corrupted.\nExpected: %x\nGot:      %x", receivedChecksum, calculatedChecksum)
	}

	ui.Success("Checksum verified successfully.")

	// 7. Post-process (Unzip if needed)
	if header.IsArchive {
		ui.Info("Extracting archive...")
		destFile.Close() // Close temp file before reading

		// Unzip to the download directory instead of current directory
		if err := unzip(outPath, downloadDir); err != nil {
			return fmt.Errorf("failed to unzip archive: %w", err)
		}
		os.Remove(outPath) // Cleanup temp file
		ui.Success("Directory received and extracted: %s", filepath.Join(downloadDir, safeName))
	} else {
		ui.Success("File received: %s", filepath.Join(downloadDir, safeName))
	}

	return nil
}

func byteCountDecimal(b int64) string {
	const unit = 1000
	if b < unit {
		return fmt.Sprintf("%d B", b)
	}
	div, exp := int64(unit), 0
	for n := b / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(b)/float64(div), "kMGTPE"[exp])
}

func unzip(src string, dest string) error {
	r, err := zip.OpenReader(src)
	if err != nil {
		return err
	}
	defer r.Close()

	for _, f := range r.File {
		fpath := filepath.Join(dest, f.Name)
		if !strings.HasPrefix(fpath, filepath.Clean(dest)+string(os.PathSeparator)) {
			return fmt.Errorf("illegal file path: %s", fpath)
		}

		if f.FileInfo().IsDir() {
			os.MkdirAll(fpath, os.ModePerm)
			continue
		}

		if err := os.MkdirAll(filepath.Dir(fpath), os.ModePerm); err != nil {
			return err
		}

		outFile, err := os.OpenFile(fpath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
		if err != nil {
			return err
		}

		rc, err := f.Open()
		if err != nil {
			outFile.Close()
			return err
		}

		_, err = io.Copy(outFile, rc)

		outFile.Close()
		rc.Close()

		if err != nil {
			return err
		}
	}
	return nil
}
