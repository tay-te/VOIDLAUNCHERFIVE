# Rendering invariants — what the in-game overlay must keep true

Scope: the in-game overlay's render, present and **input** path — `UiHost`, the GL driver,
the presentation gate, and the CSS that depends on how Ultralight actually
behaves. Its sibling `ultralight-notes.md` covers what the engine **cannot
render**; this file covers how the engine **behaves**, which is a different
subject and the one that keeps costing us.

Every invariant below was learned by breaking it. Each carries the symptom it
produced, because the symptom is how you will recognise it coming back — none of
these announced themselves as rendering bugs, and none of them appeared as a
regression in a diff.

**The failure mode this file exists to prevent.** Four times in one session, a
later fix silently invalidated an earlier fix's precondition: the supersample cap,
the GPU path after the thread split, the scrim after the blur removal, and the
scroll smoothing after the park change. In every case both changes were correct in
isolation, the second was additive, review saw nothing, and the tests stayed green.
An invariant that lives only in one function's head is not protected by anything.

Confidence key: **[measured]** = observed in game with a probe, numbers below;
**[derived]** = follows from the binding's documented rules or the engine's source.

---

## 1. `setNeedsPaint(true)` is a request, not a command — **[measured]**

Ultralight honours it only as part of a paint **that draws something**. A page
with nothing new submits no command list, and `draw_command_list` correctly
early-returns on an empty one.

**Therefore a clear may only ever be spent on a frame the page is itself
repainting.** The gate is `clearNow = pageDirty && System.nanoTime() < clearUntilNanos`.

*Symptom when violated:* the close-frame glitch. A one-shot clear was spent on
the next frame that *rendered* rather than the next that *repainted*. At +6 ms
after the keypress the page had been told `menu:false` but React had not yet
committed, so the clear landed and the repaint did not — one completely blank
frame, ~50 ms of nothing, then the menu flashing back at 40% and 12% before
leaving. Frame-by-frame luma, before the fix:

```
+0  menu solid       15.51 / 24.16 / 18.03
+1  EVERYTHING GONE  50.82 / 84.90 / 75.31   <-- the bad frame
+7  menu back ~41%   36.35 / 60.71 / 52.59   <-- the flash
```

*Corollary:* the clear is a **window**, not a flag. The frame whose content
becomes nothing is ~130 ms away, not this one, so a single clear is spent on the
first frame of the fade and gone before it is needed. 450 ms, outlasting the
page's own 400 ms exit fallback.

## 2. The driver cannot tell you whether the page produced a frame — **[measured]**

`frame_serial()` only advances inside `draw_command_list()`, which Ultralight
never calls when there is nothing to draw. So "has the page drawn something new"
is unanswerable by asking the driver, and presentation is gated on an explicit
render serial the host owns.

*Symptom when violated:* the menu-not-closing saga. Six fixes were aimed at the
input path — a latch on `keyPressed`, sibling-modifier matching, an auto-repeat
guard, a 250 ms debounce, a frame-based re-arm, and an ordering bug in
`assumeDown()`. The page had been unmounting correctly the entire time
(`overlay/menuLayer = 0/0` for 30 consecutive frames) while the compositor kept
publishing the last texture. The user's very first report — "the game focuses
when I hit right shift" — was the actual answer.

## 3. The driver must not infer a clear from an empty command list — **[measured]**

Tempting, and wrong. On a live menu the driver renders ~51 times a second and
only **4–8** of those submit a command list, so ~85% of frames take the clear
branch.

*Symptom when violated:* the menu flashed and vanished. The host must drive
`clearTarget()` explicitly.

## 4. An idle open menu paints nothing — **[measured]**

Baseline on the GL driver: **0 paints/s, 0 presents/s** with the menu open and
the cursor still. This is a hard budget, not an aspiration; it is what makes the
overlay free when nobody is touching it.

The trap: on the accelerated path `setNeedsPaint()` is **our** mechanism for
forcing a full repaint, not a report that the page changed. Ultralight clears
that flag only as part of a paint that drew something (§1), so a forced render
of an unchanged page leaves our own flag standing, the next frame reads it back
as "dirty", sets it again, and the view renders every frame for the rest of the
process.

*Symptom when violated:* 50 paints/s against 0–2 presents/s, permanently.

