import { createContext, useContext, useState, useEffect, useMemo, ReactNode } from 'react'
import { ThemeProvider as MuiThemeProvider, createTheme, Theme } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'

type ThemeMode = 'light' | 'dark'

interface ThemeContextType {
  mode: ThemeMode
  toggleMode: () => void
  theme: Theme
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined)

// Export hook separately to avoid react-refresh warning
// eslint-disable-next-line react-refresh/only-export-components
export const useTheme = () => {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used within ThemeContextProvider')
  }
  return context
}

// Theme palette configuration helpers
const getDarkPalette = () => ({
  mode: 'dark' as const,
  primary: { main: '#64b5f6', dark: '#42a5f5', light: '#90caf9' },
  secondary: { main: '#ba68c8', dark: '#ab47bc', light: '#ce93d8' },
  background: { default: '#0a0e27', paper: '#1a1f3a' },
  text: { primary: '#e0e0e0', secondary: '#b0b0b0' },
  success: { main: '#66bb6a', light: '#81c784' },
  warning: { main: '#ffa726', light: '#ffb74d' },
  error: { main: '#ef5350', light: '#e57373' },
  info: { main: '#42a5f5', light: '#64b5f6' },
})

const getLightPalette = () => ({
  mode: 'light' as const,
  primary: { main: '#1976d2', dark: '#115293', light: '#42a5f5' },
  secondary: { main: '#9c27b0', dark: '#7b1fa2', light: '#ba68c8' },
  background: { default: '#f5f7fa', paper: '#ffffff' },
  text: { primary: '#1a1a1a', secondary: '#666666' },
  success: { main: '#2e7d32', light: '#4caf50' },
  warning: { main: '#ed6c02', light: '#ff9800' },
  error: { main: '#d32f2f', light: '#f44336' },
  info: { main: '#1976d2', light: '#42a5f5' },
})

const getPaletteConfig = (isDark: boolean) => isDark ? getDarkPalette() : getLightPalette()

const getTypographyConfig = () => ({
  fontFamily: '"Plus Jakarta Sans", "Roboto", "Helvetica", sans-serif',
  h1: {
    fontFamily: '"Outfit", sans-serif',
    fontWeight: 700,
    letterSpacing: '-0.03em',
  },
  h2: {
    fontFamily: '"Outfit", sans-serif',
    fontWeight: 700,
    letterSpacing: '-0.02em',
  },
  h3: {
    fontFamily: '"Outfit", sans-serif',
    fontWeight: 600,
    letterSpacing: '-0.01em',
  },
  h4: {
    fontFamily: '"Outfit", sans-serif',
    fontWeight: 600,
  },
  h5: {
    fontFamily: '"Outfit", sans-serif',
    fontWeight: 600,
  },
  h6: {
    fontFamily: '"Outfit", sans-serif',
    fontWeight: 600,
  },
  button: {
    fontWeight: 600,
    letterSpacing: '0.02em',
  },
  overline: {
    fontFamily: '"JetBrains Mono", monospace',
    fontWeight: 500,
    letterSpacing: '0.1em',
  },
})

const getButtonStyles = (isDark: boolean) => ({
  root: {
    textTransform: 'none' as const,
    borderRadius: 10,
    padding: '10px 24px',
    fontWeight: 600,
    boxShadow: isDark
      ? '0 4px 14px 0 rgba(100, 181, 246, 0.15)'
      : '0 2px 8px rgba(25, 118, 210, 0.2)',
    '&:hover': {
      boxShadow: isDark
        ? '0 6px 20px 0 rgba(100, 181, 246, 0.25)'
        : '0 4px 12px rgba(25, 118, 210, 0.3)',
      transform: 'translateY(-1px)',
    },
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
  },
  contained: {
    background: isDark
      ? 'linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%)'
      : 'linear-gradient(135deg, #1976d2 0%, #1565c0 100%)',
    '&:hover': {
      background: isDark
        ? 'linear-gradient(135deg, #42a5f5 0%, #2196f3 100%)'
        : 'linear-gradient(135deg, #1565c0 0%, #0d47a1 100%)',
    },
  },
})

