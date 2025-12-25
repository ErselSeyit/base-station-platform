import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  LocationOn as LocationIcon,
  Visibility as ViewIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  SelectChangeEvent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTheme } from '../contexts/ThemeContext'
import { stationApi } from '../services/api'
import { BaseStation, StationStatus, StationType } from '../types'

export default function Stations() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { mode } = useTheme()
  const [openDialog, setOpenDialog] = useState(false)
  const [editingStation, setEditingStation] = useState<BaseStation | null>(null)
  const [formData, setFormData] = useState<Partial<BaseStation>>({
    stationName: '',
    location: '',
    latitude: 0,
    longitude: 0,
    stationType: StationType.MACRO_CELL,
    status: StationStatus.ACTIVE,
    powerConsumption: 0,
  })

  const { data, isLoading, error } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      // Axios wraps response in data, and our API returns array directly
      return response.data
    },
  })

  // Handle response - API returns array directly
  const stations = Array.isArray(data) ? data : []

  const createMutation = useMutation({
    mutationFn: stationApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stations'] })
      setOpenDialog(false)
      resetForm()
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<BaseStation> }) => stationApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stations'] })
      setOpenDialog(false)
      resetForm()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: stationApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stations'] })
    },
  })

  const resetForm = () => {
    setFormData({
      stationName: '',
      location: '',
      latitude: 0,
      longitude: 0,
      stationType: StationType.MACRO_CELL,
      status: StationStatus.ACTIVE,
      powerConsumption: 0,
    })
    setEditingStation(null)
  }

  const handleOpenDialog = (station?: BaseStation) => {
    if (station) {
      setEditingStation(station)
      setFormData(station)
    } else {
      resetForm()
    }
    setOpenDialog(true)
  }

  const handleSubmit = () => {
    if (editingStation?.id) {
      updateMutation.mutate({ id: editingStation.id, data: formData }, {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: ['stations'] })
        },
      })
    } else {
      createMutation.mutate(formData as BaseStation, {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: ['stations'] })
        },
      })
    }
  }

  const getStatusColor = (status: StationStatus) => {
    switch (status) {
      case StationStatus.ACTIVE:
        return 'success'
      case StationStatus.MAINTENANCE:
        return 'warning'
      case StationStatus.OFFLINE:
        return 'error'
      default:
        return 'default'
    }
  }

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
        <Box>
          <Typography
            variant="h3"
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
            Base Stations
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage and monitor your base station infrastructure
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => handleOpenDialog()}
          sx={{
            height: '48px',
            px: 3,
          }}
        >
          Add Station
        </Button>
      </Box>

      <Card>
        <CardContent sx={{ p: 0 }}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow
                  sx={{
                    background: mode === 'dark'
                      ? 'linear-gradient(135deg, rgba(100, 181, 246, 0.1) 0%, rgba(66, 165, 245, 0.05) 100%)'
                      : 'linear-gradient(135deg, rgba(25, 118, 210, 0.08) 0%, rgba(21, 101, 192, 0.05) 100%)',
                  }}
                >
                  <TableCell sx={{ fontWeight: 600 }}>ID</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Station Name</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Location</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Type</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Power (kW)</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {stations.length === 0 && !isLoading ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center">
                      <Typography variant="body2" color="text.secondary">
                        {error ? `Error loading stations: ${error.message}` : 'No stations found. Click "Add Station" to create one.'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  stations.map((station: BaseStation) => (
                    <TableRow
                      key={station.id}
                      hover
                      sx={{
                        transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
                        '&:hover': {
                          background: mode === 'dark'
                            ? 'rgba(100, 181, 246, 0.05)'
                            : 'rgba(25, 118, 210, 0.04)',
                          transform: 'scale(1.01)',
                        },
                      }}
                    >
                      <TableCell>{station.id}</TableCell>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>
                          {station.stationName}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Box display="flex" alignItems="center" gap={1}>
                          <LocationIcon fontSize="small" color="action" />
                          {station.location}
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={station.stationType}
                          size="small"
                          sx={{
                            fontWeight: 500,
                            background: mode === 'dark'
                              ? 'rgba(100, 181, 246, 0.15)'
                              : 'rgba(25, 118, 210, 0.1)',
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={station.status}
                          color={getStatusColor(station.status) as 'success' | 'warning' | 'error' | 'default'}
                          size="small"
                          sx={{ fontWeight: 600 }}
                        />
                      </TableCell>
                      <TableCell sx={{ fontWeight: 500 }}>{station.powerConsumption?.toFixed(1) || 'N/A'}</TableCell>
                      <TableCell align="right">
                        <Box display="flex" gap={1} justifyContent="flex-end">
                          <Tooltip title="View Details">
                            <IconButton
                              size="small"
                              onClick={() => navigate(`/stations/${station.id}`)}
                              sx={{
                                '&:hover': {
                                  background: mode === 'dark'
                                    ? 'rgba(100, 181, 246, 0.2)'
                                    : 'rgba(25, 118, 210, 0.1)',
                                  transform: 'scale(1.1)',
                                },
                                transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
                              }}
                            >
                              <ViewIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Edit">
                            <IconButton
                              size="small"
                              onClick={() => handleOpenDialog(station)}
                              sx={{
                                '&:hover': {
                                  background: mode === 'dark'
                                    ? 'rgba(100, 181, 246, 0.2)'
                                    : 'rgba(25, 118, 210, 0.1)',
                                  transform: 'scale(1.1)',
                                },
                                transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
                              }}
                            >
                              <EditIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Delete">
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => {
                                if (window.confirm('Are you sure you want to delete this station?')) {
                                  deleteMutation.mutate(station.id!)
                                }
                              }}
                              sx={{
                                '&:hover': {
                                  background: mode === 'dark'
                                    ? 'rgba(239, 83, 80, 0.2)'
                                    : 'rgba(211, 47, 47, 0.1)',
                                  transform: 'scale(1.1)',
                                },
                                transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
                              }}
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      <Dialog open={openDialog} onClose={() => setOpenDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {editingStation ? 'Edit Station' : 'Create New Station'}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <TextField
              id="station-name"
              name="stationName"
              fullWidth
              label="Station Name"
              value={formData.stationName}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, stationName: e.target.value })}
              margin="normal"
              required
              inputProps={{
                name: 'stationName',
              }}
            />
            <TextField
              id="station-location"
              name="location"
              fullWidth
              label="Location"
              value={formData.location}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, location: e.target.value })}
              margin="normal"
              required
              inputProps={{
                name: 'location',
              }}
            />
            <Box display="flex" gap={2}>
              <TextField
                id="station-latitude"
                name="latitude"
                fullWidth
                label="Latitude"
                type="number"
                value={formData.latitude}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, latitude: parseFloat(e.target.value) })}
                margin="normal"
                required
                inputProps={{
                  name: 'latitude',
                }}
              />
              <TextField
                id="station-longitude"
                name="longitude"
                fullWidth
                label="Longitude"
                type="number"
                value={formData.longitude}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, longitude: parseFloat(e.target.value) })}
                margin="normal"
                required
                inputProps={{
                  name: 'longitude',
                }}
              />
            </Box>
            <FormControl fullWidth margin="normal">
              <InputLabel id="station-type-label">
                Station Type
              </InputLabel>
              <Select
                id="station-type"
                name="stationType"
                labelId="station-type-label"
                value={formData.stationType}
                onChange={(e: SelectChangeEvent) => setFormData({ ...formData, stationType: e.target.value as StationType })}
                label="Station Type"
                inputProps={{
                  id: 'station-type',
                  name: 'stationType',
                }}
              >
                {Object.values(StationType).map((type) => (
                  <MenuItem key={type} value={type}>
                    {type}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl fullWidth margin="normal">
              <InputLabel id="station-status-label">
                Status
              </InputLabel>
              <Select
                id="station-status"
                name="status"
                labelId="station-status-label"
                value={formData.status}
                onChange={(e: SelectChangeEvent) => setFormData({ ...formData, status: e.target.value as StationStatus })}
                label="Status"
                inputProps={{
                  id: 'station-status',
                  name: 'status',
                }}
              >
                {Object.values(StationStatus).map((status) => (
                  <MenuItem key={status} value={status}>
                    {status}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              id="station-power-consumption"
              name="powerConsumption"
              fullWidth
              label="Power Consumption (kW)"
              type="number"
              value={formData.powerConsumption}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, powerConsumption: parseFloat(e.target.value) })}
              margin="normal"
              inputProps={{
                name: 'powerConsumption',
              }}
            />
            <TextField
              id="station-description"
              name="description"
              fullWidth
              label="Description"
              value={formData.description || ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, description: e.target.value })}
              margin="normal"
              multiline
              rows={3}
              inputProps={{
                name: 'description',
              }}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenDialog(false)}>Cancel</Button>
          <Button
            onClick={handleSubmit}
            variant="contained"
            disabled={createMutation.isPending || updateMutation.isPending}
          >
            {createMutation.isPending || updateMutation.isPending ? (
              <CircularProgress size={24} />
            ) : (
              editingStation ? 'Update' : 'Create'
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

