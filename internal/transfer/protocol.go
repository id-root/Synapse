package transfer

import (
	"encoding/binary"
	"io"
)

const (
	CompressionNone = "none"
	CompressionGzip = "gzip"
	CompressionZstd = "zstd"
)

// FileHeader is the metadata sent before the file content.
type FileHeader struct {
	Name        string `json:"name"`
	Size        int64  `json:"size"`
	IsArchive   bool   `json:"is_archive,omitempty"` // True if the content is a zip archive (directory transfer)
	Compression string `json:"compression,omitempty"` // "none", "gzip"
}

// TransferRequest is sent by the receiver to the sender to negotiate the transfer.
type TransferRequest struct {
	Offset int64 `json:"offset"` // Byte offset to resume from
}

// ChunkedWriter wraps an io.Writer and writes data in chunks with length headers.
// Format: [Length uint32][Data]. Length 0 indicates EOF.
type ChunkedWriter struct {
	w io.Writer
}

func NewChunkedWriter(w io.Writer) *ChunkedWriter {
	return &ChunkedWriter{w: w}
}

func (c *ChunkedWriter) Write(p []byte) (n int, err error) {
	if len(p) == 0 {
		return 0, nil
	}
	// Write length
	if err := binary.Write(c.w, binary.BigEndian, uint32(len(p))); err != nil {
		return 0, err
	}
	// Write data
	return c.w.Write(p)
}

func (c *ChunkedWriter) Close() error {
	// Write 0 length to signal EOF
	return binary.Write(c.w, binary.BigEndian, uint32(0))
}

// ChunkedReader reads data written by ChunkedWriter.
type ChunkedReader struct {
	r        io.Reader
	currChunk int64 // Bytes remaining in current chunk
	eof       bool
}

func NewChunkedReader(r io.Reader) *ChunkedReader {
	return &ChunkedReader{r: r}
}

func (c *ChunkedReader) Read(p []byte) (n int, err error) {
	if c.eof {
		return 0, io.EOF
	}

	if c.currChunk == 0 {
		// Read next chunk length
		var length uint32
		if err := binary.Read(c.r, binary.BigEndian, &length); err != nil {
			return 0, err
		}
		if length == 0 {
			c.eof = true
			return 0, io.EOF
		}
		c.currChunk = int64(length)
	}

	// Read from current chunk
	if int64(len(p)) > c.currChunk {
		p = p[:c.currChunk]
	}

	n, err = c.r.Read(p)
	c.currChunk -= int64(n)
	return n, err
}
