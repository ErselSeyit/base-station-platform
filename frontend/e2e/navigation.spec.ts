import { test, expect } from '@playwright/test'

test.describe('Navigation', () => {
  test('should navigate to stations page', async ({ page }) => {
    await page.goto('/')
    await page.click('text=Stations')
    await expect(page).toHaveURL(/.*stations/)
  })

  test('should navigate to map view', async ({ page }) => {
    await page.goto('/')
    await page.click('text=Map View')
    await expect(page).toHaveURL(/.*map/)
  })

  test('should navigate to alerts page', async ({ page }) => {
    await page.goto('/')
    await page.click('text=Alerts')
    await expect(page).toHaveURL(/.*alerts/)
  })

  test('should navigate to metrics page', async ({ page }) => {
    await page.goto('/')
    await page.click('text=Metrics')
    await expect(page).toHaveURL(/.*metrics/)
  })
})

