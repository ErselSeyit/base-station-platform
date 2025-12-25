import {
  Add as AddIcon,
  CellTower as CellTowerIcon,
  Close as CloseIcon,
  MyLocation as CoordinatesIcon,
  Delete as DeleteIcon,
  Description as DescriptionIcon,
  Edit as EditIcon,
  LocationOn as LocationIcon,
  ElectricBolt as PowerIcon,
  Settings as SettingsIcon,
  Visibility as ViewIcon,
} from '@mui/icons-material';
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
  Divider,
  IconButton,
  InputAdornment,
  Slide,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { TransitionProps } from '@mui/material/transitions';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTheme } from '../contexts/ThemeContext';
import { stationApi } from '../services/api';
import { BaseStation, StationStatus, StationType } from '../types';

// Slide transition for dialog
const Transition = React.forwardRef(function Transition(
  props: TransitionProps & { children: React.ReactElement },
  ref: React.Ref<unknown>,
) {
  return <Slide direction="up" ref={ref} {...props} />;
});

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
    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur()
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

      <Dialog
        open={openDialog}
        onClose={() => setOpenDialog(false)}
        maxWidth="sm"
        fullWidth
        TransitionComponent={Transition}
        PaperProps={{
          sx: {
            borderRadius: 3,
            background: mode === 'dark'
              ? 'linear-gradient(145deg, #1a1f3a 0%, #0f1425 100%)'
              : 'linear-gradient(145deg, #ffffff 0%, #f8f9fa 100%)',
            border: mode === 'dark'
              ? '1px solid rgba(100, 181, 246, 0.2)'
              : '1px solid rgba(0, 0, 0, 0.08)',
            boxShadow: mode === 'dark'
              ? '0 25px 50px -12px rgba(0, 0, 0, 0.5)'
              : '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          },
        }}
      >
        {/* Header with gradient */}
        <Box
          sx={{
            background: mode === 'dark'
              ? 'linear-gradient(135deg, rgba(100, 181, 246, 0.15) 0%, rgba(186, 104, 200, 0.15) 100%)'
              : 'linear-gradient(135deg, rgba(25, 118, 210, 0.1) 0%, rgba(156, 39, 176, 0.1) 100%)',
            px: 3,
            py: 2.5,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Box display="flex" alignItems="center" gap={2}>
            <Box
              sx={{
                width: 48,
                height: 48,
                borderRadius: 2,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: mode === 'dark'
                  ? 'linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%)'
                  : 'linear-gradient(135deg, #1976d2 0%, #1565c0 100%)',
                boxShadow: mode === 'dark'
                  ? '0 4px 14px rgba(100, 181, 246, 0.3)'
                  : '0 4px 14px rgba(25, 118, 210, 0.3)',
              }}
            >
              <CellTowerIcon sx={{ color: 'white', fontSize: 28 }} />
            </Box>
            <Box>
              <Typography variant="h5" sx={{ fontWeight: 700, letterSpacing: '-0.01em' }}>
                {editingStation ? 'Edit Station' : 'New Station'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {editingStation ? 'Update station configuration' : 'Configure a new base station'}
              </Typography>
            </Box>
          </Box>
          <IconButton
            onClick={() => setOpenDialog(false)}
            sx={{
              color: 'text.secondary',
              '&:hover': {
                background: mode === 'dark'
                  ? 'rgba(255,255,255,0.1)'
                  : 'rgba(0,0,0,0.05)',
              },
            }}
          >
            <CloseIcon />
          </IconButton>
        </Box>

        <DialogContent sx={{ px: 3, py: 3 }}>
          <Box component="form" onSubmit={(e) => { e.preventDefault(); handleSubmit(); }} noValidate>
            {/* Basic Information Section */}
            <Box sx={{ mb: 3 }}>
              <Box display="flex" alignItems="center" gap={1} mb={2}>
                <CellTowerIcon sx={{ color: 'primary.main', fontSize: 20 }} />
                <Typography variant="subtitle2" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'primary.main' }}>
                  Basic Information
                </Typography>
              </Box>
              <TextField
                id="station-name"
                name="stationName"
                fullWidth
                label="Station Name"
                placeholder="e.g., BS-NYC-001"
                value={formData.stationName}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, stationName: e.target.value })}
                required
                autoComplete="organization"
                autoFocus
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <CellTowerIcon sx={{ color: 'text.secondary', fontSize: 20 }} />
                    </InputAdornment>
                  ),
                }}
                sx={{ mb: 2 }}
              />
            <TextField
              id="station-location"
              name="location"
              fullWidth
              label="Location"
              placeholder="e.g., New York, NY, USA"
              value={formData.location}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, location: e.target.value })}
              required
              autoComplete="street-address"
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LocationIcon sx={{ color: 'text.secondary', fontSize: 20 }} />
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          <Divider sx={{ my: 3, borderColor: mode === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)' }} />

          {/* Coordinates Section */}
          <Box sx={{ mb: 3 }}>
            <Box display="flex" alignItems="center" gap={1} mb={2}>
              <CoordinatesIcon sx={{ color: 'secondary.main', fontSize: 20 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'secondary.main' }}>
                Coordinates
              </Typography>
            </Box>
            <Box display="flex" gap={2}>
              <TextField
                id="station-latitude"
                name="latitude"
                fullWidth
                label="Latitude"
                type="number"
                placeholder="40.7128"
                value={formData.latitude || ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, latitude: parseFloat(e.target.value) || 0 })}
                required
                autoComplete="off"
                inputProps={{
                  step: 'any',
                  min: -90,
                  max: 90,
                }}
                helperText="Range: -90 to 90"
              />
              <TextField
                id="station-longitude"
                name="longitude"
                fullWidth
                label="Longitude"
                type="number"
                placeholder="-74.0060"
                value={formData.longitude || ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, longitude: parseFloat(e.target.value) || 0 })}
                required
                autoComplete="off"
                inputProps={{
                  step: 'any',
                  min: -180,
                  max: 180,
                }}
                helperText="Range: -180 to 180"
              />
            </Box>
          </Box>

          <Divider sx={{ my: 3, borderColor: mode === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)' }} />

          {/* Configuration Section */}
          <Box sx={{ mb: 3 }}>
            <Box display="flex" alignItems="center" gap={1} mb={2}>
              <SettingsIcon sx={{ color: 'warning.main', fontSize: 20 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'warning.main' }}>
                Configuration
              </Typography>
            </Box>
            <Box display="flex" gap={2} mb={2}>
              <TextField
                id="station-type-select"
                name="stationType"
                fullWidth
                select
                label="Station Type"
                value={formData.stationType}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  setFormData({ ...formData, stationType: e.target.value as StationType })
                }
                autoComplete="off"
                SelectProps={{
                  native: true,
                }}
              >
                {Object.values(StationType).map((type) => (
                  <option key={type} value={type}>
                    {type.replace('_', ' ')}
                  </option>
                ))}
              </TextField>

              <TextField
                id="station-status-select"
                name="status"
                fullWidth
                select
                label="Status"
                value={formData.status}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  setFormData({ ...formData, status: e.target.value as StationStatus })
                }
                autoComplete="off"
                SelectProps={{
                  native: true,
                }}
              >
                {Object.values(StationStatus).map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </TextField>
            </Box>
            <TextField
              id="station-power-consumption"
              name="powerConsumption"
              fullWidth
              label="Power Consumption"
              type="number"
              placeholder="1500"
              value={formData.powerConsumption || ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, powerConsumption: parseFloat(e.target.value) || 0 })}
              autoComplete="off"
              inputProps={{
                step: 'any',
                min: 0,
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <PowerIcon sx={{ color: 'text.secondary', fontSize: 20 }} />
                  </InputAdornment>
                ),
                endAdornment: <InputAdornment position="end">kW</InputAdornment>,
              }}
            />
          </Box>

          <Divider sx={{ my: 3, borderColor: mode === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)' }} />

          {/* Description Section */}
          <Box>
            <Box display="flex" alignItems="center" gap={1} mb={2}>
              <DescriptionIcon sx={{ color: 'info.main', fontSize: 20 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'info.main' }}>
                Additional Details
              </Typography>
            </Box>
            <TextField
              id="station-description"
              name="description"
              fullWidth
              label="Description"
              placeholder="Enter station description, notes, or specifications..."
              value={formData.description || ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({ ...formData, description: e.target.value })}
              autoComplete="off"
              multiline
              rows={3}
            />
          </Box>
          </Box>
        </DialogContent>

        <DialogActions sx={{ px: 3, py: 2.5, background: mode === 'dark' ? 'rgba(0,0,0,0.2)' : 'rgba(0,0,0,0.02)' }}>
          <Button
            onClick={() => setOpenDialog(false)}
            sx={{
              px: 3,
              color: 'text.secondary',
              '&:hover': { background: mode === 'dark' ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)' },
            }}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            onClick={handleSubmit}
            variant="contained"
            disabled={createMutation.isPending || updateMutation.isPending || !formData.stationName || !formData.location}
            sx={{
              px: 4,
              py: 1.2,
              background: mode === 'dark'
                ? 'linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%)'
                : 'linear-gradient(135deg, #1976d2 0%, #1565c0 100%)',
              boxShadow: mode === 'dark'
                ? '0 4px 14px rgba(100, 181, 246, 0.3)'
                : '0 4px 14px rgba(25, 118, 210, 0.3)',
              '&:hover': {
                background: mode === 'dark'
                  ? 'linear-gradient(135deg, #42a5f5 0%, #2196f3 100%)'
                  : 'linear-gradient(135deg, #1565c0 0%, #0d47a1 100%)',
                boxShadow: mode === 'dark'
                  ? '0 6px 20px rgba(100, 181, 246, 0.4)'
                  : '0 6px 20px rgba(25, 118, 210, 0.4)',
              },
            }}
          >
            {createMutation.isPending || updateMutation.isPending ? (
              <CircularProgress size={24} sx={{ color: 'white' }} />
            ) : (
              <>
                {editingStation ? 'Update Station' : 'Create Station'}
              </>
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

