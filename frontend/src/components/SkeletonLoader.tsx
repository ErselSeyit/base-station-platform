import { Box, Skeleton, Grid } from '@mui/material'
import { useTheme } from '../contexts/ThemeContext'

export function DashboardSkeleton() {
  const { mode } = useTheme()

  return (
    <Box>
      {/* Header skeleton */}
      <Box sx={{ mb: 4 }}>
        <Skeleton
          variant="text"
          width="30%"
          height={60}
          sx={{ mb: 1, borderRadius: 2 }}
          animation="wave"
        />
        <Skeleton
          variant="text"
          width="50%"
          height={30}
          sx={{ borderRadius: 1 }}
          animation="wave"
        />
      </Box>

      <Grid container spacing={3}>
        {/* Stat cards skeleton */}
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <Grid item xs={12} sm={6} md={4} key={i}>
            <Skeleton
              variant="rectangular"
              height={120}
              sx={{
                borderRadius: 3,
                bgcolor: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
              }}
              animation="wave"
            />
          </Grid>
        ))}

        {/* Chart skeleton */}
        <Grid item xs={12} md={8}>
          <Skeleton
            variant="rectangular"
            height={400}
            sx={{
              borderRadius: 3,
              bgcolor: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
            }}
            animation="wave"
          />
        </Grid>

        {/* Station health skeleton */}
        <Grid item xs={12} md={4}>
          <Skeleton
            variant="rectangular"
            height={400}
            sx={{
              borderRadius: 3,
              bgcolor: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
            }}
            animation="wave"
          />
        </Grid>
      </Grid>
    </Box>
  )
}

export function CardSkeleton({ count = 1 }: { count?: number }) {
  const { mode } = useTheme()

  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton
          key={i}
          variant="rectangular"
          height={200}
          sx={{
            mb: 2,
            borderRadius: 3,
            bgcolor: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
          }}
          animation="wave"
        />
      ))}
    </>
  )
}
