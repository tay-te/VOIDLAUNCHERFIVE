// VOID logo concepts. Every mark is generated, so sizes/colors stay exact.
// node generate.mjs → writes marks/*.svg, lockups/*.svg, icons/*.svg, sheet.html
import fs from 'node:fs'; import path from 'node:path';
const OUT = path.dirname(new URL(import.meta.url).pathname);
const C = { base:'#131315', card:'#191A1C', ink:'#EDEEEF', v:'#9F8BFF', vd:'#5B47D6', vdd:'#2E2470', o:'#FF9E7A', cy:'#7ADFFF', g:'#7AE0B0', mut:'#9A9DA1' };
const svg = (vb, body, defs='') => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}" fill="none">${defs?`<defs>${defs}</defs>`:''}${body}</svg>`;
const r = (x,y,w,h,fill,rx=0,extra='') => `<rect x="${+x.toFixed(2)}" y="${+y.toFixed(2)}" width="${+w.toFixed(2)}" height="${+h.toFixed(2)}" rx="${rx}" fill="${fill}"${extra}/>`;
const poly = (pts,fill,extra='') => `<polygon points="${pts.map(p=>p.map(n=>+n.toFixed(2)).join(',')).join(' ')}" fill="${fill}"${extra}/>`;

// The 16-cell ring on a 7x7 grid — the existing VOID mark, kept as the shared DNA.
const RING = [[2,0],[3,0],[4,0],[1,1],[5,1],[0,2],[6,2],[0,3],[6,3],[0,4],[6,4],[1,5],[5,5],[2,6],[3,6],[4,6]];

const marks = {};
// 01 · Escape — the ring with one cell breaking free. Asymmetry = motion; the gap it leaves is the "void".
marks.escape = () => { const u=16, s=13.5; let b='';
  for (const [x,y] of RING) { if (x===5&&y===1) continue; b += r(4+x*u,4+y*u,s,s,C.ink,3); }
  b += r(4+5*u+7,4+1*u-7,s,s,C.v,3);
  return svg('0 0 120 120', b); };

// 02 · Event horizon — ring fades clockwise like it's spinning into the center; violet inner core cells.
marks.horizon = () => { const u=12.5, s=10.5, o=4; let b='';
  const outer=[]; for(let i=0;i<9;i++){outer.push([i,0]);} for(let i=1;i<9;i++)outer.push([8,i]); for(let i=7;i>=0;i--)outer.push([i,8]); for(let i=7;i>0;i--)outer.push([0,i]);
  const corners=new Set(['0,0','8,0','8,8','0,8']);
  const ring=outer.filter(([x,y])=>!corners.has(x+','+y));
  ring.forEach(([x,y],i)=>{ const a=0.18+0.82*(1-i/ring.length); b+=r(o+x*u,o+y*u,s,s,C.ink,2.5,` opacity="${a.toFixed(2)}"`); });
  const inner=[[3,2],[4,2],[5,2],[2,3],[6,3],[2,4],[6,4],[2,5],[6,5],[3,6],[4,6],[5,6]];
  inner.forEach(([x,y],i)=>{ b+=r(o+x*u,o+y*u,s,s,i%3===0?C.v:C.vd,2.5); });
  return svg('0 0 120 120', b); };

// 03 · Hollow block — a Minecraft block with its core carved out. Literal "void" + the game in one shape.
marks.block = () => { const k=52, cx=60, cy=8, c=0.866;
  const P=(x,y,z)=>[cx+(x-y)*k*c, cy+(x+y)*k*0.5-(z)*k+k];
  const a=0.3, bb=0.7, d=0.55;
  const top=[P(0,0,1),P(1,0,1),P(1,1,1),P(0,1,1)];
  const hole=[P(a,a,1),P(bb,a,1),P(bb,bb,1),P(a,bb,1)];
  const pth=(pts)=>'M'+pts.map(p=>p.map(n=>n.toFixed(2)).join(',')).join('L')+'Z';
  let s='';
  s+=poly([P(1,0,1),P(1,1,1),P(1,1,0),P(1,0,0)],C.vd); // right face x=1
  s+=poly([P(0,1,1),P(1,1,1),P(1,1,0),P(0,1,0)],C.v);  // left face y=1
  s+=`<path d="${pth(top)} ${pth(hole)}" fill="url(#bt)" fill-rule="evenodd"/>`;
  s+=`<g clip-path="url(#hc)">${poly(hole,C.base)}`;
  s+=poly([P(a,a,1),P(a,bb,1),P(a,bb,1-d),P(a,a,1-d)],C.vdd); // far wall x=a
  s+=poly([P(a,a,1),P(bb,a,1),P(bb,a,1-d),P(a,a,1-d)],'#3D2F99'); // far wall y=a
  s+=poly([P(a,a,1-d),P(bb,a,1-d),P(bb,bb,1-d),P(a,bb,1-d)],'#0B0B0D');
  s+=`</g>`;
  s+=`<path d="${pth(top)}" stroke="#fff" stroke-opacity=".35" stroke-width="1"/>`;
  const defs=`<linearGradient id="bt" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E4DEFF"/><stop offset="1" stop-color="#C3B6FF"/></linearGradient><clipPath id="hc"><path d="${pth(hole)}"/></clipPath>`;
  return svg('0 0 120 120', s, defs); };

