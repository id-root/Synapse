import { useState, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  UploadCloud, FolderOpen, X, File, Image, FileText,
  Video, Archive, Code, Folder, Play, StopCircle
} from 'lucide-react'
import { useToast } from '../hooks/useToast'
import styles from './SendTab.module.css'

function getFileIcon(name, isDir) {
  if (isDir) return { Icon: Folder, color: '#f59e0b' }
  const ext = name.split('.').pop().toLowerCase()
  const map = {
    jpg: { Icon: Image,    color: '#3b82f6' },
    jpeg:{ Icon: Image,    color: '#3b82f6' },
    png: { Icon: Image,    color: '#3b82f6' },
    gif: { Icon: Image,    color: '#3b82f6' },
    webp:{ Icon: Image,    color: '#3b82f6' },
    svg: { Icon: Image,    color: '#3b82f6' },
    pdf: { Icon: FileText, color: '#ef4444' },
    doc: { Icon: FileText, color: '#ef4444' },
    docx:{ Icon: FileText, color: '#ef4444' },
    txt: { Icon: FileText, color: '#ef4444' },
    md:  { Icon: FileText, color: '#ef4444' },
    mp4: { Icon: Video,    color: '#a855f7' },
    avi: { Icon: Video,    color: '#a855f7' },
    mkv: { Icon: Video,    color: '#a855f7' },
    zip: { Icon: Archive,  color: '#eab308' },
    rar: { Icon: Archive,  color: '#eab308' },
    '7z':{ Icon: Archive,  color: '#eab308' },
    gz:  { Icon: Archive,  color: '#eab308' },
    go:  { Icon: Code,     color: '#22c55e' },
    js:  { Icon: Code,     color: '#22c55e' },
    ts:  { Icon: Code,     color: '#22c55e' },
    py:  { Icon: Code,     color: '#22c55e' },
    rs:  { Icon: Code,     color: '#22c55e' },
  }
  return map[ext] || { Icon: File, color: '#64748b' }
}

