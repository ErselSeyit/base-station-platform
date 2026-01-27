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
import { useState } from 'react'

interface ReportItem {
  id: string
  name: string
  description: string
  type: 'pdf' | 'excel' | 'json'
  size: string
  lastGenerated: string
  status: 'ready' | 'generating' | 'scheduled'
}

const reports: ReportItem[] = [
  {
    id: 'bi-report',
    name: 'BI Report',
    description: 'Comprehensive business intelligence report with charts, KPIs, and station analytics',
    type: 'pdf',
    size: '2.4 MB',
    lastGenerated: new Date().toISOString(),
    status: 'ready',
  },
  {
    id: 'ai-diagnostics',
    name: 'AI Diagnostics Log',
    description: 'Complete log of AI-powered diagnostics, problems detected, and resolutions applied',
    type: 'json',
    size: '156 KB',
    lastGenerated: new Date().toISOString(),
    status: 'ready',
  },
  {
    id: 'metrics-export',
    name: 'Metrics Export',
    description: 'Raw metrics data export for all stations including CPU, memory, temperature, and signal',
    type: 'json',
    size: '4.2 MB',
    lastGenerated: new Date(Date.now() - 3600000).toISOString(),
    status: 'ready',
  },
  {
    id: 'alerts-summary',
    name: 'Alerts Summary',
    description: 'Summary of all alerts and notifications with resolution status',
    type: 'pdf',
    size: '890 KB',
    lastGenerated: new Date(Date.now() - 7200000).toISOString(),
    status: 'ready',
  },
]

function ReportCard({ report, onDownload, onGenerate }: {
  report: ReportItem
  onDownload: (id: string) => void
  onGenerate: (id: string) => void
}) {
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

  const typeColors: Record<string, { bg: string; color: string }> = {
    pdf: { bg: '#fee2e2', color: '#dc2626' },
    excel: { bg: '#dcfce7', color: '#16a34a' },
    json: { bg: '#dbeafe', color: '#2563eb' },
  }

  const typeIcons: Record<string, React.ReactNode> = {
    pdf: <PdfIcon sx={{ fontSize: 20 }} />,
    excel: <DocIcon sx={{ fontSize: 20 }} />,
    json: <DataIcon sx={{ fontSize: 20 }} />,
  }

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
          boxShadow: '0 8px 24px rgba(0,0,0,0.08)',
          borderColor: 'var(--mono-300)',
        },
      }}
    >
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
        <Box
          sx={{
            p: 1.5,
            borderRadius: '12px',
            background: typeColors[report.type].bg,
            color: typeColors[report.type].color,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {typeIcons[report.type]}
        </Box>
        <Chip
          label={report.type.toUpperCase()}
          size="small"
          sx={{
            backgroundColor: typeColors[report.type].bg,
            color: typeColors[report.type].color,
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
            Last generated: {new Date(report.lastGenerated).toLocaleString()}
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
              background: 'var(--mono-300)',
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
              border: '1px solid var(--surface-border)',
              borderRadius: '10px',
              '&:hover': {
                background: 'var(--mono-100)',
              },
            }}
          >
            <RefreshIcon sx={{ animation: isGenerating ? 'spin 1s linear infinite' : 'none' }} />
          </IconButton>
        </Tooltip>
      </Box>
    </Card>
  )
}

export default function Reports() {
  const handleDownload = async (reportId: string) => {
    // Simulate download delay
    await new Promise(resolve => setTimeout(resolve, 500))

    // Trigger actual download based on report type
    if (reportId === 'bi-report') {
      // Download the BI report PDF
      const link = document.createElement('a')
      link.href = '/api/v1/reports/bi-report.pdf'
      link.download = 'bi-report.pdf'

      // For demo, create a mock download
      const response = await fetch('/bi-report.pdf').catch(() => null)
      if (response) {
        const blob = await response.blob()
        const url = URL.createObjectURL(blob)
        link.href = url
        link.click()
        URL.revokeObjectURL(url)
      } else {
        // Fallback - show toast or notification
        console.log('BI Report download triggered')
      }
    } else if (reportId === 'ai-diagnostics') {
      // Download AI diagnostics JSON
      const response = await fetch('/ai-diagnose-log.json').catch(() => null)
      if (response) {
        const blob = await response.blob()
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = 'ai-diagnostics.json'
        link.click()
        URL.revokeObjectURL(url)
      }
    }
  }

  const handleGenerate = async (reportId: string) => {
    // Simulate report generation
    await new Promise(resolve => setTimeout(resolve, 2000))
    console.log(`Report ${reportId} regenerated`)
  }

  return (
    <Box sx={{ p: { xs: 2, sm: 2.5, md: 3 }, maxWidth: '1400px', margin: '0 auto' }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
        <Box
          sx={{
            p: 1.5,
            borderRadius: '12px',
            background: 'linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)',
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
            borderColor: 'var(--mono-300)',
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
                  background: 'linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)',
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
                  backgroundColor: 'rgba(22, 163, 74, 0.1)',
                  color: 'var(--status-active)',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  border: '1px solid rgba(22, 163, 74, 0.2)',
                  '& .MuiChip-icon': { color: 'var(--status-active) !important' }
                }}
              />
              <Chip
                icon={<TrendIcon sx={{ fontSize: 14 }} />}
                label="Performance Charts"
                size="small"
                sx={{
                  backgroundColor: 'rgba(59, 130, 246, 0.1)',
                  color: '#3b82f6',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  border: '1px solid rgba(59, 130, 246, 0.2)',
                  '& .MuiChip-icon': { color: '#3b82f6 !important' }
                }}
              />
              <Chip
                icon={<DataIcon sx={{ fontSize: 14 }} />}
                label="Network Analysis"
                size="small"
                sx={{
                  backgroundColor: 'rgba(139, 92, 246, 0.1)',
                  color: '#8b5cf6',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  border: '1px solid rgba(139, 92, 246, 0.2)',
                  '& .MuiChip-icon': { color: '#8b5cf6 !important' }
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
        {reports.map(report => (
          <Grid item xs={12} sm={6} lg={3} key={report.id}>
            <ReportCard
              report={report}
              onDownload={handleDownload}
              onGenerate={handleGenerate}
            />
          </Grid>
        ))}
      </Grid>

      {/* CSS for spin animation */}
      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </Box>
  )
}
