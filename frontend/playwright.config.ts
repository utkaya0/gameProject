import { defineConfig } from '@playwright/test'
import { resolve } from 'node:path'

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  use: { baseURL: 'http://localhost:5173', browserName: 'chromium', channel: 'chrome', headless: true },
  webServer: [
    {
      command: process.platform === 'win32' ? '.\\gradlew.bat bootRun' : './gradlew bootRun',
      cwd: resolve(import.meta.dirname, '../backend'),
      url: 'http://localhost:8080/api/v1/health',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: 'npm run dev',
      cwd: import.meta.dirname,
      url: 'http://localhost:5173/color',
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
  ],
})