*The narrow rule:* clear the flag only in the `!pageDirty` case. A flag
Ultralight raised must never be cleared here — if it wanted a paint and did not
get one this frame it must still be outstanding on the next, or the first paint
of a page still coming up is swallowed (the "cards only appear once hovered"
failure).

## 5. Full repaint each frame is required on GPU, and forbidden as a default — **[derived]**

`needsFullRepaintEachFrame() == accelerated`; `rendersEveryFrame() == !accelerated`.
Ultralight's incremental path leaves the changed region uncleared and composites
glyphs premultiplied over whatever is already there, so anything that updates —
an fps readout, a CPS counter — piles successive frames on top of itself until
unreadable. Config-level `ForceRepaint` fixes it too, but marks the view dirty
every frame: measured **22 ms against 0.5 ms**. Paying the full repaint only on
frames that actually changed is the whole difference.

**A renderer fallback changes which of these is in force.** Anything downstream
that assumes one must still be correct after a switch.

## 6. The game thread never blocks on the UI thread — **[derived]**

The invariant the entire thread split exists to protect. `hasFocusedInput()` is
cached by the UI thread each frame rather than asked synchronously, and the
answer is accepted as up to one frame stale, precisely because asking would be
either a renderer call from the wrong thread or a block.

## 7. One thread owns the renderer and every view for the life of the process — **[derived]**

Pinning, not locking: WebCore builds per-thread globals on first touch. It must
be a **Java** thread (`createRenderer` resolves resources through the class
loader of the nearest Java frame) and it must **never return from `run()`** — a
thread that has touched WebCore aborts the process on the way out. It parks;
shutdown stops drawing rather than closing.

*Consequence:* a view cannot be recreated from another thread, which constrains
any renderer-fallback design.

## 8. The accelerated path needs its own GL context sharing Minecraft's — **[measured]**

*Symptom when violated:* `SIGSEGV` in `glGetString` on the first accelerated
call from the UI thread. The thread split had left GL work on the game thread on
the reasoning that "the game thread keeps only the GL work, which is where its
context is" — true, and exactly why the other thread needs one of its own.

## 9. GPU failure must degrade, not latch — **[measured]**

`g_gpu_failed` used to be process-wide, set on `gpu::initialize()` failure, never
retried, with every subsequent accelerated render early-returning. On a machine
where the GLSL 1.20 shaders will not link the user got a **blank overlay with
no visible error**, on hardware where the CPU surface would have worked.

The GL driver is now probed before any view exists and polled once a frame
afterwards, and either answer rebuilds the view on the CPU surface. That fallback
is the entire justification for the accelerated renderer being the default, so
what it costs is worth stating exactly: the CPU path was measured rasterising
3456×1926 — 6.7 megapixels — up to 99 times a second, which is what "really
really laggy" was.

*Do not silently downgrade either.* There is precedent: natives were once
excluded from the jar, `Ultralight.load()` threw, the code fell back to
`NullWebView`, and it was invisible because Loom's log4j config kills all logging.

## 9a. A rebuilt view is a reloaded page, and the session must be re-sent — **[measured]**

*Symptom when violated:* worse than the latch it replaced. `VOID_UI_GPU_FAIL=late`
with the menu open: the driver dies, the view is rebuilt on the CPU surface, and
the entry URL is loaded again because a replacement view has no document. The menu
**vanished and never came back** — mean luma of the menu region 22.5 while up, 51.5
(bare world) from the failure frame onward, flat forever. Nothing re-emitted the
mod's state, so the new page had no loadout, no library and no `menu: true`, while
`bridgeReady` stayed latched and every frame threw
`TypeError: undefined is not an object (evaluating 'window.void.__hasFocus')`.
And because `VoidMenuScreen` was still the current screen, the player sat inside an
open screen that drew nothing: mouse ungrabbed, movement dead, no error. That is
§9's blank overlay reached through the fallback instead of through the latch.

The view reports the reload — `WebView.documentGeneration()`, bumped by every load
— and `UiHost` compares it against the document it loaded itself, immediately after
`view.update()`, which is the only call that can swap a view. On a change it drops
every per-document assumption (`bridgeReady`, the NOHUD/CLICKTEST latches, the last
pointer position) and calls `VoidBridge.pushWholeState()`.

Two rules make that hold up:

- **One definition of what a fresh page needs.** `pushWholeState()` has three
  callers — the first push at start-up, the launcher's `init`, and a reloaded
  document. Two paths that must agree about this is how one of them rots.
