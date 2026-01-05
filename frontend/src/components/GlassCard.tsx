import { Card, CardProps } from '@mui/material'
import { motion } from 'framer-motion'
import { ReactNode } from 'react'
import { useTheme } from '../contexts/ThemeContext'

interface GlassCardProps extends Omit<CardProps, 'children'> {
  children: ReactNode
  hover?: boolean
  gradient?: boolean
  delay?: number
}

export default function GlassCard({
  children,
  hover = true,
  gradient = false,
  delay = 0,
  sx,
  ...props
}: GlassCardProps) {
  const { mode } = useTheme()

  return (
    <Card
      component={motion.div}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: 0.5,
        delay,
        ease: [0.16, 1, 0.3, 1]
      }}
      whileHover={hover ? {
        y: -8,
        transition: { duration: 0.3 }
      } : {}}
      sx={{
        position: 'relative',
        overflow: 'hidden',
        background: gradient
          ? mode === 'dark'
            ? 'linear-gradient(135deg, rgba(100, 181, 246, 0.08) 0%, rgba(156, 39, 176, 0.08) 100%)'
            : 'linear-gradient(135deg, rgba(25, 118, 210, 0.05) 0%, rgba(156, 39, 176, 0.05) 100%)'
          : mode === 'dark'
            ? 'rgba(255, 255, 255, 0.05)'
            : 'rgba(255, 255, 255, 0.9)',
        backdropFilter: 'blur(20px)',
        border: '1px solid',
        borderColor: mode === 'dark'
          ? 'rgba(255, 255, 255, 0.1)'
          : 'rgba(0, 0, 0, 0.08)',
        boxShadow: mode === 'dark'
          ? '0 8px 32px rgba(0, 0, 0, 0.3)'
          : '0 8px 32px rgba(0, 0, 0, 0.08)',
        transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        '&::before': gradient ? {
          content: '""',
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: '2px',
          background: mode === 'dark'
            ? 'linear-gradient(90deg, transparent, rgba(100, 181, 246, 0.8), transparent)'
            : 'linear-gradient(90deg, transparent, rgba(25, 118, 210, 0.6), transparent)',
        } : {},
        ...sx,
      }}
      {...props}
    >
      {children}
    </Card>
  )
}
