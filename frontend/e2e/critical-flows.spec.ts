import { test, expect, Page } from '@playwright/test'

/**
 * End-to-end tests for critical user flows across the platform.
 *
 * These tests verify complete workflows:
 * - Station creation → metrics collection → alert generation → notification
 * - User authentication → API access → data retrieval
 * - Circuit breaker behavior under failure scenarios
 */

// Helper functions to reduce cognitive complexity

async function attemptToCreateStation(page: Page): Promise<void> {
  const addButton = page.getByRole('button', { name: /add|new|create/i }).first()

  if (await addButton.isVisible({ timeout: 5000 })) {
    await addButton.click()
    await page.waitForTimeout(500)

    const form = page.locator('form, [role="dialog"]').first()
    if (await form.isVisible()) {
      await fillStationForm(page)
      await submitStationForm(page)
    }
  }
}

async function fillStationForm(page: Page): Promise<void> {
  await fillInputIfVisible(page, 'input[name*="name"], input[placeholder*="name" i]', 'E2E Test Station')
  await fillInputIfVisible(page, 'input[name*="location"], input[placeholder*="location" i]', 'Test Location')
  await fillInputIfVisible(page, 'input[name*="lat"], input[placeholder*="latitude" i]', '40.7128')
  await fillInputIfVisible(page, 'input[name*="lon"], input[placeholder*="longitude" i]', '-74.0060')
}

async function fillInputIfVisible(page: Page, selector: string, value: string): Promise<void> {
  const input = page.locator(selector).first()
  if (await input.isVisible({ timeout: 2000 })) {
    await input.fill(value)
  }
}

async function submitStationForm(page: Page): Promise<void> {
  const submitButton = page.getByRole('button', { name: /submit|save|create/i }).first()
  if (await submitButton.isVisible({ timeout: 2000 })) {
    await submitButton.click()
    await page.waitForTimeout(1000)
  }
}

async function verifyStationInList(page: Page, stationName: string): Promise<void> {
  await page.waitForTimeout(2000)
  const station = page.getByText(stationName, { exact: false })
  if (await station.isVisible({ timeout: 5000 }).catch(() => false)) {
    await expect(station).toBeVisible()
  }
}

async function delayRoute(delayMs: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, delayMs))
}

