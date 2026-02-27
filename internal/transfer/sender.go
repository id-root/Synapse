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

	"github.com/example/synapse/internal/discovery"
	"github.com/example/synapse/pkg/ui"
	"github.com/klauspost/compress/zstd"
	"github.com/schollz/progressbar/v3"
	"github.com/zeebo/blake3"
)

// ProgressInfo holds progress data for callbacks
type ProgressInfo struct {
	BytesSent  int64
	TotalBytes int64
	FileName   string
	Speed      float64 // bytes per second
	PeerAddr   string
}

// SenderOptions configures the sender behavior
type SenderOptions struct {
	AllowConn  func(string) bool
	PortChan   chan<- int
	OnProgress func(ProgressInfo)
	OnComplete func(peerAddr string)
	OnError    func(peerAddr string, err error)
	Ctx        context.Context
}

// StartSender starts the file transfer process as a sender.
// It listens on a random TCP port, announces itself via mDNS,
// and waits for receivers to connect.
func StartSender(inputPath string, allowConn func(string) bool, portChan chan<- int) error {
	opts := SenderOptions{
		AllowConn: allowConn,
		PortChan:  portChan,
		Ctx:       context.Background(),
	}
	return StartSenderWithOptions(inputPath, opts)
}

// StartSenderWithOptions starts the sender with extended options for GUI support
func StartSenderWithOptions(inputPath string, opts SenderOptions) error {
	fileInfo, err := os.Stat(inputPath)
	if err != nil {
		return fmt.Errorf("failed to get file info: %w", err)
	}

	isDir := fileInfo.IsDir()
	var fileSize int64
	var sourcePath string
	var cleanup func()

	if isDir {
		tmpFile, err := os.CreateTemp("", "synapse-*.zip")
		if err != nil {
			return fmt.Errorf("failed to create temp file: %w", err)
		}

		ui.Info("Archiving directory '%s'...", inputPath)

		if err := zipDirectory(inputPath, tmpFile); err != nil {
			tmpFile.Close()
			os.Remove(tmpFile.Name())
			return fmt.Errorf("failed to zip directory: %w", err)
		}

		stat, err := tmpFile.Stat()
		if err != nil {
			tmpFile.Close()
			os.Remove(tmpFile.Name())
			return err
		}
		fileSize = stat.Size()
		sourcePath = tmpFile.Name()
		tmpFile.Close()

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

	if opts.PortChan != nil {
		opts.PortChan <- port
	}

	// 3. Announce service
	ctx := opts.Ctx
	if ctx == nil {
		ctx = context.Background()
	}
	announceCtx, announceCancel := context.WithCancel(ctx)
	defer announceCancel()

	shutdownDiscovery, err := discovery.Announce(announceCtx, port)
	if err != nil {
		return fmt.Errorf("failed to announce service: %w", err)
	}
	defer shutdownDiscovery()

	ui.Info("Waiting for receivers to connect... (Press Ctrl+C to stop)")

	var promptMu sync.Mutex

	// Close listener when context is cancelled
	go func() {
		<-ctx.Done()
		listener.Close()
	}()

	// 4. Accept loop
	for {
		conn, err := listener.Accept()
		if err != nil {
			// Context cancelled or listener closed
			return nil
		}

		go func(c net.Conn) {
			defer c.Close()

			promptMu.Lock()
			approved := opts.AllowConn(c.RemoteAddr().String())
			promptMu.Unlock()

			if !approved {
				ui.Info("Connection rejected.")
				return
			}

			ui.Success("Starting transfer to %s", c.RemoteAddr())
			transferOpts := transferOptions{
				onProgress: opts.OnProgress,
				peerAddr:   c.RemoteAddr().String(),
			}
			if err := handleTransfer(c, inputPath, sourcePath, fileSize, isDir, transferOpts); err != nil {
				ui.Error("Transfer to %s failed: %v", c.RemoteAddr(), err)
				if opts.OnError != nil {
					opts.OnError(c.RemoteAddr().String(), err)
				}
			} else {
				ui.Success("Transfer to %s completed", c.RemoteAddr())
				if opts.OnComplete != nil {
					opts.OnComplete(c.RemoteAddr().String())
				}
			}
		}(conn)
	}
}

type transferOptions struct {
	onProgress func(ProgressInfo)
	peerAddr   string
}

func handleTransfer(conn net.Conn, originalName string, sourcePath string, fileSize int64, isDir bool, opts transferOptions) error {
	compression := getCompressionMethod(originalName, isDir)

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
		offset = 0
	}

	if offset > 0 {
		ui.Info("Resuming transfer from offset %d...", offset)
	}

	file, err := os.Open(sourcePath)
	if err != nil {
		return fmt.Errorf("failed to open source file: %w", err)
	}
	defer file.Close()

	if _, err := file.Seek(offset, 0); err != nil {
		return fmt.Errorf("failed to seek file: %w", err)
	}

	// Setup progress tracking
	bar := progressbar.DefaultBytes(
		fileSize-offset,
		"sending",
	)

	hasher := blake3.New()

	var destination io.Writer = conn
	hashedDestination := io.MultiWriter(destination, hasher)

	var contentWriter io.Writer
	var closer io.Closer

	if compression == CompressionZstd {
		chunked := NewChunkedWriter(hashedDestination)
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

	// Build a reader that tracks progress from the source file.
	// We track bytes READ from file (not bytes written to wire) for accurate progress.
	var sourceReader io.Reader

	if opts.onProgress != nil {
		// GUI mode: use callback-based progress tracking
		sourceReader = &progressReader{
			inner:    file,
			total:    fileSize,
			offset:   offset,
			fileName: filepath.Base(originalName),
			peerAddr: opts.peerAddr,
			callback: opts.onProgress,
		}
	} else {
		// CLI mode: use terminal progress bar
		sourceReader = io.TeeReader(file, bar)
	}

	buf := make([]byte, 4*1024*1024)
	if _, err := io.CopyBuffer(contentWriter, sourceReader, buf); err != nil {
		return fmt.Errorf("failed to send file content: %w", err)
	}

	if closer != nil {
		if err := closer.Close(); err != nil {
			return fmt.Errorf("failed to close writers: %w", err)
		}
	}

	checksum := hasher.Sum(nil)

	if _, err := conn.Write(checksum); err != nil {
		return fmt.Errorf("failed to send checksum: %w", err)
	}

	fmt.Println()
	return nil
}

// progressReader wraps a reader and calls a progress callback on each read
type progressReader struct {
	inner    io.Reader
	read     int64
	total    int64
	offset   int64
	fileName string
	peerAddr string
	callback func(ProgressInfo)
}

func (r *progressReader) Read(p []byte) (n int, err error) {
	n, err = r.inner.Read(p)
	r.read += int64(n)
	if r.callback != nil && n > 0 {
		r.callback(ProgressInfo{
			BytesSent:  r.read + r.offset,
			TotalBytes: r.total,
			FileName:   r.fileName,
			PeerAddr:   r.peerAddr,
		})
	}
	return n, err
}

func getCompressionMethod(filename string, isDir bool) string {
	if isDir {
		return CompressionNone
	}

	ext := strings.ToLower(filepath.Ext(filename))
	switch ext {
	case ".jpg", ".png", ".mp4", ".zip", ".iso", ".dmg", ".gz", ".zst", ".7z", ".rar":
		return CompressionNone
	case ".txt", ".log", ".json", ".md", ".go":
		return CompressionZstd
	default:
		return CompressionNone
	}
}

type compositeCloser struct {
	a io.Closer
	b io.Closer
}

func (c *compositeCloser) Close() error {
	if err := c.a.Close(); err != nil {
		c.b.Close()
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
			method := getCompressionMethod(info.Name(), false)
			if method == CompressionZstd {
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
