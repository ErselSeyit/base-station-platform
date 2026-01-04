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
  IconButton,
  InputAdornment,
  Slide,
  TextField,
  Typography,
} from '@mui/material'
import { TransitionProps } from '@mui/material/transitions'
import React from 'react'
import { useTheme } from '../contexts/ThemeContext'
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

// Helper function to get dialog paper styles based on theme mode
const getDialogPaperStyles = (isDark: boolean) => ({
  borderRadius: 3,
  background: isDark
    ? 'linear-gradient(145deg, #1a1f3a 0%, #0f1425 100%)'
    : 'linear-gradient(145deg, #ffffff 0%, #f8f9fa 100%)',
  border: isDark
    ? '1px solid rgba(100, 181, 246, 0.2)'
    : '1px solid rgba(0, 0, 0, 0.08)',
  boxShadow: isDark
    ? '0 25px 50px -12px rgba(0, 0, 0, 0.5)'
    : '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
})

const getHeaderStyles = (isDark: boolean) => ({
  background: isDark
    ? 'linear-gradient(135deg, rgba(100, 181, 246, 0.15) 0%, rgba(186, 104, 200, 0.15) 100%)'
    : 'linear-gradient(135deg, rgba(25, 118, 210, 0.1) 0%, rgba(156, 39, 176, 0.1) 100%)',
  px: 3,
  py: 2.5,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
})

const getIconBoxStyles = (isDark: boolean) => ({
  width: 48,
  height: 48,
  borderRadius: 2,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: isDark
    ? 'linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%)'
    : 'linear-gradient(135deg, #1976d2 0%, #1565c0 100%)',
  boxShadow: isDark
    ? '0 4px 14px rgba(100, 181, 246, 0.3)'
    : '0 4px 14px rgba(25, 118, 210, 0.3)',
})

const getButtonHoverStyles = (isDark: boolean) => ({
  color: 'text.secondary',
  '&:hover': {
    background: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.05)',
  },
})

const getSubmitButtonStyles = (isDark: boolean) => ({
  px: 4,
  py: 1.2,
  background: isDark
    ? 'linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%)'
    : 'linear-gradient(135deg, #1976d2 0%, #1565c0 100%)',
  boxShadow: isDark
    ? '0 4px 14px rgba(100, 181, 246, 0.3)'
    : '0 4px 14px rgba(25, 118, 210, 0.3)',
  '&:hover': {
    background: isDark
      ? 'linear-gradient(135deg, #42a5f5 0%, #2196f3 100%)'
      : 'linear-gradient(135deg, #1565c0 0%, #0d47a1 100%)',
    boxShadow: isDark
      ? '0 6px 20px rgba(100, 181, 246, 0.4)'
      : '0 6px 20px rgba(25, 118, 210, 0.4)',
  },
})

const getDividerColor = (isDark: boolean) => isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)'

const getActionsBackgroundColor = (isDark: boolean) => isDark ? 'rgba(0,0,0,0.2)' : 'rgba(0,0,0,0.02)'

export default function StationFormDialog({
  open,
  onClose,
  onSubmit,
  editingStation,
  formData,
  setFormData,
  isSubmitting,
}: StationFormDialogProps) {
  const { mode } = useTheme()
  const isDark = mode === 'dark'

  const isFormValid = formData.stationName && formData.location

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      TransitionComponent={Transition}
      PaperProps={{
        sx: getDialogPaperStyles(isDark),
      }}
    >
      {/* Header with gradient */}
      <Box sx={getHeaderStyles(isDark)}>
        <Box display="flex" alignItems="center" gap={2}>
          <Box sx={getIconBoxStyles(isDark)}>
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
          onClick={onClose}
          sx={getButtonHoverStyles(isDark)}
        >
          <CloseIcon />
        </IconButton>
      </Box>

      <DialogContent sx={{ px: 3, py: 3 }}>
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
              inputProps={{
                name: 'location',
                id: 'station-location',
                autoComplete: 'address-line1',
                'aria-label': 'Location',
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LocationIcon sx={{ color: 'text.secondary', fontSize: 20 }} />
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          <Divider sx={{ my: 3, borderColor: getDividerColor(isDark) }} />

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

          <Divider sx={{ my: 3, borderColor: getDividerColor(isDark) }} />

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
                inputProps={{
                  name: 'stationType',
                  id: 'station-type-select',
                  autoComplete: 'off',
                  'aria-label': 'Station Type',
                }}
                SelectProps={{
                  native: true,
                  name: 'stationType',
                  id: 'station-type-select',
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
                inputProps={{
                  name: 'status',
                  id: 'station-status-select',
                  autoComplete: 'off',
                  'aria-label': 'Status',
                }}
                SelectProps={{
                  native: true,
                  name: 'status',
                  id: 'station-status-select',
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
                    <PowerIcon sx={{ color: 'text.secondary', fontSize: 20 }} />
                  </InputAdornment>
                ),
                endAdornment: <InputAdornment position="end">kW</InputAdornment>,
              }}
              helperText="Must be greater than 0"
            />
          </Box>

          <Divider sx={{ my: 3, borderColor: getDividerColor(isDark) }} />

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

      <DialogActions sx={{ px: 3, py: 2.5, background: getActionsBackgroundColor(isDark) }}>
        <Button
          onClick={onClose}
          sx={getButtonHoverStyles(isDark)}
        >
          Cancel
        </Button>
        <Button
          type="submit"
          onClick={onSubmit}
          variant="contained"
          disabled={isSubmitting || !isFormValid}
          sx={getSubmitButtonStyles(isDark)}
        >
          {isSubmitting ? (
            <CircularProgress size={24} sx={{ color: 'white' }} />
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
