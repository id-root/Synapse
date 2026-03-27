import { motion } from 'framer-motion'
import {
  UploadCloud, DownloadCloud, History, Settings, Zap, Wifi
} from 'lucide-react'
import styles from './Sidebar.module.css'

const navItems = [
  { id: 'send',     label: 'Send Files',       icon: UploadCloud },
  { id: 'receive',  label: 'Receive Files',     icon: DownloadCloud },
  { id: 'history',  label: 'Transfer History',  icon: History },
  { id: 'settings', label: 'Settings',          icon: Settings },
]

export default function Sidebar({ activeTab, onTabChange, deviceInfo }) {
  return (
    <motion.aside
      className={styles.sidebar}
      initial={{ x: -20, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      transition={{ duration: 0.4, ease: [0.4, 0, 0.2, 1] }}
    >
      {/* Logo */}
      <div className={styles.logo}>
        <motion.div
          className={styles.logoIcon}
          animate={{ boxShadow: ['0 0 16px rgba(99,102,241,0.3)', '0 0 28px rgba(99,102,241,0.5)', '0 0 16px rgba(99,102,241,0.3)'] }}
          transition={{ duration: 3, repeat: Infinity, ease: 'easeInOut' }}
        >
          <Zap size={22} strokeWidth={2.5} />
        </motion.div>
        <div>
          <h1 className={styles.logoText}>Synapse</h1>
          <p className={styles.logoSub}>LAN Transfer</p>
        </div>
      </div>

      {/* Nav */}
      <nav className={styles.nav}>
        {navItems.map(item => {
          const Icon = item.icon
          const isActive = activeTab === item.id
          return (
            <button
              key={item.id}
              className={`${styles.navItem} ${isActive ? styles.active : ''}`}
              onClick={() => onTabChange(item.id)}
            >
              {isActive && (
                <motion.div
                  className={styles.activePill}
                  layoutId="activePill"
                  transition={{ type: 'spring', stiffness: 400, damping: 35 }}
                />
              )}
              <span className={styles.navIcon}>
                <Icon size={18} strokeWidth={isActive ? 2.5 : 2} />
              </span>
              <span className={styles.navLabel}>{item.label}</span>
              {isActive && <span className={styles.activeDot} />}
            </button>
          )
        })}
      </nav>

      {/* Spacer */}
      <div style={{ flex: 1 }} />

      {/* Device Status */}
      <div className={styles.deviceStatus}>
        <div className={styles.statusRow}>
          <motion.div
            className={styles.statusDot}
            animate={{ scale: [1, 1.3, 1], opacity: [1, 0.7, 1] }}
            transition={{ duration: 2.5, repeat: Infinity, ease: 'easeInOut' }}
          />
          <Wifi size={13} className={styles.wifiIcon} />
        </div>
        <div className={styles.deviceInfo}>
          <span className={styles.deviceName}>{deviceInfo?.name || 'My Device'}</span>
          <span className={`${styles.deviceIp} font-mono`}>{deviceInfo?.ip || '—'}</span>
        </div>
      </div>
    </motion.aside>
  )
}
