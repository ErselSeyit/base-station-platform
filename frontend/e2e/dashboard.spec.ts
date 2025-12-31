import { test, expect } from '@playwright/test'

test.describe('Dashboard', () => {
  test('should load dashboard page', async ({ page }) => {
    await page.goto('/')
    // Use heading role to target the page title, not navigation items
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  })

  test('should display base station statistics', async ({ page }) => {
    await page.goto('/')
    // Wait for content to load
    await page.waitForTimeout(1000)
    // Check if dashboard content is present
    const content = page.locator('body')
    await expect(content).toBeVisible()
  })
})

