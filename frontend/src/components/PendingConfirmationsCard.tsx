import { RateReview as FeedbackIcon } from '@mui/icons-material'
import { Box, Button, Card, Typography } from '@mui/material'
import { CSS_VARS } from '../constants/designSystem'
import { DiagnosticSession } from '../services/api'

interface PendingConfirmationsCardProps {
  sessions: DiagnosticSession[]
  onConfirmClick: (session: DiagnosticSession) => void
}

/**
 * Displays pending solution confirmations that help the AI learn.
 * Users can confirm if solutions worked to improve future recommendations.
 */
export default function PendingConfirmationsCard({ sessions, onConfirmClick }: PendingConfirmationsCardProps) {
  if (sessions.length === 0) {
    return null
  }

  return (
    <Card
      sx={{
        mt: 4,
        background: CSS_VARS.colorVioletBg,
        border: `1px solid ${CSS_VARS.colorVioletBorder}`,
        borderRadius: '16px',
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <Box sx={{ p: { xs: 2, sm: 3 }, borderBottom: `1px solid ${CSS_VARS.mono400}` }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Box
            sx={{
              p: 1,
              borderRadius: '10px',
              background: CSS_VARS.gradientAi,
              color: 'white',
            }}
          >
            <FeedbackIcon sx={{ fontSize: 20 }} />
          </Box>
          <Box>
            <Typography
              variant="h6"
              sx={{ fontWeight: 600, color: 'var(--mono-950)', fontSize: { xs: '1rem', sm: '1.25rem' } }}
            >
              Pending Confirmations
            </Typography>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)', fontSize: { xs: '0.8125rem', sm: '0.875rem' } }}>
              Help the AI learn by confirming if solutions worked
            </Typography>
          </Box>
        </Box>
      </Box>

      {/* Session List */}
      <Box sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {sessions.map((session) => (
            <Box
              key={session.id}
              sx={{
                p: 2,
                background: 'var(--surface-base)',
                border: '1px solid var(--mono-400)',
                borderRadius: '12px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: 2,
              }}
            >
              <Box sx={{ flex: 1, minWidth: 200 }}>
                <Typography sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                  {session.problemCode.replaceAll('_', ' ')}
                </Typography>
                <Typography variant="body2" sx={{ color: 'var(--mono-500)', mt: 0.5 }}>
                  {session.stationName} - {session.message}
                </Typography>
                {session.aiSolution && (
                  <Typography variant="body2" sx={{ color: 'var(--mono-600)', mt: 1, fontStyle: 'italic' }}>
                    AI Action: {session.aiSolution.action}
                  </Typography>
                )}
              </Box>
              <Button
                variant="contained"
                startIcon={<FeedbackIcon />}
                onClick={() => onConfirmClick(session)}
                sx={{
                  background: CSS_VARS.gradientAi,
                  color: 'white',
                  textTransform: 'none',
                  fontWeight: 600,
                  px: 3,
                  '&:hover': {
                    background: CSS_VARS.gradientAiHover,
                  },
                }}
              >
                Confirm Solution
              </Button>
            </Box>
          ))}
        </Box>
      </Box>
    </Card>
  )
}
