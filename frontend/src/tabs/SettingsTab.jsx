import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Monitor, FolderOpen, Shield, Save } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import styles from './SettingsTab.module.css'

function ToggleSwitch({ checked, onChange }) {
  return (
    <label className="toggle">
      <input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)} />
      <div className="toggle-track">
        <div className="toggle-thumb" />
      </div>
    </label>
  )
}

function SettingRow({ icon: Icon, label, description, children }) {
  return (
    <div className={styles.row}>
      <div className={styles.rowLabel}>
        <div className={styles.rowIcon}><Icon size={16} /></div>
        <div>
          <div className={styles.rowTitle}>{label}</div>
          {description && <div className={styles.rowDesc}>{description}</div>}
        </div>
      </div>
      <div className={styles.rowControl}>{children}</div>
    </div>
  )
}

export default function SettingsTab() {
  const [settings, setSettings] = useState({ device_name: '', download_dir: '', auto_accept: false, port: 0 })
  const [saving, setSaving] = useState(false)
  const { showToast } = useToast()

  useEffect(() => {
    (async () => {
      try {
        const s = await window.go.gui.App.GetSettings()
        setSettings(s || {})
      } catch (e) { console.error('Failed to load settings:', e) }
    })()
  }, [])

  const chooseDir = async () => {
    try {
      const dir = await window.go.gui.App.SelectDownloadDir()
      if (dir) setSettings(s => ({ ...s, download_dir: dir }))
    } catch (e) { showToast('error', `Dir select error: ${e}`) }
  }

  const save = async () => {
    setSaving(true)
    try {
      await window.go.gui.App.SaveSettings(settings)
      showToast('success', 'Settings saved successfully')
    } catch (e) {
      showToast('error', `Failed to save: ${e}`)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={styles.tab}>
      <div className="section-header">
        <h2>Settings</h2>
        <p className="text-secondary">Configure Synapse to your preferences.</p>
      </div>

      <motion.div
        className={styles.card}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <div className={styles.cardTitle}>Device</div>

        <SettingRow icon={Monitor} label="Device Name" description="How your device appears to peers on the network">
          <input
            className={`input ${styles.nameInput}`}
            type="text"
            value={settings.device_name || ''}
            onChange={e => setSettings(s => ({ ...s, device_name: e.target.value }))}
            placeholder="My Device"
          />
        </SettingRow>

        <div className={styles.dividerLine} />

        <SettingRow icon={FolderOpen} label="Download Directory" description="Where received files are saved">
          <div className={styles.dirRow}>
            <input
              className={`input ${styles.dirInput} font-mono`}
              type="text"
              value={settings.download_dir || ''}
              readOnly
              placeholder="/home/user/Downloads"
            />
            <button className="btn btn-secondary btn-sm" onClick={chooseDir}>
              Browse
            </button>
          </div>
        </SettingRow>

        <div className={styles.dividerLine} />

        <SettingRow
          icon={Shield}
          label="Auto-Accept Connections"
          description="Automatically receive files without a confirmation prompt"
        >
          <div className="toggle-wrap">
            <ToggleSwitch
              checked={settings.auto_accept || false}
              onChange={v => setSettings(s => ({ ...s, auto_accept: v }))}
            />
            <span className="toggle-label">{settings.auto_accept ? 'Enabled' : 'Disabled'}</span>
          </div>
        </SettingRow>
      </motion.div>

      <div className={styles.saveRow}>
        <button
          className={`btn btn-primary ${saving ? '' : ''}`}
          onClick={save}
          disabled={saving}
        >
          <Save size={16} />
          {saving ? 'Saving...' : 'Save Settings'}
        </button>
        <span className="text-sm text-muted">Changes apply on next launch</span>
      </div>
    </div>
  )
}
