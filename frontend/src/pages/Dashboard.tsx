import {
  TrendingUp as TrendingUpIcon,
  CheckCircle as HealthyIcon,
  CellTower as StationIcon,
  PowerOff as OfflineIcon,
  Build as MaintenanceIcon,
  Notifications as NotificationsIcon,
  Close as CloseIcon,
  Speed as PerformanceIcon,
  Router as NetworkIcon,
  FiveG as FiveGIcon,
} from '@mui/icons-material'
import {
  Badge,
  Box,
  Drawer,
  Fab,
  Grid,
  IconButton,
  Typography,
} from '@mui/material'
import { motion } from 'framer-motion'
import { useState } from 'react'
import ErrorDisplay from '../components/ErrorDisplay'
import LiveActivityFeed from '../components/LiveActivityFeed'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import {
  StatusBanner,
  SectionHeader,
  MetricSection,
  StationsAttentionList,
  InfraCard,
  FAB_STYLES,
} from '../components/DashboardComponents'
import { CSS_VARS } from '../constants/designSystem'
import {
  useDashboardData,
  getActiveStationStatus,
  getMaintenanceStatus,
  getOfflineStatus,
} from '../hooks/useDashboardData'
import { getErrorMessage } from '../utils/statusHelpers'

export default function Dashboard() {
  const [activityDrawerOpen, setActivityDrawerOpen] = useState(false)

  // Use custom hook for all data fetching and processing
  const {
    isLoading,
    error,
    stations,
    activeCount,
    maintenanceStations,
    offlineStations,
    unreadAlerts,
    metrics,
    systemMetrics,
    nr78Metrics,
    nr28Metrics,
    qualityMetrics,
    systemStatus,
    nr78Status,
    nr28Status,
    qualityStatus,
    infraStatus,
    overallStatus,
    issueCount,
  } = useDashboardData()

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (error) {
    return <ErrorDisplay title="Failed to load dashboard" message={getErrorMessage(error)} />
  }

  // Fab button styles
  const fabStyles = unreadAlerts > 0 ? FAB_STYLES.alert : FAB_STYLES.normal

  return (
    <Box sx={{ maxWidth: '1600px', margin: '0 auto', padding: { xs: '16px', sm: '24px', md: '32px' } }}>
      {/* Header */}
      <Box component={motion.div} initial={{ opacity: 0, y: -12 }} animate={{ opacity: 1, y: 0 }} sx={{ marginBottom: '8px' }}>
        <Typography
          variant="h1"
          sx={{
            fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2rem' },
            fontWeight: 800,
            letterSpacing: '-0.03em',
            color: 'var(--mono-950)',
          }}
        >
          Operations Dashboard
        </Typography>
      </Box>

      {/* Executive Status Banner */}
      <StatusBanner status={overallStatus} issueCount={issueCount} totalMetrics={metrics.length} />

      {/* Infrastructure Overview */}
      <Box sx={{ marginBottom: '32px' }}>
        <SectionHeader
          title="Infrastructure"
          subtitle="Base station fleet status"
          icon={StationIcon}
          status={infraStatus}
        />
        <Grid container spacing={2} alignItems="stretch">
          <Grid item xs={6} md={3}>
            <InfraCard
              icon={StationIcon}
              label="Total Stations"
              value={stations.length}
              status="info"
              description="Deployed units"
              delay={0.1}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <InfraCard
              icon={HealthyIcon}
              label="Active"
              value={activeCount}
              status={getActiveStationStatus(activeCount, stations.length)}
              description="Operating normally"
              delay={0.15}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <InfraCard
              icon={MaintenanceIcon}
              label="Maintenance"
              value={maintenanceStations.length}
              status={getMaintenanceStatus(maintenanceStations.length)}
              description="Scheduled work"
              delay={0.2}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <InfraCard
              icon={OfflineIcon}
              label="Offline"
              value={offlineStations.length}
              status={getOfflineStatus(offlineStations.length)}
              description="Need attention"
              delay={0.25}
            />
          </Grid>
        </Grid>
      </Box>

      {/* System Performance */}
      <MetricSection
        title="System Performance"
        subtitle="Hardware utilization metrics"
        icon={PerformanceIcon}
        status={systemStatus}
        metrics={systemMetrics}
        baseDelay={0.3}
      />

      {/* 5G NR n78 (3.5 GHz) */}
      <MetricSection
        title="5G NR n78"
        subtitle="3.5 GHz high-speed band"
        icon={FiveGIcon}
        status={nr78Status}
        metrics={nr78Metrics}
        baseDelay={0.35}
      />

      {/* 5G NR n28 + Network Quality + Stations Requiring Attention */}
      <Grid container spacing={3} alignItems="stretch" sx={{ marginBottom: '32px' }}>
        {/* 5G NR n28 (700 MHz) - compact */}
        <Grid item xs={12} md={4}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4 }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
              padding: '20px',
              height: '100%',
            }}
          >
            <MetricSection
              title="5G NR n28"
              icon={NetworkIcon}
              status={nr28Status}
              metrics={nr28Metrics}
              baseDelay={0.4}
              compact
            />
          </Box>
        </Grid>

        {/* Network Quality - compact */}
        <Grid item xs={12} md={4}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.45 }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
              padding: '20px',
              height: '100%',
            }}
          >
            <MetricSection
              title="Network Quality"
              icon={TrendingUpIcon}
              status={qualityStatus}
              metrics={qualityMetrics}
              baseDelay={0.45}
              compact
            />
          </Box>
        </Grid>

        {/* Stations requiring attention */}
        <Grid item xs={12} md={4}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5 }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
              overflow: 'hidden',
              height: '100%',
            }}
          >
            <Box sx={{ padding: '16px 20px', borderBottom: '1px solid var(--surface-border)' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Typography sx={{ fontSize: '0.9375rem', fontWeight: 700, color: 'var(--mono-950)' }}>
                  Attention Required
                </Typography>
                {(maintenanceStations.length + offlineStations.length) > 0 && (
                  <Box sx={{ padding: '3px 10px', borderRadius: '100px', background: CSS_VARS.statusErrorBg }}>
                    <Typography sx={{ fontSize: '0.6875rem', fontWeight: 700, color: CSS_VARS.statusOffline }}>
                      {maintenanceStations.length + offlineStations.length}
                    </Typography>
                  </Box>
                )}
              </Box>
            </Box>
            <Box sx={{ maxHeight: 280, overflow: 'auto' }}>
              <StationsAttentionList offlineStations={offlineStations} maintenanceStations={maintenanceStations} />
            </Box>
          </Box>
        </Grid>
      </Grid>

      {/* Trends Chart - full width */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.55 }}
        sx={{
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
          padding: '24px',
          marginBottom: '32px',
        }}
      >
        <Typography sx={{ fontSize: '1rem', fontWeight: 700, color: 'var(--mono-950)', marginBottom: '16px' }}>
          Performance Trends
        </Typography>
        <MetricsChart />
      </Box>

      {/* Floating Activity Feed Button */}
      <Fab
        aria-label="Open activity feed"
        onClick={() => setActivityDrawerOpen(true)}
        sx={{
          position: 'fixed',
          bottom: 28,
          right: 28,
          width: 60,
          height: 60,
          background: fabStyles.background,
          boxShadow: fabStyles.boxShadow,
          animation: fabStyles.animation,
          '&:hover': { background: fabStyles.hoverBg, transform: 'scale(1.05)' },
        }}
      >
        <Badge
          badgeContent={unreadAlerts}
          max={99}
          sx={{
            '& .MuiBadge-badge': {
              background: fabStyles.badgeBg,
              color: fabStyles.badgeColor,
              fontWeight: 700,
              fontSize: '0.75rem',
              minWidth: '22px',
              height: '22px',
              top: -4,
              right: -4,
            },
          }}
        >
          <NotificationsIcon sx={{ fontSize: 28, color: 'var(--mono-50)' }} />
        </Badge>
      </Fab>

      {/* Activity Feed Drawer */}
      <Drawer
        anchor="right"
        open={activityDrawerOpen}
        onClose={() => setActivityDrawerOpen(false)}
        PaperProps={{
          sx: {
            width: { xs: '100%', sm: 420 },
            background: 'var(--surface-base)',
            borderLeft: '1px solid var(--surface-border)',
          },
        }}
      >
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '20px 24px',
              borderBottom: '1px solid var(--surface-border)',
              background: unreadAlerts > 0 ? CSS_VARS.statusErrorBgSubtle : 'transparent',
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Box
                sx={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  background: unreadAlerts > 0 ? CSS_VARS.statusOffline : CSS_VARS.statusActive,
                  animation: unreadAlerts > 0 ? 'pulse 1.5s infinite' : 'none',
                }}
              />
              <Typography sx={{ fontSize: '1.125rem', fontWeight: 700, color: 'var(--mono-950)' }}>
                Activity Feed
              </Typography>
              {unreadAlerts > 0 && (
                <Box
                  sx={{
                    padding: '4px 10px',
                    borderRadius: '100px',
                    background: CSS_VARS.statusErrorBg,
                    border: `1px solid ${CSS_VARS.statusErrorBorder}`,
                  }}
                >
                  <Typography sx={{ fontSize: '0.75rem', fontWeight: 700, color: CSS_VARS.statusOffline }}>
                    {unreadAlerts} unread
                  </Typography>
                </Box>
              )}
            </Box>
            <IconButton
              aria-label="Close activity feed"
              onClick={() => setActivityDrawerOpen(false)}
              sx={{ color: 'var(--mono-500)', '&:hover': { background: 'var(--surface-hover)' } }}
            >
              <CloseIcon />
            </IconButton>
          </Box>
          <Box sx={{ flex: 1, overflow: 'auto', padding: '20px' }}>
            <LiveActivityFeed />
          </Box>
        </Box>
      </Drawer>
    </Box>
  )
}
