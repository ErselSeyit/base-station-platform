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

    expect(screen.getAllByText('Base Station O&M')[0]).toBeInTheDocument()
    expect(screen.getAllByText('Dashboard')[0]).toBeInTheDocument()
    expect(screen.getAllByText('Stations')[0]).toBeInTheDocument()
    expect(screen.getAllByText('Map View')[0]).toBeInTheDocument()
    expect(screen.getAllByText('Alerts')[0]).toBeInTheDocument()
    expect(screen.getAllByText('Metrics')[0]).toBeInTheDocument()
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