- **Read live, never replay.** It re-serialises `loadout` and `loadouts` from
  `LiveState` rather than resending remembered envelopes, because `setGameplay`,
  `setModSetting` and `setHud` change the loadout *without emitting anything* (§6.5
  deliberately does not push a change back to the page that made it). A snapshot
  would silently drop every toggle the player flipped this session. `tick`, `keys`
  and `server` are not sent at all: their sensors push whole state every tick, so
  the queue refills before a reloading page can receive anything. `menu` is the one
  value with no other home, so the bridge remembers it — through `emitMenu`, which
  is the only way that channel may be written.

## 9b. The supersample cap belongs to the view, and nowhere else — **[measured]**

*Symptom when violated:* the machine that just lost its GPU is handed the single
most expensive configuration available. `UiHost.accelerated()` read
`WebViews.acceleratedInUse()`, a static written only inside `WebViews.create()` and
never by the mid-session fallback, so `maxSupersample()` kept returning the GPU's
cap of 4 for a software rasteriser. Measured without the HiDPI flag, where the caps
differ: started at `3416×1920 at 2.341 dp/css`, fell back, and stayed at
`3416×1920` — 6.6 megapixels rasterised in software where the cap is 2, with no
`in-game UI resized` line ever appearing.

**This is the third cache of that fact to go stale**, and the cap is one of the
four historical examples this file opens with. The first was a `UiHost` field set
once behind a `maxSupersample == 0` guard; the second was a `UiHost` field holding
both the request and the result; the third was the `WebViews` static — each a
correct read of a value that had stopped being true.

So it is no longer a read at all. `WebView.maxSupersample()` is answered off the
same volatile field as `WebView.isAccelerated()`, in the same expression, and
`WebViews.acceleratedInUse` **has been deleted**. There is exactly one expression
in the mod that produces a supersample factor and its only input is the live view;
`applySize` creates the first view at 1× and asks it, on the same call, before the
first paint, so nothing anywhere guesses a cap before there is a renderer to ask.
The wrong answer is not stored anywhere, so it cannot be read.

*The trap next to it.* Correcting the factor is not enough on its own — the device
scale has to be re-applied with it. `scaleChanged` compared the **logical** scale,
which does not move across a supersample change, so the corrected view would have
been resized to `fb × 2` while still believing in `s × 4`: the page lays out in half
the CSS width, every glyph and box twice the size it should be. The condition now
compares the scale the *view* was told (`appliedViewScale`).

---

# CSS-level engine behaviour

These are not style preferences. They are things the engine does that a browser
does not, and each one silently produced a visual bug.

## 10. `transform: scale()` in a press state corrupts glyphs — **[measured]**

A label under `scale()` renders at roughly the **device scale** — measured ~2.3×
too large against a 2.35 dp/css view — overflowing its box for every frame of
the press, and snapping back the frame the button is released.

Isolated by pressing an already-selected chip: no colour change, no weight
change, nothing but `:active`. `translateY` over the identical press is
completely clean, so it is `scale()` specifically, not transforms generally.

`.v-tab:active, .v-chip:active, .v-modtile:active { transform: none; }` exists
for this. **Do not add a scale press-state anywhere.** Note the static checker
catches `scale3d` and `translateZ` but not 2D `scale()`, which is legitimate
elsewhere — this is a rule about press states only.

## 10a. …and `transform: scale()` corrupts text **permanently**, not only under a press — **[measured]**

§10 was written about `:active` states and reads as a rule about press transforms. It is not.
Any scaled text is corrupt here, for as long as it is on screen, and the HUD had been shipping
that since `placementStyle()` was written: `hud_item.scale` and every mod's own `scale` setting
went out as `translate(...) scale(s)`.

Measured on the keystrokes pad, at `1.3×`:

```
W  A  S  D     clean            <- one glyph per cap
LMB -> "LNB"   RMB -> "RNB"     <- three glyphs, drawn at the scaled size
                                   over advances computed at the unscaled one
```

At `2.8×` every cap was unreadable. Single glyphs survive because a run of one cannot collide
with itself, which is exactly why this was invisible for so long — the widgets that scale
visibly are mostly numbers.

**The fix is `zoom`, on a box of its own.** `zoom` is a *layout* scale, so the engine lays the
text out at its final size and the metrics are the right ones; the same pad at the same `1.3×`
renders `LMB` and `RMB` correctly, and still does at `2.4×`.

