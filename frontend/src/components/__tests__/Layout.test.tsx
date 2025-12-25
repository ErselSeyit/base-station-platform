import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '../../test/test-utils'
import Layout from '../Layout'

// Mock the API
vi.mock('../../services/api', () => ({
  notificationsApi: {
    getAll: vi.fn(() => Promise.resolve({ data: [] })),
  },
}))

describe('Layout', () => {
  it('renders the layout with navigation', () => {
    render(
      <Layout>
        <div>Test Content</div>
      </Layout>
    )

    expect(screen.getByText('Base Station O&M')).toBeInTheDocument()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Stations')).toBeInTheDocument()
    expect(screen.getByText('Map View')).toBeInTheDocument()
    expect(screen.getByText('Alerts')).toBeInTheDocument()
    expect(screen.getByText('Metrics')).toBeInTheDocument()
  })

  it('renders children content', () => {
    render(
      <Layout>
        <div>Test Content</div>
      </Layout>
    )

    expect(screen.getByText('Test Content')).toBeInTheDocument()
  })
})

