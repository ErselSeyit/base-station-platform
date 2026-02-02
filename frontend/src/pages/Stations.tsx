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
  IconButton,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ConfirmDialog from '../components/ConfirmDialog'
import LoadingSpinner from '../components/LoadingSpinner'
import StationFormDialog from '../components/StationFormDialog'
import { getStationStatusColor } from '../constants/designSystem'
import { stationApi } from '../services/api'
import { BaseStation, StationStatus, StationType } from '../types'
import { ensureArray } from '../utils/arrayUtils'
import { showToast } from '../utils/toast'

// Default form data for new stations
const DEFAULT_FORM_DATA: Partial<BaseStation> = {
  stationName: '',
  location: '',
  latitude: 0,
  longitude: 0,
  stationType: StationType.MACRO_CELL,
  status: StationStatus.ACTIVE,
  powerConsumption: 1500,
} as const

export default function Stations() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [openDialog, setOpenDialog] = useState(false)
  const [editingStation, setEditingStation] = useState<BaseStation | null>(null)
  const [formData, setFormData] = useState<Partial<BaseStation>>({ ...DEFAULT_FORM_DATA })
  const [deleteConfirm, setDeleteConfirm] = useState<{ open: boolean; stationId: number | null }>({
    open: false,
    stationId: null,
  })

  const { data, isLoading, error } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  const stations = ensureArray(data as BaseStation[])

  const createMutation = useMutation({
    mutationFn: stationApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stations'] })
      setOpenDialog(false)
      resetForm()
      showToast.success('Station created successfully')
    },
    onError: (error: Error) => {
      showToast.error(`Failed to create station: ${error.message}`)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<BaseStation> }) => stationApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stations'] })
      setOpenDialog(false)
      resetForm()
      showToast.success('Station updated successfully')
    },
    onError: (error: Error) => {
      showToast.error(`Failed to update station: ${error.message}`)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: stationApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stations'] })
      showToast.success('Station deleted successfully')
    },
    onError: (error: Error) => {
      showToast.error(`Failed to delete station: ${error.message}`)
    },
  })

  const resetForm = () => {
    setFormData({ ...DEFAULT_FORM_DATA })
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
      updateMutation.mutate({ id: editingStation.id, data: formData })
    } else {
      createMutation.mutate(formData as BaseStation)
    }
  }

  const handleDelete = (stationId: number) => {
    setDeleteConfirm({ open: true, stationId })
  }

  const confirmDelete = () => {
    if (deleteConfirm.stationId !== null) {
      deleteMutation.mutate(deleteConfirm.stationId)
    }
    setDeleteConfirm({ open: false, stationId: null })
  }

  const cancelDelete = () => {
    setDeleteConfirm({ open: false, stationId: null })
  }

  if (isLoading) {
    return <LoadingSpinner />
  }

  return (
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: { xs: '16px', sm: '24px', md: '32px 24px' } }}>
      {/* Header - Responsive */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        sx={{
          display: 'flex',
          flexDirection: { xs: 'column', sm: 'row' },
          justifyContent: 'space-between',
          alignItems: { xs: 'stretch', sm: 'flex-start' },
          gap: { xs: 2, sm: 0 },
          marginBottom: { xs: '24px', md: '32px' },
        }}
      >
        <Box>
          <Typography
            variant="h1"
            sx={{
              fontSize: { xs: '1.75rem', sm: '2rem', md: '2.25rem' },
              fontWeight: 700,
              letterSpacing: '-0.025em',
              color: 'var(--mono-950)',
              marginBottom: '8px',
            }}
          >
            Stations
          </Typography>
          <Typography
            sx={{
              fontSize: { xs: '0.8125rem', sm: '0.875rem' },
              color: 'var(--mono-500)',
              letterSpacing: '0.01em',
            }}
          >
            Manage and monitor infrastructure · {stations.length} total stations
          </Typography>
        </Box>

        <Button
          component={motion.button}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => handleOpenDialog()}
          sx={{
            background: 'var(--mono-950)',
            color: 'var(--mono-50)',
            borderRadius: '8px',
            padding: { xs: '12px 16px', sm: '10px 20px' },
            fontSize: '0.875rem',
            fontWeight: 600,
            textTransform: 'none',
            boxShadow: 'var(--shadow-sm)',
            width: { xs: '100%', sm: 'auto' },
            transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
            '&:hover': {
              background: 'var(--mono-800)',
              color: 'var(--mono-50)',
              boxShadow: 'var(--shadow-md)',
            },
          }}
        >
          Add Station
        </Button>
      </Box>

      {/* Mobile Card View - shown on small screens */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
        sx={{
          display: { xs: 'flex', md: 'none' },
          flexDirection: 'column',
          gap: 2,
        }}
      >
        {stations.length === 0 && !isLoading ? (
          <Box
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '12px',
              padding: '48px 24px',
              textAlign: 'center',
            }}
          >
            <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
              {error ? `Error loading stations: ${error.message}` : 'No stations found. Click "Add Station" to create one.'}
            </Typography>
          </Box>
        ) : (
          stations.map((station: BaseStation, idx: number) => {
            const stationId = station.id
            return (
            <Box
              component={motion.div}
              key={station.id}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 + idx * 0.05, duration: 0.3 }}
              sx={{
                background: 'var(--surface-base)',
                border: '1px solid var(--surface-border)',
                borderRadius: '12px',
                padding: '16px',
                transition: 'all 0.2s ease',
                '&:hover': {
                  boxShadow: 'var(--shadow-md)',
                  borderColor: 'var(--mono-400)',
                },
              }}
            >
              {/* Card Header */}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                <Box>
                  <Typography sx={{ fontSize: '0.9375rem', fontWeight: 600, color: 'var(--mono-950)', mb: 0.5 }}>
                    {station.stationName}
                  </Typography>
                  <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.75rem', color: 'var(--mono-500)' }}>
                    #{station.id}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Box
                    sx={{
                      width: '8px',
                      height: '8px',
                      borderRadius: '50%',
                      background: getStationStatusColor(station.status),
                    }}
                  />
                  <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--mono-700)' }}>
                    {station.status}
                  </Typography>
                </Box>
              </Box>

              {/* Card Details */}
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, mb: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <LocationIcon sx={{ fontSize: '16px', color: 'var(--mono-400)' }} />
                  <Typography sx={{ fontSize: '0.8125rem', color: 'var(--mono-700)' }}>
                    {station.location}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box className="badge" sx={{ display: 'inline-flex' }}>
                    {station.stationType}
                  </Box>
                  <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8125rem', fontWeight: 600, color: 'var(--mono-700)' }}>
                    {station.powerConsumption?.toFixed(1) || 'N/A'} kW
                  </Typography>
                </Box>
              </Box>

              {/* Card Actions */}
              <Box sx={{ display: 'flex', gap: 1, pt: 2, borderTop: '1px solid var(--surface-border)' }}>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<ViewIcon sx={{ fontSize: '16px' }} />}
                  onClick={() => navigate(`/stations/${station.id}`)}
                  sx={{
                    flex: 1,
                    textTransform: 'none',
                    fontSize: '0.8125rem',
                    borderColor: 'var(--surface-border)',
                    color: 'var(--mono-700)',
                    '&:hover': {
                      borderColor: 'var(--mono-400)',
                      background: 'var(--surface-hover)',
                    },
                  }}
                >
                  View
                </Button>
                <IconButton
                  size="small"
                  aria-label={`Edit ${station.stationName}`}
                  onClick={() => handleOpenDialog(station)}
                  sx={{
                    border: '1px solid var(--surface-border)',
                    borderRadius: '8px',
                    color: 'var(--mono-600)',
                    '&:hover': {
                      background: 'var(--surface-hover)',
                    },
                  }}
                >
                  <EditIcon sx={{ fontSize: '16px' }} />
                </IconButton>
                {typeof stationId === 'number' && (
                  <IconButton
                    size="small"
                    aria-label={`Delete ${station.stationName}`}
                    onClick={() => handleDelete(stationId)}
                    sx={{
                      border: '1px solid var(--surface-border)',
                      borderRadius: '8px',
                      color: 'var(--mono-600)',
                      '&:hover': {
                        background: 'var(--accent-error)',
                        color: 'white',
                        borderColor: 'var(--accent-error)',
                      },
                    }}
                  >
                    <DeleteIcon sx={{ fontSize: '16px' }} />
                  </IconButton>
                )}
              </Box>
            </Box>
          )})
        )}
      </Box>

      {/* Desktop Table View - hidden on small screens */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
        sx={{
          display: { xs: 'none', md: 'block' },
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          borderRadius: '12px',
          overflow: 'hidden',
        }}
      >
        <Box sx={{ overflowX: 'auto' }}>
          <Box component="table" className="data-table" sx={{ width: '100%', minWidth: '800px' }}>
            <thead>
              <tr>
                <Box component="th" sx={{ width: '60px' }}>ID</Box>
                <Box component="th">Station Name</Box>
                <Box component="th">Location</Box>
                <Box component="th" sx={{ width: '140px' }}>Type</Box>
                <Box component="th" sx={{ width: '120px' }}>Status</Box>
                <Box component="th" sx={{ width: '100px', textAlign: 'right' }}>Power</Box>
                <Box component="th" sx={{ width: '120px', textAlign: 'right' }}>Actions</Box>
              </tr>
            </thead>
            <tbody>
              {stations.length === 0 && !isLoading ? (
                <tr>
                  <Box component="td" colSpan={7} sx={{ textAlign: 'center', padding: '48px 24px' }}>
                    <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
                      {error ? `Error loading stations: ${error.message}` : 'No stations found. Click "Add Station" to create one.'}
                    </Typography>
                  </Box>
                </tr>
              ) : (
                stations.map((station: BaseStation, idx: number) => {
                  const stationId = station.id
                  return (
                  <Box
                    component={motion.tr}
                    key={station.id}
                    initial={{ opacity: 0, x: -16 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.15 + idx * 0.03, duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
                  >
                    <Box component="td">
                      <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8125rem', color: 'var(--mono-600)' }}>
                        #{station.id}
                      </Typography>
                    </Box>
                    <Box component="td">
                      <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--mono-950)' }}>
                        {station.stationName}
                      </Typography>
                    </Box>
                    <Box component="td">
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <LocationIcon sx={{ fontSize: '16px', color: 'var(--mono-400)' }} />
                        <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-700)' }}>
                          {station.location}
                        </Typography>
                      </Box>
                    </Box>
                    <Box component="td">
                      <Box className="badge" sx={{ display: 'inline-flex' }}>
                        {station.stationType}
                      </Box>
                    </Box>
                    <Box component="td">
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Box
                          sx={{
                            width: '6px',
                            height: '6px',
                            borderRadius: '50%',
                            background: getStationStatusColor(station.status),
                          }}
                        />
                        <Typography sx={{ fontSize: '0.8125rem', fontWeight: 500, color: 'var(--mono-700)' }}>
                          {station.status}
                        </Typography>
                      </Box>
                    </Box>
                    <Box component="td" sx={{ textAlign: 'right' }}>
                      <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8125rem', fontWeight: 600, color: 'var(--mono-700)' }}>
                        {station.powerConsumption?.toFixed(1) || 'N/A'} kW
                      </Typography>
                    </Box>
                    <Box component="td" sx={{ textAlign: 'right' }}>
                      <Box sx={{ display: 'flex', gap: '4px', justifyContent: 'flex-end' }}>
                        <Tooltip title="View Details">
                          <IconButton
                            size="small"
                            aria-label={`View details for ${station.stationName}`}
                            onClick={() => navigate(`/stations/${station.id}`)}
                            sx={{
                              width: '28px',
                              height: '28px',
                              color: 'var(--mono-600)',
                              transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                              '&:hover': {
                                background: 'var(--surface-hover)',
                                color: 'var(--mono-950)',
                              },
                            }}
                          >
                            <ViewIcon sx={{ fontSize: '16px' }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Edit">
                          <IconButton
                            size="small"
                            aria-label={`Edit ${station.stationName}`}
                            onClick={() => handleOpenDialog(station)}
                            sx={{
                              width: '28px',
                              height: '28px',
                              color: 'var(--mono-600)',
                              transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                              '&:hover': {
                                background: 'var(--surface-hover)',
                                color: 'var(--mono-950)',
                              },
                            }}
                          >
                            <EditIcon sx={{ fontSize: '16px' }} />
                          </IconButton>
                        </Tooltip>
                        {typeof stationId === 'number' && (
                          <Tooltip title="Delete">
                            <IconButton
                              size="small"
                              aria-label={`Delete ${station.stationName}`}
                              onClick={() => handleDelete(stationId)}
                              sx={{
                                width: '28px',
                                height: '28px',
                                color: 'var(--mono-600)',
                                transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
                                '&:hover': {
                                  background: 'var(--accent-error)',
                                  color: 'white',
                                },
                              }}
                            >
                              <DeleteIcon sx={{ fontSize: '16px' }} />
                            </IconButton>
                          </Tooltip>
                        )}
                      </Box>
                    </Box>
                  </Box>
                )})
              )}
            </tbody>
          </Box>
        </Box>
      </Box>

      <StationFormDialog
        open={openDialog}
        onClose={() => setOpenDialog(false)}
        onSubmit={handleSubmit}
        editingStation={editingStation}
        formData={formData}
        setFormData={setFormData}
        isSubmitting={createMutation.isPending || updateMutation.isPending}
      />

      <ConfirmDialog
        open={deleteConfirm.open}
        title="Delete Station"
        message="Are you sure you want to delete this station? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={confirmDelete}
        onCancel={cancelDelete}
        isDestructive
      />
    </Box>
  )
}
