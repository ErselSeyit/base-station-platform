import { describe, it, expect } from 'vitest'
import { ensureArray, sum, avg, avgNumbers, partition, groupBy } from '../arrayUtils'

describe('ensureArray', () => {
  it('returns empty array for null', () => {
    expect(ensureArray(null)).toEqual([])
  })

  it('returns empty array for undefined', () => {
    expect(ensureArray(undefined)).toEqual([])
  })

  it('returns the same array for a valid array', () => {
    const input = [1, 2, 3]
    expect(ensureArray(input)).toBe(input)
  })

  it('returns empty array for an empty array input', () => {
    expect(ensureArray([])).toEqual([])
  })
})

describe('sum', () => {
  it('returns 0 for an empty array', () => {
    expect(sum([], (x: number) => x)).toBe(0)
  })

  it('sums values using a getter function', () => {
    const items = [{ value: 10 }, { value: 20 }, { value: 30 }]
    expect(sum(items, item => item.value)).toBe(60)
  })

  it('handles negative values', () => {
    const items = [{ v: -5 }, { v: 10 }, { v: -3 }]
    expect(sum(items, item => item.v)).toBe(2)
  })

  it('works with a single element', () => {
    expect(sum([42], x => x)).toBe(42)
  })
})

describe('avg', () => {
  it('returns default value for empty array', () => {
    expect(avg([], (x: number) => x)).toBe(0)
  })

  it('returns custom default value for empty array', () => {
    expect(avg([], (x: number) => x, 99)).toBe(99)
  })

  it('calculates correct average', () => {
    const items = [{ v: 10 }, { v: 20 }, { v: 30 }]
    expect(avg(items, item => item.v)).toBe(20)
  })

  it('handles single element', () => {
    expect(avg([{ v: 7 }], item => item.v)).toBe(7)
  })

  it('handles decimal results', () => {
    const items = [{ v: 1 }, { v: 2 }]
    expect(avg(items, item => item.v)).toBe(1.5)
  })
})

describe('avgNumbers', () => {
  it('returns default value for undefined', () => {
    expect(avgNumbers(undefined)).toBe(0)
  })

  it('returns default value for empty array', () => {
    expect(avgNumbers([])).toBe(0)
  })

  it('returns custom default for empty array', () => {
    expect(avgNumbers([], 42)).toBe(42)
  })

  it('calculates correct average of numbers', () => {
    expect(avgNumbers([10, 20, 30])).toBe(20)
  })

  it('handles single element', () => {
    expect(avgNumbers([5])).toBe(5)
  })

  it('handles decimal results', () => {
    expect(avgNumbers([1, 2])).toBe(1.5)
  })
})

describe('partition', () => {
  it('splits array by predicate', () => {
    const result = partition([1, 2, 3, 4, 5], x => x % 2 === 0)
    expect(result.pass).toEqual([2, 4])
    expect(result.fail).toEqual([1, 3, 5])
  })

  it('returns empty pass and fail for empty array', () => {
    const result = partition([], () => true)
    expect(result.pass).toEqual([])
    expect(result.fail).toEqual([])
  })

  it('puts all items in pass when all match', () => {
    const result = partition([1, 2, 3], () => true)
    expect(result.pass).toEqual([1, 2, 3])
    expect(result.fail).toEqual([])
  })

  it('puts all items in fail when none match', () => {
    const result = partition([1, 2, 3], () => false)
    expect(result.pass).toEqual([])
    expect(result.fail).toEqual([1, 2, 3])
  })

  it('works with objects', () => {
    const items = [
      { name: 'a', active: true },
      { name: 'b', active: false },
      { name: 'c', active: true },
    ]
    const result = partition(items, item => item.active)
    expect(result.pass).toHaveLength(2)
    expect(result.fail).toHaveLength(1)
    expect(result.fail[0].name).toBe('b')
  })
})

describe('groupBy', () => {
  it('groups items by key function', () => {
    const items = [
      { type: 'a', value: 1 },
      { type: 'b', value: 2 },
      { type: 'a', value: 3 },
    ]
    const result = groupBy(items, item => item.type)
    expect(result.get('a')).toEqual([
      { type: 'a', value: 1 },
      { type: 'a', value: 3 },
    ])
    expect(result.get('b')).toEqual([{ type: 'b', value: 2 }])
  })

  it('returns empty map for empty array', () => {
    const result = groupBy([], (x: string) => x)
    expect(result.size).toBe(0)
  })

  it('creates single group when all keys are the same', () => {
    const items = [1, 2, 3]
    const result = groupBy(items, () => 'all')
    expect(result.size).toBe(1)
    expect(result.get('all')).toEqual([1, 2, 3])
  })

  it('creates one group per item when all keys are unique', () => {
    const items = ['a', 'b', 'c']
    const result = groupBy(items, x => x)
    expect(result.size).toBe(3)
    expect(result.get('a')).toEqual(['a'])
    expect(result.get('b')).toEqual(['b'])
    expect(result.get('c')).toEqual(['c'])
  })
})
