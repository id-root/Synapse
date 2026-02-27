<p align="center">
  <a href="https://go.dev//">
    <img src="https://img.shields.io/badge/Made%20with-Go-blue.svg" alt="Made with Go">
  </a>
  <a href="https://github.com/id-root/LanDrop">
    <img src="https://img.shields.io/badge/version-2.0-blue.svg" alt="Version 2.0">
  </a>
  <a href="https://opensource.org/licenses/MIT">
    <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT">
  </a>
  <a href="https://wails.io">
    <img src="https://img.shields.io/badge/GUI-Wails%20v2-orange.svg" alt="Wails v2">
  </a>
</p>


# ⚡ Synapse

**Synapse** (formerly LanDrop) is a peer-to-peer file transfer application for Local Area Networks. It features a beautiful glassmorphic desktop GUI built with Wails v2, automatic device discovery via mDNS, and end-to-end encrypted transfers — all in a single native binary.

<p align="center">
  <img src="screenshots/send.png" alt="Send Files" width="100%">
</p>

## Screenshots

<table>
  <tr>
    <td><img src="screenshots/send.png" alt="Send Files Tab" width="100%"><br><b>Send Files</b></td>
    <td><img src="screenshots/receive.png" alt="Receive Files Tab" width="100%"><br><b>Receive Files</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/history.png" alt="Transfer History" width="100%"><br><b>Transfer History</b></td>
    <td><img src="screenshots/settings.png" alt="Settings" width="100%"><br><b>Settings</b></td>
  </tr>
</table>

## Features

- **🖥️ Native Desktop GUI** — Beautiful glassmorphic interface built with Wails v2. Single binary, no browser or Electron required.
- **📁 File & Directory Transfer** — Send individual files or entire folders (auto-zipped and streamed).
- **🔍 Zero Configuration** — Automatic peer discovery on LAN using mDNS. No IP addresses, no setup.
- **🔒 End-to-End Encrypted** — All transfers use TLS with ephemeral self-signed certificates.
- **✅ Integrity Verified** — BLAKE3 checksums verify every transfer with minimal CPU overhead.
- **⏸️ Resumable Transfers** — Detects partial files and resumes from where they left off.
- **⚡ Adaptive Compression** — Text files compressed with Zstandard; already-compressed formats sent raw.
- **📊 Real-Time Progress** — Live progress bar, speed, and percentage displayed in the GUI.
- **📜 Transfer History** — All transfers (sent and received) logged with timestamps and status.
- **⚙️ Configurable** — Device name, download directory, and auto-accept settings.
- **👥 Multi-Receiver** — Multiple receivers can download the same file simultaneously.

## Installation

### Download Pre-built Binaries

Download the latest release from [GitHub Releases](https://github.com/id-root/LanDrop/releases):

| Platform | Download |
|----------|----------|
| Linux (amd64) | `synapse-linux-amd64.tar.gz` |
| Windows (amd64) | `synapse-windows-amd64.zip` |

#### Linux Requirements
```bash
sudo apt install libgtk-3-0 libwebkit2gtk-4.1-0
```

#### Windows Requirements
- WebView2 Runtime (included in Windows 10/11)

### Build from Source

#### Prerequisites

- Go 1.21+
- [Wails CLI](https://wails.io/docs/gettingstarted/installation) v2
- Linux: `libgtk-3-dev` and `libwebkit2gtk-4.1-dev`

```bash
# Install Wails CLI
go install github.com/wailsapp/wails/v2/cmd/wails@latest

# Clone and build
git clone https://github.com/id-root/LanDrop.git
cd LanDrop

# Linux
wails build -tags webkit2_41

# Windows (on a Windows machine)
wails build
```

The binary will be at `build/bin/synapse` (or `synapse.exe` on Windows).

## Usage

### Send Files

1. Open Synapse
2. Go to **Send Files** tab (default)
3. Click **Browse Files** or **Select Folder**
4. Click **Start Sending** — the app broadcasts on your LAN
5. When a receiver connects, the transfer starts automatically

### Receive Files

1. Open Synapse on the receiving device
2. Go to **Receive Files** tab
3. Click **Scan for Peers** — discovered senders appear as cards
4. Click **Connect to Receive** on the desired peer
5. The file downloads to your configured download directory

### Settings

- **Device Name** — Customize how your device appears to peers
- **Download Directory** — Where received files are saved
- **Auto-Accept** — Automatically accept incoming connections without prompts

### Development Mode

```bash
# Hot-reload dev server
wails dev -tags webkit2_41
```

## Architecture

```
synapse/
├── main.go                    # Wails app entrypoint
├── gui/
│   ├── app.go                 # Wails-bound methods (send, receive, scan, etc.)
│   ├── settings.go            # Config persistence (~/.config/synapse/)
│   └── history.go             # Transfer history
├── frontend/
│   ├── index.html             # GUI layout
│   ├── styles.css             # Glassmorphic beige design
│   └── app.js                 # Frontend logic + Wails event bindings
├── internal/
│   ├── discovery/             # mDNS discovery (_synapse._tcp)
│   └── transfer/
│       ├── sender.go          # TLS sender with progress callbacks
│       ├── receiver.go        # TLS receiver with progress callbacks
│       ├── protocol.go        # Wire protocol (headers, chunking)
│       └── security.go        # Ephemeral TLS certificate generation
└── .github/workflows/
    └── release.yml            # CI/CD: build Linux + Windows, create release
```

### Wire Protocol

All transfers use TLS over TCP with this protocol:

1. **Header**: 8-byte length + JSON Metadata (`{"name", "size", "compression", ...}`)
2. **Request**: 8-byte length + JSON (`{"offset": ...}`) for resume support
3. **Content**: Raw or Zstd-compressed stream (chunked encoding if compressed)
4. **Footer**: BLAKE3 checksum (32 bytes on wire)

## Troubleshooting

- **"No peers found"** — Ensure both devices are on the same network. Some corporate/public WiFi blocks mDNS (multicast).
- **Firewall** — Allow incoming TCP connections and UDP multicast (port 5353).
- **Checksum Mismatch** — Retry the transfer; it will resume automatically.
- **Linux: App won't start** — Install runtime dependencies: `sudo apt install libgtk-3-0 libwebkit2gtk-4.1-0`

## License

MIT

