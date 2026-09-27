// Takes the screenshots used in the README, from the running app.
//
//   docker compose up -d        (or run the backend and frontend yourself)
//   cd frontend
//   npm run screenshots         add -- --show to watch it happen, -- --url=http://localhost:5173 for the dev server
//
// It opens Microsoft Edge (or Chrome if Edge isn't there), clicks "Try the demo", visits each page and
// saves PNGs into docs/images/ at the root of the repository, overwriting the old ones.
// playwright-core drives the browser that's already installed, so nothing big is downloaded.

import { mkdir } from 'node:fs/promises'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const args = process.argv.slice(2)
const show = args.includes('--show')
const base = (args.find((a) => a.startsWith('--url='))?.slice(6) ?? 'http://localhost:3000').replace(/\/$/, '')
const outDir = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs/images')

const WIDTH = 1280
const HEIGHT = 800
const WAIT = 15_000
const CHART_SETTLE_MS = 1_500 // charts animate in; wait for them to finish

async function main() {
  await checkAppIsRunning()
  await mkdir(outDir, { recursive: true })

  const browser = await launchBrowser()
  const page = await browser.newPage({ viewport: { width: WIDTH, height: HEIGHT }, deviceScaleFactor: 1.5 })
  const saved = []

  async function shot(name, capture) {
    process.stdout.write(`  ${name.padEnd(22)}`)
    try {
      await capture()
      const file = join(outDir, name)
      await page.screenshot({ path: file, ...(capture.clip ? { clip: capture.clip } : {}) })
      saved.push(file)
      console.log('saved')
    } catch (error) {
      console.log('failed')
      throw new Error(`Couldn't capture ${name}: ${error.message.split('\n')[0]}`)
    }
  }

  const visible = (text, options = {}) => page.getByText(text, options).first().waitFor({ timeout: WAIT })
  const nav = (label) => page.locator('header nav').getByRole('link', { name: label, exact: true }).click()
  const settle = (ms = 400) => page.waitForTimeout(ms)
  const closeDialog = async () => {
    await page.keyboard.press('Escape')
    await page.getByRole('dialog').waitFor({ state: 'detached', timeout: WAIT })
  }

  try {
    console.log(`Taking screenshots of ${base}`)

    await shot('login.png', async () => {
      await page.goto(`${base}/login`)
      await page.getByRole('button', { name: 'Try the demo' }).waitFor({ timeout: WAIT })
    })

    // Log in as a fresh demo user, then hide the "this is a demo" strip so it isn't in every picture
    await page.getByRole('button', { name: 'Try the demo' }).click()
    await visible('Net worth', { exact: true })
    await page.addStyleTag({ content: '[data-demo-banner] { display: none !important; }' })

    await shot('dashboard.png', async () => {
      await page.locator('.recharts-surface').first().waitFor({ timeout: WAIT })
      await settle(CHART_SETTLE_MS)
    })

    await shot('transactions.png', async () => {
      await nav('Transactions')
      await visible(/Page 1 of/)
    })

    await shot('budgets.png', async () => {
      await nav('Budgets')
      await visible('budgeted')
      await settle()
    })

    await shot('bills.png', async () => {
      await nav('Bills')
      await visible('Your bills and subscriptions')
    })

    const notifications = async () => {
      const bell = page.getByRole('button', { name: /^Notifications/ })
      await bell.click()
      await visible('Mark all read')
      const box = await bell.boundingBox()
      notifications.clip = { x: Math.max(0, box.x + box.width - 440), y: 0, width: 460, height: 560 }
    }
    await shot('notifications.png', notifications)
    await page.keyboard.press('Escape')
    await page.mouse.click(20, HEIGHT - 20)

    await shot('split-group.png', async () => {
      await nav('Split')
      await page.getByRole('link', { name: /Goa trip/ }).click()
      await visible('Settle up')
      await settle()
    })

    await shot('split-expense.png', async () => {
      await page.getByRole('button', { name: 'Add expense', exact: true }).click()
      await page.getByLabel('What was it for?').fill('Cab to the airport')
      await page.getByLabel('Amount (₹)').fill('2400')
      await page.getByRole('button', { name: 'Shares', exact: true }).click()
      await page.getByLabel("Rohan Kulkarni's shares").fill('2')
      await settle()
    })
    await closeDialog()

    await shot('split-upi.png', async () => {
      await nav('Split')
      await page.getByRole('link', { name: /Flat 402/ }).click()
      await page.getByRole('button', { name: 'Pay with UPI' }).first().click()
      await page.getByRole('dialog').locator('svg').first().waitFor({ timeout: WAIT })
      await settle()
    })
    await closeDialog()

    await shot('investments.png', async () => {
      await nav('Investments')
      await visible('Current value', { exact: true }).catch(() => {
        throw new Error('the demo has no funds. The backend needs internet access to fetch NAVs from mfapi.in when it starts')
      })
      await page.locator('.recharts-surface').first().waitFor({ timeout: WAIT })
      await settle(CHART_SETTLE_MS)
    })

    await shot('investments-add.png', async () => {
      await page.getByRole('button', { name: 'Add purchase' }).first().click()
      await page.getByLabel('Search for a fund').fill('nifty 50 index')
      await page.getByRole('list', { name: 'Matching funds' }).waitFor({ timeout: WAIT })
      await settle()
    })
    await closeDialog()

    await shot('import.png', async () => {
      await nav('Import')
      await visible('Recent imports')
      await visible('Categorisation rules')
    })

    console.log(`\nSaved ${saved.length} screenshots to ${relative(process.cwd(), outDir) || outDir}`)
  } finally {
    await browser.close()
  }
}

async function checkAppIsRunning() {
  try {
    const response = await fetch(`${base}/login`)
    if (!response.ok) throw new Error(`status ${response.status}`)
  } catch {
    throw new Error(`FinLedger isn't answering at ${base}. Start it first with "docker compose up -d" from the project root.`)
  }
}

async function launchBrowser() {
  const headless = !show
  if (process.env.SCREENSHOT_BROWSER_PATH) {
    return chromium.launch({ executablePath: process.env.SCREENSHOT_BROWSER_PATH, headless })
  }
  for (const channel of ['msedge', 'chrome']) {
    try {
      return await chromium.launch({ channel, headless })
    } catch {
      // not installed; try the next one
    }
  }
  throw new Error('Couldn\'t start Microsoft Edge or Google Chrome. Set SCREENSHOT_BROWSER_PATH to a Chromium-based browser.')
}

main().catch((error) => {
  console.error(`\n${error.message}`)
  process.exit(1)
})