Two boxes, and the split is load-bearing. `.hud-item` keeps the placement — `position`, the edge
offsets, `translate(dx, dy)`, `transform-origin` — in an **unzoomed** coordinate system, and
`.hud-item__zoom` inside it carries `zoom: s`. Zooming the outer box instead would scale its own
`translate()` too, quietly redefining every `dx`/`dy` Java has stored; and because `zoom` is
layout, the outer box shrink-wraps to the widget's *drawn* size, which is what both the anchor
arithmetic and the HUD editor's selection frame measure.

*Symptom when violated:* nobody reports it. Garbled small text on a HUD chip reads as a small
HUD chip.

## 10b. jsdom does not serialise `zoom`, so the test for §10a cannot see it — **[measured]**

§10a makes `zoom` the only legal way to scale a widget, and this is the reason the gate on it
nearly did not work.

jsdom implements `zoom` on the CSSOM but not in the style **attribute**. Given
`<div style={{ zoom: 2, opacity: .5 }} />` it produces

```
outerHTML   <div style="opacity: 0.5;"></div>
style.zoom  "2"
cssText     "opacity: 0.5;"
```

So any test that compares rendered markup — `innerHTML`, `outerHTML`, a DOM snapshot — is
**blind to the one property the invariant above requires**. `test/preview.test.tsx` compares
the mod page's drawing before and after each setting is changed; with an `innerHTML` comparison
`scale` came back inert on every live preview, and the obvious reading of that result is "the
preview ignores `scale`", not "the test cannot see `scale`".

**Read `zoom` off `el.style`, never out of serialised markup.** `drawing()` in that file appends
every descendant's `style.zoom` to the snapshot for exactly this reason.

*Symptom when violated:* a green suite over a preview that does not scale, or an afternoon
spent fixing a scale that was never broken.

## 11. Tabular figures are inert — **[measured]**

`font-variant-numeric: tabular-nums` and `font-feature-settings: 'tnum' 1` have
**no effect**: `111` and `888` render at identical widths with and without them.
Outfit genuinely ships a `tnum` feature, so this is the engine, not the font.

**Never rely on tabular figures for layout.** Outfit's proportional digits vary
enormously — `1` is 338 units against `0` at 657 — and character count changes
anyway between `8%`, `85%` and `100%`, which no amount of digit alignment fixes.
Reserve width geometrically instead.

The declarations are kept where they appear, with comments recording that they
are inert, so nobody "cleans up" correct CSS that will start working if the
engine ever gains the feature.

## 12. `visibility` is inherited **and overridable** — **[measured]**

Hiding a subtree with `visibility: hidden` on its root is not enough: any
descendant may take it straight back. `.menu-layer--hidden` hid the warm-up
layer and `.inspector--open { visibility: visible }` un-hid the properties panel
inside it.

*Symptom when violated:* the warm-up ghost. The hidden layer painted exactly one
element — a panel whose own background is transparent — leaving its text and
controls welded over the game world for the life of the process, with no panel,
no scrim and no chrome. It was not a stale texture: the host was faithfully
showing the only thing the page had ever drawn.

Force the whole subtree, and prefer `height: 0; overflow: hidden` where the goal
is to measure something without showing it.

## 12a. `display: contents` is supported, and it has no box — **[measured]**

Which makes it a perfect place to lose a drag. The HUD editor hung its pointer handlers on a
`<div style={{display:'contents'}}>` wrapped around each widget and measured the gesture from
`e.currentTarget.getBoundingClientRect()`:

```
hud-item[keystrokes] rect = 31.0,536.0  130.0x175.0     <- the widget
wrapper <div> computedDisplay="contents"
                      rect =  0.0,  0.0    0.0x0.0
                      clientRects=0  offsetParent=no
```

Every gesture therefore began from a widget the editor believed was at the layer origin with no
size. Grabbing a chip **teleported** it — dragged by (+502, -306) it landed at anchor `top`
`dx -227` instead of `bottom-left dx 533 dy 230` — the zero size meant `clampToViewport` never
clamped, and `anchorForPosition` picked the anchor from a corner rather than a centre. The user's
report was "the HUD editor doesn't work", and the editor's code was entirely reasonable.

**Handlers belong on the element that has the geometry.** `HudLayer` takes `slotProps` and puts
them on `.hud-item`; there is no wrapper.

