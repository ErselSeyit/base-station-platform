import { Edit as EditIcon } from '@mui/icons-material'
import { Alert, Box, Button, Card, CardContent, Chip, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useState, useEffect } from 'react'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import { useNavigate } from 'react-router-dom'
import LoadingSpinner from '../components/LoadingSpinner'
import { useTheme } from '../contexts/ThemeContext'
import { stationApi } from '../services/api'
import { BaseStation, StationStatus } from '../types'

// Fix for default marker icons in React-Leaflet
// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
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

// Helper function to determine status color
function getStatusColor(status: StationStatus): 'success' | 'warning' | 'error' {
  if (status === StationStatus.ACTIVE) return 'success'
  if (status === StationStatus.MAINTENANCE) return 'warning'
  return 'error'
}

export default function MapView() {
  const navigate = useNavigate()
  const { mode } = useTheme()
  const [selectedStation, setSelectedStation] = useState<BaseStation | null>(null)
  const { data, isLoading } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  const stations = Array.isArray(data) ? data : []

  // Filter out stations with invalid coordinates (latitude must be -90 to 90, longitude -180 to 180)
  const validStations = stations.filter((s: BaseStation) =>
    s.latitude >= -90 && s.latitude <= 90 &&
    s.longitude >= -180 && s.longitude <= 180 &&
    !Number.isNaN(s.latitude) && !Number.isNaN(s.longitude)
  )

  const invalidStations = stations.filter((s: BaseStation) =>
    !(s.latitude >= -90 && s.latitude <= 90 &&
      s.longitude >= -180 && s.longitude <= 180 &&
      !Number.isNaN(s.latitude) && !Number.isNaN(s.longitude))
  )

  // Calculate center point (average of valid stations)
  const centerLat = validStations.length > 0
    ? validStations.reduce((sum: number, s: BaseStation) => sum + s.latitude, 0) / validStations.length
    : 41.0064 // Default to Istanbul if no valid stations
  const centerLng = validStations.length > 0
    ? validStations.reduce((sum: number, s: BaseStation) => sum + s.longitude, 0) / validStations.length
    : 28.9759 // Default to Istanbul if no valid stations

  if (isLoading) {
    return <LoadingSpinner />
  }

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography
          variant="h3"
          gutterBottom
          sx={{
            fontWeight: 700,
            mb: 1,
            background: mode === 'dark'
              ? 'linear-gradient(135deg, #64b5f6 0%, #90caf9 50%, #ba68c8 100%)'
              : 'linear-gradient(135deg, #1976d2 0%, #42a5f5 50%, #9c27b0 100%)',
            backgroundClip: 'text',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
          }}
        >
          Map View
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Interactive map showing all base station locations
        </Typography>
      </Box>

      {invalidStations.length > 0 && (
        <Alert
          severity="warning"
          sx={{
            mb: 2,
            borderRadius: 2,
            boxShadow: mode === 'dark'
              ? '0 4px 12px rgba(255, 167, 38, 0.2)'
              : '0 4px 12px rgba(237, 108, 2, 0.15)',
          }}
        >
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
            {invalidStations.length} station(s) have invalid coordinates and cannot be displayed on the map:
          </Typography>
          {invalidStations.map((s: BaseStation) => (
            <Box
              key={s.id}
              sx={{
                mb: 1,
                p: 1.5,
                borderRadius: 1,
                background: mode === 'dark'
                  ? 'rgba(255, 167, 38, 0.1)'
                  : 'rgba(255, 152, 0, 0.08)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {s.stationName} ({s.location})
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Current: Lat {s.latitude}, Lng {s.longitude} (Invalid - must be Lat: -90 to 90, Lng: -180 to 180)
                </Typography>
                {s.location.toLowerCase().includes('istanbul') && (
                  <Typography variant="caption" color="success.main" sx={{ display: 'block', mt: 0.5 }}>
                    💡 For Istanbul, use: Lat 41.0064, Lng 28.9759
                  </Typography>
                )}
              </Box>
              <Button
                size="small"
                variant="outlined"
                startIcon={<EditIcon />}
                onClick={() => navigate(`/stations/${s.id}`)}
                sx={{ ml: 2 }}
              >
                Edit
              </Button>
            </Box>
          ))}
        </Alert>
      )}

      <Card sx={{ height: 'calc(100vh - 200px)', minHeight: 600 }}>
        <CardContent sx={{ height: '100%', p: 0 }}>
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
                  <Box>
                    <Typography variant="h6" gutterBottom>
                      {station.stationName}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      {station.location}
                    </Typography>
                    <Chip
                      label={station.status}
                      size="small"
                      sx={{ mt: 1 }}
                      color={getStatusColor(station.status)}
                    />
                    <Typography variant="body2" sx={{ mt: 1 }}>
                      Power: {station.powerConsumption?.toFixed(1)} kW
                    </Typography>
                    <Typography variant="caption" color="textSecondary" sx={{ display: 'block', mt: 1 }}>
                      Coordinates: {station.latitude.toFixed(4)}, {station.longitude.toFixed(4)}
                    </Typography>
                  </Box>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        </CardContent>
      </Card>

      {selectedStation && (
        <Card sx={{ mt: 2 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Selected Station: {selectedStation.stationName}
            </Typography>
            <Typography variant="body2">
              Location: {selectedStation.location}
            </Typography>
            <Typography variant="body2">
              Coordinates: {selectedStation.latitude}, {selectedStation.longitude}
            </Typography>
            <Typography variant="body2">
              Type: {selectedStation.stationType}
            </Typography>
            <Typography variant="body2">
              Status: {selectedStation.status}
            </Typography>
            <Typography variant="body2">
              Power Consumption: {selectedStation.powerConsumption?.toFixed(1)} kW
            </Typography>
          </CardContent>
        </Card>
      )}
    </Box>
  )
}

