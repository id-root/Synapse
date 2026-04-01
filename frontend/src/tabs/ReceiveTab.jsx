import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Search, Monitor, WifiOff, RefreshCw, Download } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import styles from './ReceiveTab.module.css'

function RadarAnimation({ scanning }) {
  const canvasRef = useRef(null)
  const animRef   = useRef(null)
  const stateRef  = useRef({ angle: 0, blips: [] })

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    const W = canvas.width, H = canvas.height
    const cx = W / 2, cy = H / 2, R = W / 2 - 4

    const draw = () => {
      const { angle, blips } = stateRef.current
      ctx.clearRect(0, 0, W, H)

      // Rings
      for (let i = 1; i <= 3; i++) {
        ctx.beginPath()
        ctx.arc(cx, cy, (R * i) / 3, 0, Math.PI * 2)
        ctx.strokeStyle = 'rgba(99,102,241,0.15)'
        ctx.lineWidth = 1
        ctx.stroke()
      }

      // Grid lines
      ctx.strokeStyle = 'rgba(99,102,241,0.08)'
      ctx.lineWidth = 0.5
      ctx.beginPath(); ctx.moveTo(cx - R, cy); ctx.lineTo(cx + R, cy); ctx.stroke()
      ctx.beginPath(); ctx.moveTo(cx, cy - R); ctx.lineTo(cx, cy + R); ctx.stroke()

      if (scanning) {
        // Sweep cone
        const grad = ctx.createConicalGradient
          ? null
          : (() => {
              const g = ctx.createLinearGradient(cx, cy, cx + R, cy)
              g.addColorStop(0, 'rgba(99,102,241,0)')
              g.addColorStop(1, 'rgba(99,102,241,0.35)')
              return g
            })()

        ctx.save()
        ctx.translate(cx, cy)
        ctx.rotate(angle)
        ctx.beginPath()
        ctx.moveTo(0, 0)
        ctx.arc(0, 0, R, -Math.PI / 6, 0)
        ctx.closePath()
        ctx.fillStyle = 'rgba(195, 153, 114, 0.15)'
        ctx.fill()
        ctx.restore()

        // Sweep line
        ctx.save()
        ctx.translate(cx, cy)
        ctx.rotate(angle)
        ctx.beginPath()
        ctx.moveTo(0, 0)
        ctx.lineTo(R, 0)
        ctx.strokeStyle = 'rgba(195, 153, 114, 0.6)'
        ctx.lineWidth = 1.5
        ctx.stroke()
        ctx.restore()

        stateRef.current.angle += 0.03

        // Spawn blips randomly
        if (Math.random() < 0.015 && blips.length < 5) {
          const r2 = Math.random() * (R - 10) + 5
          const a2 = Math.random() * Math.PI * 2
          blips.push({ x: cx + r2 * Math.cos(a2), y: cy + r2 * Math.sin(a2), age: 0, maxAge: 80 })
        }
      }

      // Draw blips
      stateRef.current.blips = blips.filter(b => b.age < b.maxAge)
      stateRef.current.blips.forEach(b => {
        const life = 1 - b.age / b.maxAge
        ctx.beginPath()
        ctx.arc(b.x, b.y, 3 * life + 1, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(195, 153, 114,${life * 0.9})`
        ctx.fill()
        ctx.beginPath()
        ctx.arc(b.x, b.y, 8 * (1 - life) + 3, 0, Math.PI * 2)
        ctx.strokeStyle = `rgba(195, 153, 114,${life * 0.3})`
        ctx.lineWidth = 1
        ctx.stroke()
        b.age++
      })

      // Center dot
      ctx.beginPath()
      ctx.arc(cx, cy, 4, 0, Math.PI * 2)
      ctx.fillStyle = 'rgba(195, 153, 114, 0.8)'
      ctx.fill()

      animRef.current = requestAnimationFrame(draw)
    }

    draw()
    return () => cancelAnimationFrame(animRef.current)
  }, [scanning])

  return <canvas ref={canvasRef} width={200} height={200} className={styles.radarCanvas} />
}

export default function ReceiveTab() {
  const [scanning, setScanning]   = useState(false)
  const [peers, setPeers]         = useState([])
  const [scanned, setScanned]     = useState(false)
  const [connecting, setConnecting] = useState(null)
  const { showToast } = useToast()

  const scan = async () => {
    setScanning(true)
    setScanned(false)
    setPeers([])
    try {
      const result = await window.go.gui.App.ScanPeers()
      setPeers(result || [])
      setScanned(true)
      if (!result?.length) showToast('info', 'No peers found on this network')
      else showToast('success', `Found ${result.length} peer(s)`)
    } catch (e) {
      showToast('error', `Scan failed: ${e}`)
    } finally {
      setScanning(false)
    }
  }

  const connect = async (peer) => {
    setConnecting(peer.address)
    showToast('info', `Connecting to ${peer.name}...`)
    try {
      await window.go.gui.App.ConnectToReceive(peer.address)
    } catch (e) {
      showToast('error', `Connection failed: ${e}`)
    } finally {
      setConnecting(null)
    }
  }

  return (
    <div className={styles.tab}>
      <div className="section-header">
        <h2>Receive Files</h2>
        <p className="text-secondary">Discover devices sending files on your network via mDNS.</p>
      </div>

      {/* Radar */}
      <div className={styles.radarSection}>
        <div className={styles.radarWrap}>
          <RadarAnimation scanning={scanning} />
        </div>
        <div className={styles.radarInfo}>
          <p className={scanning ? styles.scanningText : styles.idleText}>
            {scanning ? 'Scanning via mDNS...' : scanned ? `Found ${peers.length} peer(s)` : 'Click scan to discover peers'}
          </p>
          <motion.button
            className={`btn ${scanning ? 'btn-secondary' : 'btn-primary'}`}
            onClick={scan}
            disabled={scanning}
            whileTap={{ scale: 0.97 }}
          >
            {scanning
              ? <><motion.span animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }} style={{ display:'inline-flex' }}><RefreshCw size={16} /></motion.span> Scanning...</>
              : <><Search size={16} /> Scan for Peers</>}
          </motion.button>
        </div>
      </div>

      {/* Peer Cards */}
      <AnimatePresence>
        {scanned && peers.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
          >
            <div className={styles.peersLabel}>
              <span className="text-secondary text-sm">Discovered Devices</span>
              <span className="badge badge-success">{peers.length} online</span>
            </div>
            <div className={styles.peerGrid}>
              {peers.map((peer, i) => (
                <motion.div
                  key={peer.address}
                  className={styles.peerCard}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.06 }}
                  whileHover={{ y: -2 }}
                >
                  <div className={styles.peerAvatar}>
                    <Monitor size={22} />
                  </div>
                  <div className={styles.peerInfo}>
                    <div className={styles.peerName}>{peer.name}</div>
                    <div className={`${styles.peerAddr} font-mono`}>{peer.address}</div>
                  </div>
                  <button
                    className="btn btn-primary btn-sm"
                    onClick={() => connect(peer)}
                    disabled={connecting === peer.address}
                  >
                    <Download size={14} />
                    {connecting === peer.address ? 'Connecting...' : 'Connect'}
                  </button>
                </motion.div>
              ))}
            </div>
          </motion.div>
        )}

        {scanned && peers.length === 0 && (
          <motion.div
            className={`${styles.emptyBox}`}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
          >
            <WifiOff size={36} style={{ color: 'var(--text-muted)', marginBottom: '0.75rem' }} />
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
              No peers found. Make sure someone is sending on the same network.
            </p>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