// 04 · Portal — stepped pixel O, glowing core, 4-point spark. Reads as "O" in VOID and as a portal.
marks.portal = () => { const u=10, o=5; let b='';
  const rows=['..XXXXXXX..','.XX.....XX.','XX.......XX','X.........X','X.........X','X.........X','X.........X','X.........X','XX.......XX','.XX.....XX.','..XXXXXXX..'];
  b+=`<circle cx="60" cy="60" r="40" fill="url(#pg)"/>`;
  rows.forEach((row,y)=>[...row].forEach((ch,x)=>{ if(ch==='X') b+=r(o+x*u,o+y*u,u,u,C.ink); }));
  b+=`<path d="M60 40 C62 56 64 58 80 60 C64 62 62 64 60 80 C58 64 56 62 40 60 C56 58 58 56 60 40Z" fill="${C.ink}"/>`;
  return svg('0 0 120 120', b, `<radialGradient id="pg"><stop offset="0" stop-color="${C.v}" stop-opacity=".9"/><stop offset=".6" stop-color="${C.vd}" stop-opacity=".45"/><stop offset="1" stop-color="${C.vd}" stop-opacity="0"/></radialGradient>`); };

// 05 · Strike V — a V built from stepped cells; the tip lands as an orange hit cell. Monogram + sword strike.
marks.strike = () => { const u=14, s=12.5, o=4; let b='';
  const cells=[[0,0],[1,0],[6,0],[7,0],[0,1],[1,1],[2,1],[5,1],[6,1],[7,1],[1,2],[2,2],[5,2],[6,2],[1,3],[2,3],[3,3],[4,3],[5,3],[6,3],[2,4],[3,4],[4,4],[5,4],[2,5],[3,5],[4,5],[5,5],[3,6],[4,6]];
  const set=new Set(cells.map(c=>c+''));
  for(const [x,y] of cells){ if(y===6) continue; b+=r(o+x*u,o+y*u+4,s,s,C.ink,2.5); }
  b+=r(o+3*u,o+6*u+4,s*2+1.5,s,C.o,2.5);
  return svg('0 0 120 120', b); };

// 06 · Crit — 3x3 with the center deleted and the corners blown outward. The hit moment, as a glyph.
marks.crit = () => { let b=''; const s=26, g=8, base=60-s*1.5-g;
  for(let y=0;y<3;y++)for(let x=0;x<3;x++){ if(x===1&&y===1) continue;
    const corner=(x!==1&&y!==1); const dx=(x-1)*(corner?7:0), dy=(y-1)*(corner?7:0);
    b+=r(base+x*(s+g)+dx,base+y*(s+g)+dy,s,s,corner?C.ink:C.v,5,corner?'':''); }
  b+=`<path d="M60 49 L71 60 L60 71 L49 60Z" fill="${C.o}"/>`;
  return svg('0 0 120 120', b); };

