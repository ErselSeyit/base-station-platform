import { CheckCircle, Error, Info, Warning } from '@mui/icons-material'
import toast, { ToastOptions } from 'react-hot-toast'

export const showToast = {
  success: (message: string, options?: ToastOptions) => {
    toast.success(message, {
      ...options,
      icon: <CheckCircle sx={{ color: 'var(--status-active)' }} />,
      style: { borderLeft: '4px solid var(--status-active)' },
    })
  },

  error: (message: string, options?: ToastOptions) => {
    toast.error(message, {
      ...options,
      icon: <Error sx={{ color: 'var(--status-offline)' }} />,
      style: { borderLeft: '4px solid var(--status-offline)' },
    })
  },

  info: (message: string, options?: ToastOptions) => {
    toast(message, {
      ...options,
      icon: <Info sx={{ color: 'var(--status-info)' }} />,
      style: { borderLeft: '4px solid var(--status-info)' },
    })
  },

  warning: (message: string, options?: ToastOptions) => {
    toast(message, {
      ...options,
      icon: <Warning sx={{ color: 'var(--status-maintenance)' }} />,
      style: { borderLeft: '4px solid var(--status-maintenance)' },
    })
  },

  custom: (message: string, options?: ToastOptions) => {
    toast(message, options)
  },
}