function formatBytes(bytes) {
  if (!bytes || isNaN(bytes) || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0, size = Number(bytes)
  while (size >= 1000 && i < units.length - 1) { size /= 1000; i++ }
  return `${size.toFixed(1)} ${units[i]}`
}

export default function SendTab({ onSendingStart, onSendingStop, isSending, senderPort }) {
  const [selectedFiles, setSelectedFiles] = useState([])
  const [dragOver, setDragOver] = useState(false)
  const { showToast } = useToast()

  const addFile = (fileInfo) => {
    setSelectedFiles(prev => {
      if (prev.find(f => f.path === fileInfo.path)) return prev
      return [...prev, fileInfo]
    })
  }

  const removeFile = (idx) => {
    setSelectedFiles(prev => prev.filter((_, i) => i !== idx))
  }

  const browseFiles = async (e) => {
    e.stopPropagation()
    try {
      const files = await window.go.gui.App.SelectFiles()
      if (files?.length > 0) {
        for (const fp of files) {
          const info = await window.go.gui.App.GetFileInfo(fp)
          addFile(info)
        }
      }
    } catch (e) { showToast('error', `File select error: ${e}`) }
  }

  const browseFolder = async (e) => {
    e.stopPropagation()
    try {
      const dir = await window.go.gui.App.SelectFolder()
      if (dir) {
        const info = await window.go.gui.App.GetFileInfo(dir)
        addFile(info)
      }
    } catch (e) { showToast('error', `Folder select error: ${e}`) }
  }

  const startSend = async () => {
    if (selectedFiles.length === 0) { showToast('error', 'No files selected'); return }
    try {
      const paths = selectedFiles.map(f => f.path)
      await window.go.gui.App.StartSending(paths)
      const label = selectedFiles.length === 1 ? `"${selectedFiles[0].name}"` : `${selectedFiles.length} items`
      showToast('success', `Now sharing ${label} on the network`)
      onSendingStart?.()
    } catch (e) { showToast('error', `Failed to start: ${e}`) }
  }

  const stopSend = () => {
    window.go.gui.App.StopSending()
    showToast('info', 'Stopped sharing')
    onSendingStop?.()
  }

  return (
    <div className={styles.tab}>
      <div className="section-header">
        <h2>Send Files</h2>
        <p className="text-secondary">Select files or folders to share on your local network.</p>
      </div>

      {/* Drop Zone */}
      <motion.div
        className={`${styles.dropZone} ${dragOver ? styles.dragOver : ''}`}
        onClick={browseFiles}
        onDragOver={e => { e.preventDefault(); setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={e => { e.preventDefault(); setDragOver(false); showToast('info', 'Use Browse button to select files') }}
        animate={dragOver ? { scale: 1.01 } : { scale: 1 }}
        transition={{ type: 'spring', stiffness: 300, damping: 25 }}
      >
        <motion.div
          animate={dragOver ? { y: -4, scale: 1.1 } : { y: 0, scale: 1 }}
          transition={{ type: 'spring', stiffness: 300 }}
        >
          <UploadCloud size={48} className={styles.dropIcon} />
        </motion.div>
        <h3>Drag & Drop files here</h3>
        <p className="text-secondary">or click to browse from your computer</p>
        <div className={styles.dropActions}>
          <button className="btn btn-primary" onClick={browseFiles}>
            <File size={16} /> Browse Files
          </button>
          <button className="btn btn-secondary" onClick={browseFolder}>
            <FolderOpen size={16} /> Select Folder
          </button>
        </div>
      </motion.div>

      {/* File List */}
      <AnimatePresence>
        {selectedFiles.length > 0 && (
          <motion.div
            className={styles.fileSection}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 10 }}
          >
            <div className={styles.fileHeader}>
              <span className="text-secondary text-sm">Ready to Send</span>
              <span className="badge badge-accent">{selectedFiles.length} file{selectedFiles.length > 1 ? 's' : ''}</span>
            </div>
            <div className={styles.fileList}>
              <AnimatePresence mode="popLayout">
                {selectedFiles.map((file, i) => {
                  const { Icon, color } = getFileIcon(file.name, file.is_dir)
                  return (
                    <motion.div
                      key={file.path}
                      className={styles.fileItem}
                      layout
                      initial={{ opacity: 0, x: -16 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: 16 }}
                      transition={{ type: 'spring', stiffness: 350, damping: 30 }}
                    >
                      <div className={styles.fileIcon} style={{ color, background: `${color}18` }}>
                        <Icon size={20} />
                      </div>
                      <div className={styles.fileDetails}>
                        <span className={styles.fileName}>{file.name}</span>
                        <span className={`${styles.fileSize} font-mono`}>{formatBytes(file.size)}</span>
                      </div>
                      <button className="icon-btn danger" onClick={() => removeFile(i)}>
                        <X size={15} />
                      </button>
                    </motion.div>
                  )
                })}
              </AnimatePresence>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Action Bar */}
      <AnimatePresence>
        {selectedFiles.length > 0 && (
          <motion.div
            className={styles.actionBar}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 10 }}
          >
            {isSending ? (
              <div className={styles.sendingStatus}>
                <div className={styles.pulseWrap}>
                  <motion.div
                    className={styles.pulseRing}
                    animate={{ scale: [1, 1.8, 1], opacity: [0.6, 0, 0.6] }}
                    transition={{ duration: 1.8, repeat: Infinity }}
                  />
                  <motion.div
                    className={styles.pulseDot}
                    animate={{ scale: [1, 1.15, 1] }}
                    transition={{ duration: 1.8, repeat: Infinity }}
                  />
                </div>
                <div>
                  <div className={styles.sendingLabel}>Waiting for receiver on</div>
                  <div className={`${styles.portLabel} font-mono`}>Port {senderPort || '—'}</div>
                </div>
                <button className="btn btn-danger btn-sm" onClick={stopSend}>
                  <StopCircle size={15} /> Stop
                </button>
              </div>
            ) : (
              <div className={styles.readyBar}>
                <span className="text-secondary text-sm">Ready to broadcast on LAN</span>
                <button className="btn btn-primary" onClick={startSend}>
                  <Play size={16} /> Start Sending
                </button>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
