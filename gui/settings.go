package gui

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

const configFileName = "config.json"

// Settings holds GUI configuration
type Settings struct {
	DownloadDir string `json:"download_dir"`
	AutoAccept  bool   `json:"auto_accept"`
	Port        int    `json:"port"`
	DeviceName  string `json:"device_name"`
}

func defaultSettings() Settings {
	home, _ := os.UserHomeDir()
	return Settings{
		DownloadDir: filepath.Join(home, "Synapse-Downloads"),
		AutoAccept:  false,
		Port:        0, // 0 means random
		DeviceName:  getHostname(),
	}
}

func getHostname() string {
	name, err := os.Hostname()
	if err != nil {
		return "My Device"
	}
	return name
}

func configDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(home, ".config", "synapse")
	if err := os.MkdirAll(dir, 0755); err != nil {
		return "", err
	}
	return dir, nil
}

func loadSettings() Settings {
	dir, err := configDir()
	if err != nil {
		return defaultSettings()
	}

	data, err := os.ReadFile(filepath.Join(dir, configFileName))
	if err != nil {
		return defaultSettings()
	}

	var s Settings
	if err := json.Unmarshal(data, &s); err != nil {
		return defaultSettings()
	}

	// Set defaults for empty fields
	if s.DownloadDir == "" {
		s.DownloadDir = defaultSettings().DownloadDir
	}
	if s.DeviceName == "" {
		s.DeviceName = defaultSettings().DeviceName
	}

	return s
}

func saveSettings(s Settings) error {
	dir, err := configDir()
	if err != nil {
		return fmt.Errorf("failed to get config dir: %w", err)
	}

	data, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal settings: %w", err)
	}

	return os.WriteFile(filepath.Join(dir, configFileName), data, 0644)
}
