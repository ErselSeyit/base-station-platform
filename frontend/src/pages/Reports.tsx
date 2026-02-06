import {
  Assessment as ReportIcon,
  Download as DownloadIcon,
  PictureAsPdf as PdfIcon,
  Schedule as ScheduleIcon,
  Storage as DataIcon,
  TrendingUp as TrendIcon,
  Description as DocIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Grid,
  IconButton,
  Tooltip,
  Typography,
} from '@mui/material'
import { useCallback, useState } from 'react'
import { CSS_VARS } from '../constants/designSystem'
import { formatTimestamp } from '../utils/statusHelpers'
import { showToast } from '../utils/toast'

// Delay before revoking blob URL to ensure download starts
const URL_REVOKE_DELAY_MS = 1000

// Report type styling - extracted to avoid recreation on each render
const TYPE_COLORS = {
  pdf: { bg: CSS_VARS.statusErrorBgSubtle, color: CSS_VARS.statusOffline },
  excel: { bg: CSS_VARS.statusActiveBgSubtle, color: CSS_VARS.statusActive },
  json: { bg: CSS_VARS.colorBlueBg, color: CSS_VARS.colorBlue500 },
} as const

const TYPE_ICONS: Record<string, React.ReactNode> = {
  pdf: <PdfIcon sx={{ fontSize: 20 }} />,
  excel: <DocIcon sx={{ fontSize: 20 }} />,
  json: <DataIcon sx={{ fontSize: 20 }} />,
}

interface ReportItem {
  id: string
  name: string
  description: string
  type: 'pdf' | 'excel' | 'json'
  size: string
  lastGenerated: string
  status: 'ready' | 'generating' | 'scheduled'
}

// Static report metadata - timestamps are placeholders
// Future: Could fetch from /api/reports/metadata for dynamic report listing
const STATIC_REPORTS: ReportItem[] = [
  {
    id: 'bi-report',
    name: 'BI Report',
    description: 'Comprehensive business intelligence report with charts, KPIs, and station analytics',
    type: 'pdf',
    size: '2.4 MB',
    lastGenerated: '2026-02-02T00:00:00.000Z',
    status: 'ready',
  },
  {
    id: 'ai-diagnostics',
    name: 'AI Diagnostics Log',
    description: 'Complete log of AI-powered diagnostics, problems detected, and resolutions applied',
    type: 'json',
    size: '156 KB',
    lastGenerated: '2026-02-02T00:00:00.000Z',
    status: 'ready',
  },
  {
    id: 'metrics-export',
    name: 'Metrics Export',
    description: 'Raw metrics data export for all stations including CPU, memory, temperature, and signal',
    type: 'json',
    size: '4.2 MB',
    lastGenerated: '2026-02-01T23:00:00.000Z',
    status: 'ready',
  },
  {
    id: 'alerts-summary',
    name: 'Alerts Summary',
    description: 'Summary of all alerts and notifications with resolution status',
    type: 'pdf',
    size: '890 KB',
    lastGenerated: '2026-02-01T22:00:00.000Z',
    status: 'ready',
  },
]

