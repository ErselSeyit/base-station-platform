import { lazy, Suspense, useEffect, useState } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { CircularProgress, Box } from '@mui/material'
import Layout from './components/Layout'
import { ToastProvider } from './components/ToastProvider'
import { authService } from './services/authService'

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

function App() {
  const [isValidating, setIsValidating] = useState(true)

  // Validate session on mount to restore auth state from HttpOnly cookies
  useEffect(() => {
    const validateAuth = async () => {
      await authService.validateSession()
      setIsValidating(false)
    }
    validateAuth()
  }, [])

  // Show loader while validating session
  if (isValidating) {
    return <PageLoader />
  }

  return (
    <>
      <ToastProvider />
      <Suspense fallback={<PageLoader />}>
        <Routes>
          {/* Public route */}
          <Route path="/login" element={<Login />} />

          {/* Protected routes */}
          <Route
            path="/*"
            element={
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
            }
          />
        </Routes>
      </Suspense>
    </>
  )
}

export default App

