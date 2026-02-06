import {
  AutoFixHigh as SONIcon,
  BarChart as BarChartIcon,
  Bolt as PowerIcon,
  CellTower as CellTowerIcon,
  Dashboard as DashboardIcon,
  DarkMode as DarkModeIcon,
  FiveG as FiveGIcon,
  LightMode as LightModeIcon,
  Logout as LogoutIcon,
  Map as MapIcon,
  Menu as MenuIcon,
  Notifications as NotificationsIcon,
  Psychology as AIIcon,
  Assessment as ReportIcon,
} from '@mui/icons-material'
import { showToast } from '../utils/toast'
import {
  AppBar,
  Badge,
  Box,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { notificationsApi } from '../services/api'
import { authService } from '../services/authService'
import { Notification } from '../types'
import { ensureArray } from '../utils/arrayUtils'
import { POLLING_INTERVALS } from '../constants/designSystem'

const drawerWidth = 240

interface LayoutProps {
  readonly children: React.ReactNode
}

const getMenuItems = (unreadCount: number) => [
  { text: 'Dashboard', icon: <DashboardIcon />, path: '/' },
  { text: '5G Network', icon: <FiveGIcon />, path: '/5g' },
  { text: 'Power & Env', icon: <PowerIcon />, path: '/power' },
  { text: 'Stations', icon: <CellTowerIcon />, path: '/stations' },
  { text: 'Map View', icon: <MapIcon />, path: '/map' },
  { text: 'Alerts', icon: <NotificationsIcon />, path: '/alerts', badge: unreadCount },
  { text: 'Metrics', icon: <BarChartIcon />, path: '/metrics' },
  { text: 'AI Diagnostics', icon: <AIIcon />, path: '/ai-diagnostics' },
  { text: 'SON', icon: <SONIcon />, path: '/son' },
  { text: 'Reports', icon: <ReportIcon />, path: '/reports' },
]

const TOAST_DISPLAY_DELAY_MS = 300

const handleLogout = async (navigate: ReturnType<typeof useNavigate>) => {
  await authService.logout()
  showToast.success('Signed out')
  // Small delay to allow toast to render before navigation
  setTimeout(() => navigate('/login'), TOAST_DISPLAY_DELAY_MS)
}

interface DrawerContentProps {
  readonly menuItems: Array<{ text: string; icon: React.ReactNode; path: string; badge?: number }>
  readonly currentPath: string
  readonly onNavigate: (path: string) => void
}

function DrawerContent({ menuItems, currentPath, onNavigate }: DrawerContentProps) {
  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Minimal Header - matches AppBar height */}
      <Box
        sx={{
          height: '64px',
          padding: '0 16px',
          display: 'flex',
          alignItems: 'center',
          borderBottom: '1px solid var(--surface-border)',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <CellTowerIcon sx={{ fontSize: 24, color: 'var(--mono-950)' }} />
          <Typography
            sx={{
              fontSize: '1.125rem',
              fontWeight: 700,
              letterSpacing: '-0.02em',
              color: 'var(--mono-950)',
            }}
          >
            Base Station O&M
          </Typography>
        </Box>
      </Box>

      {/* Menu Items */}
      <List sx={{ padding: '16px 8px', flex: 1 }}>
        {menuItems.map((item) => (
          <ListItem key={item.text} disablePadding sx={{ marginBottom: '4px' }}>
            <ListItemButton
              selected={currentPath === item.path}
              onClick={() => onNavigate(item.path)}
              sx={{
                borderRadius: '8px',
                padding: '10px 12px',
                transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                '&.Mui-selected': {
                  background: 'var(--mono-950)',
                  color: 'var(--mono-50)',
                  '&:hover': {
                    background: 'var(--mono-900)',
                  },
                },
                '&:hover': {
                  background: 'var(--surface-hover)',
                },
              }}
            >
              <ListItemIcon
                sx={{
                  color: currentPath === item.path ? 'var(--mono-50)' : 'var(--mono-600)',
                  minWidth: 36,
                  transition: 'color 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                }}
              >
                {item.badge ? (
                  <Badge
                    badgeContent={item.badge}
                    sx={{
                      '& .MuiBadge-badge': {
                        background: 'var(--status-offline)',
                        color: 'white',
                        fontSize: '0.6875rem',
                        fontWeight: 600,
                        minWidth: '18px',
                        height: '18px',
                      },
                    }}
                  >
                    {item.icon}
                  </Badge>
                ) : (
                  item.icon
                )}
              </ListItemIcon>
              <ListItemText
                primary={item.text}
                primaryTypographyProps={{
                  fontSize: '0.875rem',
                  fontWeight: currentPath === item.path ? 600 : 500,
                  color: currentPath === item.path ? 'var(--mono-50)' : 'var(--mono-950)',
                }}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </Box>
  )
}

export default function Layout({ children }: LayoutProps) {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [isDark, setIsDark] = useState(() => {
    const saved = localStorage.getItem('theme')
    if (saved) return saved === 'dark'
    return globalThis.matchMedia('(prefers-color-scheme: dark)').matches
  })
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    document.documentElement.dataset.theme = isDark ? 'dark' : 'light'
    localStorage.setItem('theme', isDark ? 'dark' : 'light')
  }, [isDark])

  const { data: notifications, error: notificationsError } = useQuery({
    queryKey: ['notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
  })

  // Gracefully handle error - show 0 unread if query fails
  const notificationsList = notificationsError ? [] : ensureArray(notifications as Notification[])
  // Count both UNREAD and PENDING as unread (PENDING = not yet processed)
  const unreadCount = notificationsList.filter(
    (n) => n.status === 'UNREAD' || n.status === 'PENDING'
  ).length || 0

  const menuItems = useMemo(() => getMenuItems(unreadCount), [unreadCount])

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen)
  }

  const handleNavigate = (path: string) => {
    navigate(path)
    setMobileOpen(false)
  }

  const drawer = (
    <DrawerContent
      menuItems={menuItems}
      currentPath={location.pathname}
      onNavigate={handleNavigate}
    />
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Minimal AppBar */}
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          ml: { sm: `${drawerWidth}px` },
          background: 'var(--surface-base)',
          borderBottom: '1px solid var(--surface-border)',
          boxShadow: 'none',
        }}
      >
        <Toolbar sx={{ minHeight: '63px !important', height: '63px' }}>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{
              mr: 2,
              display: { sm: 'none' },
              color: 'var(--mono-950)',
              '&:hover': {
                background: 'var(--surface-hover)',
              },
            }}
          >
            <MenuIcon />
          </IconButton>
          <Typography
            variant="h6"
            noWrap
            component="div"
            sx={{
              flexGrow: 1,
              fontSize: '0.875rem',
              fontWeight: 600,
              color: 'var(--mono-950)',
              letterSpacing: '-0.01em',
            }}
          >
            {menuItems.find(item => item.path === location.pathname)?.text || 'Base Station Platform'}
          </Typography>
          <Tooltip title={isDark ? 'Light mode' : 'Dark mode'}>
            <IconButton
              aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
              onClick={() => setIsDark(!isDark)}
              sx={{
                width: '36px',
                height: '36px',
                color: 'var(--mono-600)',
                transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                mr: 1,
                '&:hover': {
                  background: 'var(--surface-hover)',
                  color: 'var(--mono-950)',
                },
              }}
            >
              {isDark ? <LightModeIcon sx={{ fontSize: 20 }} /> : <DarkModeIcon sx={{ fontSize: 20 }} />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Logout">
            <IconButton
              aria-label="Logout"
              onClick={() => handleLogout(navigate)}
              sx={{
                width: '36px',
                height: '36px',
                color: 'var(--mono-600)',
                transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                '&:hover': {
                  background: 'var(--surface-hover)',
                  color: 'var(--mono-950)',
                },
              }}
            >
              <LogoutIcon sx={{ fontSize: 20 }} />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      {/* Drawer */}
      <Box
        component="nav"
        sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}
      >
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{
            keepMounted: false, // Don't keep modal mounted to prevent click blocking
          }}
          sx={{
            display: { xs: 'block', sm: 'none' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawerWidth,
              background: 'var(--surface-base)',
              borderRight: '1px solid var(--surface-border)',
            },
            '& .MuiBackdrop-root': {
              backgroundColor: 'rgba(0, 0, 0, 0.5)',
            },
          }}
        >
          {drawer}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', sm: 'block' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawerWidth,
              background: 'var(--surface-base)',
              borderRight: '1px solid var(--surface-border)',
            },
          }}
          open
        >
          {drawer}
        </Drawer>
      </Box>

      {/* Main Content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 1.5, sm: 2, md: 3 },
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          backgroundColor: 'var(--surface-base)',
          minHeight: '100vh',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <Toolbar sx={{ minHeight: '64px !important' }} />
        {children}
      </Box>
    </Box>
  )
}
