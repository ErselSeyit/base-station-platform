import { Card, CardProps } from '@mui/material'
import { motion } from 'framer-motion'
import { ReactNode } from 'react'

interface GlassCardProps extends Omit<CardProps, 'children'> {
  children: ReactNode
  hover?: boolean
  delay?: number
}

export default function GlassCard({
  children,
  hover = true,
  delay = 0,
  sx,
  ...props
}: Readonly<GlassCardProps>) {
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
        y: -4,
        transition: { duration: 0.25, ease: [0.16, 1, 0.3, 1] }
      } : {}}
      sx={{
        position: 'relative',
        overflow: 'hidden',
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderRadius: '12px',
        boxShadow: 'var(--shadow-sm)',
        transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
        '&:hover': hover ? {
          boxShadow: 'var(--shadow-md)',
          borderColor: 'var(--mono-400)',
        } : {},
        ...sx,
      }}
      {...props}
    >
      {children}
    </Card>
  )
}
