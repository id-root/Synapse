package gui

import (
	"context"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/example/synapse/internal/discovery"
	"github.com/example/synapse/internal/transfer"
	"github.com/grandcat/zeroconf"
	wailsRuntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// App struct is the main GUI application
type App struct {
	ctx context.Context

	// Sender state
	senderMu     sync.Mutex
	senderCancel context.CancelFunc
	senderPort   int
	isSending    bool
	sendFiles    []string

	// Settings
	settings Settings
}

// NewApp creates a new App instance
func NewApp() *App {
	return &App{
		settings: loadSettings(),
	}
}

// Startup is called when the Wails app starts
func (a *App) Startup(ctx context.Context) {
	a.ctx = ctx
	loadHistory()
}

// DeviceInfo holds the device's network information
type DeviceInfo struct {
	Name string `json:"name"`
	IP   string `json:"ip"`
}

// GetDeviceInfo returns the current device info
func (a *App) GetDeviceInfo() DeviceInfo {
	name := a.settings.DeviceName
	if name == "" {
		name = getHostname()
	}

	ip := getLocalIP()

	return DeviceInfo{
		Name: name,
		IP:   ip,
	}
}

func getLocalIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "Unknown"
	}

	for _, addr := range addrs {
		if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
			if ipnet.IP.To4() != nil {
				return ipnet.IP.String()
			}
		}
	}
	return "Unknown"
}

// SelectFiles opens a file picker dialog and returns selected file paths
func (a *App) SelectFiles() []string {
	files, err := wailsRuntime.OpenMultipleFilesDialog(a.ctx, wailsRuntime.OpenDialogOptions{
		Title: "Select Files to Send",
	})
	if err != nil {
		return nil
	}
	return files
}

// SelectFolder opens a folder picker dialog
func (a *App) SelectFolder() string {
	dir, err := wailsRuntime.OpenDirectoryDialog(a.ctx, wailsRuntime.OpenDialogOptions{
		Title: "Select Folder to Send",
	})
	if err != nil {
		return ""
	}
	return dir
}

// FileInfo holds info about a selected file
type FileInfo struct {
	Name  string `json:"name"`
	Path  string `json:"path"`
	Size  int64  `json:"size"`
	IsDir bool   `json:"is_dir"`
}

// GetFileInfo returns info about a file path
func (a *App) GetFileInfo(path string) FileInfo {
	info, err := os.Stat(path)
	if err != nil {
		return FileInfo{Name: path}
	}

	size := info.Size()
	if info.IsDir() {
		// Calculate directory size
		size = 0
		_ = walkDirSize(path, &size)
	}

	return FileInfo{
		Name:  info.Name(),
		Path:  path,
		Size:  size,
		IsDir: info.IsDir(),
	}
}

func walkDirSize(path string, total *int64) error {
	entries, err := os.ReadDir(path)
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if entry.IsDir() {
			walkDirSize(filepath.Join(path, entry.Name()), total)
		} else {
			info, err := entry.Info()
			if err == nil {
				*total += info.Size()
			}
		}
	}
	return nil
}

// StartSending starts the file sender for the given path
func (a *App) StartSending(filePath string) error {
	a.senderMu.Lock()
	if a.isSending {
		a.senderMu.Unlock()
		return fmt.Errorf("already sending")
	}
	a.isSending = true
	a.senderMu.Unlock()

	ctx, cancel := context.WithCancel(context.Background())

	a.senderMu.Lock()
	a.senderCancel = cancel
	a.senderMu.Unlock()

	portChan := make(chan int, 1)

	go func() {
		opts := transfer.SenderOptions{
			AllowConn: func(addr string) bool {
				if a.settings.AutoAccept {
					wailsRuntime.EventsEmit(a.ctx, "connection:accepted", addr)
					return true
				}
				// Emit event and wait for response
				wailsRuntime.EventsEmit(a.ctx, "connection:request", addr)
				// For now, auto-accept (proper dialog would need a response channel)
				return true
			},
			PortChan: portChan,
			OnProgress: func(info transfer.ProgressInfo) {
				wailsRuntime.EventsEmit(a.ctx, "transfer:progress", map[string]interface{}{
					"bytes_sent":  info.BytesSent,
					"total_bytes": info.TotalBytes,
					"file_name":   info.FileName,
					"peer_addr":   info.PeerAddr,
					"direction":   "send",
				})
			},
			OnComplete: func(peerAddr string) {
				baseName := filepath.Base(filePath)
				_ = addHistoryEntry(HistoryEntry{
					FileName:  baseName,
					Direction: "send",
					PeerName:  peerAddr,
					Status:    "completed",
				})
				wailsRuntime.EventsEmit(a.ctx, "transfer:complete", map[string]interface{}{
					"file_name": baseName,
					"peer_addr": peerAddr,
					"direction": "send",
				})
			},
			OnError: func(peerAddr string, err error) {
				baseName := filepath.Base(filePath)
				_ = addHistoryEntry(HistoryEntry{
					FileName:  baseName,
					Direction: "send",
					PeerName:  peerAddr,
					Status:    "failed",
					Error:     err.Error(),
				})
				wailsRuntime.EventsEmit(a.ctx, "transfer:error", map[string]interface{}{
					"error":     err.Error(),
					"peer_addr": peerAddr,
					"direction": "send",
				})
			},
			Ctx: ctx,
		}

		if err := transfer.StartSenderWithOptions(filePath, opts); err != nil {
			wailsRuntime.EventsEmit(a.ctx, "sender:error", err.Error())
		}

		a.senderMu.Lock()
		a.isSending = false
		a.senderCancel = nil
		a.senderMu.Unlock()
		wailsRuntime.EventsEmit(a.ctx, "sender:stopped", nil)
	}()

	// Wait for port
	select {
	case port := <-portChan:
		a.senderMu.Lock()
		a.senderPort = port
		a.senderMu.Unlock()
		wailsRuntime.EventsEmit(a.ctx, "sender:started", port)
		return nil
	case <-time.After(5 * time.Second):
		cancel()
		return fmt.Errorf("timeout waiting for sender to start")
	}
}

