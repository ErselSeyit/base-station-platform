import { Component, ErrorInfo, ReactNode } from 'react'
import { Box, Button, Typography } from '@mui/material'
import { Error as ErrorIcon, Refresh as RefreshIcon } from '@mui/icons-material'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error?: Error
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('ErrorBoundary caught an error:', error, errorInfo)
  }

  handleReload = (): void => {
    globalThis.location.reload()
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '100vh',
            padding: '32px',
            background: 'var(--surface-base)',
            textAlign: 'center',
          }}
        >
          <ErrorIcon
            sx={{
              fontSize: 64,
              color: 'var(--status-offline)',
              marginBottom: '24px',
            }}
          />
          <Typography
            variant="h4"
            sx={{
              fontWeight: 600,
              color: 'var(--mono-950)',
              marginBottom: '12px',
            }}
          >
            Something went wrong
          </Typography>
          <Typography
            sx={{
              color: 'var(--mono-600)',
              marginBottom: '32px',
              maxWidth: '500px',
            }}
          >
            An unexpected error occurred. Please try reloading the page.
          </Typography>
          {this.state.error && (
            <Box
              sx={{
                background: 'var(--mono-50)',
                border: '1px solid var(--surface-border)',
                borderRadius: '8px',
                padding: '16px',
                marginBottom: '32px',
                maxWidth: '600px',
                textAlign: 'left',
              }}
            >
              <Typography
                sx={{
                  fontFamily: 'JetBrains Mono, monospace',
                  fontSize: '0.875rem',
                  color: 'var(--status-offline)',
                  wordBreak: 'break-word',
                }}
              >
                {this.state.error.message}
              </Typography>
            </Box>
          )}
          <Button
            variant="contained"
            startIcon={<RefreshIcon />}
            onClick={this.handleReload}
            sx={{
              background: 'var(--mono-950)',
              color: 'var(--mono-50)',
              padding: '10px 24px',
              borderRadius: '8px',
              textTransform: 'none',
              fontWeight: 500,
              fontSize: '0.875rem',
              transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
              '&:hover': {
                background: 'var(--mono-900)',
                color: 'var(--mono-50)',
                transform: 'translateY(-1px)',
                boxShadow: 'var(--shadow-md)',
              },
            }}
          >
            Reload Page
          </Button>
        </Box>
      )
    }

    return this.props.children
  }
}
