// Synapse GUI - Frontend Application Logic
// Bridges the HTML/CSS frontend to the Go backend via Wails bindings

document.addEventListener('DOMContentLoaded', () => {
    // ========================================
    // Tab Navigation
    // ========================================
    const navItems = document.querySelectorAll('.nav-item');
    const tabContents = document.querySelectorAll('.tab-content');

    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            navItems.forEach(nav => nav.classList.remove('active'));
            tabContents.forEach(tab => tab.classList.remove('active'));
            item.classList.add('active');
            const tabId = item.getAttribute('data-tab');
            const tab = document.getElementById(`${tabId}-tab`);
            if (tab) tab.classList.add('active');

            // Auto-load data when switching tabs
            if (tabId === 'history') loadHistory();
            if (tabId === 'settings') loadSettings();
            if (tabId === 'receive') { } // radar animation runs always
        });
    });

    // ========================================
    // Device Info
    // ========================================
    async function loadDeviceInfo() {
        try {
            const info = await window.go.gui.App.GetDeviceInfo();
            document.getElementById('device-name').textContent = info.name;
            document.getElementById('device-ip').textContent = info.ip;
        } catch (e) {
            console.error('Failed to load device info:', e);
        }
    }
    loadDeviceInfo();

    // ========================================
    // File Selection & Send
    // ========================================
    let selectedFiles = []; // Array of {name, path, size, is_dir}

    const uploadArea = document.getElementById('upload-area');
    const btnBrowse = document.getElementById('btn-browse-files');
    const btnFolder = document.getElementById('btn-select-folder');
    const fileListContainer = document.getElementById('selected-files-container');
    const fileListEl = document.getElementById('file-list');
    const filesHeader = document.getElementById('selected-files-header');
    const actionBar = document.getElementById('action-bar');
    const btnStartSend = document.getElementById('btn-start-send');
    const btnStopSend = document.getElementById('btn-stop-send');
    const sendingStatusBar = document.getElementById('sending-status-bar');

    btnBrowse.addEventListener('click', async (e) => {
        e.stopPropagation();
        try {
            const files = await window.go.gui.App.SelectFiles();
            if (files && files.length > 0) {
                for (const filepath of files) {
                    const info = await window.go.gui.App.GetFileInfo(filepath);
                    addFile(info);
                }
            }
        } catch (e) {
            console.error('File select error:', e);
        }
    });

    btnFolder.addEventListener('click', async (e) => {
        e.stopPropagation();
        try {
            const dir = await window.go.gui.App.SelectFolder();
            if (dir) {
                const info = await window.go.gui.App.GetFileInfo(dir);
                addFile(info);
            }
        } catch (e) {
            console.error('Folder select error:', e);
        }
    });

    // Drag & drop
    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.classList.add('drag-over');
    });

    uploadArea.addEventListener('dragleave', () => {
        uploadArea.classList.remove('drag-over');
    });

    uploadArea.addEventListener('drop', async (e) => {
        e.preventDefault();
        uploadArea.classList.remove('drag-over');
        // Note: Wails doesn't support drag-drop file paths directly from OS
        // The file dialog is the primary method
        showToast('info', 'Use the Browse button to select files');
    });

    function addFile(fileInfo) {
        // Avoid duplicates
        if (selectedFiles.find(f => f.path === fileInfo.path)) return;
        selectedFiles.push(fileInfo);
        renderFileList();
    }

    function removeFile(index) {
        selectedFiles.splice(index, 1);
        renderFileList();
    }

    function renderFileList() {
        if (selectedFiles.length === 0) {
            fileListContainer.style.display = 'none';
            actionBar.style.display = 'none';
            return;
        }

        fileListContainer.style.display = 'block';
        actionBar.style.display = 'flex';
        filesHeader.textContent = `Ready to Send (${selectedFiles.length})`;

        fileListEl.innerHTML = '';
        selectedFiles.forEach((file, index) => {
            const item = document.createElement('div');
            item.className = 'file-item glass-card';
            const iconInfo = getFileIcon(file.name, file.is_dir);
            item.innerHTML = `
                <div class="file-icon ${iconInfo.bg}"><i class="${iconInfo.icon}"></i></div>
                <div class="file-details">
                    <span class="file-name">${escapeHtml(file.name)}</span>
                    <span class="file-size">${formatBytes(file.size)}</span>
                </div>
                <button class="icon-btn" data-index="${index}"><i class="ri-close-line"></i></button>
            `;
            item.querySelector('.icon-btn').addEventListener('click', () => removeFile(index));
            fileListEl.appendChild(item);
        });
    }

    // Start sending
    btnStartSend.addEventListener('click', async () => {
        if (selectedFiles.length === 0) {
            showToast('error', 'No files selected');
            return;
        }

        // Send the first file (multi-file can be extended later)
        const file = selectedFiles[0];
        try {
            await window.go.gui.App.StartSending(file.path);
            showToast('success', `Now sharing "${file.name}" on the network`);
        } catch (e) {
            showToast('error', `Failed to start sending: ${e}`);
        }
    });

    // Stop sending
    btnStopSend.addEventListener('click', () => {
        window.go.gui.App.StopSending();
        sendingStatusBar.style.display = 'none';
        btnStartSend.style.display = 'inline-flex';
        btnStopSend.style.display = 'none';
        showToast('info', 'Stopped sharing');
    });

    // ========================================
    // Receive / Peer Scanning
    // ========================================
    const btnScan = document.getElementById('btn-scan-peers');
    const radarText = document.getElementById('radar-text');
    const peerGrid = document.getElementById('peer-grid');
    const peersListContainer = document.getElementById('peers-list-container');
    const noPeers = document.getElementById('no-peers');

    btnScan.addEventListener('click', async () => {
        radarText.textContent = 'Scanning for peers via mDNS...';
        btnScan.disabled = true;
        btnScan.innerHTML = '<i class="ri-loader-4-line"></i> Scanning...';
        peersListContainer.style.display = 'none';
        noPeers.style.display = 'none';

        try {
            const peers = await window.go.gui.App.ScanPeers();
            if (peers && peers.length > 0) {
                radarText.textContent = `Found ${peers.length} peer(s)`;
                renderPeers(peers);
                peersListContainer.style.display = 'block';
                noPeers.style.display = 'none';
            } else {
                radarText.textContent = 'No peers found';
                peersListContainer.style.display = 'none';
                noPeers.style.display = 'block';
            }
        } catch (e) {
            radarText.textContent = 'Scan failed';
            showToast('error', `Scan error: ${e}`);
        }

        btnScan.disabled = false;
        btnScan.innerHTML = '<i class="ri-search-line"></i> Scan for Peers';
    });

    function renderPeers(peers) {
        peerGrid.innerHTML = '';
        peers.forEach(peer => {
            const card = document.createElement('div');
            card.className = 'peer-card glass-card';
            card.innerHTML = `
                <div class="peer-header">
                    <div class="peer-avatar"><i class="ri-computer-line"></i></div>
                    <div class="peer-info">
                        <h4>${escapeHtml(peer.name)}</h4>
                        <span>${escapeHtml(peer.address)}</span>
                    </div>
                </div>
                <div class="peer-actions">
                    <button class="btn btn-primary btn-full" data-address="${escapeHtml(peer.address)}">Connect to Receive</button>
                </div>
            `;
            card.querySelector('button').addEventListener('click', () => connectToReceive(peer));
            peerGrid.appendChild(card);
        });
    }

    async function connectToReceive(peer) {
        showToast('info', `Connecting to ${peer.name}...`);
        try {
            await window.go.gui.App.ConnectToReceive(peer.address);
        } catch (e) {
            showToast('error', `Connection failed: ${e}`);
        }
    }

    // ========================================
    // Transfer History
    // ========================================
    const historyList = document.getElementById('history-list');
    const noHistory = document.getElementById('no-history');

    async function loadHistory() {
        try {
            const entries = await window.go.gui.App.GetTransferHistory();
            if (entries && entries.length > 0) {
                historyList.style.display = 'block';
                noHistory.style.display = 'none';
                renderHistory(entries);
            } else {
                historyList.style.display = 'none';
                noHistory.style.display = 'block';
            }
        } catch (e) {
            historyList.style.display = 'none';
            noHistory.style.display = 'block';
        }
    }

    function renderHistory(entries) {
        historyList.innerHTML = '';
        entries.forEach(entry => {
            const item = document.createElement('div');
            item.className = 'history-item';
            const isReceive = entry.direction === 'receive';
            const statusClass = entry.status === 'completed' ? 'success' : 'failed';
            const statusText = entry.status === 'completed' ? 'Completed' : `Failed${entry.error ? ' (' + entry.error + ')' : ''}`;
            const time = entry.timestamp ? formatTimestamp(entry.timestamp) : '';
            const peerLabel = isReceive ? `From ${entry.peer_name}` : `To ${entry.peer_name}`;

            item.innerHTML = `
                <div class="history-icon ${entry.direction}">
                    <i class="${isReceive ? 'ri-arrow-down-line' : 'ri-arrow-up-line'}"></i>
                </div>
                <div class="file-details">
                    <span class="file-name">${escapeHtml(entry.file_name || 'Unknown')}</span>
                    <span class="file-time">${time} • ${escapeHtml(peerLabel)}</span>
                </div>
                <div class="history-status ${statusClass}">${escapeHtml(statusText)}</div>
            `;
            historyList.appendChild(item);
        });
    }

    // ========================================
    // Settings
    // ========================================
    const settingDeviceName = document.getElementById('setting-device-name');
    const settingDownloadDir = document.getElementById('setting-download-dir');
    const settingAutoAccept = document.getElementById('setting-auto-accept');
    const btnChooseDir = document.getElementById('btn-choose-dir');
    const btnSaveSettings = document.getElementById('btn-save-settings');

    async function loadSettings() {
        try {
            const s = await window.go.gui.App.GetSettings();
            settingDeviceName.value = s.device_name || '';
            settingDownloadDir.value = s.download_dir || '';
            settingAutoAccept.checked = s.auto_accept || false;
        } catch (e) {
            console.error('Failed to load settings:', e);
        }
    }
    loadSettings();

    btnChooseDir.addEventListener('click', async () => {
        try {
            const dir = await window.go.gui.App.SelectDownloadDir();
            if (dir) {
                settingDownloadDir.value = dir;
            }
        } catch (e) {
            console.error('Dir select error:', e);
        }
    });

    btnSaveSettings.addEventListener('click', async () => {
        const settings = {
            device_name: settingDeviceName.value,
            download_dir: settingDownloadDir.value,
            auto_accept: settingAutoAccept.checked,
            port: 0
        };

        try {
            await window.go.gui.App.SaveSettings(settings);
            showToast('success', 'Settings saved successfully');
            loadDeviceInfo();
        } catch (e) {
            showToast('error', `Failed to save settings: ${e}`);
        }
    });

    // ========================================
    // Wails Events
    // ========================================
    if (window.runtime) {
        // Sender started
        window.runtime.EventsOn('sender:started', (port) => {
            document.getElementById('sender-port').textContent = port;
            sendingStatusBar.style.display = 'flex';
            btnStartSend.style.display = 'none';
            btnStopSend.style.display = 'inline-flex';
        });

        // Sender stopped
        window.runtime.EventsOn('sender:stopped', () => {
            sendingStatusBar.style.display = 'none';
            btnStartSend.style.display = 'inline-flex';
            btnStopSend.style.display = 'none';
        });

        // Sender error
        window.runtime.EventsOn('sender:error', (err) => {
            showToast('error', `Sender error: ${err}`);
            sendingStatusBar.style.display = 'none';
            btnStartSend.style.display = 'inline-flex';
            btnStopSend.style.display = 'none';
        });

        // Transfer progress
        window.runtime.EventsOn('transfer:progress', (data) => {
            const overlay = document.getElementById('transfer-overlay');
            overlay.style.display = 'block';

            const percent = data.total_bytes > 0
                ? Math.round((data.bytes_sent / data.total_bytes) * 100)
                : 0;

            document.getElementById('transfer-progress').style.width = `${percent}%`;
            document.getElementById('transfer-file').textContent = data.file_name || '—';
            document.getElementById('transfer-bytes').textContent =
                `${formatBytes(data.bytes_sent)} / ${formatBytes(data.total_bytes)}`;
            document.getElementById('transfer-title').textContent =
                data.direction === 'send' ? `Sending to ${data.peer_addr}` : `Receiving from ${data.peer_addr}`;
            document.getElementById('transfer-eta').textContent = `${percent}%`;
        });

        // Transfer complete
        window.runtime.EventsOn('transfer:complete', (data) => {
            const overlay = document.getElementById('transfer-overlay');
            document.getElementById('transfer-progress').style.width = '100%';
            showToast('success', `Transfer complete: ${data.file_name}`);
            setTimeout(() => {
                overlay.style.display = 'none';
                document.getElementById('transfer-progress').style.width = '0%';
            }, 2000);
        });

        // Transfer error
        window.runtime.EventsOn('transfer:error', (data) => {
            showToast('error', `Transfer failed: ${data.error}`);
            const overlay = document.getElementById('transfer-overlay');
            setTimeout(() => {
                overlay.style.display = 'none';
                document.getElementById('transfer-progress').style.width = '0%';
            }, 2000);
        });

        // Connection request
        window.runtime.EventsOn('connection:request', (addr) => {
            showToast('info', `Incoming connection from ${addr}`);
        });

        // Connection accepted
        window.runtime.EventsOn('connection:accepted', (addr) => {
            showToast('info', `Connection accepted from ${addr}`);
        });
    }

    // Cancel transfer button
    document.getElementById('btn-cancel-transfer').addEventListener('click', () => {
        window.go.gui.App.StopSending();
        document.getElementById('transfer-overlay').style.display = 'none';
    });

    // ========================================
    // Utility Functions
    // ========================================
    function formatBytes(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        const units = ['B', 'KB', 'MB', 'GB', 'TB'];
        let i = 0;
        let size = bytes;
        while (size >= 1000 && i < units.length - 1) {
            size /= 1000;
            i++;
        }
        return `${size.toFixed(1)} ${units[i]}`;
    }

    function getFileIcon(name, isDir) {
        if (isDir) return { icon: 'ri-folder-3-fill', bg: 'bg-folder' };
        const ext = name.split('.').pop().toLowerCase();
        const map = {
            jpg: { icon: 'ri-image-2-line', bg: 'bg-image' },
            jpeg: { icon: 'ri-image-2-line', bg: 'bg-image' },
            png: { icon: 'ri-image-2-line', bg: 'bg-image' },
            gif: { icon: 'ri-image-2-line', bg: 'bg-image' },
            svg: { icon: 'ri-image-2-line', bg: 'bg-image' },
            webp: { icon: 'ri-image-2-line', bg: 'bg-image' },
            pdf: { icon: 'ri-file-text-line', bg: 'bg-doc' },
            doc: { icon: 'ri-file-text-line', bg: 'bg-doc' },
            docx: { icon: 'ri-file-text-line', bg: 'bg-doc' },
            txt: { icon: 'ri-file-text-line', bg: 'bg-doc' },
            md: { icon: 'ri-file-text-line', bg: 'bg-doc' },
            mp4: { icon: 'ri-video-line', bg: 'bg-video' },
            avi: { icon: 'ri-video-line', bg: 'bg-video' },
            mkv: { icon: 'ri-video-line', bg: 'bg-video' },
            zip: { icon: 'ri-file-zip-line', bg: 'bg-archive' },
            rar: { icon: 'ri-file-zip-line', bg: 'bg-archive' },
            '7z': { icon: 'ri-file-zip-line', bg: 'bg-archive' },
            gz: { icon: 'ri-file-zip-line', bg: 'bg-archive' },
            go: { icon: 'ri-code-s-slash-line', bg: 'bg-code' },
            js: { icon: 'ri-code-s-slash-line', bg: 'bg-code' },
            py: { icon: 'ri-code-s-slash-line', bg: 'bg-code' },
            rs: { icon: 'ri-code-s-slash-line', bg: 'bg-code' },
            html: { icon: 'ri-code-s-slash-line', bg: 'bg-code' },
            css: { icon: 'ri-code-s-slash-line', bg: 'bg-code' },
        };
        return map[ext] || { icon: 'ri-file-3-line', bg: 'bg-default' };
    }

    function formatTimestamp(ts) {
        try {
            const d = new Date(ts);
            const now = new Date();
            const isToday = d.toDateString() === now.toDateString();
            const yesterday = new Date(now);
            yesterday.setDate(yesterday.getDate() - 1);
            const isYesterday = d.toDateString() === yesterday.toDateString();

            const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

            if (isToday) return `Today, ${time}`;
            if (isYesterday) return `Yesterday, ${time}`;
            return d.toLocaleDateString([], { month: 'short', day: 'numeric' }) + `, ${time}`;
        } catch {
            return ts;
        }
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.appendChild(document.createTextNode(text || ''));
        return div.innerHTML;
    }

    function showToast(type, message) {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;

        const icons = {
            success: 'ri-check-line',
            error: 'ri-error-warning-line',
            info: 'ri-information-line'
        };

        toast.innerHTML = `<i class="${icons[type] || icons.info}"></i><span>${escapeHtml(message)}</span>`;
        container.appendChild(toast);

        setTimeout(() => {
            toast.classList.add('removing');
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }
});
