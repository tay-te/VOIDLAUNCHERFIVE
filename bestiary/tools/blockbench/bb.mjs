// Drives the real Blockbench desktop app (installed by the user) through the Chrome DevTools Protocol, so Fray's
// Blockbench conversion is checked against Blockbench's own exporter rather than a hand-written sample.
//
//   node tools/blockbench/bb.mjs roundtrip <in.bbmodel> <out dir>
//       loads the project in Blockbench and writes, from Blockbench itself:
//         <name>.bbmodel            Blockbench's own save of the project (uncompressed, texture embedded)
//         <name>.java               File > Export > Java Entity (the modded entity exporter, Mojang mappings)
//         <name>_animations.java    Animation > Export Animations (vanilla AnimationDefinition keyframes)
//         <name>_front.png, <name>_side.png   the viewport from Blockbench's front and right angle presets
//   node tools/blockbench/bb.mjs eval "<js>"          evaluates JS in a running (or freshly started) Blockbench
//
// Blockbench is started with its own user-data folder (build/blockbench-user, so the user's settings and recent files
// are untouched), a remote-debugging port (9333 unless BB_PORT is set) and closed again at the end.
import { spawn } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { basename, dirname, resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const project = resolve(here, '..', '..');
const port = Number(process.env.BB_PORT || 9333);
const exe = process.env.BLOCKBENCH || join(process.env.LOCALAPPDATA || 'C:/Users/bitto/AppData/Local', 'Programs/Blockbench/Blockbench.exe');
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function targets() {
  try {
    return await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  } catch (e) {
    return null;
  }
}

async function start() {
  if (await targets()) return null;
  if (!existsSync(exe)) throw new Error('Blockbench not found at ' + exe + ' (set BLOCKBENCH)');
  const user = join(project, 'build', 'blockbench-user');
  mkdirSync(user, { recursive: true });
  const child = spawn(exe, ['--userData', user, `--remote-debugging-port=${port}`], { detached: false, stdio: 'ignore' });
  for (let i = 0; i < 60; i++) {
    await sleep(1000);
    const t = await targets();
    if (t && t.find(x => x.type === 'page')) return child;
  }
  throw new Error('Blockbench did not open its debugging port');
}

async function connect() {
  const page = (await targets()).find(t => t.type === 'page');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  let id = 0;
  const call = (method, params = {}) => new Promise((res, rej) => {
    const my = ++id;
    const h = ev => {
      const m = JSON.parse(ev.data);
      if (m.id !== my) return;
      ws.removeEventListener('message', h);
      m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result);
    };
    ws.addEventListener('message', h);
    ws.send(JSON.stringify({ id: my, method, params }));
  });
  const evaluate = async expr => {
    const r = await call('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error('Blockbench: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text));
    return r.result.value;
  };
  return { ws, call, evaluate };
}

async function quit(child) {
  try {
    const v = await (await fetch(`http://127.0.0.1:${port}/json/version`)).json();
    const ws = new WebSocket(v.webSocketDebuggerUrl);
    await new Promise(res => { ws.onopen = res; ws.onerror = res; });
    ws.send(JSON.stringify({ id: 1, method: 'Browser.close' }));
    await sleep(1500);
  } catch (e) { /* already gone */ }
  if (child && child.exitCode === null) child.kill();
}

async function ready(bb) {
  for (let i = 0; i < 60; i++) {
    if (await bb.evaluate(`typeof Codecs === 'object' && typeof loadModelFile === 'function' && typeof Blockbench === 'object'`)) return;
    await sleep(500);
  }
  throw new Error('Blockbench never became ready');
}

async function roundtrip(input, outDir) {
  const child = await start();
  try {
    const bb = await connect();
    await ready(bb);
    const name = basename(input).replace(/\.bbmodel$/, '');
    const content = readFileSync(input, 'utf8');
    const path = resolve(input).replace(/\\/g, '/');
    const version = await bb.evaluate(`Blockbench.version`);
    await bb.evaluate(`(() => {
      // Close anything open (the start screen has no project), then load the file the way File > Open does.
      for (const p of ModelProject.all.slice()) p.close(true);
      loadModelFile({ content: ${JSON.stringify(content)}, path: ${JSON.stringify(path)}, name: ${JSON.stringify(basename(input))} });
      return true;
    })()`);
    for (let i = 0; i < 40 && !(await bb.evaluate(`!!(window.Project && Project.format && Project.format.id === 'modded_entity' && Group.all.length)`)); i++) await sleep(250);
    await sleep(1500);
    mkdirSync(outDir, { recursive: true });
    const saved = await bb.evaluate(`Codecs.project.compile({ bitmaps: true, absolute_paths: false })`);
    const java = await bb.evaluate(`Codecs.modded_entity.compile()`);
    const anims = await bb.evaluate(`Animation.all.length ? AnimationCodec.codecs.modded_entity.compileFile(Animation.all) : ''`);
    writeFileSync(join(outDir, name + '.bbmodel'), saved.endsWith('\n') ? saved : saved + '\n');
    writeFileSync(join(outDir, name + '.java'), java);
    if (anims) writeFileSync(join(outDir, name + '_animations.java'), anims);
    // Viewport pictures from Blockbench's own angle presets (for comparing with the game).
    for (const [label, preset] of [
      ['front', { projection: 'perspective', position: [0, 26, -95], target: [0, 22, 0] }],
      ['side', { projection: 'perspective', position: [95, 26, 0], target: [0, 22, 0] }],
      ['iso', { projection: 'perspective', position: [-62, 50, -62], target: [0, 21, 0] }]]) {
      const ok = await bb.evaluate(`(() => {
        const p = Preview.selected || Preview.all[0];
        if (!p) return false;
        p.loadAnglePreset(${JSON.stringify(preset)});
        return true;
      })()`).catch(() => false);
      if (!ok) continue;
      await sleep(700);
      const rect = await bb.evaluate(`(() => { const c = (Preview.selected || Preview.all[0]).canvas.getBoundingClientRect(); return {x: c.x, y: c.y, width: c.width, height: c.height}; })()`);
      const shot = await bb.call('Page.captureScreenshot', { format: 'png', clip: { ...rect, scale: 1 } });
      writeFileSync(join(outDir, `${name}_${label}.png`), Buffer.from(shot.data, 'base64'));
    }
    console.log(`Blockbench ${version}: wrote ${name}.bbmodel, ${name}.java${anims ? ', ' + name + '_animations.java' : ''} to ${outDir}`);
    bb.ws.close();
  } finally {
    await quit(child);
  }
}

async function evalCmd(expr) {
  const child = await start();
  try {
    const bb = await connect();
    await ready(bb);
    console.log(JSON.stringify(await bb.evaluate(expr), null, 1));
    bb.ws.close();
  } finally {
    await quit(child);
  }
}

const [cmd, ...args] = process.argv.slice(2);
if (cmd === 'roundtrip') await roundtrip(args[0], args[1]);
else if (cmd === 'eval') await evalCmd(args[0]);
else {
  console.error('usage: node tools/blockbench/bb.mjs roundtrip <in.bbmodel> <out dir> | eval "<js>"');
  process.exit(2);
}
