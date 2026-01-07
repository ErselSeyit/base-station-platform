import { Toaster } from 'react-hot-toast'

export function ToastProvider() {
  return (
    <Toaster
      position="top-right"
      toastOptions={{
        duration: 4000,
        style: {
          background: 'var(--surface-elevated)',
          color: 'var(--mono-950)',
          padding: '16px 20px',
          borderRadius: '12px',
          boxShadow: 'var(--shadow-lg)',
          border: '1px solid var(--surface-border)',
          fontSize: '14px',
          fontWeight: 500,
          maxWidth: '400px',
        },
        success: {
          iconTheme: {
            primary: 'var(--status-active)',
            secondary: 'var(--surface-elevated)',
          },
          style: {
            borderLeft: '4px solid var(--status-active)',
          },
        },
        error: {
          iconTheme: {
            primary: 'var(--status-offline)',
            secondary: 'var(--surface-elevated)',
          },
          style: {
            borderLeft: '4px solid var(--status-offline)',
          },
        },
      }}
    />
  )
}