function ReportCard({ report, onDownload, onGenerate }: Readonly<{
  report: ReportItem
  onDownload: (id: string) => Promise<void>
  onGenerate: (id: string) => Promise<void>
}>) {
  const [isDownloading, setIsDownloading] = useState(false)
  const [isGenerating, setIsGenerating] = useState(false)

  const handleDownload = async () => {
    setIsDownloading(true)
    await onDownload(report.id)
    setIsDownloading(false)
  }

  const handleGenerate = async () => {
    setIsGenerating(true)
    await onGenerate(report.id)
    setIsGenerating(false)
  }

  const typeStyle = TYPE_COLORS[report.type]

  return (
    <Card
      sx={{
        p: 3,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: '16px',
        transition: 'all 0.2s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: 'var(--shadow-lg)',
          borderColor: CSS_VARS.mono400,
        },
      }}
    >
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
        <Box
          sx={{
            p: 1.5,
            borderRadius: '12px',
            background: typeStyle.bg,
            color: typeStyle.color,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {TYPE_ICONS[report.type]}
        </Box>
        <Chip
          label={report.type.toUpperCase()}
          size="small"
          sx={{
            backgroundColor: typeStyle.bg,
            color: typeStyle.color,
            fontWeight: 600,
            fontSize: '0.7rem',
          }}
        />
      </Box>

      {/* Content */}
      <Typography
        variant="h6"
        sx={{
          fontWeight: 600,
          color: 'var(--mono-950)',
          mb: 1,
        }}
      >
        {report.name}
      </Typography>

      <Typography
        variant="body2"
        sx={{
          color: 'var(--mono-600)',
          mb: 2,
          flex: 1,
          lineHeight: 1.6,
        }}
      >
        {report.description}
      </Typography>

      {/* Meta Info */}
      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          <ScheduleIcon sx={{ fontSize: 16, color: 'var(--mono-400)' }} />
          <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
            Last generated: {formatTimestamp(report.lastGenerated)}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <DataIcon sx={{ fontSize: 16, color: 'var(--mono-400)' }} />
          <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
            Size: {report.size}
          </Typography>
        </Box>
      </Box>

      {/* Actions */}
      <Box sx={{ display: 'flex', gap: 1.5 }}>
        <Button
          variant="contained"
          startIcon={isDownloading ? <CircularProgress size={16} color="inherit" /> : <DownloadIcon />}
          onClick={handleDownload}
          disabled={isDownloading || report.status !== 'ready'}
          sx={{
            flex: 1,
            background: 'var(--mono-950)',
            color: 'var(--mono-50)',
            textTransform: 'none',
            fontWeight: 600,
            borderRadius: '10px',
            py: 1,
            '&:hover': {
              background: 'var(--mono-800)',
            },
            '&:disabled': {
              background: 'var(--mono-400)',
              color: 'var(--mono-500)',
            },
          }}
        >
          {isDownloading ? 'Downloading...' : 'Download'}
        </Button>
        <Tooltip title="Regenerate report">
          <IconButton
            onClick={handleGenerate}
            disabled={isGenerating}
            sx={{
              border: '1px solid var(--mono-400)',
              borderRadius: '10px',
              width: 40,
              height: 40,
              color: 'var(--mono-700)',
              '&:hover': {
                background: 'var(--surface-hover)',
                borderColor: 'var(--mono-400)',
                color: 'var(--mono-900)',
              },
              '&:disabled': {
                opacity: 0.5,
                borderColor: 'var(--mono-400)',
              },
            }}
          >
            <RefreshIcon sx={{ fontSize: 20, animation: isGenerating ? 'spin 1s linear infinite' : 'none', color: 'inherit' }} />
          </IconButton>
        </Tooltip>
      </Box>
    </Card>
  )
}

// Report download configuration
const REPORT_CONFIG = {
  'bi-report': {
    endpoint: '/api/reports/bi',
    filename: 'bi-report',
    extension: 'pdf',
    mimeType: 'application/pdf',
    isJson: false,
  },
  'ai-diagnostics': {
    endpoint: '/api/reports/diagnostics',
    filename: 'ai-diagnostics',
    extension: 'json',
    mimeType: 'application/json',
    isJson: true,
  },
} as const

type ReportId = keyof typeof REPORT_CONFIG

/**
 * Downloads a report file by fetching from the backend and triggering browser download.
 * Shared by both handleDownload and handleGenerate since they perform the same action.
 */
async function downloadReport(reportId: ReportId): Promise<void> {
  const config = REPORT_CONFIG[reportId]
  if (!config) return

  try {
    const response = await fetch(config.endpoint)
    if (!response.ok) {
      showToast.error(`Failed to download ${config.filename}`)
      return
    }

    let blob: Blob
    if (config.isJson) {
      const data = await response.json()
      blob = new Blob([JSON.stringify(data, null, 2)], { type: config.mimeType })
    } else {
      const arrayBuffer = await response.arrayBuffer()
      blob = new Blob([arrayBuffer], { type: config.mimeType })
    }

    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${config.filename}-${new Date().toISOString().slice(0, 10)}.${config.extension}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    // Delay cleanup to ensure download starts
    setTimeout(() => URL.revokeObjectURL(url), URL_REVOKE_DELAY_MS)
  } catch {
    showToast.error(`Failed to download report`)
  }
}

export default function Reports() {
  // Both download and generate perform the same action - fetch fresh report from backend
  const handleDownload = useCallback((reportId: string) => downloadReport(reportId as ReportId), [])
  const handleGenerate = useCallback((reportId: string) => downloadReport(reportId as ReportId), [])

  return (
    <Box sx={{ p: { xs: 2, sm: 2.5, md: 3 }, maxWidth: '1400px', margin: '0 auto' }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
        <Box
          sx={{
            p: 1.5,
            borderRadius: '12px',
            background: CSS_VARS.gradientBlue,
            color: 'white',
          }}
        >
          <ReportIcon sx={{ fontSize: 28 }} />
        </Box>
        <Box>
          <Typography
            variant="h4"
            sx={{
              fontWeight: 700,
              color: 'var(--mono-950)',
              letterSpacing: '-0.02em',
              fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2.125rem' },
            }}
          >
            Reports
          </Typography>
          <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
            Download and generate platform reports
          </Typography>
        </Box>
      </Box>

      {/* Quick Stats - BI Report Card - matching theme */}
      <Card
        sx={{
          p: { xs: 2.5, sm: 3 },
          mb: 4,
          background: 'var(--surface-elevated)',
          borderRadius: '16px',
          border: '1px solid var(--surface-border)',
          transition: 'all 0.2s ease',
          '&:hover': {
            boxShadow: 'var(--shadow-lg)',
            borderColor: 'var(--mono-400)',
          },
        }}
      >
        <Grid container spacing={{ xs: 2, sm: 3 }} alignItems="center">
          <Grid item xs={12} md={7}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
              <Box
                sx={{
                  p: 1,
                  borderRadius: '10px',
                  background: CSS_VARS.gradientBlue,
                  color: 'white',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <ReportIcon sx={{ fontSize: 20 }} />
              </Box>
              <Typography
                variant="h5"
                sx={{
                  fontWeight: 700,
                  fontSize: { xs: '1.125rem', sm: '1.25rem' },
                  color: 'var(--mono-950)',
                }}
              >
                Business Intelligence Report
              </Typography>
            </Box>
            <Typography
              variant="body2"
              sx={{
                mb: 2,
                color: 'var(--mono-600)',
                lineHeight: 1.6,
                fontSize: { xs: '0.8125rem', sm: '0.875rem' },
              }}
            >
              7-page comprehensive analysis with executive summary, KPIs, performance metrics, and geographic distribution
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              <Chip
                icon={<CheckIcon sx={{ fontSize: 14 }} />}
                label="Executive Summary"
                size="small"
                sx={{
                  backgroundColor: CSS_VARS.statusActiveBgSubtle,
                  color: CSS_VARS.statusActive,
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  border: `1px solid ${CSS_VARS.statusActiveBorder}`,
                  '& .MuiChip-icon': { color: `${CSS_VARS.statusActive} !important` }
                }}
              />
              <Chip
                icon={<TrendIcon sx={{ fontSize: 14 }} />}
                label="Performance Charts"
                size="small"
                sx={{
                  backgroundColor: CSS_VARS.colorBlueBg,
                  color: CSS_VARS.colorBlue500,
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  border: `1px solid ${CSS_VARS.colorBlueBorder}`,
                  '& .MuiChip-icon': { color: `${CSS_VARS.colorBlue500} !important` }
                }}
              />
              <Chip
                icon={<DataIcon sx={{ fontSize: 14 }} />}
                label="Network Analysis"
                size="small"
                sx={{
                  backgroundColor: CSS_VARS.colorPurpleBg,
                  color: CSS_VARS.colorPurple500,
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  border: `1px solid ${CSS_VARS.colorPurpleBorder}`,
                  '& .MuiChip-icon': { color: `${CSS_VARS.colorPurple500} !important` }
                }}
              />
            </Box>
          </Grid>
          <Grid item xs={12} md={5} sx={{ textAlign: { xs: 'left', md: 'right' } }}>
            <Button
              variant="contained"
              size="large"
              startIcon={<DownloadIcon />}
              onClick={() => handleDownload('bi-report')}
              sx={{
                background: 'var(--mono-950)',
                color: 'var(--mono-50)',
                textTransform: 'none',
                fontWeight: 600,
                borderRadius: '12px',
                px: { xs: 3, sm: 4 },
                py: 1.5,
                width: { xs: '100%', sm: 'auto' },
                boxShadow: 'var(--shadow-sm)',
                '&:hover': {
                  background: 'var(--mono-800)',
                  boxShadow: 'var(--shadow-md)',
                },
              }}
            >
              Download BI Report
            </Button>
          </Grid>
        </Grid>
      </Card>

      {/* Report Cards */}
      <Typography
        variant="h6"
        sx={{
          fontWeight: 600,
          color: 'var(--mono-950)',
          mb: 3,
        }}
      >
        Available Reports
      </Typography>

      <Grid container spacing={3}>
        {STATIC_REPORTS.map(report => (
          <Grid item xs={12} sm={6} lg={3} key={report.id}>
            <ReportCard
              report={report}
              onDownload={handleDownload}
              onGenerate={handleGenerate}
            />
          </Grid>
        ))}
      </Grid>

    </Box>
  )
}
