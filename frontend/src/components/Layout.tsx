import {
  BarChart as BarChartIcon,
  CellTower as CellTowerIcon,
  Dashboard as DashboardIcon,
  DarkMode as DarkModeIcon,
  LightMode as LightModeIcon,
  Logout as LogoutIcon,
  Map as MapIcon,
  Menu as MenuIcon,
  Notifications as NotificationsIcon,
} from '@mui/icons-material'
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
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { notificationsApi } from '../services/api'
import { authService } from '../services/authService'
import { Notification } from '../types'

const drawerWidth = 240

interface LayoutProps {
  readonly children: React.ReactNode
}

const getMenuItems = (unreadCount: number) => [
  { text: 'Dashboard', icon: <DashboardIcon />, path: '/' },
  { text: 'Stations', icon: <CellTowerIcon />, path: '/stations' },
  { text: 'Map View', icon: <MapIcon />, path: '/map' },
  { text: 'Alerts', icon: <NotificationsIcon />, path: '/alerts', badge: unreadCount },
  { text: 'Metrics', icon: <BarChartIcon />, path: '/metrics' },
]

const handleLogout = (navigate: ReturnType<typeof useNavigate>) => {
  authService.logout()
  navigate('/login')
}

interface DrawerContentProps {
  readonly menuItems: Array<{ text: string; icon: React.ReactNode; path: string; badge?: number }>
  readonly currentPath: string
  readonly onNavigate: (path: string) => void
}

function DrawerContent({ menuItems, currentPath, onNavigate }: DrawerContentProps) {
  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Minimal Header */}
      <Box
        sx={{
          padding: '20px 16px',
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
                  background: 'var(--mono-100)',
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
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  })
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light')
    localStorage.setItem('theme', isDark ? 'dark' : 'light')
  }, [isDark])

  const { data: notifications } = useQuery({
    queryKey: ['notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
    refetchInterval: 30000,
  })

  const notificationsList = Array.isArray(notifications) ? notifications : []
  const unreadCount = notificationsList.filter(
    (n: Notification) => n.status === 'UNREAD'
  ).length || 0

  const menuItems = getMenuItems(unreadCount)

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
        <Toolbar sx={{ minHeight: '64px !important' }}>
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
                background: 'var(--mono-100)',
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
              onClick={() => setIsDark(!isDark)}
              sx={{
                width: '36px',
                height: '36px',
                color: 'var(--mono-600)',
                transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                mr: 1,
                '&:hover': {
                  background: 'var(--mono-100)',
                  color: 'var(--mono-950)',
                },
              }}
            >
              {isDark ? <LightModeIcon sx={{ fontSize: 20 }} /> : <DarkModeIcon sx={{ fontSize: 20 }} />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Logout">
            <IconButton
              onClick={() => handleLogout(navigate)}
              sx={{
                width: '36px',
                height: '36px',
                color: 'var(--mono-600)',
                transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                '&:hover': {
                  background: 'var(--mono-100)',
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
            keepMounted: true,
          }}
          sx={{
            display: { xs: 'block', sm: 'none' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawerWidth,
              background: 'var(--surface-base)',
              borderRight: '1px solid var(--surface-border)',
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
          p: 3,
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          backgroundColor: 'var(--surface-base)',
          minHeight: '100vh',
        }}
      >
        <Toolbar />
        {children}
      </Box>
    </Box>
  )
}
