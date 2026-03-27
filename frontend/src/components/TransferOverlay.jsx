import { AnimatePresence, motion } from 'framer-motion'
import { X, Upload, Download, Zap } from 'lucide-react'
import styles from './TransferOverlay.module.css'

function CircularProgress({ percent }) {
  const r = 26
  const circ = 2 * Math.PI * r
  const offset = circ - (percent / 100) * circ

  return (
    <svg width="68" height="68" className={styles.ring}>
      <defs>
        <linearGradient id="progressGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="var(--accent-1)" />
          <stop offset="100%" stopColor="var(--accent-2)" />
        </linearGradient>
      </defs>
      {/* Track */}
      <circle cx="34" cy="34" r={r} fill="none" stroke="var(--bg-elevated)" strokeWidth="5" />
      {/* Progress */}
      <circle
        cx="34" cy="34" r={r}
        fill="none"
        stroke="url(#progressGrad)"
        strokeWidth="5"
        strokeLinecap="round"
        strokeDasharray={circ}
        strokeDashoffset={offset}
        transform="rotate(-90 34 34)"
        style={{ transition: 'stroke-dashoffset 0.4s cubic-bezier(0.4,0,0.2,1)' }}
      />
      <text x="34" y="34" textAnchor="middle" dominantBaseline="central"
        fill="var(--text-primary)" fontSize="12" fontWeight="600">
        {percent}%
      </text>
    </svg>
  )
}

export default function TransferOverlay({ transfer, onCancel }) {
  const { visible, title, fileName, speed, percent, bytesSent, totalBytes, direction } = transfer || {}

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          className={styles.overlay}
          initial={{ opacity: 0, y: 20, scale: 0.95 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 20, scale: 0.95 }}
          transition={{ type: 'spring', stiffness: 380, damping: 30 }}
        >
          {/* Header */}
          <div className={styles.header}>
            <div className={styles.headerLeft}>
              <div className={styles.dirIcon}>
                {direction === 'send' ? <Upload size={15} /> : <Download size={15} />}
              </div>
              <div>
                <div className={styles.title}>{title || 'Transferring...'}</div>
                <div className={styles.speed}>
                  <Zap size={11} style={{ marginRight: 3 }} />
                  {speed || '— B/s'}
                </div>
              </div>
            </div>
            <button className={`icon-btn ${styles.closeBtn}`} onClick={onCancel}>
              <X size={15} />
            </button>
          </div>

          {/* Body */}
          <div className={styles.body}>
            <CircularProgress percent={percent || 0} />
            <div className={styles.fileInfo}>
              <div className={styles.fileName} title={fileName}>{fileName || '—'}</div>
              <div className={styles.bytes}>
                {bytesSent || '0 B'} / {totalBytes || '0 B'}
              </div>
              <div className={styles.progressWrap}>
                <div className="progress-bar" style={{ marginBottom: 0 }}>
                  <div className="progress-fill" style={{ width: `${percent || 0}%` }} />
                </div>
              </div>
            </div>
          </div>

          <button className={`btn btn-danger btn-sm btn-full ${styles.cancelBtn}`} onClick={onCancel}>
            Cancel Transfer
          </button>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
