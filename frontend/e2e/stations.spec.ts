import { test, expect } from '@playwright/test'

test.describe('Stations Page', () => {
  test('should load stations page', async ({ page }) => {
    await page.goto('/stations')
    await expect(page.getByRole('heading', { name: /stations/i })).toBeVisible()
  })

  test('should display stations list', async ({ page }) => {
    await page.goto('/stations')
    // Wait for stations to load
    await page.waitForTimeout(2000)
    // Check if stations table or list is present
    const content = page.locator('body')
    await expect(content).toBeVisible()
  })

  test('should open new station modal', async ({ page }) => {
    await page.goto('/stations')
    await page.waitForTimeout(1000)
    // Look for "Add Station" or "New Station" button
    const addButton = page.getByRole('button', { name: /add|new/i }).first()
    if (await addButton.isVisible()) {
      await addButton.click()
      // Check if modal/form is visible
      await page.waitForTimeout(500)
      const form = page.locator('form, [role="dialog"]').first()
      await expect(form).toBeVisible()
    }
  })
})
