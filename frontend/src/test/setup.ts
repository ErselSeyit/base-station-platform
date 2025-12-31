import { afterEach, beforeEach, vi, expect } from 'vitest'
import * as matchers from '@testing-library/jest-dom/matchers'
import { cleanup } from '@testing-library/react'

// Extend Vitest's expect with jest-dom matchers
expect.extend(matchers)

// Create a proper localStorage mock
class LocalStorageMock implements Storage {
  private store: Record<string, string> = {}

  get length(): number {
    return Object.keys(this.store).length
  }

  clear(): void {
    this.store = {}
  }

  getItem(key: string): string | null {
    return this.store[key] || null
  }

  setItem(key: string, value: string): void {
    this.store[key] = String(value)
  }

  removeItem(key: string): void {
    delete this.store[key]
  }

  key(index: number): string | null {
    const keys = Object.keys(this.store)
    return keys[index] || null
  }
}

// Set up localStorage mock
const localStorageMock = new LocalStorageMock()

// Set up localStorage before each test to ensure it's available and not overridden by jsdom
beforeEach(() => {
  // Clear the store first
  localStorageMock.clear()
  
  // Re-apply the mock in case jsdom overrode it
  vi.stubGlobal('localStorage', localStorageMock)
  
  // Also set it on globalThis as fallback
  if (globalThis.window !== undefined) {
    Object.defineProperty(globalThis.window, 'localStorage', {
      value: localStorageMock,
      writable: true,
      configurable: true,
    })
  }
  if (globalThis !== undefined) {
    Object.defineProperty(globalThis, 'localStorage', {
      value: localStorageMock,
      writable: true,
      configurable: true,
    })
  }
})

// Cleanup after each test
afterEach(() => {
  cleanup()
})

// Mock window.matchMedia
Object.defineProperty(globalThis, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {
      // Mock implementation
    },
    removeListener: () => {
      // Mock implementation
    },
    addEventListener: () => {
      // Mock implementation
    },
    removeEventListener: () => {
      // Mock implementation
    },
    dispatchEvent: () => {
      // Mock implementation
    },
  }),
})

// Mock IntersectionObserver
globalThis.IntersectionObserver = class IntersectionObserver {
  disconnect() {
    // Mock implementation
  }
  observe() {
    // Mock implementation
  }
  takeRecords() {
    return []
  }
  unobserve() {
    // Mock implementation
  }
} as unknown as typeof IntersectionObserver

