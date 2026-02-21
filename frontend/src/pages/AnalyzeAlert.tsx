import { useState } from 'react'
import {
  Box,
  Card,
  Typography,
  TextField,
  Button,
  LinearProgress,
  Chip,
  Grid,
  Alert,
  Tooltip,
  IconButton,
} from '@mui/material'
import {
  Search as AnalyzeIcon,
  ContentPaste as PasteIcon,
  Clear as ClearIcon,
  CheckCircle as CheckIcon,
  Warning as WarningIcon,
  Info as InfoIcon,
  Speed as SpeedIcon,
} from '@mui/icons-material'
import { useMutation } from '@tanstack/react-query'
import { api } from '../services/api'
import { CSS_VARS, getConfidenceColor } from '../constants/designSystem'
import { formatSnakeCase } from '../utils/formatUtils'

// Types
interface AlertAnalysisResponse {
  problemCode: string
  suggestedFix: string
  reasoning: string
  confidence: number
  commands: string[]
  riskLevel: string
  expectedOutcome: string
  extractedMetrics: Record<string, number>
  detectedPatterns: string[]
}

interface AlertAnalysisRequest {
  rawAlert: string
  deviceType?: string
  location?: string
}

// Sample alerts for demo
const SAMPLE_ALERTS = [
  {
    label: 'Signal Drop',
    text: `Feb 10 03:14:22 bs-042 CRITICAL: RSSI dropped to -115dBm
Feb 10 03:14:23 bs-042 WARNING: Handover failure rate 45%
Feb 10 03:14:25 bs-042 CRITICAL: Cell unavailable`,
  },
  {
    label: 'CPU Overload',
    text: `2024-02-10 14:32:01 BTS-128 ALERT: CPU usage critical at 98%
2024-02-10 14:32:01 BTS-128 WARNING: Memory usage 87%
2024-02-10 14:32:02 BTS-128 WARNING: Process baseband_proc consuming 45% CPU`,
  },
  {
    label: 'Temperature',
    text: `Site: ENB-045 Location: Istanbul-Kadikoy
ALARM MAJOR: Temperature sensor reading 78C (threshold: 65C)
Fan status: FAN1=OK, FAN2=FAIL, FAN3=OK
Power consumption: 2.4kW (normal: 1.8kW)`,
  },
  {
    label: 'Backhaul',
    text: `[2024-02-10T15:45:00Z] gNB-201 S1 link down
Latency: 450ms (threshold: 50ms)
Packet loss: 12%
Backhaul interface eth0 flapping - 3 events in last 5 minutes`,
  },
]

