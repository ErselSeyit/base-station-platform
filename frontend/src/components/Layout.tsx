import {
  BarChart as BarChartIcon,
  CellTower as CellTowerIcon,
  DarkMode as DarkModeIcon,
  Dashboard as DashboardIcon,
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
  Divider,
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
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useTheme } from '../contexts/ThemeContext'
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

interface DrawerHeaderProps {
  readonly mode: 'light' | 'dark'
}

function DrawerHeader({ mode }: DrawerHeaderProps) {
  return (
    <Toolbar
      sx={{
        background: mode === 'dark'
          ? 'linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%)'
          : 'linear-gradient(135deg, #1976d2 0%, #115293 100%)',
        color: 'white',
        boxShadow: '0 4px 20px rgba(0, 0, 0, 0.1)',
      }}
    >
      <CellTowerIcon sx={{ mr: 2, fontSize: 28 }} />
      <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 700, letterSpacing: '0.02em' }}>
        Base Station O&M
      </Typography>
    </Toolbar>
  )
}

interface DrawerContentProps {
  readonly mode: 'light' | 'dark'
  readonly menuItems: Array<{ text: string; icon: React.ReactNode; path: string; badge?: number }>
  readonly currentPath: string
  readonly onNavigate: (path: string) => void
}

function DrawerContent({ mode, menuItems, currentPath, onNavigate }: DrawerContentProps) {
  return (
    <Box>
      <DrawerHeader mode={mode} />
      <Divider sx={{ borderColor: mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)' }} />
      <List sx={{ pt: 2 }}>
        {menuItems.map((item) => (
          <ListItem key={item.text} disablePadding sx={{ mb: 0.5, px: 1 }}>
            <ListItemButton
              selected={currentPath === item.path}
              onClick={() => onNavigate(item.path)}
              sx={{
                borderRadius: 2,
                py: 1.5,
                transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                willChange: 'transform, background',
                backfaceVisibility: 'hidden',
                '&.Mui-selected': {
                  background: mode === 'dark'
                    ? 'linear-gradient(135deg, rgba(100, 181, 246, 0.2) 0%, rgba(66, 165, 245, 0.15) 100%)'
                    : 'linear-gradient(135deg, rgba(25, 118, 210, 0.15) 0%, rgba(21, 101, 192, 0.1) 100%)',
                  color: 'primary.main',
                  borderLeft: '4px solid',
                  borderColor: 'primary.main',
                  '&:hover': {
                    background: mode === 'dark'
                      ? 'linear-gradient(135deg, rgba(100, 181, 246, 0.25) 0%, rgba(66, 165, 245, 0.2) 100%)'
                      : 'linear-gradient(135deg, rgba(25, 118, 210, 0.2) 0%, rgba(21, 101, 192, 0.15) 100%)',
                  },
                },
                '&:hover': {
                  background: mode === 'dark'
                    ? 'rgba(100, 181, 246, 0.1)'
                    : 'rgba(25, 118, 210, 0.08)',
                },
              }}
            >
              <ListItemIcon
                sx={{
                  color: currentPath === item.path ? 'primary.main' : 'inherit',
                  minWidth: 40,
                }}
              >
                {item.badge ? (
                  <Badge badgeContent={item.badge} color="error">
                    {item.icon}
                  </Badge>
                ) : (
                  item.icon
                )}
              </ListItemIcon>
              <ListItemText
                primary={item.text}
                primaryTypographyProps={{
                  fontWeight: currentPath === item.path ? 600 : 500,
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
  const navigate = useNavigate()
  const location = useLocation()
  const { mode, toggleMode } = useTheme()

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
      mode={mode}
      menuItems={menuItems}
      currentPath={location.pathname}
      onNavigate={handleNavigate}
    />
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          ml: { sm: `${drawerWidth}px` },
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{
              mr: 2,
              display: { sm: 'none' },
              '&:hover': {
                background: mode === 'dark' ? 'rgba(100, 181, 246, 0.1)' : 'rgba(25, 118, 210, 0.08)',
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
              fontWeight: 600,
              background: mode === 'dark'
                ? 'linear-gradient(135deg, #64b5f6 0%, #90caf9 100%)'
                : 'linear-gradient(135deg, #1976d2 0%, #42a5f5 100%)',
              backgroundClip: 'text',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
            }}
          >
            {menuItems.find(item => item.path === location.pathname)?.text || 'Base Station Platform'}
          </Typography>
          <Tooltip title={mode === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}>
            <IconButton
              onClick={toggleMode}
              sx={{
                color: mode === 'dark' ? '#ffb74d' : '#1976d2',
                background: mode === 'dark'
                  ? 'rgba(255, 183, 77, 0.1)'
                  : 'rgba(25, 118, 210, 0.1)',
                '&:hover': {
                  background: mode === 'dark'
                    ? 'rgba(255, 183, 77, 0.2)'
                    : 'rgba(25, 118, 210, 0.2)',
                  transform: 'rotate(15deg) scale(1.1)',
                },
                transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                border: mode === 'dark'
                  ? '1px solid rgba(255, 183, 77, 0.3)'
                  : '1px solid rgba(25, 118, 210, 0.3)',
              }}
            >
              {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Logout">
            <IconButton
              onClick={() => handleLogout(navigate)}
              sx={{
                ml: 1,
                color: mode === 'dark' ? '#ef5350' : '#d32f2f',
                background: mode === 'dark'
                  ? 'rgba(239, 83, 80, 0.1)'
                  : 'rgba(211, 47, 47, 0.1)',
                '&:hover': {
                  background: mode === 'dark'
                    ? 'rgba(239, 83, 80, 0.2)'
                    : 'rgba(211, 47, 47, 0.2)',
                  transform: 'scale(1.1)',
                },
                transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                border: mode === 'dark'
                  ? '1px solid rgba(239, 83, 80, 0.3)'
                  : '1px solid rgba(211, 47, 47, 0.3)',
              }}
            >
              <LogoutIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>
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
            },
          }}
          open
        >
          {drawer}
        </Drawer>
      </Box>
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          backgroundColor: 'background.default',
          minHeight: '100vh',
          background: mode === 'dark'
            ? 'radial-gradient(ellipse at top, rgba(100, 181, 246, 0.05) 0%, transparent 50%), radial-gradient(ellipse at bottom, rgba(186, 104, 200, 0.05) 0%, transparent 50%), #0a0e27'
            : 'radial-gradient(ellipse at top, rgba(25, 118, 210, 0.03) 0%, transparent 50%), radial-gradient(ellipse at bottom, rgba(156, 39, 176, 0.03) 0%, transparent 50%), #f5f7fa',
          transition: 'background 0.3s ease-in-out',
        }}
      >
        <Toolbar />
        {children}
      </Box>
    </Box>
  )
}

