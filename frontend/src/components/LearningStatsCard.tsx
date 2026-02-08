import { School as LearnIcon } from '@mui/icons-material'
import { Box, Card, Chip, Grid, Tooltip, Typography } from '@mui/material'
import { CSS_VARS } from '../constants/designSystem'

// Threshold for pattern success classification
const SUCCESS_RATE_THRESHOLD_PERCENT = 70

interface LearnedPattern {
  problemCode: string
  successRate: number
  totalCases: number
  adjustedConfidence: number
}

interface LearningStats {
  totalFeedback: number
  resolved: number
  failed: number
  autoApplied: number
  successRate: number
  topPatterns?: LearnedPattern[]
}

interface LearningStatsCardProps {
  stats: LearningStats
}

/**
 * Displays AI learning progress and statistics.
 * Shows feedback counts, success rates, and top learned patterns.
 */
export default function LearningStatsCard({ stats }: LearningStatsCardProps) {
  if (!stats || stats.totalFeedback <= 0) {
    return null
  }

  return (
    <Card
      sx={{
        mt: 4,
        background: 'var(--surface-elevated)',
        border: '1px solid var(--mono-400)',
        borderRadius: '16px',
        p: { xs: 2, sm: 3 },
      }}
    >
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
        <Box
          sx={{
            p: 1,
            borderRadius: '10px',
            background: CSS_VARS.gradientSuccess,
            color: 'white',
          }}
        >
          <LearnIcon sx={{ fontSize: 20 }} />
        </Box>
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
            AI Learning Progress
          </Typography>
          <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
            The AI improves based on your feedback
          </Typography>
        </Box>
      </Box>

      {/* Stats Grid */}
      <Grid container spacing={3}>
        <Grid item xs={6} sm={2.4}>
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="h4" sx={{ fontWeight: 700, color: 'var(--mono-950)' }}>
              {stats.totalFeedback}
            </Typography>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
              Total Feedback
            </Typography>
          </Box>
        </Grid>
        <Grid item xs={6} sm={2.4}>
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="h4" sx={{ fontWeight: 700, color: 'var(--status-active)' }}>
              {stats.resolved}
            </Typography>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
              Successful
            </Typography>
          </Box>
        </Grid>
        <Grid item xs={6} sm={2.4}>
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="h4" sx={{ fontWeight: 700, color: 'var(--status-offline)' }}>
              {stats.failed}
            </Typography>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
              Failed
            </Typography>
          </Box>
        </Grid>
        <Grid item xs={6} sm={2.4}>
          <Box sx={{ textAlign: 'center' }}>
            <Tooltip title="Solutions auto-applied due to high AI confidence (95%+)">
              <Typography variant="h4" sx={{ fontWeight: 700, color: CSS_VARS.colorPurple500, cursor: 'help' }}>
                {stats.autoApplied || 0}
              </Typography>
            </Tooltip>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
              Auto-Applied
            </Typography>
          </Box>
        </Grid>
        <Grid item xs={6} sm={2.4}>
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="h4" sx={{ fontWeight: 700, color: 'var(--color-violet-500)' }}>
              {stats.successRate.toFixed(1)}%
            </Typography>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
              Success Rate
            </Typography>
          </Box>
        </Grid>
      </Grid>

      {/* Top Patterns */}
      {stats.topPatterns && stats.topPatterns.length > 0 && (
        <Box sx={{ mt: 3, pt: 3, borderTop: '1px solid var(--mono-400)' }}>
          <Typography variant="subtitle2" sx={{ color: 'var(--mono-600)', mb: 2 }}>
            Top Learned Patterns
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            {stats.topPatterns.map((pattern) => (
              <Chip
                key={pattern.problemCode}
                label={`${pattern.problemCode.replaceAll('_', ' ')} (${pattern.successRate.toFixed(0)}%)`}
                size="small"
                sx={{
                  backgroundColor: pattern.successRate >= SUCCESS_RATE_THRESHOLD_PERCENT ? CSS_VARS.colorEmeraldBg : CSS_VARS.colorAmberBg,
                  color: pattern.successRate >= SUCCESS_RATE_THRESHOLD_PERCENT ? CSS_VARS.colorEmerald600 : CSS_VARS.colorAmber600,
                  fontWeight: 600,
                }}
              />
            ))}
          </Box>
        </Box>
      )}
    </Card>
  )
}
