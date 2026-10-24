// Launch a private headless Chrome and hand back a CDP connection to one page.
//
// Every run gets a fresh, throwaway profile, so localStorage (the launcher keeps its
// prefs, friends and "Notify me" wishes there) starts empty and two runs see the same
// state. The profile is deleted again on close.

import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { CDP } from './cdp.mjs';

const DEFAULT_CHROME = [
  process.env.WEBCAP_CHROME,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].filter(Boolean);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function launchChrome({ chrome, width, height, headful = false, gpu = false, extraArgs = [] } = {}) {
  const exe = chrome ?? DEFAULT_CHROME.find((p) => existsSync(p));
  if (!exe) throw new Error('Chrome not found; pass --chrome <path> or set WEBCAP_CHROME');
  const profile = mkdtempSync(join(tmpdir(), 'webcap-'));
  const args = [
    ...(headful ? [] : ['--headless=new']),
    '--remote-debugging-port=0',
    `--user-data-dir=${profile}`,
    `--window-size=${width},${height}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    '--disable-sync',
    '--disable-default-apps',
    '--disable-component-update',
    '--disable-background-networking',
    // Keep the page "foreground" however long a 4K frame takes to encode.
    '--disable-background-timer-throttling',
    '--disable-renderer-backgrounding',
    '--disable-backgrounding-occluded-windows',
    '--disable-hang-monitor',
    '--hide-scrollbars',
    // Wheel actions jump instead of easing on a real-time compositor animation.
    '--disable-smooth-scrolling',
    '--mute-audio',
    // Same colours on every machine, independent of the monitor's ICC profile.
    '--force-color-profile=srgb',
    '--font-render-hinting=none',
    // Software rendering by default. With the GPU, filtered layers (the Coming soon glow is
    // a blur(84px)) come out up to 1 level different when the gaps between frames vary;
    // in software every frame is bit-identical however slow the capture. 2-3x slower.
    ...(gpu ? [] : ['--disable-gpu']),
    ...extraArgs,
    'about:blank',
  ];
  const proc = spawn(exe, args, { stdio: ['ignore', 'ignore', 'pipe'] });
  // Never leave a headless Chrome behind, whatever way this process ends.
  const reap = () => {
    if (proc.exitCode === null) proc.kill();
  };
  process.once('exit', reap);
  for (const sig of ['SIGINT', 'SIGTERM']) process.once(sig, () => process.exit(130));
  let stderr = '';
  proc.stderr.on('data', (d) => {
    stderr = (stderr + d).slice(-4000);
  });

  // --remote-debugging-port=0 picks a free port and writes it to DevToolsActivePort.
  const portFile = join(profile, 'DevToolsActivePort');
  let port;
  for (let i = 0; i < 200 && !port; i++) {
    if (proc.exitCode !== null) throw new Error(`Chrome exited early (${proc.exitCode}):\n${stderr}`);
    if (existsSync(portFile)) {
      const [p] = readFileSync(portFile, 'utf8').split('\n');
      if (p && /^\d+$/.test(p.trim())) port = Number(p.trim());
    }
    if (!port) await sleep(50);
  }
  if (!port) throw new Error(`Chrome did not open a DevTools port:\n${stderr}`);

  const base = `http://127.0.0.1:${port}`;
  const version = await (await fetch(`${base}/json/version`)).json();
  let page;
  for (let i = 0; i < 100 && !page; i++) {
    const list = await (await fetch(`${base}/json/list`)).json();
    page = list.find((t) => t.type === 'page');
    if (!page) await sleep(50);
  }
  if (!page) throw new Error('Chrome has no page target');
  const cdp = await CDP.connect(page.webSocketDebuggerUrl);

  async function close() {
    cdp.close();
    try {
      const b = await CDP.connect(version.webSocketDebuggerUrl);
      await Promise.race([b.send('Browser.close').catch(() => {}), sleep(3000)]);
      b.close();
    } catch {
      /* fall through to kill */
    }
    for (let i = 0; i < 60 && proc.exitCode === null; i++) await sleep(50);
    if (proc.exitCode === null) proc.kill();
    for (let i = 0; i < 20; i++) {
      try {
        rmSync(profile, { recursive: true, force: true });
        break;
      } catch {
        await sleep(150); // Windows holds the profile's files for a moment after exit
      }
    }
  }

  return { cdp, close, version: version.Browser, proc };
}
