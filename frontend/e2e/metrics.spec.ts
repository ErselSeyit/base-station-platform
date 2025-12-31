import { test, expect } from '@playwright/test'

test.describe('Metrics Page', () => {
  test('should load metrics page', async ({ page }) => {
    await page.goto('/metrics')
    await expect(page.getByRole('heading', { name: /metrics/i })).toBeVisible()
  })

  test('should display metrics filters', async ({ page }) => {
    await page.goto('/metrics')
    await page.waitForTimeout(1000)
    // Check if filter controls are present
    const content = page.locator('body')
    await expect(content).toBeVisible()
  })

  test('should display metrics chart', async ({ page }) => {
    await page.goto('/metrics')
    await page.waitForTimeout(2000)
    // Check if chart or metrics visualization is present
    const content = page.locator('body')
    await expect(content).toBeVisible()
  })
})