// 07 · Dissolve — a clean ring that breaks into cells, smooth to pixel. "Modern client, Minecraft soul."
marks.dissolve = () => { let b=''; const R=40, sw=13;
  const arc=(a0,a1)=>{const p=(a)=>[60+R*Math.cos(a),60+R*Math.sin(a)];const [x0,y0]=p(a0),[x1,y1]=p(a1);return `M${x0.toFixed(2)} ${y0.toFixed(2)} A${R} ${R} 0 ${a1-a0>Math.PI?1:0} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`;};
  const start=-Math.PI*0.95, end=Math.PI*0.25;
  b+=`<path d="${arc(start,end)}" stroke="${C.ink}" stroke-width="${sw}" stroke-linecap="butt"/>`;
  const n=7; for(let i=0;i<n;i++){ const t=(i+1)/(n+1); const a=end+(Math.PI*2-(end-start))*t*0.92; const sz=sw*(1-t*0.75); const jitter=(i%2?1:-1)*t*6;
    const cx=60+(R+jitter)*Math.cos(a), cy=60+(R+jitter)*Math.sin(a);
    b+=r(cx-sz/2,cy-sz/2,sz,sz,i<2?C.ink:C.v,sz*0.18,` opacity="${(1-t*0.55).toFixed(2)}"`); }
  return svg('0 0 120 120', b); };

// 08 · Monolith — the ring compressed into a keycap-like square: four L-brackets around an empty core.
marks.frame = () => { let b=''; const u=14,s=12.5,o=11;
  const L=[[0,0],[1,0],[0,1], [5,0],[6,0],[6,1], [0,5],[0,6],[1,6], [6,5],[6,6],[5,6]];
  for(const [x,y] of L) b+=r(o+x*u,o+y*u,s,s,C.ink,2.5);
  b+=r(o+3*u-1,o+3*u-1,s+2,s+2,C.v,3);
  return svg('0 0 120 120', b); };

// ---------- Wordmarks (drawn as geometry, no font dependency) ----------
function wordGeo(markFn){ // V  [ring O]  I  D, stroke weight 14 on a 120 cap height
  const sw=14, h=120;
  let b=`<g stroke="${C.ink}" stroke-width="${sw}" stroke-linecap="butt" stroke-linejoin="miter" fill="none">`;
  b+=`<path d="M7 0 L48 ${h-6} L89 0"/>`;
  b+=`<path d="M${120+96+33} 0 V${h}"/>`;
  b+=`<path d="M${120+96+70} ${sw/2} H${120+96+100} C${120+96+150} ${sw/2} ${120+96+163} 30 ${120+96+163} 60 C${120+96+163} 90 ${120+96+150} ${h-sw/2} ${120+96+100} ${h-sw/2} H${120+96+70} Z"/>`;
  b+=`</g>`;
  const inner=markFn().replace(/^<svg[^>]*>/,'').replace(/<\/svg>$/,'');
  b+=`<svg x="104" y="0" width="120" height="120" viewBox="0 0 120 120">${inner}</svg>`;
  return svg(`-4 -4 ${120+96+175} 128`, b); }
function wordPixel(){ // 5x7 pixel VOID, O swapped for the ring
  const F={V:['X...X','X...X','X...X','X...X','.X.X.','.X.X.','..X..'],I:['XXX','.X.','.X.','.X.','.X.','.X.','XXX'],D:['XXXX.','X...X','X...X','X...X','X...X','X...X','XXXX.']};
  const u=16,s=14; let b='', x0=0;
  const draw=(g,col=C.ink)=>{g.forEach((row,y)=>[...row].forEach((ch,x)=>{if(ch==='X')b+=r(x0+x*u,y*u,s,s,col,2.5);}));x0+=(g[0].length+1)*u;};
  draw(F.V); for(const [x,y] of RING) b+=r(x0+x*u,y*u,s,s,C.v,2.5); x0+=8*u; draw(F.I); draw(F.D);
  return svg(`-4 -4 ${x0-u+8} ${7*u+6}`, b); }

// ---------- App icon: keycap slab around a mark ----------
function icon(markFn, id){ const inner=markFn().replace(/^<svg[^>]*>/,'').replace(/<\/svg>$/,'');
  return svg('0 0 256 256', `<rect x="8" y="14" width="240" height="236" rx="56" fill="#08080A"/><rect x="8" y="8" width="240" height="236" rx="56" fill="url(#ic${id})"/><rect x="8.5" y="8.5" width="239" height="235" rx="55.5" stroke="#fff" stroke-opacity=".10"/><svg x="56" y="54" width="144" height="144" viewBox="0 0 120 120">${inner}</svg>`,
  `<linearGradient id="ic${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#26272B"/><stop offset="1" stop-color="#17181A"/></linearGradient>`); }

