import {
  CheckCircle as SuccessIcon,
  Cancel as FailedIcon,
  Star as StarIcon,
  StarBorder as StarBorderIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { DiagnosticSession, diagnosticsApi, FeedbackRequest } from '../services/api'

interface FeedbackDialogProps {
  open: boolean
  session: DiagnosticSession | null
  onClose: () => void
  onSubmit: (session: DiagnosticSession) => void
}

export default function FeedbackDialog({ open, session, onClose, onSubmit }: FeedbackDialogProps) {
  const [wasEffective, setWasEffective] = useState<boolean | null>(null)
  const [rating, setRating] = useState<number>(0)
  const [operatorNotes, setOperatorNotes] = useState('')
  const [actualOutcome, setActualOutcome] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async () => {
    if (!session || wasEffective === null) return

    setIsSubmitting(true)
    try {
      const feedback: FeedbackRequest = {
        wasEffective,
        rating: rating > 0 ? rating : undefined,
        operatorNotes: operatorNotes || undefined,
        actualOutcome: actualOutcome || undefined,
      }

      const response = await diagnosticsApi.submitFeedback(session.id, feedback)
      onSubmit(response.data)
      handleClose()
    } catch (error) {
      console.error('Failed to submit feedback:', error)
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleClose = () => {
    setWasEffective(null)
    setRating(0)
    setOperatorNotes('')
    setActualOutcome('')
    onClose()
  }

  if (!session) return null

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: {
          borderRadius: '16px',
          background: 'var(--surface-base)',
        }
      }}
    >
      <DialogTitle sx={{ pb: 1 }}>
        <Typography variant="h6" fontWeight={700} color="var(--mono-950)">
          Confirm Solution Effectiveness
        </Typography>
        <Typography variant="body2" color="var(--mono-500)" sx={{ mt: 0.5 }}>
          Your feedback helps the AI learn and improve future recommendations
        </Typography>
      </DialogTitle>

      <DialogContent sx={{ pt: 2 }}>
        {/* Problem Summary */}
        <Box sx={{
          p: 2,
          mb: 3,
          borderRadius: '12px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)'
        }}>
          <Typography variant="subtitle2" color="var(--mono-500)" sx={{ mb: 1 }}>
            Problem
          </Typography>
          <Typography variant="body1" fontWeight={600} color="var(--mono-950)">
            {session.problemCode}
          </Typography>
          <Typography variant="body2" color="var(--mono-600)" sx={{ mt: 0.5 }}>
            {session.message}
          </Typography>
          {session.aiSolution && (
            <>
              <Typography variant="subtitle2" color="var(--mono-500)" sx={{ mt: 2, mb: 1 }}>
                AI Recommended Action
              </Typography>
              <Typography variant="body2" color="var(--mono-800)">
                {session.aiSolution.action}
              </Typography>
            </>
          )}
        </Box>

        {/* Effectiveness Question */}
        <Typography variant="subtitle1" fontWeight={600} color="var(--mono-950)" sx={{ mb: 2 }}>
          Did this solution resolve the problem?
        </Typography>

        <ToggleButtonGroup
          value={wasEffective}
          exclusive
          onChange={(_, value) => value !== null && setWasEffective(value)}
          sx={{ mb: 3, width: '100%' }}
        >
          <ToggleButton
            value={true}
            sx={{
              flex: 1,
              py: 2,
              borderRadius: '12px !important',
              borderColor: wasEffective === true ? 'var(--status-active) !important' : 'var(--surface-border)',
              backgroundColor: wasEffective === true ? 'rgba(34, 197, 94, 0.1)' : 'transparent',
              '&:hover': {
                backgroundColor: 'rgba(34, 197, 94, 0.05)',
              }
            }}
          >
            <SuccessIcon sx={{ mr: 1, color: 'var(--status-active)' }} />
            <Typography fontWeight={600} color={wasEffective === true ? 'var(--status-active)' : 'var(--mono-600)'}>
              Yes, Resolved
            </Typography>
          </ToggleButton>
          <ToggleButton
            value={false}
            sx={{
              flex: 1,
              py: 2,
              borderRadius: '12px !important',
              borderColor: wasEffective === false ? 'var(--status-offline) !important' : 'var(--surface-border)',
              backgroundColor: wasEffective === false ? 'rgba(239, 68, 68, 0.1)' : 'transparent',
              '&:hover': {
                backgroundColor: 'rgba(239, 68, 68, 0.05)',
              }
            }}
          >
            <FailedIcon sx={{ mr: 1, color: 'var(--status-offline)' }} />
            <Typography fontWeight={600} color={wasEffective === false ? 'var(--status-offline)' : 'var(--mono-600)'}>
              No, Still an Issue
            </Typography>
          </ToggleButton>
        </ToggleButtonGroup>

        {/* Rating (only show if effective) */}
        {wasEffective === true && (
          <Box sx={{ mb: 3 }}>
            <Typography variant="subtitle2" color="var(--mono-600)" sx={{ mb: 1 }}>
              Rate this solution (optional)
            </Typography>
            <Box sx={{ display: 'flex', gap: 0.5 }}>
              {[1, 2, 3, 4, 5].map((star) => (
                <IconButton
                  key={star}
                  onClick={() => setRating(star)}
                  sx={{
                    p: 0.5,
                    color: star <= rating ? '#f59e0b' : 'var(--mono-300)',
                    '&:hover': { color: '#f59e0b' }
                  }}
                >
                  {star <= rating ? <StarIcon /> : <StarBorderIcon />}
                </IconButton>
              ))}
            </Box>
          </Box>
        )}

        {/* Actual Outcome */}
        <TextField
          label="What actually happened?"
          multiline
          rows={2}
          fullWidth
          value={actualOutcome}
          onChange={(e) => setActualOutcome(e.target.value)}
          sx={{ mb: 2 }}
          placeholder={wasEffective
            ? "e.g., Temperature dropped to normal within 5 minutes"
            : "e.g., CPU usage remained high after applying the fix"
          }
        />

        {/* Operator Notes */}
        <TextField
          label="Additional notes (optional)"
          multiline
          rows={2}
          fullWidth
          value={operatorNotes}
          onChange={(e) => setOperatorNotes(e.target.value)}
          placeholder="Any additional context or observations..."
        />
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button
          onClick={handleClose}
          sx={{
            color: 'var(--mono-600)',
            '&:hover': { backgroundColor: 'var(--mono-100)' }
          }}
        >
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={wasEffective === null || isSubmitting}
          sx={{
            background: 'var(--mono-950)',
            color: 'var(--mono-50)',
            px: 3,
            '&:hover': {
              background: 'var(--mono-800)',
            },
            '&.Mui-disabled': {
              background: 'var(--mono-200)',
              color: 'var(--mono-400)',
            }
          }}
        >
          {isSubmitting ? 'Submitting...' : 'Submit Feedback'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