*Why review and the suite both missed it:* jsdom has no layout, so **every** rect there is
`0,0 0x0` and the broken code and the fixed code behave identically. The regression gate is
therefore structural — no box in the editor may be `display: contents`, and a `pointerdown` on
`.hud-item` must select the widget — rather than a measurement (`test/screens.test.tsx`).

## 12b. Pointer events **do** fire, and capture works — **[measured]**

Recorded because it was suspected for a day and is wrong, and because the next person will
suspect it again. Driven through the real host path (`UiHost.mouseDown` → `View::FireMouseEvent`
→ WebCore), a single drag produced:

```
pointerdown  #1  id=1 type=mouse primary=true   target=button.modcell__select
  setPointerCapture ok -> hasPointerCapture=true
gotpointercapture #1
pointermove  #7 #13 #19 #25 …                   target=button.modcell__select  (500px away)
pointerup    #1
lostpointercapture #1
click        #1                                 target=div.mods-grid
```

25 moves for 24 posted, one per game frame. `setPointerCapture` succeeds, `gotpointercapture`
fires, and **capture retargets correctly**: every move kept `target` on the captured element
after the pointer had left it entirely. `pointerdown` arrives *before* `mousedown` — the pointer
events are primary and the mouse events are the compatibility pair, so `preventDefault()` on
`pointerdown` suppresses the mouse ones.

So both event families work. `CellMeter` uses mouse events, the HUD editor uses pointer events,
and neither is wrong.

## 13. `MouseEvent.buttons` is 0 for an entire drag — **[measured]**

The host fires moves as `kMouseButton_None`, so `buttons` reads 0 throughout.
Drag code written to browser habit — reading `buttons` to know the pointer is
held — is silently dead in game while passing every jsdom test.

Mouse moves are **coalesced, not queued**: they arrive once per game frame and
only the newest position means anything, so replaying a stale trail would be
worse than skipping it. Clicks re-send their own position first, so a click
lands where the cursor was.

## 13a. A mouse event carries **no modifier state at all** — **[derived, and measured]**

`WebView.fireMouseEvent(type, x, y, button)` has no modifier parameter, and neither does
`ULMouseEvent`. There is nowhere for one to travel, so `altKey`, `shiftKey`, `ctrlKey` and
`metaKey` are `false` on every mouse and pointer event the page will ever see — confirmed on
every event of a real drag.

*Symptom when violated:* the HUD editor's frame printed `⌥ drag to scale` and the code read
`e.altKey`, which is a compile-time constant here. Not a bug that fails: a gesture that is simply
never entered, and a hint that sends the player looking for a fault in their own hands. Scaling
is on the selection frame's corner grips now.

**A modifier chord on the keyboard is a separate and still-open question.** `keyDown` *does*
carry a mask (`VoidMenuScreen.modifiers()` → `ulCreateKeyEvent`), and a bare Shift or Alt press
arrives with its own flag set. Whether `Shift+Arrow` or `⌘K` arrive with the modifier attached
could not be settled by synthesised input — CGEvent flags do not reach LWJGL's `isKeyDown`, and a
posted modifier key-down was swallowed before `keyPressed` — so **⌘K's in-game behaviour is
unverified**, and nothing new should be built on a keyboard chord until a human has pressed one.
The HUD editor's nudge is therefore unmodified arrows only.

---

# Verification

## 14. Removing a registry setting eats saved loadouts — **[measured]**

Not a render invariant, but it lives here because it is the same species as the
rest of this file: it does not announce itself, nothing in the change looks
dangerous, and the tests stay green.

