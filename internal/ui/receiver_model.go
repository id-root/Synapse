package ui

import (
	"context"
	"fmt"
	"time"

	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/example/landrop/internal/discovery"
	"github.com/example/landrop/pkg/ui" // Import the shared styles
	"github.com/grandcat/zeroconf"
)

type sessionState int

const (
	stateScanning sessionState = iota
	stateSelecting
	stateTransferring
	stateDone
	stateError
)

type peerItem struct {
	entry *zeroconf.ServiceEntry
}

func (i peerItem) Title() string       { return i.entry.Instance }
func (i peerItem) Description() string { 
	if len(i.entry.AddrIPv4) > 0 {
		return fmt.Sprintf("%s:%d", i.entry.AddrIPv4[0], i.entry.Port)
	}
	return "Unknown Address"
}
func (i peerItem) FilterValue() string { return i.entry.Instance }

type Model struct {
	state      sessionState
	spinner    spinner.Model
	list       list.Model
	peers      []*zeroconf.ServiceEntry
	selected   *zeroconf.ServiceEntry
	err        error
	width      int
	height     int
	cancelScan context.CancelFunc
}

func NewReceiverModel() Model {
	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = lipgloss.NewStyle().Foreground(lipgloss.Color("205"))

	l := list.New([]list.Item{}, list.NewDefaultDelegate(), 0, 0)
	l.Title = "Select a Peer"
	l.SetShowStatusBar(false)

	return Model{
		state:   stateScanning,
		spinner: s,
		list:    l,
	}
}

func (m Model) Init() tea.Cmd {
	return tea.Batch(
		m.spinner.Tick,
		scanPeersCmd,
	)
}

func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.list.SetSize(msg.Width, msg.Height-4) // Adjust for margin

	case tea.KeyMsg:
		switch msg.String() {
		case "q", "ctrl+c":
			if m.cancelScan != nil {
				m.cancelScan()
			}
			return m, tea.Quit
		}

	case peersFoundMsg:
		m.peers = msg.peers
		items := make([]list.Item, len(m.peers))
		for i, p := range m.peers {
			items[i] = peerItem{entry: p}
		}
		m.list.SetItems(items)
		m.state = stateSelecting
		return m, nil

	case errMsg:
		m.err = msg.err
		m.state = stateError
		return m, tea.Quit // Or just show error
	}

	// State specific update
	switch m.state {
	case stateScanning:
		m.spinner, cmd = m.spinner.Update(msg)
		return m, cmd

	case stateSelecting:
		switch msg := msg.(type) {
		case tea.KeyMsg:
			if msg.String() == "enter" {
				i, ok := m.list.SelectedItem().(peerItem)
				if ok {
					m.selected = i.entry
					m.state = stateTransferring
					// We need to quit Bubble Tea to let the transfer function handle stdout/progress bar
					// Or we could run transfer in a command. 
					// The requirements say "Allow the user to navigate... and press Enter to connect."
					// And "Implement a rich TUI".
					// But `transfer.ReceiveConnect` uses `progressbar/v3` which writes to stdout.
					// If we stay in Bubble Tea, we should capture progress.
					// BUT, existing `transfer.ReceiveConnect` logic is synchronous and writes directly.
					// Easiest path: Quit Bubble Tea, then run Connect.
					return m, tea.Quit
				}
			}
		}
		m.list, cmd = m.list.Update(msg)
		return m, cmd
	}

	return m, nil
}

func (m Model) View() string {
	if m.err != nil {
		return fmt.Sprintf("\n%s\n", ui.Render("Error: "+m.err.Error()))
	}

	switch m.state {
	case stateScanning:
		return fmt.Sprintf("\n %s Scanning for peers...\n\n", m.spinner.View())

	case stateSelecting:
		return "\n" + m.list.View()

	case stateTransferring:
		return fmt.Sprintf("\nConnecting to %s...\n", m.selected.Instance)
	
	default:
		return ""
	}
}

// Commands and Messages

type peersFoundMsg struct {
	peers []*zeroconf.ServiceEntry
}

type errMsg struct{ err error }

func scanPeersCmd() tea.Msg {
	// Scan for 2 seconds
	entries := make(chan *zeroconf.ServiceEntry)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	go func() {
		if err := discovery.Browse(ctx, entries); err != nil {
			// In a real app we might send an error msg, but browse can fail if no listeners
		}
	}()

	var peers []*zeroconf.ServiceEntry
	for entry := range entries {
		peers = append(peers, entry)
	}

	if len(peers) == 0 {
		return errMsg{err: fmt.Errorf("no peers found")}
	}

	return peersFoundMsg{peers: peers}
}

// Helper to get selected peer after model quits
func (m Model) GetSelectedPeer() *zeroconf.ServiceEntry {
	return m.selected
}
