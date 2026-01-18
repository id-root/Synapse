# LanDrop

LanDrop is a command-line tool for peer-to-peer file transfer over Local Area Networks (LAN) or Hotspots. It allows you to send files and directories between devices without manually entering IP addresses, using mDNS for automatic discovery.

## Features

*   **Interactive TUI**: A modern Terminal User Interface built with `bubbletea` and `lipgloss` for a professional experience.
*   **Directory Transfer**: Support for transferring entire directories (automatically zipped and streamed).
*   **Zero Configuration**: Automatically discovers peers on the local network using mDNS.
*   **Direct Transfer**: Streams files directly between devices via TCP (no intermediate server).
*   **Secure**:
    *   **Encryption**: All transfers are encrypted using TLS with ephemeral self-signed certificates.
    *   **Connection Approval**: Senders must explicitly approve incoming connections via an interactive prompt.
*   **Reliable**:
    *   **Integrity Verification**: **BLAKE3** checksums are calculated and verified for every transfer to ensure data integrity with minimal CPU overhead.
    *   **Resumable Transfers**: Automatically detects partial files and resumes downloads from where they left off.
*   **Performant**:
    *   **Adaptive Compression**: Automatically compresses text-based files (e.g., .txt, .json, .go) using **zstd** for high speed and ratio. Skips compression for already compressed formats (e.g., .jpg, .zip, .mp4).
    *   **Optimized Buffering**: Uses large buffers (4MB) and `io.CopyBuffer` to maximize throughput.
    *   **Multi-Receiver Support**: Allows multiple receivers to download the same file simultaneously.
*   **Cross-Platform**: Works on any system where Go can act as a CLI (Linux, macOS, Windows).

## Installation

### Prerequisites

*   Go 1.21 or higher.

### Build from Source

```bash
git clone https://github.com/example/landrop.git
cd landrop
go mod tidy
go build -o landrop .
```

## Usage

### 1. Send a File or Directory

On the sender device, run:

```bash
./landrop send <path-to-file-or-directory>
```

Example:
```bash
./landrop send my-photo.jpg
# or
./landrop send my-project-folder/
```

The sender will start listening on a random port and broadcast its presence on the network. If a directory is selected, it will be archived on the fly.

**Note:** When a receiver attempts to connect, you will be prompted to approve the connection:
```
Incoming connection from 192.168.1.45:54321. Accept? (y/n):
```

### 2. Receive a File

On the receiver device (must be on the same WiFi/LAN), run:

```bash
./landrop receive
```

The receiver will:
1.  Scan for available senders (showing a spinner).
2.  Display a list of discovered peers.
3.  Allow you to select a peer using arrow keys and press **Enter**.
4.  Negotiate resume offset (if partial file exists).
5.  Download, verify integrity, and (if applicable) extract the content.

## Architecture

*   **UI**: Built with Charm libraries (`lipgloss`, `bubbletea`).
*   **Discovery**: Uses `_landrop._tcp` mDNS service via `zeroconf`.
*   **Transport**: TLS over TCP with a custom negotiation protocol:
    1.  **Header**: Length (8 bytes) + JSON Metadata (`{"name": "...", "size": ..., "compression": "..."}`)
    2.  **Request**: Length (8 bytes) + JSON Request (`{"offset": ...}`)
    3.  **Content**: Raw or Compressed Stream (Chunked encoding if compressed)
    4.  **Footer**: BLAKE3 Checksum (32 bytes)
*   **Language**: Go (Golang).
*   **CLI**: Built with `cobra`.

## Troubleshooting

*   **"No senders found"**: Ensure both devices are on the same network subnet. Some corporate or public WiFi networks block mDNS (multicast).
*   **Firewall**: Ensure your firewall allows incoming TCP connections and UDP multicast (port 5353).
*   **Checksum Mismatch**: If a transfer fails integrity verification, retry the transfer (it will resume automatically).
