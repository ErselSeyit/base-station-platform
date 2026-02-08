import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/reports': {
        target: 'http://localhost:9091',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/reports/, '/reports'),
      },
    },
  },
  preview: {
    port: 3000,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    // Target modern browsers for smaller bundles
    target: 'es2020',
    // CSS code splitting - each chunk gets its own CSS
    cssCodeSplit: true,
    // Minification settings
    minify: 'esbuild',
    rollupOptions: {
      output: {
        // Optimize chunk naming for better caching
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
        manualChunks: {
          // Core React - must stay together (react + react-dom + scheduler)
          'react-vendor': ['react', 'react-dom', 'scheduler'],
          'react-router': ['react-router-dom'],

          // MUI - split into core and icons
          'mui-core': ['@mui/material', '@emotion/react', '@emotion/styled'],
          'mui-icons': ['@mui/icons-material'],

          // Data fetching
          'query': ['@tanstack/react-query'],

          // Charts - only loaded on pages that need them
          'charts': ['recharts'],

          // Maps - only loaded on map page
          'maps': ['leaflet', 'react-leaflet'],

          // Animation
          'animation': ['framer-motion'],

          // Utilities
          'utils': ['axios', 'date-fns'],
        },
      },
    },
    chunkSizeWarningLimit: 600,
  },
  // Optimize dependency pre-bundling
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query'],
    // Exclude large deps that are code-split anyway
    exclude: ['recharts', 'leaflet'],
  },
})

