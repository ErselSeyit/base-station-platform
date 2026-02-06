import { Box, Skeleton, Grid } from '@mui/material'

export function DashboardSkeleton() {
  return (
    <Box>
      {/* Header skeleton */}
      <Box sx={{ mb: 4 }}>
        <Skeleton
          variant="text"
          width="30%"
          height={60}
          sx={{ mb: 1, borderRadius: 2, bgcolor: 'var(--mono-100)' }}
          animation="wave"
        />
        <Skeleton
          variant="text"
          width="50%"
          height={30}
          sx={{ borderRadius: 1, bgcolor: 'var(--mono-100)' }}
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
                bgcolor: 'var(--mono-100)',
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
              bgcolor: 'var(--mono-100)',
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
              bgcolor: 'var(--mono-100)',
            }}
            animation="wave"
          />
        </Grid>
      </Grid>
    </Box>
  )
}

export function CardSkeleton({ count = 1 }: Readonly<{ count?: number }>) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => `skeleton-${i}`).map((id) => (
        <Skeleton
          key={id}
          variant="rectangular"
          height={200}
          sx={{
            mb: 2,
            borderRadius: 3,
            bgcolor: 'var(--mono-100)',
          }}
          animation="wave"
        />
      ))}
    </>
  )
}