const getCardStyles = (isDark: boolean) => ({
  root: {
    borderRadius: 16,
    background: isDark
      ? 'linear-gradient(135deg, rgba(26, 31, 58, 0.8) 0%, rgba(20, 25, 45, 0.8) 100%)'
      : 'linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 255, 255, 0.7) 100%)',
    backdropFilter: 'blur(10px)',
    border: isDark
      ? '1px solid rgba(100, 181, 246, 0.1)'
      : '1px solid rgba(255, 255, 255, 0.8)',
    boxShadow: isDark
      ? '0 8px 32px 0 rgba(0, 0, 0, 0.37), 0 2px 8px rgba(100, 181, 246, 0.1)'
      : '0 8px 32px 0 rgba(31, 38, 135, 0.15), 0 2px 8px rgba(0, 0, 0, 0.1)',
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    '&:hover': {
      transform: 'translateY(-4px)',
      boxShadow: isDark
        ? '0 12px 40px 0 rgba(0, 0, 0, 0.5), 0 4px 12px rgba(100, 181, 246, 0.15)'
        : '0 12px 40px 0 rgba(31, 38, 135, 0.2), 0 4px 12px rgba(0, 0, 0, 0.15)',
    },
  },
})

const getAppBarStyles = (isDark: boolean) => ({
  root: {
    background: isDark
      ? 'linear-gradient(135deg, rgba(26, 31, 58, 0.95) 0%, rgba(20, 25, 45, 0.95) 100%)'
      : 'linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(250, 252, 255, 0.95) 100%)',
    backdropFilter: 'blur(20px)',
    borderBottom: isDark
      ? '1px solid rgba(100, 181, 246, 0.1)'
      : '1px solid rgba(0, 0, 0, 0.05)',
    boxShadow: isDark
      ? '0 4px 20px rgba(0, 0, 0, 0.3)'
      : '0 2px 10px rgba(0, 0, 0, 0.08)',
  },
})

const getDrawerStyles = (isDark: boolean) => ({
  paper: {
    background: isDark
      ? 'linear-gradient(180deg, #1a1f3a 0%, #0f1425 100%)'
      : 'linear-gradient(180deg, #ffffff 0%, #f8f9fa 100%)',
    borderRight: isDark
      ? '1px solid rgba(100, 181, 246, 0.1)'
      : '1px solid rgba(0, 0, 0, 0.05)',
    isolation: 'isolate' as const,
  },
})

const getTextFieldStyles = (isDark: boolean) => ({
  root: {
    '& .MuiOutlinedInput-root': {
      borderRadius: 12,
      transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
      '&:hover': {
        transform: 'translateY(-1px)',
      },
      '&.Mui-focused': {
        transform: 'translateY(-2px)',
        boxShadow: isDark
          ? '0 4px 12px rgba(100, 181, 246, 0.2)'
          : '0 4px 12px rgba(25, 118, 210, 0.15)',
      },
    },
  },
})

const getComponentsConfig = (isDark: boolean) => ({
  MuiButton: { styleOverrides: getButtonStyles(isDark) },
  MuiCard: { styleOverrides: getCardStyles(isDark) },
  MuiAppBar: { styleOverrides: getAppBarStyles(isDark) },
  MuiDrawer: { styleOverrides: getDrawerStyles(isDark) },
  MuiTextField: { styleOverrides: getTextFieldStyles(isDark) },
  MuiChip: {
    styleOverrides: {
      root: {
        borderRadius: 8,
        fontWeight: 600,
        fontSize: '0.75rem',
      },
    },
  },
})

const createAppTheme = (mode: ThemeMode): Theme => {
  const isDark = mode === 'dark'

  return createTheme({
    palette: getPaletteConfig(isDark),
    typography: getTypographyConfig(),
    shape: {
      borderRadius: 12,
    },
    components: getComponentsConfig(isDark),
  })
}

interface ThemeContextProviderProps {
  children: ReactNode
}

export const ThemeContextProvider = ({ children }: ThemeContextProviderProps) => {
  const [mode, setMode] = useState<ThemeMode>(() => {
    const saved = localStorage.getItem('themeMode')
    return (saved as ThemeMode) || 'light'
  })

  useEffect(() => {
    localStorage.setItem('themeMode', mode)
  }, [mode])

  const toggleMode = () => {
    setMode((prevMode) => (prevMode === 'light' ? 'dark' : 'light'))
  }

  const theme = useMemo(() => createAppTheme(mode), [mode])

  const contextValue = useMemo(
    () => ({ mode, toggleMode, theme }),
    [mode, theme]
  )

  return (
    <ThemeContext.Provider value={contextValue}>
      <MuiThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </MuiThemeProvider>
    </ThemeContext.Provider>
  )
}