// ---------- write files + sheet ----------
const meta = {
  escape:['Escape','Your current ring, one cell breaking free. Asymmetry reads as motion; the gap it leaves is the void.'],
  horizon:['Event Horizon','The ring fades clockwise into a violet core. It looks like it is spinning even when it isn\'t.'],
  block:['Hollow Block','A Minecraft block with its core carved out. The game and the word "void" in one shape.'],
  portal:['Portal','A stepped pixel O around a glowing core. It is the O in VOID and a portal at the same time.'],
  strike:['Strike V','A V monogram in stepped cells that lands on an orange hit cell, like a sword strike.'],
  crit:['Crit','A 3×3 grid with the center deleted and the corners blown out. The hit moment as a glyph; survives 16px.'],
  dissolve:['Dissolve','A clean ring that breaks into cells. A modern client with a Minecraft soul.'],
  frame:['Bracket','Four corner brackets around one violet cell. A crosshair and a focus target; very calm.'],
};
for (const d of ['marks','lockups','icons']) fs.mkdirSync(path.join(OUT,d),{recursive:true});
const files={};
Object.entries(marks).forEach(([k,f],i)=>{ fs.writeFileSync(path.join(OUT,'marks',`${String(i+1).padStart(2,'0')}-${k}.svg`), f()); fs.writeFileSync(path.join(OUT,'icons',`${String(i+1).padStart(2,'0')}-${k}-icon.svg`), icon(f,k)); files[k]=f(); });
fs.writeFileSync(path.join(OUT,'lockups','wordmark-geometric.svg'), wordGeo(marks.escape));
fs.writeFileSync(path.join(OUT,'lockups','wordmark-pixel.svg'), wordPixel());
const mono = (s)=>s.replaceAll(C.ink,'#131315').replaceAll(C.v,'#131315').replaceAll(C.vd,'#131315').replaceAll(C.o,'#131315');
const card=(k,i)=>`<div class="card"><div class="art">${files[k]}</div><div class="cap"><b>${String(i+1).padStart(2,'0')} · ${meta[k][0]}</b><span>${meta[k][1]}</span></div>
 <div class="sizes">${[16,24,32,48].map(z=>`<div style="width:${z}px;height:${z}px">${files[k]}</div>`).join('')}<div class="light" style="width:48px;height:48px">${mono(files[k])}</div><div class="ic">${icon(marks[k],k+'s')}</div></div></div>`;
const html=`<!doctype html><html><head><meta charset="utf-8"><link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500&display=swap" rel="stylesheet"><style>
body{margin:0;background:#0F0F11;color:#EDEEEF;font-family:Outfit,sans-serif;padding:56px;width:1688px}
h1{font-weight:500;font-size:40px;margin:0 0 6px;letter-spacing:-.02em}p.s{color:#9A9DA1;margin:0 0 40px;font-size:17px}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:20px}
.card{background:#191A1C;border:1px solid #ffffff14;border-radius:28px;padding:22px;display:flex;flex-direction:column;gap:16px}
.art{background:radial-gradient(circle at 50% 45%,#9F8BFF22,transparent 65%),#131315;border-radius:20px;aspect-ratio:1;display:grid;place-items:center;box-shadow:inset 0 2px 4px #0006}
.art>svg{width:58%;height:58%}.cap b{display:block;font-weight:500;font-size:17px;margin-bottom:4px}.cap span{color:#9A9DA1;font-size:13.5px;line-height:1.45}
.sizes{display:flex;align-items:center;gap:14px;border-top:1px solid #ffffff10;padding-top:14px}.sizes>div>svg{width:100%;height:100%;display:block}
.light{background:#EDEEEF;border-radius:10px;padding:6px;box-sizing:content-box}.ic{width:56px;height:56px;margin-left:auto}
h2{font-weight:500;font-size:22px;margin:56px 0 18px}.lock{display:flex;gap:20px}.lock>div{flex:1;background:#191A1C;border:1px solid #ffffff14;border-radius:28px;padding:56px;display:grid;place-items:center}.lock>div>svg{height:84px}
</style></head><body><h1>VOID · logo concepts</h1><p class="s">8 marks in the cell language. Each card shows the mark at 16, 24, 32 and 48 px, a one-colour version on light, and the app icon.</p>
<div class="grid">${Object.keys(marks).map(card).join('')}</div>
<h2>Wordmarks</h2><div class="lock"><div>${wordGeo(marks.escape)}</div><div>${wordPixel()}</div></div></body></html>`;
fs.writeFileSync(path.join(OUT,'sheet.html'),html);
console.log('ok', Object.keys(marks).length);
