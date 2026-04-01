import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle, XCircle, Info, X } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import styles from './Toast.module.css'

const icons = {
  success: <CheckCircle size={18} />,
  error:   <XCircle size={18} />,
  info:    <Info size={18} />,
}

const colors = {
  success: 'var(--success)',
  error:   'var(--danger)',
  info:    'var(--info)',
}

export default function Toast() {
  const { toasts } = useToast()

  return (
    <div className={styles.container}>
      <AnimatePresence mode="popLayout">
        {toasts.map(toast => (
          <motion.div
            key={toast.id}
            layout
            initial={{ opacity: 0, x: 60, scale: 0.94 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: 60, scale: 0.94 }}
            transition={{ type: 'spring', stiffness: 400, damping: 30 }}
            className={styles.toast}
            style={{ '--toast-color': colors[toast.type] }}
          >
            <span className={styles.icon} style={{ color: colors[toast.type] }}>
              {icons[toast.type]}
            </span>
            <span className={styles.message}>{toast.message}</span>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  )
}