Every `*Settings` struct in `crates/void-loadout/src/mods.rs` is
`#[serde(deny_unknown_fields)]`, mirroring the schema's `additionalProperties:
false`. That is correct — an unknown key really is invalid — and it means
**deleting a setting makes every loadout already on disk that carries it stop
deserialising**. `Store::load` returns `Error::Json`, and the player's loadout
does not degrade to defaults. It disappears.

*Symptom when violated:* a player who had built loadouts opens the launcher
after an update and one of them is gone, with a JSON parse error naming a key
that is no longer in the schema — a key they never typed and cannot remove,
because the file they would edit is the one that will not load.

**Therefore removing a setting is a two-part change.** Take the key out of
`schema/mods.json`, the Rust struct, `ModRegistry.java` and the UI *and* add it
to `REMOVED_SETTINGS` in `crates/void-loadout/src/store.rs`. `read_loadout_json`
strips listed keys on the way in and never writes them back, so the next save is
the migration. Entries retire once no file can plausibly still carry them.

The tolerance is deliberately narrow: a key that was **never** ours still fails
loudly (`an_unknown_setting_that_was_never_ours_still_fails_loudly`). "Accept
anything" would trade this bug for a worse one — silently loading loadouts that
are wrong rather than refusing loadouts that are invalid.

Found while removing `toggle_sprint.show_status`, the first setting ever deleted
from the registry — which is why nothing had hit it before, and why the next
person to delete one would have.

## 14a. The bundle is inlined with `String.replace`, whose replacement is a pattern — **[measured]**

Same species as §14 and §15: silent, invisible in review, tests green, and it blamed the wrong
change.

`vite.config.ts` folds the emitted JS into `index.html` with `html.replace(marker, code)`. A
two-argument `String.prototype.replace` treats `$&`, `` $` ``, `$'`, `$$` and `$<name>` **in the
replacement string** as substitution patterns — and the replacement here is a minified bundle, in
which esbuild is free to name a variable `$`. The day it did, the shim's

```js
if (typeof $ === 'object' && $ !== null && 'returns' in $ && $.c === j)
```

was written into the page as

```js
if(typeof $=="object"&&$!==null&&"returns"in </body>&$.c===j)
```

— `$&` replaced by the text it had matched.

*Symptom:* the page never executed a line. No HUD, no menu, no ghost, nothing at all on screen,
and the entire diagnostic was one line on stderr:

```
[voidultralight/console/error] SyntaxError: Unexpected token '<' (file:///index.html:346)
```

`window.addEventListener('error')` never fires — there is no script to run it — so no in-page
instrumentation can see this. Worse, **nothing near that code had changed**: an unrelated edit
elsewhere had merely shifted esbuild's name allocation so that `$` landed on a variable followed
by `&&`. It will therefore always look like it was caused by whatever was committed that day.

Two guards, because one of them is a rule someone has to remember:

- `insert()` in `vite.config.ts` replaces through a **function**, which is never scanned for
  patterns, and asserts afterwards that the replacement landed verbatim.
- `check-ultralight.mjs` parses every inline `<script>` in the built `index.html` with
  `new Function` and fails the build naming the block. That check takes a second and would have
  replaced an afternoon; it is the reason a corrupt page can no longer reach a client.

*If you see `Unexpected token '<'` from the console and a blank overlay,* run
`pnpm --filter @void/ingame lint:ultralight` before suspecting anything you wrote.

## 15. A lookup that misses is silent, four times over — **[measured]**

The single most productive bug shape in this codebase, and the reason this
section is a list rather than an entry. Four separate failures in one day were
the same mechanism: **a table is consulted, the key is not there, the fallback
is nothing, and nothing anywhere says so.**

| the lookup | the miss | what the player saw |
|---|---|---|
| `void-shim.js` `EVENTS` | a channel the shim's closed list does not name | `on` returns a no-op subscription, `__emit` drops every envelope — the `session` channel was pushed by Java and consumed by the page for days without arriving |
| `SETTING_ENUMS[id.key]` | an enum setting with no options table | `PositionChips` over `[]` — a property row with its label and **nothing** where its control should be (`watermark.style`) |
| the swatch table | a colour the widget never read | the swatches wrote a value nothing drew |
| `loadout.hud[]` | a HUD mod that is `on` but unplaced | `HudEntry` needs both, so the widget is simply absent, and the editor's Reset cannot rescue it because Reset reads the same table |

Every one of them passed review, and **every one of them passed jsdom** — an
empty chip row, a dropped envelope and an absent widget all render without a
warning. So the rule is not "be careful with tables":

**A lookup whose miss produces silence must be gated by a test that enumerates
the domain, not by a test of the case somebody thought of.** `SETTING_ENUMS` is
now checked against every enum property `modProperties` can produce over the
whole registry (`test/registry.test.tsx`), which fails `pnpm check` and names the
missing key; the shim's channel list is checked against `bridge.json`'s enum; the
sprite's cell names are checked against every `IconName` the overlay renders. The
one that walks the domain is the one that catches the *next* mod.

