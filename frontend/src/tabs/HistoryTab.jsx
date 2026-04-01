import { useEffect, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ArrowUpRight, ArrowDownLeft, Inbox, CheckCircle, XCircle, X } from 'lucide-react'
import styles from './HistoryTab.module.css'

function formatBytes(bytes) {
  if (!bytes || isNaN(bytes) || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, size = Number(bytes)
  while (size >= 1000 && i < units.length - 1) { size /= 1000; i++ }
  return `${size.toFixed(1)} ${units[i]}`
}

function formatTS(ts) {
  try {
    const d = new Date(ts), now = new Date()
    const yesterday = new Date(now); yesterday.setDate(yesterday.getDate() - 1)
    const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    if (d.toDateString() === now.toDateString()) return `Today, ${time}`
    if (d.toDateString() === yesterday.toDateString()) return `Yesterday, ${time}`
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' }) + `, ${time}`
  } catch { return ts }
}

export default function HistoryTab() {
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [selectedEntry, setSelectedEntry] = useState(null)

  useEffect(() => {
    (async () => {
      try {
        const data = await window.go.gui.App.GetTransferHistory()
        setEntries(data || [])
      } catch { setEntries([]) }
      finally { setLoading(false) }
    })()
  }, [])

  return (
    <div className={styles.tab}>
      <div className="section-header">
        <h2>Transfer History</h2>
        <p className="text-secondary">All your recent file transfers, sent and received.</p>
      </div>

      {loading ? (
        <div className={styles.loadingRows}>
          {[1,2,3].map(i => <div key={i} className={styles.skeleton} />)}
        </div>
      ) : entries.length === 0 ? (
        <motion.div className="empty-state" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
          <Inbox size={52} />
          <h3 style={{ color: 'var(--text-secondary)' }}>No transfers yet</h3>
          <p>Your sent and received files will appear here.</p>
        </motion.div>
      ) : (
        <div className={styles.list}>
          <AnimatePresence>
            {entries.map((e, i) => {
              const isReceive = e.direction === 'receive'
              const ok = e.status === 'completed'
              return (
                <motion.div
                  key={`${e.id || e.file_name}-${i}`}
                  className={styles.item}
                  initial={{ opacity: 0, x: -12 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.04 }}
                  onClick={() => setSelectedEntry(e)}
                  style={{ cursor: 'pointer' }}
                >
                  <div className={`${styles.dirIcon} ${isReceive ? styles.receive : styles.send}`}>
                    {isReceive ? <ArrowDownLeft size={16} /> : <ArrowUpRight size={16} />}
                  </div>

                  <div className={styles.info}>
                    <div className={styles.fileName}>{e.file_name || 'Unknown'}</div>
                    <div className={styles.meta}>
                      <span>{formatTS(e.timestamp)}</span>
                      <span className={styles.dot}>·</span>
                      <span>{isReceive ? 'From' : 'To'} {e.peer_name || 'Unknown'}</span>
                      {e.file_size > 0 && <>
                        <span className={styles.dot}>·</span>
                        <span className="font-mono">{formatBytes(e.file_size)}</span>
                      </>}
                    </div>
                  </div>

                  <div className={`badge ${ok ? 'badge-success' : 'badge-danger'}`}>
                    {ok ? <CheckCircle size={12} /> : <XCircle size={12} />}
                    {ok ? 'Completed' : 'Failed'}
                  </div>
                </motion.div>
              )
            })}
          </AnimatePresence>
        </div>
      )}

      {/* Details Modal */}
      <AnimatePresence>
        {selectedEntry && (
          <div className={styles.modalOverlay} onClick={() => setSelectedEntry(null)}>
            <motion.div 
              className={styles.modalContent}
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              onClick={e => e.stopPropagation()}
            >
              <div className={styles.modalHeader}>
                <h3>Transfer Details</h3>
                <button className="icon-btn" onClick={() => setSelectedEntry(null)}>
                  <X size={16} />
                </button>
              </div>
              <div className={styles.modalBody}>
                <div className={styles.detailRow}>
                  <span className={styles.detailLabel}>File Name</span>
                  <span className={styles.detailValue}>{selectedEntry.file_name || 'Unknown'}</span>
                </div>
                <div className={styles.detailRow}>
                  <span className={styles.detailLabel}>Network Direction</span>
                  <span className={styles.detailValue} style={{ textTransform: 'capitalize' }}>{selectedEntry.direction}</span>
                </div>
                <div className={styles.detailRow}>
                  <span className={styles.detailLabel}>Peer Device</span>
                  <span className={styles.detailValue}>{selectedEntry.peer_name || 'Unknown'}</span>
                </div>
                <div className={styles.detailRow}>
                  <span className={styles.detailLabel}>Timestamp</span>
                  <span className={styles.detailValue}>{new Date(selectedEntry.timestamp).toLocaleString()}</span>
                </div>
                {selectedEntry.file_size > 0 && (
                  <div className={styles.detailRow}>
                    <span className={styles.detailLabel}>Total Size</span>
                    <span className={`${styles.detailValue} font-mono`}>{formatBytes(selectedEntry.file_size)}</span>
                  </div>
                )}
                <div className={styles.detailRow}>
                  <span className={styles.detailLabel}>Final Status</span>
                  <span className={styles.detailValue} style={{ 
                    color: selectedEntry.status === 'completed' ? 'var(--success)' : 'var(--danger)',
                    textTransform: 'capitalize',
                    fontWeight: 600
                  }}>
                    {selectedEntry.status}
                  </span>
                </div>
                {selectedEntry.error && (
                  <div className={styles.detailRow} style={{ marginTop: '1rem', flexWrap: 'wrap' }}>
                    <span className={styles.detailLabel}>Error Reason</span>
                    <div className={styles.errorLog}>{selectedEntry.error}</div>
                  </div>
                )}
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  )
}

