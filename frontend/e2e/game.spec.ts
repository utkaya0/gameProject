import { expect, test } from '@playwright/test'

test('single game starts and accepts a guess on a phone viewport', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const page = await context.newPage()
  try {
    await page.goto('/color')
    await page.getByRole('button', { name: 'Oyuna başla' }).click()
    await expect(page.getByRole('heading', { name: /Raund 01.*05/ })).toBeVisible()
    const hex = page.getByLabel(/HEX RENK KODU/i)
    await expect(hex).toBeVisible()
    await hex.fill('#5588AA')
    const submit = page.getByRole('button', { name: 'Tahminimi gönder' })
    await expect(submit).toBeEnabled()
    await submit.click()
    await expect(page.getByText('RAUND SONUCU', { exact: true })).toBeVisible()
  } finally {
    await context.close()
  }
})

test('two browsers finish a match, refresh, and start a rematch', async ({ browser }) => {
  const hostContext = await browser.newContext()
  const guestContext = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const host = await hostContext.newPage()
  const guest = await guestContext.newPage()
  try {
    await host.goto('/color/lobby')
    await host.locator('form').nth(0).getByLabel('Takma adın').fill('Ada')
    await host.getByRole('button', { name: 'Lobi oluştur' }).click()
    const code = await host.locator('.lobby-room__top strong').textContent()
    expect(code).toMatch(/^[A-Z0-9]{6}$/)

    await guest.goto('/color/lobby')
    await guest.locator('form').nth(1).getByLabel('Takma adın').fill('Bora')
    await guest.getByLabel('Lobi kodu').fill(code!)
    await guest.getByRole('button', { name: 'Lobiye katıl' }).click()
    await expect(host.getByText('Bora', { exact: true })).toBeVisible()
    await host.getByRole('button', { name: 'Oyunu başlat' }).click()
    await expect(host.getByRole('heading', { name: 'Raund 1 / 5' })).toBeVisible()
    await expect(guest.getByRole('heading', { name: 'Raund 1 / 5' })).toBeVisible()

    for (let round = 1; round <= 5; round++) {
      if (round === 2) {
        await guest.reload()
        await expect(guest.getByRole('heading', { name: 'Raund 2 / 5' })).toBeVisible()
      }
      for (const page of [host, guest]) {
        await page.getByLabel('HEX renk kodu').fill('#5588AA')
        const submit = page.getByRole('button', { name: 'Tahminimi gönder' })
        await expect(submit).toBeEnabled()
        await submit.click()
      }
      for (const page of [host, guest]) {
        await expect(page.getByText('Bu raundun puanları')).toBeVisible()
        await page.getByRole('button', { name: 'Devam', exact: true }).click()
      }
      if (round < 5) {
        await expect(host.getByRole('heading', { name: `Raund ${round + 1} / 5` })).toBeVisible()
        await expect(guest.getByRole('heading', { name: `Raund ${round + 1} / 5` })).toBeVisible()
      }
    }
    await expect(host.getByRole('heading', { name: 'Final sıralaması' })).toBeVisible()
    await expect(guest.getByRole('heading', { name: 'Final sıralaması' })).toBeVisible()
    await host.getByRole('button', { name: 'Rövanş başlat' }).click()
    await expect(host.getByRole('heading', { name: 'Raund 1 / 5' })).toBeVisible()
    await expect(guest.getByRole('heading', { name: 'Raund 1 / 5' })).toBeVisible()
  } finally {
    await hostContext.close()
    await guestContext.close()
  }
})

test('a display name containing markup is shown as text', async ({ browser }) => {
  const hostContext = await browser.newContext()
  const guestContext = await browser.newContext()
  const host = await hostContext.newPage()
  const guest = await guestContext.newPage()
  try {
    await host.goto('/color/lobby')
    await host.locator('form').nth(0).getByLabel('Takma adın').fill('Ada')
    await host.getByRole('button', { name: 'Lobi oluştur' }).click()
    const code = await host.locator('.lobby-room__top strong').textContent()
    await guest.goto('/color/lobby')
    const name = '<img src=x onerror=1>'
    await guest.locator('form').nth(1).getByLabel('Takma adın').fill(name)
    await guest.getByLabel('Lobi kodu').fill(code!)
    await guest.getByRole('button', { name: 'Lobiye katıl' }).click()
    await expect(host.getByText(name, { exact: true })).toBeVisible()
    await expect(host.locator('.lobby-members img')).toHaveCount(0)
  } finally {
    await hostContext.close()
    await guestContext.close()
  }
})
