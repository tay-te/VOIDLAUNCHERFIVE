/**
 * Drive `pnpm dev:web` in a real Chromium and take the eight review shots.
 *
 * The browser preview is the whole point of the mocked `@tauri-apps/api`: every screen,
 * every launch phase and every error state renders with fixture data, so a designer's
 * pass needs no Rust and no webview. This just walks it.
 */

import { mkdirSync, rmSync } from 'node:fs';
import path from 'node:path';

import { need, OUT, SHOTS, VIEWPORT } from './lib.mjs';

const URL_BASE = process.env.VQA_URL ?? 'http://127.0.0.1:5183/';
const TAG = process.argv[2] ?? 'after'; // `before` for the baseline pass

/**
 * `--no-backdrop` runs the preview with the dev-only design crop switched off, so the
 * canvas shows the gradient placeholder the shipped launcher draws. That is the pass
 * to compare against an older baseline; the default pass is the one that isolates the
 * components from the art the repository does not have.
 */
const PAGE = URL_BASE + (process.argv.includes('--no-backdrop') ? '?no-backdrop' : '');

const { chromium } = await need('playwright');

rmSync(path.join(OUT, TAG), { recursive: true, force: true });
mkdirSync(path.join(OUT, TAG), { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage({
  viewport: VIEWPORT,
  deviceScaleFactor: 1,
  colorScheme: 'dark',
  reducedMotion: 'reduce',
});

await page.goto(PAGE, { waitUntil: 'networkidle' });

// The preview banner says "you are looking at the mock". It is true and it belongs on
// the screen — but it is not in any frame, so it is hidden for the comparison shots.
await page.addStyleTag({ content: '.banner--preview { display: none !important; }' });
await page.waitForTimeout(400);

/** The preview starts signed out; the frames are all drawn signed in as Searge. */
async function signIn() {
  await page.getByRole('button', { name: 'Settings', exact: true }).first().click();
  const field = page.getByLabel('Offline account name');
  await field.waitFor();
  await field.fill('Searge');
  await page.getByRole('button', { name: 'Use', exact: true }).click();
  await page.waitForTimeout(250);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(250);
}

async function go(screen) {
  await page.getByRole('button', { name: screen, exact: true }).first().click();
  await page.waitForTimeout(450);
}

const shot = async (id) => {
  // Every frame is one 1300 × 820 window, so a viewport shot is the comparable unit.
  await page.screenshot({ path: path.join(OUT, TAG, `${id}.png`), animations: 'disabled' });
  process.stdout.write(`  ${id}\n`);
};

process.stdout.write(`capturing (${TAG}) from ${PAGE}\n`);
await signIn();

await go('Play');
await shot('play');

await go('Mods');
await shot('mods');

await go('Cosmetics');
await shot('cosmetics');

await go('Servers');
await page.waitForTimeout(900); // let the fixture pings land so the pane has numbers
await shot('servers');

await go('Friends');
await shot('friends');

// Settings — the gear, over whichever screen is up. Play, to match the frames.
await go('Play');
await page.getByRole('button', { name: 'Settings', exact: true }).first().click();
await page.waitForTimeout(500);
await shot('settings');
await page.keyboard.press('Escape');
await page.waitForTimeout(300);

// The ⌘K palette, with a query typed so the result list is populated.
await page.keyboard.press('ControlOrMeta+k');
await page.waitForTimeout(400);
await page.keyboard.type('key');
await page.waitForTimeout(400);
await shot('palette');
await page.keyboard.press('Escape');
await page.waitForTimeout(300);

// The dock mid-launch. `prepare` in the mock walks the same steps the real one does,
// so wait for the CTA to actually enter its progress state rather than guessing.
await page.getByRole('button', { name: /^Launch/ }).click();
await page.locator('.dock__launch').waitFor({ state: 'attached', timeout: 10_000 });
await page.waitForTimeout(700);
await shot('launching');

await browser.close();
process.stdout.write(`wrote ${SHOTS.length} shots to visual-qa/out/${TAG}\n`);
