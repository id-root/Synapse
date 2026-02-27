package transfer

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestTransferIntegration(t *testing.T) {
	// Create a dummy file
	tmpDir := t.TempDir()
	srcFile := filepath.Join(tmpDir, "test_file.txt")
	content := []byte("Hello, this is a test file for LanDrop integration testing! " +
		"It should be long enough to be interesting but small enough to be fast.")

	if err := os.WriteFile(srcFile, content, 0644); err != nil {
		t.Fatalf("Failed to create source file: %v", err)
	}

	// Create a channel to get the port
	portChan := make(chan int)

	// Start Sender in a goroutine
	go func() {
		// Auto-approve all connections
		allowConn := func(addr string) bool {
			return true
		}
		if err := StartSender(srcFile, allowConn, portChan); err != nil {
			// This might happen if listener fails or we stop it (but we don't stop it here)
			// t.Errorf("StartSender failed: %v", err) // Data race if we access t here after test ends?
		}
	}()

	// Wait for port
	var port int
	select {
	case p := <-portChan:
		port = p
	case <-time.After(5 * time.Second):
		t.Fatalf("Timed out waiting for sender to start")
	}

	t.Logf("Sender started on port %d", port)

	// Connect Receiver
	// We need to change CWD to tmpDir so the received file goes there (ReceiveConnect writes to CWD)
	// But ReceiveConnect writes to CWD.
	// We should switch to a different directory for receiving.
	recvDir := filepath.Join(tmpDir, "received")
	if err := os.Mkdir(recvDir, 0755); err != nil {
		t.Fatalf("Failed to create recv dir: %v", err)
	}

	wd, err := os.Getwd()
	if err != nil {
		t.Fatalf("Failed to get wd: %v", err)
	}
	defer os.Chdir(wd) // Restore WD

	if err := os.Chdir(recvDir); err != nil {
		t.Fatalf("Failed to chdir: %v", err)
	}

	address := fmt.Sprintf("127.0.0.1:%d", port)
	if err := ReceiveConnect(address); err != nil {
		t.Fatalf("ReceiveConnect failed: %v", err)
	}

	// Verify file - receiver saves to received_files/ subdirectory
	destFile := filepath.Join(recvDir, "received_files", "test_file.txt")
	receivedContent, err := os.ReadFile(destFile)
	if err != nil {
		t.Fatalf("Failed to read received file: %v", err)
	}

	if string(receivedContent) != string(content) {
		t.Errorf("Content mismatch.\nExpected: %s\nGot:      %s", content, receivedContent)
	}
}
