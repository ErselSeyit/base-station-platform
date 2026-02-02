import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material'

interface ConfirmDialogProps {
  readonly open: boolean
  readonly title: string
  readonly message: string
  readonly confirmLabel?: string
  readonly cancelLabel?: string
  readonly onConfirm: () => void
  readonly onCancel: () => void
  readonly isDestructive?: boolean
}

/**
 * Reusable confirmation dialog component.
 * Use instead of window.confirm for accessible, styled confirmations.
 */
export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  onConfirm,
  onCancel,
  isDestructive = false,
}: ConfirmDialogProps) {
  return (
    <Dialog
      open={open}
      onClose={onCancel}
      aria-labelledby="confirm-dialog-title"
      aria-describedby="confirm-dialog-description"
      PaperProps={{
        sx: {
          borderRadius: '12px',
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          minWidth: 320,
        },
      }}
    >
      <DialogTitle
        id="confirm-dialog-title"
        sx={{
          fontWeight: 600,
          color: 'var(--mono-950)',
          pb: 1,
        }}
      >
        {title}
      </DialogTitle>
      <DialogContent>
        <DialogContentText
          id="confirm-dialog-description"
          sx={{ color: 'var(--mono-600)' }}
        >
          {message}
        </DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button
          onClick={onCancel}
          sx={{
            color: 'var(--mono-600)',
            '&:hover': { background: 'var(--surface-hover)' },
          }}
        >
          {cancelLabel}
        </Button>
        <Button
          onClick={onConfirm}
          variant="contained"
          autoFocus
          sx={{
            background: isDestructive ? 'var(--status-offline)' : 'var(--mono-950)',
            color: 'var(--mono-50)',
            fontWeight: 600,
            '&:hover': {
              background: isDestructive ? 'var(--status-error-dark)' : 'var(--mono-800)',
            },
          }}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
