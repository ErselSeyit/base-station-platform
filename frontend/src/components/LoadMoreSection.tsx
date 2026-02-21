/**
 * Load More Section
 *
 * Shared component for infinite scroll pagination.
 * Displays loading spinner, "Load more" button, or completion message.
 */
import { Box, Button, CircularProgress, Typography } from '@mui/material'

interface LoadMoreSectionProps {
  /** Whether currently fetching next page */
  readonly isFetching: boolean
  /** Whether there are more items to load */
  readonly hasMore: boolean
  /** Total count of items */
  readonly totalCount: number
  /** Current loaded item count */
  readonly itemCount: number
  /** Callback to load more items */
  readonly onLoadMore: () => void
  /** Label for items (e.g., "diagnostic events") */
  readonly itemLabel?: string
}

export function LoadMoreSection({
  isFetching,
  hasMore,
  totalCount,
  itemCount,
  onLoadMore,
  itemLabel = 'items',
}: LoadMoreSectionProps) {
  const renderContent = () => {
    if (isFetching) {
      return <CircularProgress size={24} />
    }

    if (hasMore) {
      return (
        <Button
          variant="text"
          onClick={onLoadMore}
          sx={{ color: 'var(--mono-600)' }}
        >
          Load more
        </Button>
      )
    }

    if (itemCount > 0) {
      return (
        <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-500)' }}>
          All {totalCount.toLocaleString()} {itemLabel} loaded
        </Typography>
      )
    }

    return null
  }

  return (
    <Box
      sx={{
        padding: '16px',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '60px',
      }}
    >
      {renderContent()}
    </Box>
  )
}

export default LoadMoreSection
