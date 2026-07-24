import { describe, it, expect } from 'vitest'
import { render, screen } from '../../test/test-utils'
import NR5GQuickStatus from '../NR5GQuickStatus'
import NR5GMetricsCard from '../NR5GMetricsCard'

// Band-neutral readings in the shape the API now delivers: a metric type plus a
// separate band dimension. A passing n78 downlink (>1000 Mbps) and a failing
// n28 downlink (<25 Mbps), so pass/fail counting and band grouping are both
// exercised. CPU_USAGE is band-less and not an SSV metric, so it must be
// ignored by both components.
const readings = [
  { type: 'DL_THROUGHPUT', band: 'N78', value: 1500 },
  { type: 'DL_THROUGHPUT', band: 'N28', value: 10 },
  { type: 'CPU_USAGE', value: 42 },
] as const

describe('NR5GQuickStatus', () => {
  it('groups band-neutral readings by band and counts pass/fail', () => {
    render(<NR5GQuickStatus metrics={readings} />)

    // The n78 reading passes, the n28 reading fails.
    expect(screen.getByText('1 Pass')).toBeInTheDocument()
    expect(screen.getByText('1 Fail')).toBeInTheDocument()

    // Both DL throughput chips render, distinguished by their band short label.
    expect(screen.getByText(/3\.5G/)).toBeInTheDocument()
    expect(screen.getByText(/700M/)).toBeInTheDocument()
  })

  it('renders nothing when given no readings', () => {
    const { container } = render(<NR5GQuickStatus metrics={[]} />)
    expect(container).toBeEmptyDOMElement()
  })
})

describe('NR5GMetricsCard', () => {
  it('renders per-band sections and the passing count', () => {
    render(<NR5GMetricsCard metrics={readings} />)

    // One SSV metric per band section, one of two passing.
    expect(screen.getByText('1/2 Passing')).toBeInTheDocument()
    expect(screen.getByText(/Band n78 \(3\.5GHz\)/)).toBeInTheDocument()
    expect(screen.getByText(/Band n28 \(700MHz\)/)).toBeInTheDocument()
  })
})