Where the miss cannot be gated away, **degrade to something visible**. The enum
branch now renders the stored value instead of an empty box: still wrong, but
wrong in a way a person looking at the screen can see. An absence is the one
failure mode that survives being looked at.

## A base that is one of the options hides a missing option — **[derived]**

`armor_status.orientation` is `horizontal | vertical`, and for months it changed
a **width**. The base rule was `flex-direction: column` and the `--vertical`
modifier only set `width: auto`, so `horizontal` — the default — drew a vertical
list, and the setting appeared to do something, which is what kept anyone from
looking. The enum was never wrong; the second layout had simply never been
written, and the default silently stood in for it.

**When a property has N values, give it N rules and let the base carry only what
they share.** A base that doubles as one of the values cannot tell you that
another value is missing, because the missing one still renders.

## 16. A build artifact nothing rebuilds is a cache with no invalidation — **[measured]**

The same species as §14 and §15 — silent, invisible in review, tests green — and the most
expensive one yet, because it does not corrupt a result, it corrupts **every** result: for
three days, anything measured through the launcher was measured against code that had
already been replaced.

`Play` ran a jar from Sep 4. Four separate causes, none of which announces itself, and each
sufficient on its own:

| the artifact | why it was stale | what it looked like |
|---|---|---|
| `mods/void-client-*.jar` | `config.mod_jar` was unset, so `launch()`'s `if let Some(jar)` skipped the install *and* the stale-jar sweep inside it, with no `else` | the game launched, the mod connected, `hello` was answered — a healthy session running old code |
| `build/libs/*-<os>-<arch>.jar` | `platformJar*` were registered but nothing depended on them, so `./gradlew build` refreshed only the base jar | two jars with the same version in their names, hours apart |
| `native/build-macx64/natives/` | a **hand-made copy** of a directory `scripts/build.sh --arch x86_64` never writes (it writes `build-x86_64/`) | `UnsatisfiedLinkError: …Native.rendererProbeAccelerated(J)Z`, one line, `in-game UI disabled`, and a HUD that simply was not there |
| the JVM itself | `detect_java8` took the first Java 8 `read_dir` returned, which on this Mac is **arm64** — and LWJGL 2 has no arm64 natives | the recorded M1-gate symptom: the window never composites, 0x0, `BackgroundOnly`, no error anywhere |

**The unifying shape: a healthy-looking success is the failure.** A stale jar answers the
handshake, so the launcher's 45-second "nothing connected" watchdog never fires for it. A
mismatched JVM starts, prints a normal log and runs a game loop. A four-day-old dylib loads
and exports every symbol but one. Not one of these reaches a person as an error.

*The rules that came out of it:*

- **Wire the artifact to the thing that builds it.** `build` now depends on `platformJars`;
  `assemble` deliberately does not, because that is `runClient`'s path and it needs classes.
- **Discover inputs, do not enumerate them.** `nativeStages` walks every
  `native/build*/natives/<key>/` and takes the newest `voidultralight.dylib`. A path written
  into a build script by hand is a path that will not be there next time.
- **Say which binary is running, every time.** `launch::installed_mod_jars` lists what is in
  `mods/` and the launcher writes the file name and its age into the log drawer — on
  `stderr` when `mod_jar` is unset, because then nothing was installed this run.
- **A constraint that governs a download governs detection too.** `adoptium_arch` had known
  since it was written that macOS must be x64; `detect_java8` did not, and they never met.
  `JavaInstall::runs_lwjgl2` is now the one expression, and an unusable JVM is returned only
  when it is the only one, with a warning that names the consequence.

*How to know it has come back:* the launch log's first three lines. The jar's name and age,
`found Java 8 … arch="x86_64"`, and — in the game log — `loadout '<id>' applied from
launcher`, not `pushed to the page from the mod's own defaults`.

## Numbers that must hold

