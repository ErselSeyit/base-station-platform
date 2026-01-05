import {
  CellTower as CellTowerIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  Bolt as BoltIcon,
} from '@mui/icons-material'
import {
  Alert,
  Box,
  CardContent,
  Grid,
  Typography,
  LinearProgress,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import AnimatedCounter from '../components/AnimatedCounter'
import LiveActivityFeed from '../components/LiveActivityFeed'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import PulsingStatus from '../components/PulsingStatus'
import { notificationsApi, stationApi } from '../services/api'
import { BaseStation, Notification, StationStatus } from '../types'

// Neon color palette
const NEON = {
  pink: '#FF006E',
  blue: '#00F5FF',
  purple: '#B537F2',
  green: '#00FF9F',
  yellow: '#FFD60A',
  orange: '#FF6B35',
}

// Neon Card Component with 3D effects and glow
interface NeonCardProps {
  children: React.ReactNode
  delay?: number
  color?: string
  className?: string
}

const NeonCard = ({ children, delay = 0, color = NEON.blue, className = '' }: NeonCardProps) => (
  <Box
    component={motion.div}
    initial={{ opacity: 0, y: 50, scale: 0.9 }}
    animate={{ opacity: 1, y: 0, scale: 1 }}
    transition={{
      delay,
      duration: 0.8,
      ease: [0.34, 1.56, 0.64, 1], // Bounce easing
    }}
    className={className}
    sx={{
      height: '100%',
      background: 'linear-gradient(145deg, #1a1f3a, #141829)',
      border: `2px solid ${color}40`,
      borderRadius: 3,
      boxShadow: `
        20px 20px 60px #0c0f1c,
        -20px -20px 60px #222847,
        0 0 30px ${color}30
      `,
      transition: 'all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)',
      cursor: 'pointer',
      position: 'relative',
      overflow: 'hidden',
      '&::before': {
        content: '""',
        position: 'absolute',
        top: '-50%',
        left: '-50%',
        width: '200%',
        height: '200%',
        background: `radial-gradient(circle, ${color}20 0%, transparent 60%)`,
        animation: 'rotate3d 10s linear infinite',
        opacity: 0.3,
      },
      '&:hover': {
        transform: 'translateY(-12px) scale(1.02)',
        boxShadow: `
          25px 25px 70px #0a0d18,
          -25px -25px 70px #242a4f,
          0 0 60px ${color}60,
          inset 0 0 20px ${color}20
        `,
        borderColor: `${color}80`,
      },
    }}
  >
    {children}
  </Box>
)

// Helper function to get status color in neon palette
function getStatusNeonColor(status: StationStatus): string {
  switch (status) {
    case StationStatus.ACTIVE:
      return NEON.green
    case StationStatus.MAINTENANCE:
      return NEON.yellow
    case StationStatus.OFFLINE:
      return NEON.pink
    default:
      return NEON.pink
  }
}

export default function Dashboard() {
  const { data: stations, isLoading: stationsLoading } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })
  const { data: notifications } = useQuery({
    queryKey: ['recent-notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
  })

  if (stationsLoading) {
    return <LoadingSpinner />
  }

  const stationsData = Array.isArray(stations) ? stations : []
  const activeCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.ACTIVE).length
  const maintenanceCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.MAINTENANCE).length
  const offlineCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.OFFLINE).length
  const totalPower = stationsData.reduce((sum: number, s: BaseStation) => sum + (s.powerConsumption || 0), 0)
  const notificationsList = Array.isArray(notifications) ? notifications : []
  const unreadAlerts = notificationsList.filter((n: Notification) => n.status === 'UNREAD').length || 0

  const statCards = [
    {
      title: 'TOTAL STATIONS',
      value: stationsData.length,
      icon: <CellTowerIcon sx={{ fontSize: 48 }} />,
      color: NEON.blue,
      unit: '',
    },
    {
      title: 'ACTIVE',
      value: activeCount,
      icon: <CheckCircleIcon sx={{ fontSize: 48 }} />,
      color: NEON.green,
      unit: '',
    },
    {
      title: 'MAINTENANCE',
      value: maintenanceCount,
      icon: <WarningIcon sx={{ fontSize: 48 }} />,
      color: NEON.yellow,
      unit: '',
    },
    {
      title: 'OFFLINE',
      value: offlineCount,
      icon: <ErrorIcon sx={{ fontSize: 48 }} />,
      color: NEON.pink,
      unit: '',
    },
    {
      title: 'TOTAL POWER',
      value: totalPower.toFixed(1),
      icon: <BoltIcon sx={{ fontSize: 48 }} />,
      color: NEON.purple,
      unit: 'kW',
    },
    {
      title: 'UNREAD ALERTS',
      value: unreadAlerts,
      icon: <WarningIcon sx={{ fontSize: 48 }} />,
      color: NEON.orange,
      unit: '',
    },
  ]

  return (
    <Box sx={{ position: 'relative' }}>
      {/* Cyber grid background */}
      <Box
        sx={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundImage: `
            linear-gradient(${NEON.blue}15 1px, transparent 1px),
            linear-gradient(90deg, ${NEON.blue}15 1px, transparent 1px)
          `,
          backgroundSize: '50px 50px',
          opacity: 0.15,
          pointerEvents: 'none',
          zIndex: 0,
        }}
      />

      <Box sx={{ position: 'relative', zIndex: 1 }}>
        {/* Mission Control Title */}
        <Box
          component={motion.div}
          initial={{ opacity: 0, y: -50 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
          sx={{ mb: 5, textAlign: 'center' }}
        >
          <Typography
            variant="h2"
            className="neon-text"
            sx={{
              fontFamily: "'Space Grotesk', sans-serif",
              fontWeight: 800,
              fontSize: { xs: '2.5rem', md: '4rem' },
              letterSpacing: '0.1em',
              textTransform: 'uppercase',
              color: NEON.blue,
              textShadow: `
                0 0 10px ${NEON.blue}ff,
                0 0 20px ${NEON.blue}cc,
                0 0 40px ${NEON.blue}88,
                0 0 80px ${NEON.blue}44
              `,
              animation: 'neonPulse 3s ease-in-out infinite',
              mb: 2,
            }}
          >
            MISSION CONTROL
          </Typography>
          <Typography
            variant="h6"
            sx={{
              fontFamily: "'Inter', sans-serif",
              color: NEON.purple,
              textShadow: `0 0 10px ${NEON.purple}80`,
              letterSpacing: '0.2em',
              textTransform: 'uppercase',
              fontSize: '0.9rem',
            }}
          >
            Base Station Operations Dashboard
          </Typography>
        </Box>

        {/* Unread alerts banner */}
        {unreadAlerts > 0 && (
          <Box
            component={motion.div}
            initial={{ opacity: 0, x: -100 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.6 }}
            sx={{ mb: 4 }}
          >
            <Alert
              severity="warning"
              className="glow-border"
              sx={{
                background: `linear-gradient(135deg, ${NEON.orange}20, ${NEON.yellow}20)`,
                border: `2px solid ${NEON.orange}`,
                borderRadius: 3,
                color: NEON.yellow,
                boxShadow: `0 0 30px ${NEON.orange}40`,
                fontWeight: 600,
                fontSize: '1.1rem',
                '& .MuiAlert-icon': {
                  color: NEON.orange,
                  filter: `drop-shadow(0 0 10px ${NEON.orange})`,
                },
              }}
            >
              {unreadAlerts} UNREAD ALERT{unreadAlerts > 1 ? 'S' : ''} DETECTED
            </Alert>
          </Box>
        )}

        {/* Stat Cards Grid */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          {statCards.map((card, index) => (
            <Grid item xs={12} sm={6} md={4} key={card.title}>
              <NeonCard delay={index * 0.15} color={card.color}>
                <CardContent sx={{ p: 3 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                    <Typography
                      variant="overline"
                      sx={{
                        fontFamily: "'Space Grotesk', sans-serif",
                        fontSize: '0.7rem',
                        fontWeight: 700,
                        letterSpacing: '0.15em',
                        color: card.color,
                        textShadow: `0 0 10px ${card.color}80`,
                      }}
                    >
                      {card.title}
                    </Typography>
                    <Box
                      component={motion.div}
                      animate={{
                        rotate: [0, 10, -10, 0],
                        scale: [1, 1.1, 1.1, 1],
                      }}
                      transition={{
                        duration: 3,
                        repeat: Infinity,
                        ease: 'easeInOut',
                      }}
                      sx={{
                        color: card.color,
                        filter: `drop-shadow(0 0 15px ${card.color})`,
                      }}
                    >
                      {card.icon}
                    </Box>
                  </Box>

                  <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                    <Typography
                      component={motion.div}
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      transition={{ delay: index * 0.15 + 0.3, type: 'spring', stiffness: 200 }}
                      sx={{
                        fontFamily: "'Space Grotesk', sans-serif",
                        fontSize: '3rem',
                        fontWeight: 800,
                        color: card.color,
                        textShadow: `
                          0 0 5px ${card.color}ff,
                          0 0 10px ${card.color}cc,
                          0 0 20px ${card.color}88
                        `,
                        lineHeight: 1,
                      }}
                    >
                      <AnimatedCounter
                        value={typeof card.value === 'number' ? card.value : Number.parseFloat(String(card.value)) || 0}
                        decimals={card.title.includes('POWER') ? 1 : 0}
                        duration={2000}
                      />
                    </Typography>
                    {card.unit && (
                      <Typography
                        variant="h6"
                        sx={{
                          color: card.color,
                          opacity: 0.7,
                          fontWeight: 600,
                        }}
                      >
                        {card.unit}
                      </Typography>
                    )}
                  </Box>

                  {/* Animated progress bar */}
                  <Box
                    component={motion.div}
                    initial={{ width: 0 }}
                    animate={{ width: '100%' }}
                    transition={{ delay: index * 0.15 + 0.5, duration: 1.2, ease: 'easeOut' }}
                    sx={{ mt: 2 }}
                  >
                    <LinearProgress
                      variant="determinate"
                      value={100}
                      sx={{
                        height: 4,
                        borderRadius: 2,
                        backgroundColor: `${card.color}20`,
                        '& .MuiLinearProgress-bar': {
                          backgroundColor: card.color,
                          boxShadow: `0 0 10px ${card.color}`,
                          animation: 'shimmerNeon 2s linear infinite',
                        },
                      }}
                    />
                  </Box>
                </CardContent>
              </NeonCard>
            </Grid>
          ))}
        </Grid>

        {/* Charts and Health Section */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          <Grid item xs={12} md={8}>
            <NeonCard delay={0.9} color={NEON.purple}>
              <CardContent sx={{ p: 3 }}>
                <Typography
                  variant="h5"
                  sx={{
                    fontFamily: "'Space Grotesk', sans-serif",
                    fontWeight: 700,
                    mb: 3,
                    color: NEON.purple,
                    textShadow: `0 0 10px ${NEON.purple}80`,
                    letterSpacing: '0.1em',
                    textTransform: 'uppercase',
                  }}
                >
                  METRICS ANALYSIS
                </Typography>
                <MetricsChart />
              </CardContent>
            </NeonCard>
          </Grid>

          <Grid item xs={12} md={4}>
            <NeonCard delay={1.05} color={NEON.green}>
              <CardContent sx={{ p: 3 }}>
                <Typography
                  variant="h6"
                  sx={{
                    fontFamily: "'Space Grotesk', sans-serif",
                    fontWeight: 700,
                    mb: 3,
                    color: NEON.green,
                    textShadow: `0 0 10px ${NEON.green}80`,
                    letterSpacing: '0.1em',
                    textTransform: 'uppercase',
                  }}
                >
                  STATION HEALTH
                </Typography>

                {/* Animated Health Distribution */}
                <Box sx={{ mb: 3 }}>
                  <Box sx={{ display: 'flex', height: 16, borderRadius: 2, overflow: 'hidden', mb: 2 }}>
                    {activeCount > 0 && (
                      <Box
                        component={motion.div}
                        initial={{ width: 0 }}
                        animate={{ width: `${(activeCount / stationsData.length) * 100}%` }}
                        transition={{ delay: 1.2, duration: 1, ease: 'easeOut' }}
                        sx={{
                          bgcolor: NEON.green,
                          boxShadow: `0 0 20px ${NEON.green}`,
                        }}
                      />
                    )}
                    {maintenanceCount > 0 && (
                      <Box
                        component={motion.div}
                        initial={{ width: 0 }}
                        animate={{ width: `${(maintenanceCount / stationsData.length) * 100}%` }}
                        transition={{ delay: 1.3, duration: 1, ease: 'easeOut' }}
                        sx={{
                          bgcolor: NEON.yellow,
                          boxShadow: `0 0 20px ${NEON.yellow}`,
                        }}
                      />
                    )}
                    {offlineCount > 0 && (
                      <Box
                        component={motion.div}
                        initial={{ width: 0 }}
                        animate={{ width: `${(offlineCount / stationsData.length) * 100}%` }}
                        transition={{ delay: 1.4, duration: 1, ease: 'easeOut' }}
                        sx={{
                          bgcolor: NEON.pink,
                          boxShadow: `0 0 20px ${NEON.pink}`,
                        }}
                      />
                    )}
                  </Box>

                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <PulsingStatus color={NEON.green} size={10} animate />
                      <Typography variant="body2" sx={{ color: NEON.green, fontWeight: 600 }}>
                        Active: {Math.round((activeCount / stationsData.length) * 100) || 0}%
                      </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <PulsingStatus color={NEON.yellow} size={10} animate />
                      <Typography variant="body2" sx={{ color: NEON.yellow, fontWeight: 600 }}>
                        Maintenance: {Math.round((maintenanceCount / stationsData.length) * 100) || 0}%
                      </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <PulsingStatus color={NEON.pink} size={10} animate />
                      <Typography variant="body2" sx={{ color: NEON.pink, fontWeight: 600 }}>
                        Offline: {Math.round((offlineCount / stationsData.length) * 100) || 0}%
                      </Typography>
                    </Box>
                  </Box>
                </Box>

                {/* Top Stations */}
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 700,
                    mb: 2,
                    color: NEON.blue,
                    textShadow: `0 0 8px ${NEON.blue}60`,
                    letterSpacing: '0.1em',
                    textTransform: 'uppercase',
                  }}
                >
                  Top Power Consumers
                </Typography>
                {[...stationsData]
                  .sort((a: BaseStation, b: BaseStation) => (b.powerConsumption || 0) - (a.powerConsumption || 0))
                  .slice(0, 5)
                  .map((station: BaseStation, idx: number) => (
                    <Box
                      key={station.id}
                      component={motion.div}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: 1.5 + idx * 0.1, duration: 0.5 }}
                      sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        p: 1.5,
                        mb: 1,
                        borderRadius: 2,
                        background: `linear-gradient(135deg, ${getStatusNeonColor(station.status)}20, transparent)`,
                        border: `1px solid ${getStatusNeonColor(station.status)}40`,
                        transition: 'all 0.3s ease',
                        '&:hover': {
                          transform: 'translateX(8px)',
                          boxShadow: `0 0 20px ${getStatusNeonColor(station.status)}40`,
                          borderColor: `${getStatusNeonColor(station.status)}80`,
                        },
                      }}
                    >
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <PulsingStatus
                          color={getStatusNeonColor(station.status)}
                          size={10}
                          animate={station.status === StationStatus.ACTIVE}
                        />
                        <Typography
                          variant="body2"
                          sx={{
                            fontWeight: 600,
                            color: '#fff',
                          }}
                        >
                          {station.stationName}
                        </Typography>
                      </Box>
                      <Typography
                        variant="body2"
                        sx={{
                          fontWeight: 700,
                          fontFamily: "'JetBrains Mono', monospace",
                          color: NEON.yellow,
                          textShadow: `0 0 5px ${NEON.yellow}80`,
                        }}
                      >
                        {(station.powerConsumption || 0).toFixed(1)} kW
                      </Typography>
                    </Box>
                  ))}
              </CardContent>
            </NeonCard>
          </Grid>
        </Grid>

        {/* Live Activity Feed */}
        <Grid container spacing={3}>
          <Grid item xs={12}>
            <NeonCard delay={1.2} color={NEON.orange}>
              <CardContent sx={{ p: 3 }}>
                <LiveActivityFeed />
              </CardContent>
            </NeonCard>
          </Grid>
        </Grid>
      </Box>
    </Box>
  )
}
