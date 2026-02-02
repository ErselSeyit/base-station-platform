import { Edit as EditIcon, LocationOn as LocationIcon } from '@mui/icons-material'
import { Alert, Box, Button, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import { useState, useEffect, useMemo } from 'react'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import { useNavigate } from 'react-router-dom'
import ErrorDisplay from '../components/ErrorDisplay'
import LoadingSpinner from '../components/LoadingSpinner'
import { getStationStatusColor, GRID_AUTO_FIT_SX } from '../constants/designSystem'
import { stationApi } from '../services/api'
import { BaseStation } from '../types'
import { ensureArray, partition, avg } from '../utils/arrayUtils'

// Fix for default marker icons in React-Leaflet - bundle locally instead of CDN
// The _getIconUrl property needs to be deleted to prevent CDN URL generation
// @ts-expect-error _getIconUrl exists at runtime but is not in TypeScript definition
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
})

// World bounds to prevent panning outside valid coordinates
const worldBounds = L.latLngBounds(
  L.latLng(-85, -180), // Southwest corner
  L.latLng(85, 180)    // Northeast corner
)

// Component to handle map configuration and bounds
function MapController({ stations }: { stations: BaseStation[] }) {
  const map = useMap()

  useEffect(() => {
    // Set max bounds to prevent panning outside world
    map.setMaxBounds(worldBounds)
    map.setMinZoom(2)
    map.setMaxZoom(18)

    // If we have stations, fit bounds to show all of them
    if (stations.length > 0) {
      const bounds = L.latLngBounds(
        stations.map(s => L.latLng(s.latitude, s.longitude))
      )
      // Add padding around stations
      map.fitBounds(bounds.pad(0.2), { maxZoom: 14 })
    }
  }, [map, stations])

  return null
}

