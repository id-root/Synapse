package cmd

import (
	"fmt"
	"os"
	"strings"

	"github.com/example/landrop/internal/transfer"
	"github.com/example/landrop/pkg/ui"
	"github.com/spf13/cobra"
)

var sendCmd = &cobra.Command{
	Use:   "send [file/directory]",
	Short: "Send a file or directory to a peer on the local network",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		ui.PrintBanner()
		filePath := args[0]
		if _, err := os.Stat(filePath); os.IsNotExist(err) {
			ui.Error("File or directory '%s' does not exist", filePath)
			os.Exit(1)
		}

		ui.Info("Preparing to send '%s'...", filePath)
		
		allowConn := func(addr string) bool {
			ui.Info("Incoming connection from %s. Accept? (y/n): ", addr)
			var response string
			fmt.Scanln(&response)
			return strings.ToLower(strings.TrimSpace(response)) == "y"
		}

		// Pass nil for portChan
		if err := transfer.StartSender(filePath, allowConn, nil); err != nil {
			ui.Error("Error sending data: %v", err)
			os.Exit(1)
		}
	},
}

func init() {
	rootCmd.AddCommand(sendCmd)
}
