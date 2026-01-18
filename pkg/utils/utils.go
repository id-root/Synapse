package utils

import (
	"path/filepath"
	"strings"
)

// SanitizeFilename ensures the filename is safe to use.
// Although we handle this in receiver, having a utility function is good practice
// and satisfies the architecture requirement for pkg/utils.
func SanitizeFilename(name string) string {
	safeName := filepath.Base(name)
	if safeName == "." || safeName == "/" || strings.TrimSpace(safeName) == "" {
		return "downloaded_file"
	}
	return safeName
}