export default function MapView() {
  const navigate = useNavigate()
  const [selectedStation, setSelectedStation] = useState<BaseStation | null>(null)
  const { data, isLoading, error } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  const stations = ensureArray(data as BaseStation[])

  // Memoize partition and center calculations - expensive with large datasets
  const { validStations, invalidStations, centerLat, centerLng } = useMemo(() => {
    const isValidCoordinates = (s: BaseStation): boolean =>
      s.latitude >= -90 && s.latitude <= 90 &&
      s.longitude >= -180 && s.longitude <= 180 &&
      !Number.isNaN(s.latitude) && !Number.isNaN(s.longitude)

    const { pass, fail } = partition(stations, isValidCoordinates)

    return {
      validStations: pass,
      invalidStations: fail,
      centerLat: avg(pass, s => s.latitude, 41.0064), // Default to Istanbul
      centerLng: avg(pass, s => s.longitude, 28.9759),
    }
  }, [stations])

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (error) {
    return <ErrorDisplay title="Failed to load map data" message={error.message} />
  }

  return (
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: { xs: '16px 12px', sm: '24px 16px', md: '32px 24px' } }}>
      {/* Header - Brutally minimal */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        sx={{ marginBottom: '32px' }}
      >
        <Typography
          variant="h1"
          sx={{
            fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2.25rem' },
            fontWeight: 700,
            letterSpacing: '-0.025em',
            color: 'var(--mono-950)',
            marginBottom: '8px',
          }}
        >
          Map View
        </Typography>
        <Typography
          sx={{
            fontSize: '0.875rem',
            color: 'var(--mono-500)',
            letterSpacing: '0.01em',
          }}
        >
          Geographic distribution of infrastructure · {validStations.length} mapped stations
        </Typography>
      </Box>

      {/* Invalid coordinates warning - minimal */}
      {invalidStations.length > 0 && (
        <Box
          component={motion.div}
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
          sx={{ marginBottom: '24px' }}
        >
          <Alert
            severity="warning"
            sx={{
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderLeft: '3px solid var(--status-maintenance)',
              borderRadius: '8px',
              padding: '12px 16px',
              fontSize: '0.875rem',
              '& .MuiAlert-icon': {
                fontSize: '18px',
                color: 'var(--status-maintenance)',
              },
            }}
          >
            <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, marginBottom: '8px', color: 'var(--mono-950)' }}>
              {invalidStations.length} station(s) have invalid coordinates
            </Typography>
            {invalidStations.map((s: BaseStation) => (
              <Box
                key={s.id}
                sx={{
                  marginBottom: '8px',
                  padding: '12px',
                  borderRadius: '6px',
                  background: 'var(--surface-base)',
                  border: '1px solid var(--surface-border)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  '&:last-child': {
                    marginBottom: 0,
                  },
                }}
              >
                <Box>
                  <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--mono-950)' }}>
                    {s.stationName}
                  </Typography>
                  <Typography sx={{ fontSize: '0.75rem', color: 'var(--mono-500)', fontFamily: "'JetBrains Mono', monospace" }}>
                    Lat {s.latitude}, Lng {s.longitude}
                  </Typography>
                  {s.location.toLowerCase().includes('istanbul') && (
                    <Typography sx={{ fontSize: '0.75rem', color: 'var(--status-active)', marginTop: '4px' }}>
                      Suggested: Lat 41.0064, Lng 28.9759
                    </Typography>
                  )}
                </Box>
                <Button
                  size="small"
                  variant="outlined"
                  aria-label={`Edit coordinates for ${s.stationName}`}
                  startIcon={<EditIcon />}
                  onClick={() => navigate(`/stations/${s.id}`)}
                  sx={{
                    marginLeft: '12px',
                    borderColor: 'var(--surface-border)',
                    color: 'var(--mono-700)',
                    textTransform: 'none',
                    fontSize: '0.8125rem',
                    '&:hover': {
                      borderColor: 'var(--mono-400)',
                      background: 'var(--surface-hover)',
                    },
                  }}
                >
                  Edit
                </Button>
              </Box>
            ))}
          </Alert>
        </Box>
      )}

      {/* Map container - minimal chrome */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
        sx={{
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          borderRadius: '12px',
          overflow: 'hidden',
          height: { xs: 'calc(100vh - 200px)', sm: 'calc(100vh - 240px)', md: 'calc(100vh - 280px)' },
          minHeight: { xs: '400px', sm: '500px', md: '600px' },
          transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
          '&:hover': {
            boxShadow: 'var(--shadow-md)',
            borderColor: 'var(--mono-400)',
          },
        }}
      >
        <MapContainer
          center={[centerLat, centerLng]}
          zoom={validStations.length > 0 ? 10 : 4}
          style={{ height: '100%', width: '100%', zIndex: 1 }}
          maxBounds={worldBounds}
          maxBoundsViscosity={1}
          minZoom={2}
          maxZoom={18}
          worldCopyJump={true}
        >
          <MapController stations={validStations} />
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            noWrap={false}
          />
          {validStations.map((station: BaseStation) => (
            <Marker
              key={station.id}
              position={[station.latitude, station.longitude]}
              eventHandlers={{
                click: () => setSelectedStation(station),
              }}
            >
              <Popup>
                <Box sx={{ padding: '4px' }}>
                  <Typography sx={{ fontSize: '0.9375rem', fontWeight: 600, color: 'var(--mono-950)', marginBottom: '4px' }}>
                    {station.stationName}
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
                    <LocationIcon sx={{ fontSize: '14px', color: 'var(--mono-500)' }} />
                    <Typography sx={{ fontSize: '0.8125rem', color: 'var(--mono-600)' }}>
                      {station.location}
                    </Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
                    <Box
                      sx={{
                        width: '6px',
                        height: '6px',
                        borderRadius: '50%',
                        background: getStationStatusColor(station.status),
                      }}
                    />
                    <Typography sx={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--mono-700)' }}>
                      {station.status}
                    </Typography>
                  </Box>
                  <Typography sx={{ fontSize: '0.8125rem', color: 'var(--mono-600)', marginBottom: '4px' }}>
                    Type: {station.stationType}
                  </Typography>
                  <Typography sx={{ fontSize: '0.8125rem', fontFamily: "'JetBrains Mono', monospace", color: 'var(--mono-700)', marginBottom: '8px' }}>
                    {station.powerConsumption?.toFixed(1)} kW
                  </Typography>
                  <Typography sx={{ fontSize: '0.6875rem', fontFamily: "'JetBrains Mono', monospace", color: 'var(--mono-500)' }}>
                    {station.latitude.toFixed(4)}, {station.longitude.toFixed(4)}
                  </Typography>
                </Box>
              </Popup>
            </Marker>
          ))}
        </MapContainer>
      </Box>

      {/* Selected station details - minimal card */}
      {selectedStation && (
        <Box
          component={motion.div}
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
          sx={{
            marginTop: '24px',
            background: 'var(--surface-base)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
            padding: '24px',
            transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
            '&:hover': {
              boxShadow: 'var(--shadow-md)',
              borderColor: 'var(--mono-400)',
            },
          }}
        >
          <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--mono-950)', marginBottom: '16px' }}>
            Selected Station
          </Typography>
          <Box sx={GRID_AUTO_FIT_SX}>
            <Box>
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--mono-500)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '4px' }}>
                Name
              </Typography>
              <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-950)' }}>
                {selectedStation.stationName}
              </Typography>
            </Box>
            <Box>
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--mono-500)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '4px' }}>
                Location
              </Typography>
              <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-950)' }}>
                {selectedStation.location}
              </Typography>
            </Box>
            <Box>
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--mono-500)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '4px' }}>
                Type
              </Typography>
              <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-950)' }}>
                {selectedStation.stationType}
              </Typography>
            </Box>
            <Box>
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--mono-500)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '4px' }}>
                Status
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Box
                  sx={{
                    width: '6px',
                    height: '6px',
                    borderRadius: '50%',
                    background: getStationStatusColor(selectedStation.status),
                  }}
                />
                <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-950)' }}>
                  {selectedStation.status}
                </Typography>
              </Box>
            </Box>
            <Box>
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--mono-500)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '4px' }}>
                Power
              </Typography>
              <Typography sx={{ fontSize: '0.875rem', fontFamily: "'JetBrains Mono', monospace", color: 'var(--mono-950)' }}>
                {selectedStation.powerConsumption?.toFixed(1)} kW
              </Typography>
            </Box>
            <Box>
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--mono-500)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '4px' }}>
                Coordinates
              </Typography>
              <Typography sx={{ fontSize: '0.875rem', fontFamily: "'JetBrains Mono', monospace", color: 'var(--mono-950)' }}>
                {selectedStation.latitude.toFixed(4)}, {selectedStation.longitude.toFixed(4)}
              </Typography>
            </Box>
          </Box>
        </Box>
      )}
    </Box>
  )
}