export default function AnalyzeAlert() {
  const [rawAlert, setRawAlert] = useState('')
  const [result, setResult] = useState<AlertAnalysisResponse | null>(null)

  const analyzeMutation = useMutation({
    mutationFn: async (request: AlertAnalysisRequest) => {
      const response = await api.post<AlertAnalysisResponse>('/alerts/analyze', request)
      return response.data
    },
    onSuccess: (data) => {
      setResult(data)
    },
  })

  const handleAnalyze = () => {
    if (rawAlert.trim().length < 10) return
    analyzeMutation.mutate({ rawAlert })
  }

  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText()
      setRawAlert(text)
    } catch {
      // Clipboard API not available
    }
  }

  const handleClear = () => {
    setRawAlert('')
    setResult(null)
    analyzeMutation.reset()
  }

  const handleSampleClick = (sample: typeof SAMPLE_ALERTS[0]) => {
    setRawAlert(sample.text)
    setResult(null)
  }

  const getRiskColor = (risk: string) => {
    switch (risk?.toLowerCase()) {
      case 'low': return CSS_VARS.statusActive
      case 'medium': return CSS_VARS.statusMaintenance
      case 'high': return CSS_VARS.statusOffline
      default: return CSS_VARS.mono500
    }
  }

  return (
    <Box sx={{ p: { xs: 2, sm: 2.5, md: 3 }, maxWidth: '1200px', margin: '0 auto' }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
        <Box
          sx={{
            p: 1.5,
            borderRadius: '12px',
            background: 'var(--mono-950)',
            color: 'var(--mono-50)',
          }}
        >
          <AnalyzeIcon sx={{ fontSize: 28 }} />
        </Box>
        <Box>
          <Typography
            variant="h4"
            sx={{
              fontWeight: 700,
              color: 'var(--mono-950)',
              letterSpacing: '-0.02em',
            }}
          >
            Analyze Alert
          </Typography>
          <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
            Paste any alert log and get instant AI-powered analysis
          </Typography>
        </Box>
      </Box>

      <Grid container spacing={3}>
        {/* Input Section */}
        <Grid item xs={12} lg={6}>
          <Card
            sx={{
              p: 3,
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
              height: '100%',
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                Paste Your Alert
              </Typography>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Tooltip title="Paste from clipboard">
                  <IconButton
                    onClick={handlePaste}
                    size="small"
                    sx={{
                      color: 'var(--mono-600)',
                      '&:hover': {
                        backgroundColor: 'var(--surface-hover)',
                        color: 'var(--mono-950)',
                      },
                    }}
                  >
                    <PasteIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Clear">
                  <IconButton
                    onClick={handleClear}
                    size="small"
                    sx={{
                      color: 'var(--mono-600)',
                      '&:hover': {
                        backgroundColor: 'var(--surface-hover)',
                        color: 'var(--mono-950)',
                      },
                    }}
                  >
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Box>
            </Box>

            <TextField
              multiline
              rows={12}
              fullWidth
              value={rawAlert}
              onChange={(e) => setRawAlert(e.target.value)}
              placeholder="Paste syslog, SNMP trap, or any alert text here...

Example:
Feb 10 03:14:22 bs-042 CRITICAL: RSSI dropped to -115dBm
Feb 10 03:14:23 bs-042 WARNING: Handover failure rate 45%"
              sx={{
                mb: 2,
                '& .MuiOutlinedInput-root': {
                  fontFamily: "'JetBrains Mono', monospace",
                  fontSize: '0.875rem',
                  backgroundColor: 'var(--surface-base)',
                  color: 'var(--mono-950)',
                  '& .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'var(--surface-border)',
                  },
                  '&:hover .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'var(--mono-400)',
                  },
                  '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'var(--mono-600)',
                  },
                },
                '& .MuiInputBase-input': {
                  color: 'var(--mono-950)',
                  '&::placeholder': {
                    color: 'var(--mono-500)',
                    opacity: 1,
                  },
                },
              }}
            />

            <Button
              variant="contained"
              fullWidth
              size="large"
              onClick={handleAnalyze}
              disabled={rawAlert.trim().length < 10 || analyzeMutation.isPending}
              sx={{
                py: 1.5,
                background: 'var(--mono-950)',
                color: 'var(--mono-50)',
                fontWeight: 600,
                fontSize: '1rem',
                borderRadius: '8px',
                '&:hover': {
                  background: 'var(--mono-900)',
                },
                '&.Mui-disabled': {
                  background: 'var(--mono-300)',
                  color: 'var(--mono-500)',
                },
              }}
            >
              {analyzeMutation.isPending ? 'Analyzing...' : 'Analyze Alert'}
            </Button>

            {analyzeMutation.isPending && (
              <LinearProgress sx={{ mt: 2, borderRadius: 1 }} />
            )}

            {/* Sample Alerts */}
            <Box sx={{ mt: 3 }}>
              <Typography variant="body2" sx={{ color: 'var(--mono-500)', mb: 1.5 }}>
                Or try a sample:
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {SAMPLE_ALERTS.map((sample) => (
                  <Chip
                    key={sample.label}
                    label={sample.label}
                    onClick={() => handleSampleClick(sample)}
                    sx={{
                      cursor: 'pointer',
                      backgroundColor: 'var(--surface-subtle)',
                      color: 'var(--mono-950)',
                      '&:hover': {
                        backgroundColor: 'var(--surface-hover)',
                      },
                    }}
                  />
                ))}
              </Box>
            </Box>
          </Card>
        </Grid>

        {/* Results Section */}
        <Grid item xs={12} lg={6}>
          <Card
            sx={{
              p: 3,
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
              height: '100%',
              minHeight: '500px',
            }}
          >
            <Typography variant="h6" sx={{ fontWeight: 600, color: 'var(--mono-950)', mb: 2 }}>
              AI Analysis
            </Typography>

            {analyzeMutation.isError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                Analysis failed. Please try again.
              </Alert>
            )}

            {!result && !analyzeMutation.isPending && (
              <Box
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  height: '400px',
                  color: 'var(--mono-500)',
                }}
              >
                <AnalyzeIcon sx={{ fontSize: 64, mb: 2, opacity: 0.3 }} />
                <Typography>Paste an alert and click Analyze</Typography>
              </Box>
            )}

            {result && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                {/* Problem Code */}
                <Box>
                  <Typography variant="body2" sx={{ color: 'var(--mono-500)', mb: 0.5 }}>
                    Detected Problem
                  </Typography>
                  <Chip
                    icon={<WarningIcon />}
                    label={formatSnakeCase(result.problemCode)}
                    sx={{
                      backgroundColor: 'var(--status-error-bg)',
                      color: 'var(--status-offline)',
                      fontWeight: 600,
                      fontSize: '0.875rem',
                      py: 2,
                    }}
                  />
                </Box>

                {/* Confidence */}
                <Box>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                    <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
                      Confidence
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700, color: 'var(--mono-950)' }}>
                      {result.confidence}%
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate"
                    value={result.confidence}
                    sx={{
                      height: 10,
                      borderRadius: 5,
                      backgroundColor: 'var(--mono-200)',
                      '& .MuiLinearProgress-bar': {
                        borderRadius: 5,
                        backgroundColor: getConfidenceColor(result.confidence / 100),
                      },
                    }}
                  />
                </Box>

                {/* Suggested Fix */}
                <Box>
                  <Typography variant="body2" sx={{ color: 'var(--mono-500)', mb: 0.5, display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <CheckIcon fontSize="small" sx={{ color: CSS_VARS.statusActive }} />
                    Suggested Fix
                  </Typography>
                  <Typography sx={{ fontWeight: 600, color: 'var(--mono-950)', fontSize: '1.125rem' }}>
                    {result.suggestedFix}
                  </Typography>
                </Box>

                {/* Reasoning */}
                <Box>
                  <Typography variant="body2" sx={{ color: 'var(--mono-500)', mb: 0.5, display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <InfoIcon fontSize="small" sx={{ color: CSS_VARS.colorBlue500 }} />
                    Reasoning
                  </Typography>
                  <Typography sx={{ color: 'var(--mono-900)', fontSize: '0.9375rem' }}>
                    {result.reasoning}
                  </Typography>
                </Box>

                {/* Commands */}
                {result.commands && result.commands.length > 0 && (
                  <Box>
                    <Typography variant="body2" sx={{ color: 'var(--mono-500)', mb: 1 }}>
                      Commands to Execute
                    </Typography>
                    <Box
                      sx={{
                        p: 2,
                        backgroundColor: 'var(--mono-950)',
                        borderRadius: '8px',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontSize: '0.8125rem',
                      }}
                    >
                      {/* Commands may have duplicates, index is safe since list is static */}
                      {result.commands.map((cmd, i) => (
                        // NOSONAR: S6479 - static list, no reordering
                        <Typography key={`${cmd}-${i}`} sx={{ color: 'var(--mono-100)', mb: 0.5 }}>
                          $ {cmd}
                        </Typography>
                      ))}
                    </Box>
                  </Box>
                )}

                {/* Risk & Outcome */}
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Box sx={{ p: 2, backgroundColor: 'var(--surface-subtle)', borderRadius: '8px' }}>
                      <Typography variant="body2" sx={{ color: 'var(--mono-500)', mb: 0.5 }}>
                        Risk Level
                      </Typography>
                      <Chip
                        label={result.riskLevel?.toUpperCase() || 'UNKNOWN'}
                        size="small"
                        sx={{
                          backgroundColor: getRiskColor(result.riskLevel),
                          color: 'white',
                          fontWeight: 600,
                        }}
                      />
                    </Box>
                  </Grid>
                  <Grid item xs={6}>
                    <Box sx={{ p: 2, backgroundColor: 'var(--surface-subtle)', borderRadius: '8px' }}>
                      <Typography variant="body2" sx={{ color: 'var(--mono-500)', mb: 0.5 }}>
                        Expected Outcome
                      </Typography>
                      <Typography sx={{ color: 'var(--mono-950)', fontSize: '0.875rem' }}>
                        {result.expectedOutcome || 'Issue resolved'}
                      </Typography>
                    </Box>
                  </Grid>
                </Grid>

                {/* Extracted Metrics */}
                {result.extractedMetrics && Object.keys(result.extractedMetrics).length > 0 && (
                  <Box>
                    <Typography variant="body2" sx={{ color: 'var(--mono-500)', mb: 1, display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <SpeedIcon fontSize="small" />
                      Extracted Metrics
                    </Typography>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                      {Object.entries(result.extractedMetrics).map(([key, value]) => (
                        <Chip
                          key={key}
                          label={`${formatSnakeCase(key)}: ${value}`}
                          size="small"
                          sx={{
                            backgroundColor: 'var(--surface-subtle)',
                            color: 'var(--mono-950)',
                            fontFamily: "'JetBrains Mono', monospace",
                            fontSize: '0.75rem',
                          }}
                        />
                      ))}
                    </Box>
                  </Box>
                )}
              </Box>
            )}
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}
