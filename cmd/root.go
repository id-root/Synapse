package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

var rootCmd = &cobra.Command{
	Use:   "synapse",
	Short: "Synapse is a peer-to-peer file transfer tool for LAN",
	Long:  `Synapse is a tool that allows you to transfer files between devices on the same local network without manual IP entry.`,
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Println(err)
		os.Exit(1)
	}
}
