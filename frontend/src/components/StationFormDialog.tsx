import {
  CellTower as CellTowerIcon,
  Close as CloseIcon,
  Description as DescriptionIcon,
  MyLocation as CoordinatesIcon,
  LocationOn as LocationIcon,
  ElectricBolt as PowerIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  Divider,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  SelectChangeEvent,
  Slide,
  TextField,
  Typography,
} from '@mui/material'
import { TransitionProps } from '@mui/material/transitions'
import React from 'react'
import { BaseStation, StationStatus, StationType } from '../types'

const Transition = React.forwardRef(function Transition(
  props: TransitionProps & { children: React.ReactElement },
  ref: React.Ref<unknown>,
) {
  return <Slide direction="up" ref={ref} {...props} />
})

interface StationFormDialogProps {
  readonly open: boolean
  readonly onClose: () => void
  readonly onSubmit: () => void
  readonly editingStation: BaseStation | null
  readonly formData: Partial<BaseStation>
  readonly setFormData: (data: Partial<BaseStation>) => void
  readonly isSubmitting: boolean
}

export default function StationFormDialog({
  open,
  onClose,
  onSubmit,
  editingStation,
  formData,
  setFormData,
  isSubmitting,
}: StationFormDialogProps) {
  const isFormValid = formData.stationName && formData.location

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      TransitionComponent={Transition}
      PaperProps={{
        sx: {
          borderRadius: '12px',
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          boxShadow: 'var(--shadow-lg)',
        },
      }}
    >
      {/* Minimal Header */}
      <Box
        sx={{
          px: 3,
          py: 2.5,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid var(--surface-border)',
        }}
      >
        <Box display="flex" alignItems="center" gap={2}>
          <Box
            sx={{
              width: 48,
              height: 48,
              borderRadius: '8px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: 'var(--mono-950)',
              border: '1px solid var(--surface-border)',
            }}
          >
            <CellTowerIcon sx={{ color: 'var(--mono-50)', fontSize: 28 }} />
          </Box>
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 700, letterSpacing: '-0.01em', color: 'var(--mono-950)' }}>
              {editingStation ? 'Edit Station' : 'New Station'}
            </Typography>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
              {editingStation ? 'Update station configuration' : 'Configure a new base station'}
            </Typography>
          </Box>
        </Box>
        <IconButton
          onClick={onClose}
          sx={{
            width: 36,
            height: 36,
            borderRadius: '10px',
            color: 'var(--mono-600)',
            '&:hover': {
              background: 'var(--mono-100)',
              color: 'var(--mono-950)',
            },
          }}
        >
          <CloseIcon sx={{ fontSize: 20 }} />
        </IconButton>
      </Box>

      <DialogContent
        sx={{
          px: 3,
          py: 3,
          '& .MuiTextField-root': {
            '& .MuiOutlinedInput-root': {
              '& fieldset': {
                borderColor: 'var(--surface-border)',
              },
              '&:hover fieldset': {
                borderColor: 'var(--mono-400)',
              },
              '&.Mui-focused fieldset': {
                borderColor: 'var(--mono-600)',
              },
            },
            '& .MuiInputBase-input': {
              color: 'var(--mono-950)',
              '&::placeholder': {
                color: 'var(--mono-400)',
                opacity: 1,
              },
            },
            '& .MuiInputLabel-root': {
              color: 'var(--mono-500)',
              '&.Mui-focused': {
                color: 'var(--mono-700)',
              },
            },
            '& .MuiFormHelperText-root': {
              color: 'var(--mono-500)',
            },
          },
        }}
      >
        <form
          id="station-form"
          name="stationForm"
          autoComplete="on"
          onSubmit={(e) => { e.preventDefault(); onSubmit(); }}
          noValidate
        >
          {/* Basic Information Section */}
          <Box sx={{ mb: 3 }}>
            <Box display="flex" alignItems="center" gap={1} mb={2}>
              <CellTowerIcon sx={{ color: 'var(--mono-500)', fontSize: 20 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--mono-500)' }}>
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
              autoFocus
              inputProps={{
                name: 'stationName',
                id: 'station-name',
                autoComplete: 'organization',
                'aria-label': 'Station Name',
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <CellTowerIcon sx={{ color: 'var(--mono-500)', fontSize: 20 }} />
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
              inputProps={{
                name: 'location',
                id: 'station-location',
                autoComplete: 'address-line1',
                'aria-label': 'Location',
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LocationIcon sx={{ color: 'var(--mono-500)', fontSize: 20 }} />
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          <Divider sx={{ my: 3, borderColor: 'var(--surface-border)' }} />

          {/* Coordinates Section */}
          <Box sx={{ mb: 3 }}>
            <Box display="flex" alignItems="center" gap={1} mb={2}>
              <CoordinatesIcon sx={{ color: 'var(--mono-500)', fontSize: 20 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--mono-500)' }}>
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
                value={formData.latitude ?? ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                  const value = e.target.value === '' ? undefined : Number.parseFloat(e.target.value)
                  setFormData({ ...formData, latitude: value })
                }}
                required
                inputProps={{
                  name: 'latitude',
                  id: 'station-latitude',
                  autoComplete: 'off',
                  'aria-label': 'Latitude',
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
                value={formData.longitude ?? ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                  const value = e.target.value === '' ? undefined : Number.parseFloat(e.target.value)
                  setFormData({ ...formData, longitude: value })
                }}
                required
                inputProps={{
                  name: 'longitude',
                  id: 'station-longitude',
                  autoComplete: 'off',
                  'aria-label': 'Longitude',
                  step: 'any',
                  min: -180,
                  max: 180,
                }}
                helperText="Range: -180 to 180"
              />
            </Box>
          </Box>

          <Divider sx={{ my: 3, borderColor: 'var(--surface-border)' }} />

          {/* Configuration Section */}
          <Box sx={{ mb: 3 }}>
            <Box display="flex" alignItems="center" gap={1} mb={2}>
              <SettingsIcon sx={{ color: 'var(--mono-500)', fontSize: 20 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--mono-500)' }}>
                Configuration
              </Typography>
            </Box>
            <Box display="flex" gap={2} mb={2}>
              <FormControl fullWidth>
                <InputLabel
                  id="station-type-label"
                  sx={{
                    color: 'var(--mono-500)',
                    '&.Mui-focused': { color: 'var(--mono-700)' },
                  }}
                >
                  Station Type
                </InputLabel>
                <Select
                  labelId="station-type-label"
                  id="station-type-select"
                  name="stationType"
                  value={formData.stationType || ''}
                  label="Station Type"
                  onChange={(e: SelectChangeEvent) =>
                    setFormData({ ...formData, stationType: e.target.value as StationType })
                  }
                  sx={{
                    '& .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'var(--surface-border)',
                    },
                    '&:hover .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'var(--mono-400)',
                    },
                    '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'var(--mono-600)',
                    },
                    '& .MuiSelect-select': {
                      color: 'var(--mono-950)',
                    },
                  }}
                  MenuProps={{
                    PaperProps: {
                      sx: {
                        background: 'var(--surface-base)',
                        border: '1px solid var(--surface-border)',
                        borderRadius: '8px',
                        boxShadow: 'var(--shadow-lg)',
                        '& .MuiMenuItem-root': {
                          color: 'var(--mono-950)',
                          '&:hover': {
                            background: 'var(--mono-100)',
                          },
                          '&.Mui-selected': {
                            background: 'var(--mono-200)',
                            '&:hover': {
                              background: 'var(--mono-200)',
                            },
                          },
                        },
                      },
                    },
                  }}
                >
                  {Object.values(StationType).map((type) => (
                    <MenuItem key={type} value={type}>
                      {type.replace('_', ' ')}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <FormControl fullWidth>
                <InputLabel
                  id="station-status-label"
                  sx={{
                    color: 'var(--mono-500)',
                    '&.Mui-focused': { color: 'var(--mono-700)' },
                  }}
                >
                  Status
                </InputLabel>
                <Select
                  labelId="station-status-label"
                  id="station-status-select"
                  name="status"
                  value={formData.status || ''}
                  label="Status"
                  onChange={(e: SelectChangeEvent) =>
                    setFormData({ ...formData, status: e.target.value as StationStatus })
                  }
                  sx={{
                    '& .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'var(--surface-border)',
                    },
                    '&:hover .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'var(--mono-400)',
                    },
                    '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'var(--mono-600)',
                    },
                    '& .MuiSelect-select': {
                      color: 'var(--mono-950)',
                    },
                  }}
                  MenuProps={{
                    PaperProps: {
                      sx: {
                        background: 'var(--surface-base)',
                        border: '1px solid var(--surface-border)',
                        borderRadius: '8px',
                        boxShadow: 'var(--shadow-lg)',
                        '& .MuiMenuItem-root': {
                          color: 'var(--mono-950)',
                          '&:hover': {
                            background: 'var(--mono-100)',
                          },
                          '&.Mui-selected': {
                            background: 'var(--mono-200)',
                            '&:hover': {
                              background: 'var(--mono-200)',
                            },
                          },
                        },
                      },
                    },
                  }}
                >
                  {Object.values(StationStatus).map((status) => (
                    <MenuItem key={status} value={status}>
                      {status}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>
            <TextField
              id="station-power-consumption"
              name="powerConsumption"
              fullWidth
              label="Power Consumption"
              type="number"
              placeholder="1500"
              value={formData.powerConsumption ?? ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                const value = e.target.value === '' ? undefined : Number.parseFloat(e.target.value)
                setFormData({ ...formData, powerConsumption: value })
              }}
              inputProps={{
                name: 'powerConsumption',
                id: 'station-power-consumption',
                autoComplete: 'off',
                'aria-label': 'Power Consumption',
                step: 'any',
                min: 0.01,
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <PowerIcon sx={{ color: 'var(--mono-500)', fontSize: 20 }} />
                  </InputAdornment>
                ),
                endAdornment: <InputAdornment position="end">kW</InputAdornment>,
              }}
              helperText="Must be greater than 0"
            />
          </Box>

          <Divider sx={{ my: 3, borderColor: 'var(--surface-border)' }} />

          {/* Description Section */}
          <Box>
            <Box display="flex" alignItems="center" gap={1} mb={2}>
              <DescriptionIcon sx={{ color: 'var(--mono-500)', fontSize: 20 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--mono-500)' }}>
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
              multiline
              rows={3}
              inputProps={{
                name: 'description',
                id: 'station-description',
                autoComplete: 'off',
                'aria-label': 'Description',
              }}
            />
          </Box>
        </form>
      </DialogContent>

      <DialogActions
        sx={{
          px: 3,
          py: 2.5,
          background: 'var(--surface-elevated)',
          borderTop: '1px solid var(--surface-border)',
        }}
      >
        <Button
          onClick={onClose}
          sx={{
            color: 'var(--mono-600)',
            '&:hover': {
              background: 'var(--mono-100)',
            },
          }}
        >
          Cancel
        </Button>
        <Button
          type="submit"
          onClick={onSubmit}
          variant="contained"
          disabled={isSubmitting || !isFormValid}
          sx={{
            px: 4,
            py: 1.2,
            background: 'var(--mono-950)',
            color: 'var(--mono-50)',
            fontWeight: 600,
            transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
            '&:hover': {
              background: 'var(--mono-900)',
              color: 'var(--mono-50)',
              transform: 'translateY(-1px)',
            },
            '&:disabled': {
              background: 'var(--mono-400)',
              color: 'var(--mono-600)',
            },
          }}
        >
          {isSubmitting ? (
            <CircularProgress size={24} sx={{ color: 'var(--mono-600)' }} />
          ) : (
            <>
              {editingStation ? 'Update Station' : 'Create Station'}
            </>
          )}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
