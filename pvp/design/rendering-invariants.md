# Rendering invariants — what the in-game overlay must keep true

Scope: the in-game overlay's render and present path — `UiHost`, the GL driver,
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

## 9. GPU failure must degrade, not latch — **[measured, being fixed]**

`g_gpu_failed` is process-wide, set on `gpu::initialize()` failure, never
retried, and every subsequent accelerated render early-returns. On a machine
where the GLSL 1.20 shaders will not link the user gets a **blank overlay with
no visible error**, on hardware where the CPU surface would have worked.

This is the sole reason GPU is not the default, and the cost of that default is
real: the CPU path was measured rasterising 3456×1926 — 6.7 megapixels — up to
99 times a second, which is what "really really laggy" was.

*Do not silently downgrade either.* There is precedent: natives were once
excluded from the jar, `Ultralight.load()` threw, the code fell back to
`NullWebView`, and it was invisible because Loom's log4j config kills all logging.

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

## 13. `MouseEvent.buttons` is 0 for an entire drag — **[measured]**

The host fires moves as `kMouseButton_None`, so `buttons` reads 0 throughout.
Drag code written to browser habit — reading `buttons` to know the pointer is
held — is silently dead in game while passing every jsdom test.

Mouse moves are **coalesced, not queued**: they arrive once per game frame and
only the newest position means anything, so replaying a stale trail would be
worse than skipping it. Clicks re-send their own position first, so a click
lands where the cursor was.

---

# Verification

## Numbers that must hold

| invariant | baseline | how |
|---|---|---|
| idle open menu, GL driver | 0 paints/s, 0 presents/s | `ui:` profile line |
| click → pixel | ~1.11 game frames, ~14.7 ms mean | per-frame probe |
| close | monotonic fade, no blank frame, no flash | back-buffer strip |
| ghost, 20 s after close | none, incl. on a genuinely empty page | full-frame capture |
| readout width across a value change | constant to the pixel column | back-buffer crop |
| glyph overflow on press | background-only in the top band | luminance detector |

## What does not exist yet

**There is no harness.** Every number above was produced by a throwaway probe,
used once and deleted; per-frame back-buffer readback has been rebuilt from
scratch at least three times by three different agents. Until these are
runnable on demand, this table is documentation rather than defence, and
everything in this file is enforced only by whoever remembers to read it.

## Running a client at all

Three flags, each of which fails silently when omitted:

- `VOID_UI_RENDERER=gpu` — the default is the CPU surface. An agent lost an hour
  to lag that was simply the software rasteriser.
- `-Dvoid.ultralight.nativeDir=…` — without it `Ultralight.load()` throws and
  you get `NullWebView`, invisibly.
- `-x generateLog4jConfig` — without it Loom's `LoggerNamePatternSelector` kills
  all logging, so every diagnostic above disappears.
- `-Dorg.lwjgl.opengl.Display.enableHighDPI=true` — without it Minecraft sizes
  from points, renders at 1×, and macOS upscales. This was the "blurry menu" bug.
  HiDPI measured **+7 fps**, because the compositor's upscale costs more than the
  extra pixels do.

Two `runClient` invocations collide on the run directory and the Gradle lock.
One client at a time.
