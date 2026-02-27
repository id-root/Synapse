package gui

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

const historyFileName = "history.json"

// HistoryEntry represents a single transfer record
type HistoryEntry struct {
	ID        string `json:"id"`
	FileName  string `json:"file_name"`
	FileSize  int64  `json:"file_size"`
	Direction string `json:"direction"` // "send" or "receive"
	PeerName  string `json:"peer_name"`
	Status    string `json:"status"` // "completed", "failed"
	Error     string `json:"error,omitempty"`
	Timestamp string `json:"timestamp"`
}

type historyStore struct {
	mu      sync.RWMutex
	entries []HistoryEntry
}

var store = &historyStore{}

func loadHistory() []HistoryEntry {
	store.mu.Lock()
	defer store.mu.Unlock()

	dir, err := configDir()
	if err != nil {
		return nil
	}

	data, err := os.ReadFile(filepath.Join(dir, historyFileName))
	if err != nil {
		return nil
	}

	var entries []HistoryEntry
	if err := json.Unmarshal(data, &entries); err != nil {
		return nil
	}

	store.entries = entries
	return entries
}

func addHistoryEntry(entry HistoryEntry) error {
	store.mu.Lock()
	defer store.mu.Unlock()

	if entry.ID == "" {
		entry.ID = fmt.Sprintf("%d", time.Now().UnixNano())
	}
	if entry.Timestamp == "" {
		entry.Timestamp = time.Now().Format(time.RFC3339)
	}

	store.entries = append([]HistoryEntry{entry}, store.entries...)

	// Keep max 100 entries
	if len(store.entries) > 100 {
		store.entries = store.entries[:100]
	}

	return saveHistory()
}

func saveHistory() error {
	dir, err := configDir()
	if err != nil {
		return fmt.Errorf("failed to get config dir: %w", err)
	}

	data, err := json.MarshalIndent(store.entries, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal history: %w", err)
	}

	return os.WriteFile(filepath.Join(dir, historyFileName), data, 0644)
}
