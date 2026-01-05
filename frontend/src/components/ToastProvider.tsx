import { CheckCircle, Error, Info, Warning } from '@mui/icons-material'
import toast, { Toaster, ToastOptions } from 'react-hot-toast'

export function ToastProvider() {
  return (
    <Toaster
      position="top-right"
      toastOptions={{
        duration: 4000,
        style: {
          background: '#1e1e2e',
          color: '#fff',
          padding: '16px 20px',
          borderRadius: '12px',
          boxShadow: '0 10px 40px rgba(0, 0, 0, 0.3)',
          border: '1px solid rgba(255, 255, 255, 0.1)',
          fontSize: '14px',
          fontWeight: 500,
          maxWidth: '400px',
        },
        success: {
          iconTheme: {
            primary: '#10b981',
            secondary: '#fff',
          },
          style: {
            borderLeft: '4px solid #10b981',
          },
        },
        error: {
          iconTheme: {
            primary: '#ef4444',
            secondary: '#fff',
          },
          style: {
            borderLeft: '4px solid #ef4444',
          },
        },
      }}
    />
  )
}

// Custom toast functions with icons
export const showToast = {
  success: (message: string, options?: ToastOptions) => {
    toast.success(message, {
      ...options,
      icon: <CheckCircle sx={{ color: '#10b981' }} />,
    })
  },

  error: (message: string, options?: ToastOptions) => {
    toast.error(message, {
      ...options,
      icon: <Error sx={{ color: '#ef4444' }} />,
    })
  },

  info: (message: string, options?: ToastOptions) => {
    toast(message, {
      ...options,
      icon: <Info sx={{ color: '#3b82f6' }} />,
    })
  },

  warning: (message: string, options?: ToastOptions) => {
    toast(message, {
      ...options,
      icon: <Warning sx={{ color: '#f59e0b' }} />,
      style: {
        borderLeft: '4px solid #f59e0b',
      },
    })
  },

  custom: (message: string, options?: ToastOptions) => {
    toast(message, options)
  },
}
