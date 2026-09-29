import { expect, test } from '@playwright/test'

test('back buttons sit inside the color and lobby panels and navigate', async ({ page }) => {
  await page.goto('/color')
  await expect(page.locator('.site-header .panel-back')).toHaveCount(0)
  await page.locator('.landing__panel .panel-back').click()
  await expect(page).toHaveURL(/\/$/)

  await page.goto('/color/lobby')
  await expect(page.locator('.lobby-header .panel-back')).toHaveCount(0)
  await page.locator('.lobby-panel .panel-back').click()
  await expect(page).toHaveURL(/\/color$/)

  await page.goto('/color/lobby')
  await page.locator('#player-name').fill('Ada')
  await page.locator('.lobby-panel__submit').click()
  await expect(page.locator('.lobby-room .panel-back')).toBeVisible()
  await page.locator('.lobby-room .panel-back').click()
  await expect(page).toHaveURL(/\/color$/)
})

test('single game starts and accepts a guess on a phone viewport', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const page = await context.newPage()
  try {
    await page.goto('/color')
    let startRequests = 0
    page.on('request', request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/single-games') startRequests++
    })
    await page.getByRole('button', { name: 'Oyuna başla' }).click()
    const gamePanel = page.locator('main.game-screen--single')
    await expect(gamePanel).toBeVisible()
    await expect(page.getByRole('status', { name: 'Oyun başlıyor: 1' })).toBeVisible()
    expect(startRequests).toBe(0)
    await expect(page.getByRole('status', { name: 'Oyun başlıyor: 2' })).toBeVisible()
    expect(startRequests).toBe(0)
    await expect(page.getByRole('status', { name: 'Oyun başlıyor: GO' })).toBeVisible()
    await expect.poll(() => startRequests).toBe(1)
    await expect(page.getByRole('heading', { name: /Raund 01.*05/ })).toHaveCount(0)
    await expect(gamePanel).toBeVisible()
    await expect(gamePanel.locator('input[type="text"]')).toHaveCount(0)
    const colorPicker = page.getByLabel('Renk seçici')
    await expect(colorPicker).toBeVisible()
    await colorPicker.fill('#5588AA')
    await expect(page.locator('body')).not.toContainText(/#[0-9a-f]{6}/i)
    const submit = page.getByRole('button', { name: 'Tahminimi gönder' })
    await expect(submit).toBeEnabled()
    await submit.click()
    await expect(page.getByText('RAUND SONUCU', { exact: true })).toBeVisible()
    await expect(page.locator('body')).not.toContainText(/#[0-9a-f]{6}/i)
    for (let round = 2; round <= 5; round++) {
      await page.getByRole('button', { name: 'Devam', exact: true }).click()
      await expect(page.locator('.round-progress__item--active')).toHaveAttribute('aria-label', `Raund ${round}: oynanıyor`)
      await expect(submit).toBeEnabled()
      await submit.click()
      await expect(page.getByText('RAUND SONUCU', { exact: true })).toBeVisible()
    }
    await page.getByRole('button', { name: 'Devam', exact: true }).click()
    const finalCard = page.locator('.final-card')
    await expect(finalCard).toBeVisible()
    await expect(page.locator('.final-screen > .section-label, .final-screen > h1')).toHaveCount(0)
    await expect(finalCard.locator('.small-label, .final-card__mark, .result-row__label')).toHaveCount(0)
    await expect(finalCard.locator('.result-row')).toHaveCount(5)
    await expect(finalCard.locator('.result-row__number')).toHaveText(['1', '2', '3', '4', '5'])
    await expect(finalCard).toHaveCSS('border-top-color', 'rgb(17, 17, 17)')
    await expect(finalCard.locator('.final-card__replay')).toBeVisible()
    await expect(finalCard.locator('.final-card__replay')).toHaveCSS('background-color', 'rgb(17, 17, 17)')
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
    await expect(host.locator('form')).toHaveCount(1)
    await host.getByLabel('Takma adın').fill('Ada')
    await host.getByRole('button', { name: 'Lobi oluştur' }).click()
    await expect(host.locator('.lobby-room__heading small')).toHaveText('1 / 8')
    const code = await host.locator('.lobby-room__top strong').textContent()
    expect(code).toMatch(/^[A-Z0-9]{6}$/)

    await guest.goto('/color/lobby')
    await guest.getByRole('button', { name: 'Kodla katıl' }).click()
    await expect(guest.locator('form')).toHaveCount(1)
    await guest.getByLabel('Takma adın').fill('Bora')
    await guest.getByLabel('Lobi kodu').fill(code!)
    await guest.getByRole('button', { name: 'Lobiye katıl' }).click()
    await expect(host.getByText('Bora', { exact: true })).toBeVisible()
    await host.getByRole('button', { name: 'Oyunu başlat' }).click()
    await expect(host.getByRole('heading', { name: 'Raund 1 / 5' })).toBeVisible()
    await expect(guest.getByRole('heading', { name: 'Raund 1 / 5' })).toBeVisible()

    for (let round = 1; round <= 5; round++) {
      if (round === 1) await expect(host.locator('.multi-stage .panel-back')).toBeVisible()
      if (round === 2) {
        await guest.reload()
        await expect(guest.getByRole('heading', { name: 'Raund 2 / 5' })).toBeVisible()
      }
      for (const page of [host, guest]) {
        await expect(page.locator('.multi-controls input[type="text"]')).toHaveCount(0)
        await page.locator('#multi-color').fill('#5588AA')
        await expect(page.locator('body')).not.toContainText(/#[0-9a-f]{6}/i)
        const submit = page.getByRole('button', { name: 'Tahminimi gönder' })
        await expect(submit).toBeEnabled()
        await submit.click()
      }
      for (const page of [host, guest]) {
        await expect(page.getByText('Bu raundun puanları')).toBeVisible()
        await expect(page.locator('body')).not.toContainText(/#[0-9a-f]{6}/i)
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
    await host.getByLabel('Takma adın').fill('Ada')
    await host.getByRole('button', { name: 'Lobi oluştur' }).click()
    const code = await host.locator('.lobby-room__top strong').textContent()
    await guest.goto('/color/lobby')
    const name = '<img src=x onerror=1>'
    await guest.getByRole('button', { name: 'Kodla katıl' }).click()
    await guest.getByLabel('Takma adın').fill(name)
    await guest.getByLabel('Lobi kodu').fill(code!)
    await guest.getByRole('button', { name: 'Lobiye katıl' }).click()
    await expect(host.getByText(name, { exact: true })).toBeVisible()
    await expect(host.locator('.lobby-members img')).toHaveCount(0)
  } finally {
    await hostContext.close()
    await guestContext.close()
  }
})
