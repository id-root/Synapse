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

	"github.com/example/synapse/pkg/ui"
	"github.com/example/synapse/pkg/utils"
	"github.com/klauspost/compress/zstd"
	"github.com/schollz/progressbar/v3"
	"github.com/zeebo/blake3"
)

// ReceiverOptions configures the receiver behavior for GUI support
type ReceiverOptions struct {
	DownloadDir string
	OnProgress  func(ProgressInfo)
	OnComplete  func(fileName string)
	OnError     func(err error)
}

// ReceiveConnect connects to a specific peer and downloads the file/directory
func ReceiveConnect(address string) error {
	opts := ReceiverOptions{
		DownloadDir: "received_files",
	}
	return ReceiveConnectWithOptions(address, opts)
}

// ReceiveConnectWithOptions connects with extended options for GUI support
func ReceiveConnectWithOptions(address string, opts ReceiverOptions) error {
	ui.Info("Connecting to %s...", address)

	tlsConfig := &tls.Config{
		InsecureSkipVerify: true,
	}

	conn, err := tls.Dial("tcp", address, tlsConfig)
	if err != nil {
		return fmt.Errorf("failed to connect to sender: %w", err)
	}
	defer conn.Close()

	ui.Info("Waiting for sender approval...")

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

	safeName := utils.SanitizeFilename(header.Name)

	downloadDir := opts.DownloadDir
	if downloadDir == "" {
		downloadDir = "received_files"
	}
	if err := os.MkdirAll(downloadDir, 0755); err != nil {
		return fmt.Errorf("failed to create download directory: %w", err)
	}

	if header.IsArchive {
		ui.Info("Receiving directory: %s (%s)", safeName, byteCountDecimal(header.Size))
	} else {
		ui.Info("Receiving file: %s (%s)", safeName, byteCountDecimal(header.Size))
	}

	var offset int64 = 0
	var outPath string
	var destFile *os.File

	if header.IsArchive {
		destFile, err = os.CreateTemp("", "synapse-recv-*.zip")
		if err != nil {
			return fmt.Errorf("failed to create destination file: %w", err)
		}
		offset = 0
	} else {
		finalPath := filepath.Join(downloadDir, safeName)

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
		remaining := header.Size - offset
		limitedReader := io.LimitReader(conn, remaining)
		hashedReader := io.TeeReader(limitedReader, hasher)
		contentReader = hashedReader
	}

	bar := progressbar.DefaultBytes(
		header.Size-offset,
		"receiving",
	)

	// Build the destination writer with optional progress callback
	var destWriter io.Writer
	if opts.OnProgress != nil {
		pw := &recvProgressWriter{
			inner:    destFile,
			total:    header.Size,
			offset:   offset,
			fileName: safeName,
			peerAddr: address,
			callback: opts.OnProgress,
		}
		destWriter = pw
	} else {
		destWriter = io.MultiWriter(destFile, bar)
	}

	buf := make([]byte, 4*1024*1024)
	if _, err := io.CopyBuffer(destWriter, contentReader, buf); err != nil {
		return fmt.Errorf("failed to write file content: %w", err)
	}

	fmt.Println()

	receivedChecksum := make([]byte, 32)
	if _, err := io.ReadFull(conn, receivedChecksum); err != nil {
		return fmt.Errorf("failed to read checksum: %w", err)
	}

	calculatedChecksum := hasher.Sum(nil)

	if !bytes.Equal(calculatedChecksum, receivedChecksum) {
		return fmt.Errorf("checksum mismatch! File may be corrupted.\nExpected: %x\nGot:      %x", receivedChecksum, calculatedChecksum)
	}

	ui.Success("Checksum verified successfully.")

	if header.IsArchive {
		ui.Info("Extracting archive...")
		destFile.Close()

		if err := unzip(outPath, downloadDir); err != nil {
			return fmt.Errorf("failed to unzip archive: %w", err)
		}
		os.Remove(outPath)
		ui.Success("Directory received and extracted: %s", filepath.Join(downloadDir, safeName))
	} else {
		ui.Success("File received: %s", filepath.Join(downloadDir, safeName))
	}

	if opts.OnComplete != nil {
		opts.OnComplete(safeName)
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

// recvProgressWriter wraps a writer and reports progress via callback
type recvProgressWriter struct {
	inner    io.Writer
	written  int64
	total    int64
	offset   int64
	fileName string
	peerAddr string
	callback func(ProgressInfo)
}

func (w *recvProgressWriter) Write(p []byte) (n int, err error) {
	n, err = w.inner.Write(p)
	w.written += int64(n)
	if w.callback != nil && n > 0 {
		w.callback(ProgressInfo{
			BytesSent:  w.written + w.offset,
			TotalBytes: w.total,
			FileName:   w.fileName,
			PeerAddr:   w.peerAddr,
		})
	}
	return n, err
}