// StopSending stops the active sender
func (a *App) StopSending() {
	a.senderMu.Lock()
	defer a.senderMu.Unlock()

	if a.senderCancel != nil {
		a.senderCancel()
		a.senderCancel = nil
		a.isSending = false
	}
}

// IsSending returns whether we are currently sending
func (a *App) IsSending() bool {
	a.senderMu.Lock()
	defer a.senderMu.Unlock()
	return a.isSending
}

// GetSenderPort returns the port the sender is listening on
func (a *App) GetSenderPort() int {
	a.senderMu.Lock()
	defer a.senderMu.Unlock()
	return a.senderPort
}

// PeerInfo holds discovered peer data
type PeerInfo struct {
	Name    string `json:"name"`
	Address string `json:"address"`
	Port    int    `json:"port"`
	IP      string `json:"ip"`
}

// ScanPeers discovers peers on the network
func (a *App) ScanPeers() []PeerInfo {
	entries := make(chan *zeroconf.ServiceEntry, 10)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	go func() {
		_ = discovery.Browse(ctx, entries)
	}()

	var peers []PeerInfo
	for entry := range entries {
		ip := ""
		if len(entry.AddrIPv4) > 0 {
			ip = entry.AddrIPv4[0].String()
		}
		peers = append(peers, PeerInfo{
			Name:    entry.Instance,
			Address: fmt.Sprintf("%s:%d", ip, entry.Port),
			Port:    entry.Port,
			IP:      ip,
		})
	}

	return peers
}

// ConnectToReceive connects to a peer to receive a file
func (a *App) ConnectToReceive(address string) error {
	downloadDir := a.settings.DownloadDir
	if downloadDir == "" {
		downloadDir = "received_files"
	}

	go func() {
		opts := transfer.ReceiverOptions{
			DownloadDir: downloadDir,
			OnProgress: func(info transfer.ProgressInfo) {
				wailsRuntime.EventsEmit(a.ctx, "transfer:progress", map[string]interface{}{
					"bytes_sent":  info.BytesSent,
					"total_bytes": info.TotalBytes,
					"file_name":   info.FileName,
					"peer_addr":   info.PeerAddr,
					"direction":   "receive",
				})
			},
			OnComplete: func(fileName string) {
				_ = addHistoryEntry(HistoryEntry{
					FileName:  fileName,
					Direction: "receive",
					PeerName:  address,
					Status:    "completed",
				})
				wailsRuntime.EventsEmit(a.ctx, "transfer:complete", map[string]interface{}{
					"file_name": fileName,
					"peer_addr": address,
					"direction": "receive",
				})
			},
			OnError: func(err error) {
				_ = addHistoryEntry(HistoryEntry{
					Direction: "receive",
					PeerName:  address,
					Status:    "failed",
					Error:     err.Error(),
				})
				wailsRuntime.EventsEmit(a.ctx, "transfer:error", map[string]interface{}{
					"error":     err.Error(),
					"peer_addr": address,
					"direction": "receive",
				})
			},
		}

		if err := transfer.ReceiveConnectWithOptions(address, opts); err != nil {
			wailsRuntime.EventsEmit(a.ctx, "transfer:error", map[string]interface{}{
				"error":     err.Error(),
				"peer_addr": address,
				"direction": "receive",
			})
		}
	}()

	return nil
}

// GetTransferHistory returns the transfer history
func (a *App) GetTransferHistory() []HistoryEntry {
	return loadHistory()
}

// GetSettings returns current settings
func (a *App) GetSettings() Settings {
	return a.settings
}

// SaveSettings saves settings
func (a *App) SaveSettings(s Settings) error {
	if err := saveSettings(s); err != nil {
		return err
	}
	a.settings = s
	return nil
}

// SelectDownloadDir opens a folder dialog for download directory
func (a *App) SelectDownloadDir() string {
	dir, err := wailsRuntime.OpenDirectoryDialog(a.ctx, wailsRuntime.OpenDialogOptions{
		Title: "Select Download Directory",
	})
	if err != nil {
		return ""
	}
	return dir
}
