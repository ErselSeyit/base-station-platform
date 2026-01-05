import { lazy, Suspense } from 'react'
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

