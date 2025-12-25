import { createContext, useContext, useState, useEffect, ReactNode } from 'react'
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

const createAppTheme = (mode: ThemeMode): Theme => {
  const isDark = mode === 'dark'
  
  return createTheme({
    palette: {
      mode,
      primary: {
        main: isDark ? '#64b5f6' : '#1976d2',
        dark: isDark ? '#42a5f5' : '#115293',
        light: isDark ? '#90caf9' : '#42a5f5',
      },
      secondary: {
        main: isDark ? '#ba68c8' : '#9c27b0',
        dark: isDark ? '#ab47bc' : '#7b1fa2',
        light: isDark ? '#ce93d8' : '#ba68c8',
      },
      background: {
        default: isDark ? '#0a0e27' : '#f5f7fa',
        paper: isDark ? '#1a1f3a' : '#ffffff',
      },
      text: {
        primary: isDark ? '#e0e0e0' : '#1a1a1a',
        secondary: isDark ? '#b0b0b0' : '#666666',
      },
      success: {
        main: isDark ? '#66bb6a' : '#2e7d32',
        light: isDark ? '#81c784' : '#4caf50',
      },
      warning: {
        main: isDark ? '#ffa726' : '#ed6c02',
        light: isDark ? '#ffb74d' : '#ff9800',
      },
      error: {
        main: isDark ? '#ef5350' : '#d32f2f',
        light: isDark ? '#e57373' : '#f44336',
      },
      info: {
        main: isDark ? '#42a5f5' : '#1976d2',
        light: isDark ? '#64b5f6' : '#42a5f5',
      },
    },
    typography: {
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
    },
    shape: {
      borderRadius: 12,
    },
    components: {
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
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
        },
      },
      MuiCard: {
        styleOverrides: {
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
        },
      },
      MuiAppBar: {
        styleOverrides: {
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
        },
      },
      MuiDrawer: {
        styleOverrides: {
          paper: {
            background: isDark
              ? 'linear-gradient(180deg, #1a1f3a 0%, #0f1425 100%)'
              : 'linear-gradient(180deg, #ffffff 0%, #f8f9fa 100%)',
            borderRight: isDark
              ? '1px solid rgba(100, 181, 246, 0.1)'
              : '1px solid rgba(0, 0, 0, 0.05)',
          },
        },
      },
      MuiTextField: {
        styleOverrides: {
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
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            borderRadius: 8,
            fontWeight: 600,
            fontSize: '0.75rem',
          },
        },
      },
    },
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

  const theme = createAppTheme(mode)

  return (
    <ThemeContext.Provider value={{ mode, toggleMode, theme }}>
      <MuiThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </MuiThemeProvider>
    </ThemeContext.Provider>
  )
}