| invariant | baseline | how |
|---|---|---|
| idle open menu, GL driver | 0 paints/s, 0 presents/s | `ui:` profile line |
| menu region after a mid-session fallback | luma unchanged (22.5 → 21.7, not 51.5) | full-frame capture |
| supersample after a mid-session fallback | `resized to … 1708×960 at 1.171 dp/css` | `in-game UI resized` line |
| click → pixel | ~1.11 game frames, ~14.7 ms mean | per-frame probe |
| close | monotonic fade, no blank frame, no flash | back-buffer strip |
| ghost, 20 s after close | none, incl. on a genuinely empty page | full-frame capture |
| readout width across a value change | constant to the pixel column | back-buffer crop |
| glyph overflow on press | background-only in the top band | luminance detector |
| a scaled HUD widget's text | `LMB` reads `LMB`, at 1.3x and at 2.4x | window crop |
| a HUD-editor drag | the widget lands where it was dropped, frame on it | window crop + readout |
| a HUD-editor gesture | exactly one `hud` protocol frame, on the drop | fake launcher, `-Dvoid.port` |
| a HUD drag, across a restart | the widget is where it was dropped, in the next session | real Tauri `Play`, twice |
| a global written in game | `settings.json` follows, and the next session reads it | real Tauri `Play`, twice |
| the jar the game runs | named, with its age, in the launch log | log drawer, first lines |

## What does not exist yet

**There is no harness.** Every number above was produced by a throwaway probe,
used once and deleted; per-frame back-buffer readback has been rebuilt from
scratch at least three times by three different agents. Until these are
runnable on demand, this table is documentation rather than defence, and
everything in this file is enforced only by whoever remembers to read it.

**The input probe is the fourth thing to be rebuilt and thrown away.** §12b, §13a and the
`display: contents` measurement in §12a all came from one temporary module that logged every
mouse, pointer and key event as it arrived, with the console going to stderr through
`on_console_message`. It cost twenty minutes to write and it answered a question that had been
open for a day. Its two non-obvious requirements, for whoever writes the fifth one: the host's
console callback keeps **only the first argument**, so every line must already be one string; and
synthesised input needs the app activated (`NSRunningApplication.activateWithOptions_`) in a
retry loop, because a single call loses the race with the terminal that made it.

**A listen-only fake launcher is the way to see what the mod sends.** `scripts/verify-mods.mjs`
pushes loadouts *in*; a fifty-line variant that answers `hello` with a bare `init` and prints
every inbound frame is what proved one drag produces exactly one `hud` message carrying the whole
layout. Run the client with `-Dvoid.port=… -Dvoid.token=…` against it. Note what it also shows:
`LiveState.Sink` has `state` and `hud` and **nothing for globals**, so `setGlobal` is in-process
only and no global survives a restart unless a real launcher wrote it.

## Running a client at all

Three flags, each of which fails silently when omitted:

- ~~`VOID_UI_RENDERER=gpu`~~ — no longer needed: the GL driver is now the default,
  and `VOID_UI_RENDERER=cpu` is the opt-out. An agent lost an hour to lag that was
  simply the software rasteriser, and that is why the default moved. A machine
  whose driver cannot run the GLSL 1.20 programs falls back to the CPU surface by
  itself and says so on stderr; `VOID_UI_GPU_FAIL` (see `mod/native/README.md`)
  is how that fallback is forced on a machine where the driver works.
- `-Dvoid.ultralight.nativeDir=…` — without it `Ultralight.load()` throws and
  you get `NullWebView`, invisibly.
- `-x generateLog4jConfig` — without it Loom's `LoggerNamePatternSelector` kills
  all logging, so every diagnostic above disappears.
- `-Dorg.lwjgl.opengl.Display.enableHighDPI=true` — without it Minecraft sizes
  from points, renders at 1×, and macOS upscales. This was the "blurry menu" bug.
  HiDPI measured **+7 fps**, because the compositor's upscale costs more than the
  extra pixels do.

**Through the launcher there are two more**, and they are §16's, not Loom's:

- `~/.void-pvp/config.json` must name a **current** `mod_jar` for **this JVM's** arch —
  `macos-x64` on any Mac, Rosetta included. It is settable from Settings → Data & updates
  now; when it is unset the launcher installs nothing and the log says so on stderr.
- the Java 8 it picks must be **x64**. It now prefers one that can load LWJGL 2, and prints
  `found Java 8 … arch="x86_64"`. If that line says `aarch64`, the window will not appear.

Verified end to end on 2026-09-07 through the real Tauri `Play` path: a HUD drag in game
reached `~/.void-pvp/loadouts/sword-pvp.json` (`keystrokes left dx 40` → `right dx -224`),
the Snap toggle reached `~/.void-pvp/settings.json` (`hud_editor_grid` 4 → 0 → 8), and after
restarting **both** the launcher and the client the widget came back on the right and the
Snap toggle came back in the state it was left in.

Two `runClient` invocations collide on the run directory and the Gradle lock.
One client at a time.
