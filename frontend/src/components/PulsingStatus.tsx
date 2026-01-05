import { Box } from '@mui/material'
import { keyframes } from '@mui/system'

const pulseAnimation = keyframes`
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(1.1);
  }
`

const rippleAnimation = keyframes`
  0% {
    transform: scale(0.8);
    opacity: 1;
  }
  100% {
    transform: scale(2.4);
    opacity: 0;
  }
`

interface PulsingStatusProps {
  color: string
  size?: number
  animate?: boolean
}

export default function PulsingStatus({ color, size = 12, animate = true }: PulsingStatusProps) {
  return (
    <Box
      sx={{
        position: 'relative',
        width: size,
        height: size,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {/* Main dot */}
      <Box
        sx={{
          width: size,
          height: size,
          borderRadius: '50%',
          backgroundColor: color,
          boxShadow: `0 0 ${size / 2}px ${color}`,
          animation: animate ? `${pulseAnimation} 2s ease-in-out infinite` : 'none',
          zIndex: 2,
        }}
      />

      {/* Ripple effect */}
      {animate && (
        <>
          <Box
            sx={{
              position: 'absolute',
              width: size,
              height: size,
              borderRadius: '50%',
              border: `2px solid ${color}`,
              animation: `${rippleAnimation} 2s ease-out infinite`,
              zIndex: 1,
            }}
          />
          <Box
            sx={{
              position: 'absolute',
              width: size,
              height: size,
              borderRadius: '50%',
              border: `2px solid ${color}`,
              animation: `${rippleAnimation} 2s ease-out infinite 1s`,
              zIndex: 1,
            }}
          />
        </>
      )}
    </Box>
  )
}
