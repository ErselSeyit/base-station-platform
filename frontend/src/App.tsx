import { lazy, Suspense, useEffect } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { CircularProgress, Box } from '@mui/material'
import Layout from './components/Layout'
import { ToastProvider } from './components/ToastProvider'
import { authService } from './services/authService'
import { useRoutePrefetch } from './hooks/useRoutePrefetch'

// Lazy load pages for code splitting
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Stations = lazy(() => import('./pages/Stations'))
const StationDetail = lazy(() => import('./pages/StationDetail'))
const MapView = lazy(() => import('./pages/MapView'))
const Alerts = lazy(() => import('./pages/Alerts'))
const Metrics = lazy(() => import('./pages/Metrics'))
const AIDiagnostics = lazy(() => import('./pages/AIDiagnostics'))
const Reports = lazy(() => import('./pages/Reports'))
const FiveGDashboard = lazy(() => import('./pages/FiveGDashboard'))
const PowerDashboard = lazy(() => import('./pages/PowerDashboard'))
const SONRecommendations = lazy(() => import('./pages/SONRecommendations'))
const Login = lazy(() => import('./pages/Login'))

// Loading fallback component
const PageLoader = () => (
  <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
    <CircularProgress />
  </Box>
)

// Protected route wrapper
function ProtectedRoute({ children }: { readonly children: React.ReactNode }) {
  if (!authService.isAuthenticated()) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

// Hook to handle unauthorized events (session expiry)
function useAuthListener() {
  const navigate = useNavigate()

  useEffect(() => {
    const handleUnauthorized = () => {
      authService.clearLocalState()
      navigate('/login', { replace: true })
    }

    globalThis.addEventListener('auth:unauthorized', handleUnauthorized)
    return () => globalThis.removeEventListener('auth:unauthorized', handleUnauthorized)
  }, [navigate])
}

// Wrapper component that uses the auth listener
function AuthenticatedApp() {
  useAuthListener()
  useRoutePrefetch() // Prefetch likely next routes for faster navigation

  return (
    <ProtectedRoute>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/stations" element={<Stations />} />
          <Route path="/stations/:id" element={<StationDetail />} />
          <Route path="/map" element={<MapView />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route path="/metrics" element={<Metrics />} />
          <Route path="/ai-diagnostics" element={<AIDiagnostics />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/5g" element={<FiveGDashboard />} />
          <Route path="/power" element={<PowerDashboard />} />
          <Route path="/son" element={<SONRecommendations />} />
          {/* 404 catch-all */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Layout>
    </ProtectedRoute>
  )
}

function App() {
  return (
    <>
      <ToastProvider />
      <Suspense fallback={<PageLoader />}>
        <Routes>
          {/* Public route */}
          <Route path="/login" element={<Login />} />

          {/* Protected routes with auth listener */}
          <Route path="/*" element={<AuthenticatedApp />} />
        </Routes>
      </Suspense>
    </>
  )
}

export default App

