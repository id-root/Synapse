package cmd

import (
	"fmt"
	"os"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/example/synapse/internal/transfer"
	localUI "github.com/example/synapse/internal/ui"
	"github.com/example/synapse/pkg/ui"
	"github.com/spf13/cobra"
)

var receiveCmd = &cobra.Command{
	Use:   "receive",
	Short: "Receive a file from a peer on the local network",
	Run: func(cmd *cobra.Command, args []string) {
		ui.PrintBanner()

		model := localUI.NewReceiverModel()
		p := tea.NewProgram(model)

		// Run the TUI
		finalModel, err := p.Run()
		if err != nil {
			ui.Error("Error running TUI: %v", err)
			os.Exit(1)
		}

		// Check if a peer was selected
		m, ok := finalModel.(localUI.Model)
		if !ok {
			ui.Error("Internal error: invalid model")
			os.Exit(1)
		}

		peer := m.GetSelectedPeer()
		if peer == nil {
			// User quit or error occurred
			os.Exit(0)
		}

		// Start transfer
		if len(peer.AddrIPv4) == 0 {
			ui.Error("Peer has no IPv4 address")
			os.Exit(1)
		}

		address := fmt.Sprintf("%s:%d", peer.AddrIPv4[0], peer.Port)
		if err := transfer.ReceiveConnect(address); err != nil {
			ui.Error("Error receiving data: %v", err)
			os.Exit(1)
		}
	},
}

func init() {
	rootCmd.AddCommand(receiveCmd)
}