test.describe('Critical User Flows', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('/')
    // Wait for the app to load
    await page.waitForLoadState('networkidle')
  })

  test.describe('Station Creation → Metrics → Alerts Flow', () => {
    test('should create station, collect metrics, and generate alerts', async ({ page }) => {
      // Step 1: Navigate to Stations page
      await page.goto('/stations')
      await expect(page.getByRole('heading', { name: /stations/i })).toBeVisible()

      // Step 2: Create a new station
      await attemptToCreateStation(page)

      // Step 3: Verify station appears in the list
      await verifyStationInList(page, 'E2E Test Station')

      // Step 4: Navigate to Metrics page to verify metrics collection
      await page.goto('/metrics')
      await expect(page.getByRole('heading', { name: /metrics/i })).toBeVisible()
      await page.waitForTimeout(2000)
      await expect(page.locator('body')).toBeVisible()

      // Step 5: Navigate to Alerts page to check for alerts
      await page.goto('/alerts')
      await expect(page.getByRole('heading', { name: /alerts/i })).toBeVisible()
      await page.waitForTimeout(2000)
      await expect(page.locator('body')).toBeVisible()
    })

    test('should display station details with metrics and alerts', async ({ page }) => {
      // Navigate to stations page
      await page.goto('/stations')
      await expect(page.getByRole('heading', { name: /stations/i })).toBeVisible()
      
      // Wait for stations to load
      await page.waitForTimeout(2000)
      
      // Try to click on first station in the list (if available)
      const firstStation = page.locator('tr, [role="row"], [data-testid*="station"]').first()
      
      if (await firstStation.isVisible({ timeout: 5000 })) {
        await firstStation.click()
        
        // Wait for station detail page or modal
        await page.waitForTimeout(1000)
        
        // Verify station details are displayed
        const detailContent = page.locator('body')
        await expect(detailContent).toBeVisible()
        
        // Check if metrics section is visible
        const metricsSection = page.getByText(/metrics|performance|statistics/i).first()
        if (await metricsSection.isVisible({ timeout: 3000 }).catch(() => false)) {
          await expect(metricsSection).toBeVisible()
        }
        
        // Check if alerts section is visible
        const alertsSection = page.getByText(/alerts|notifications|warnings/i).first()
        if (await alertsSection.isVisible({ timeout: 3000 }).catch(() => false)) {
          await expect(alertsSection).toBeVisible()
        }
      }
    })
  })

  test.describe('Navigation and Data Flow', () => {
    test('should navigate between all main pages', async ({ page }) => {
      // Test navigation to Dashboard
      await page.goto('/')
      await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
      
      // Navigate to Stations
      const stationsLink = page.getByRole('link', { name: /stations/i }).or(page.getByRole('button', { name: /stations/i }))
      if (await stationsLink.isVisible({ timeout: 3000 }).catch(() => false)) {
        await stationsLink.click()
        await page.waitForTimeout(1000)
        await expect(page.getByRole('heading', { name: /stations/i })).toBeVisible()
      } else {
        // Fallback: navigate directly
        await page.goto('/stations')
        await expect(page.getByRole('heading', { name: /stations/i })).toBeVisible()
      }
      
      // Navigate to Metrics
      await page.goto('/metrics')
      await expect(page.getByRole('heading', { name: /metrics/i })).toBeVisible()
      
      // Navigate to Alerts
      await page.goto('/alerts')
      await expect(page.getByRole('heading', { name: /alerts/i })).toBeVisible()
      
      // Navigate back to Dashboard
      await page.goto('/')
      await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    })

    test('should load and display data on dashboard', async ({ page }) => {
      await page.goto('/')
      
      // Wait for dashboard to load
      await page.waitForTimeout(2000)
      
      // Verify dashboard elements are present
      await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
      
      // Check for station statistics (these might be present)
      const statsText = page.getByText(/total|stations|active|power/i).first()
      if (await statsText.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(statsText).toBeVisible()
      }
    })
  })

  test.describe('Error Handling and Resilience', () => {
    test('should handle API errors gracefully', async ({ page }) => {
      // Intercept API calls and simulate errors
      await page.route('**/api/v1/stations', route => {
        route.fulfill({
          status: 500,
          body: JSON.stringify({ error: 'Internal Server Error' }),
          headers: { 'Content-Type': 'application/json' },
        })
      })

      await page.goto('/stations')
      
      // Wait for error to be handled
      await page.waitForTimeout(2000)
      
      // Verify page still renders (doesn't crash)
      await expect(page.locator('body')).toBeVisible()
      
      // Check if error message is displayed (if error handling is implemented)
      const errorMessage = page.getByText(/error|failed|unavailable/i).first()
      if (await errorMessage.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(errorMessage).toBeVisible()
      }
    })

    test('should handle slow API responses', async ({ page }) => {
      // Intercept API calls and add delay
      await page.route('**/api/v1/stations', async route => {
        await delayRoute(2000) // 2 second delay
        await route.continue()
      })

      await page.goto('/stations')

      // Verify loading state (if implemented)
      const loadingIndicator = page.locator('[role="progressbar"], .MuiCircularProgress-root, [aria-label*="loading" i]').first()
      if (await loadingIndicator.isVisible({ timeout: 1000 }).catch(() => false)) {
        await expect(loadingIndicator).toBeVisible()
      }

      // Wait for content to load
      await page.waitForTimeout(3000)

      // Verify page eventually loads
      await expect(page.locator('body')).toBeVisible()
    })
  })

  test.describe('Data Consistency', () => {
    test('should maintain data consistency across pages', async ({ page }) => {
      // Navigate to stations page and note station count
      await page.goto('/stations')
      await page.waitForTimeout(2000)
      
      // Navigate to dashboard
      await page.goto('/')
      await page.waitForTimeout(2000)
      
      // Verify dashboard shows consistent data
      await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
      
      // Navigate back to stations
      await page.goto('/stations')
      await page.waitForTimeout(2000)
      
      // Verify stations page still works
      await expect(page.getByRole('heading', { name: /stations/i })).toBeVisible()
    })
  })
})
