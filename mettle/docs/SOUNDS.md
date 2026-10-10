# VOID Mettle — the card draft's sounds

The perk draft (DRAFT.md, CARDS.md) is a card game: dark obsidian cards whose pixel edges burn in the skill's colour.
These sounds belong to that table: embers that catch when you point at a card, a flare and a thunk when you keep one,
the other two curling into ash, and a Mythic card that takes the lights down before it turns.

**Status (2026-10-10):** built and playing in game, with previews to listen to. Every choice below is a proposal: sound
was left open with the user, and §8 lists the taste calls to confirm.

## 1. Principles

- **Minecraft first.** The bones are Minecraft's own sounds (book pages, the chiseled bookshelf, fire and flint, lava
  pops, amethyst, note blocks), so the draft sounds like it belongs in the game. Mettle's own synthesised layers fill in
  what vanilla has no sound for (a card's swish, an ember crackle short enough for a hover, the Mythic's crack of light)
  and are finished warm and slightly lo-fi: soft saturation, the top above 11 kHz rolled off, no reverb tails.
- **The same key as the moments.** The level-up chimes (`MomentSounds`, UI.md §6b) are note-block arpeggios on F♯.
  Every pitched layer here is in F♯ too (note-block semitones, F♯ major chords in the synth pads), so a level-up chime
  and a card pick never clash.
- **Short, and fine the hundredth time.** Nothing but the Mythic reveal is longer than 1.5 s. Every layer has several
  variants or a pitch jitter (±3–10 %), so a hover never repeats exactly; hovers are throttled (one per 60 ms) so
  sweeping across the hand sounds like a crackle, not a machine gun.
- **Loudness is anchored.** The level-up chime is the reference (about −32 dB on the meter in §6). A hover sits near
  the vanilla button click (−41 dB), a deal near the level chime, a pick a little above it, and only the Mythic reveal
  is allowed past the feat fanfare. All sounds are UI sounds: the UI volume slider governs them.
- **No vanilla audio ships.** Vanilla layers are named in `assets/mettle/sounds.json` (`"minecraft:fire/ignite"`) and
  loaded from the player's own game assets. Only Mettle's synthesised files are in the jar (38 small Ogg files, 0.5 MB).

## 2. The palette

Times are from the start of the cue. *Vanilla* layers are Minecraft files by name; *synth* layers are Mettle's own
(`tools/sounds/synth.py`). Pitch is a playback-speed factor as in Minecraft; volumes are the cue's (`card_cues.json`).

| Event | When | Length | Layers | Mix notes |
|---|---|---|---|---|
| **Draft dealt** (`deal`) | the overlay fans a hand out | 0.55 s | three card **slides** (synth swish, 75 ms apart, each a little higher) each with a **page flip** (vanilla book, ×1.45–1.65); a soft **tap** as they land (item frame); two small **crackles** as the edges catch (synth) | the page flips carry it; the synth swish is under them. Pitches rise card to card so the fan reads left to right |
| **Rarity stinger** (`stinger.*`) | 380 ms into a deal, if the hand holds Epic, Cursed, Wildcard, Earned or Mastery: **one**, the hand's highest (Cursed › Earned › Mastery › Wildcard › Epic) | 0.4–1.2 s | Epic: enchanting-table sparkle + two chimes (a third and a seventh). Mastery: sparkle + chime fifth + bell. Cursed: ominous-bottle hiss, a **falling tritone** on note-block bass, the cursed drone. Wildcard: a card flick + a two-note pling (fifth, octave). Earned: the gold glint + bell and chime | a Mythic in the hand plays no stinger: the reveal is its stinger |
| **Card hover** (`hover.<rarity>`) | the pointer reaches a card | 0.15–0.9 s | an ember **crackle** (synth, 6 variants) that grows with rarity: Common one crackle; Rare two; Epic two and an ember **sizzle**; Mastery adds a faint high chime; Wildcard a card **flick**; Earned a small **gold glint**; Mythic a hotter sizzle, the amethyst shimmer and the idle loop | −41 dB (Common) up to −34 dB (Mythic); first pop within 5 ms so it answers the mouse |
| **Cursed hover** (`hover.cursed`) | pointer on a Cursed card | 0.9 s | a darker crackle (×0.75) over **the wrongness** (synth): a 55 Hz tone beating against a slightly sharp copy and a tritone, sinking 5 % as it plays, a breath, and a crackle **played backwards** so it swells into its pops | low enough to feel, harmonics up to 1.3 kHz so laptop speakers still carry it |
| **Mythic idle shimmer** (`loops.mythic_idle`) | held while a Mythic card is hovered | loop 4 s | a seamless loop (synth): an F♯ chord shimmering on slow tremolos, high pentatonic sparkles and faint embers that wrap round the loop's end | fades in 0.3 s, out 0.45 s; quiet (−37 dB) so it can sit under the cursor for a minute |
| **Card pick** (`pick` + `pick.<rarity>`) | `MettleActions.pickCard` | 0.5 s | the **flare** (synth ignition fwoomp with a low thump and sparks) + **flint and steel** (vanilla, ×1.3); 70 ms later **the thunk**: the chiseled bookshelf taking a book (×0.85), a book put down (×0.8) and a touch of lodestone for weight | then the rarity's sweetener on note blocks, in the moments' style: Rare a fifth; Epic third + sixth; Mastery adds a bell; Cursed a low tritone and the ominous hiss; Wildcard a pling fifth and octave; Earned bell, chime and gold; Mythic a full strummed chord + amethyst |
| **The unkept cards burn away** (`burn`) | 260 ms after a pick | 1.1 s | two **burns** (synth, 90 ms apart): a card catches, curls (a dense crinkle), hisses and thins to ashy ticks; a whisper of vanilla **fizz** (×1.4); two **lava pops** (×1.6, ×1.8) as embers jump; **ash** settling (synth) at 650 ms | −33 dB: under the pick, it is the pick's tail |
| **Redeal / reroll** (`reroll`) | `MettleActions.reroll` | 0.65 s | a **whoosh** (synth) as the hand is swept off, a page flick, the deck **riffled** (synth: a run of card-edge clicks that speeds up, then squares off), a tap | the overlay's `deal` for the new hand follows it |
| **No reroll left** (`reroll_denied`) | reroll with none held, or no hand | 0.3 s | the bundle's dull "full" sound + a dying crackle | −37 dB |
| **Queued-draft nudge** (`nudge`) | a new draft starts waiting while no screen is open, 0.9 s after the level-up chime; at most every 20 s | 0.3 s | a soft high page flick and one crackle | −42 dB: the chime already said "level up"; this only says "and a card" |
| **Deck slot filled** (`slot`) | the overlay places a card in a deck slot | 0.6 s | the chiseled bookshelf taking an *enchanted* book, a book put down, one chime (fifth) | −33 dB |
| **Set bonus / deck complete** (`set_complete`) | a set or deck completes | 1.3 s | **a chord**: a warm F♯ pad swell (synth) under a strummed note-block chime chord (root, third, fifth, octave, 45 ms apart), a low harp root, a bell on top, a soft flare and the amethyst shimmer | −29 dB, the warmest thing in the palette |
| **Mythic reveal** (`mythic_reveal`) | the overlay starts the reveal | 5.3 s | see §3 | −24 dB peak moment |

## 3. The Mythic reveal

One cue, with **markers** the overlay times its animation to (`draft.sound.markers.mythic_reveal` in the state; ms):

| ms | Visual | Sound |
|---|---|---|
| 0 (`dark`) | the room dims; the card lies face down | the respawn anchor powering down (×0.8): the lights going out. The **tremble rumble** starts (synth, 2.5 s): a low shaking rumble that grows, with the card rattling on the table in time with the shaking |
| 250, 1050 | the card trembles | two soft **heartbeats** (vanilla Warden heartbeat, ×0.9, quiet) |
| 900, 1300, 1650 (`cracks`) | cracks of light open | three **cracks** (synth: a sharp tick, a falling zip, glassy rings, debris), each with amethyst breaking high (×1.7–1.9) or an eye of ender set (×1.4) |
| 1500 (`swell`) | light pours through | the **swell** (synth: a reversed-cymbal rise and an F♯ chord climbing into the turn) over the beacon powering up (×1.25) |
| 2500 (`flip`) | the card flips into a burst | the **burst** (synth: a round low boom, a whoomp, a bright chord bloom, a spray of sparks) with the respawn anchor's set-spawn, amethyst breaking, flint and steel low (the edges catching fire), and a strummed note-block chord with a bell |
| 2650–5300 (`settled`) | the aura settles | the **tail** (synth: high F♯-pentatonic sparkles falling away over a fading shimmer, a few embers) and the amethyst shimmer |

The rumble ends exactly on the flip, so the burst lands in a clean space. Kept tasteful: the boom is round (low-passed,
soft-saturated) rather than loud, nothing passes −24 dB, and there is no riser longer than a second.

## 4. How it is made

| Piece | What | Where |
|---|---|---|
| Synth layers | 17 sounds, 38 files (variants), mono 48 kHz Ogg Vorbis, deterministic (seeded) | `tools/sounds/synth.py` (numpy only), `tools/sounds/dsp.py` → `src/main/resources/assets/mettle/sounds/{card,mythic}/` |
| Sound events | 37 events: 17 synth, 20 vanilla-backed (by file name) | `tools/sounds/events.py` → `assets/mettle/sounds.json`; registered by `character.draft.CardSounds` |
| Cues | every event above as layers `[at ms, event, pitch, volume, jitter]`, the loop, the markers, the timing rules | `assets/mettle/card_cues.json`, read by `character.draft.CardCues` |
| Player | schedules layers to the millisecond (flushed each client tick and each frame of an open screen), the Mythic loop, results from picks and rerolls, the nudge | `client.draft.CardAudio`, `client.draft.MythicShimmer` |
| Previews | the same cue file mixed offline like the engine mixes it: weighted variant, pitch as a speed change clamped 0.5–2, volume clamped to 1, vanilla files decoded from Loom's asset cache | `tools/sounds/preview.py` → `../sound-previews/*.wav` (not committed) |

Rebuild everything: `python tools/sounds/synth.py && python tools/sounds/events.py && python tools/sounds/preview.py`
(Python 3 with numpy and soundfile; soundfile's libsndfile writes Vorbis). **Tuning live:** start the client with
`-Dmettle.cues=<path to a copy of card_cues.json>`; Mettle reads that file instead and reads it again whenever it
changes, so a volume can be tuned with the game open.

Checks: `CardCuesTest` (events match sounds.json, every file ships, every layer names a known event at a pitch Minecraft
will not clamp, every rarity has a hover and a pick, stingers pick the rarest card, the reveal's markers match its
layers); `CardSoundJourney` in the client tour (every event resolves and every file, vanilla ones included, is in the
real game's resources; the overlay calls play every cue through; the Mythic shimmer starts on hover and lets go).

## 5. For the overlay (the VOID Hub)

The Hub plays nothing itself; it tells Mettle when its animations run. One reflective call:

```java
MettleActions.cardSound(String cue, String arg)   // or cardSound("cue#arg")
```

| Call | When |
|---|---|
| `("deal", "mettle:mining")` | a skill's hand starts to fan out (the first card's slide starts now). Mettle reads the hand and adds the rarity stinger itself |
| `("hover", "epic")` or `("hover", "mettle:mining#2")` | the pointer reaches a card: its rarity (`common rare epic mastery cursed wildcard earned mythic`) or the skill and card index. Hovering a Mythic starts its shimmer; hovering anything else lets it go |
| `("unhover", "")` | the pointer leaves the cards |
| `("mythic_reveal", "")` | the reveal starts; time the visuals to `draft.sound.markers.mythic_reveal` (`dark`, `cracks`, `swell`, `flip`, `settled`) |
| `("slot", "")`, `("set_complete", "")` | a card lands in a deck slot; a set or deck completes |
| `("stop", "")` | the draft closes or the reveal is skipped: every pending layer and the shimmer stop |

**Automatic:** `MettleActions.pickCard` plays the pick, its rarity's sweetener and the burn-away; `MettleActions.reroll`
plays the shuffle (or the dull "no"); a new waiting draft plays the nudge when no screen is open. Don't call these
from the Hub as well. The "learned" moment chime is hushed for 2.5 s after a pick from the overlay (the pick said it);
a pick made from chat (`/mettle draft`) still chimes.

State: `draft.sound` = `{ markers: { mythic_reveal: {...} }, hoverGapMs, stingerAtMs, burnAfterPickMs }`. Suggested
animation timing to match: card fan 75 ms apart; let the kept card flare for ~250 ms before the others start burning
(`burnAfterPickMs`); the burn takes ~1.1 s; a reroll's new hand can start fanning ~650 ms after the reroll.

## 6. Levels

Measured on the previews (short-term loudness, 400 ms window, rough K-weighting; game volume 100 %):

| | dB | | dB |
|---|---|---|---|
| vanilla button click (0.25) | −39 | Mettle level-up chime | −32 |
| Mettle feat fanfare | −30 | hover Common → Rare → Epic → Mythic | −41 → −39 → −37 → −34 |
| hover Cursed | −37 | deal (with or without a stinger) | −29 to −31 |
| pick (+ sweetener) | −27 to −28 | burn-away | −33 |
| reroll | −30 to −32 | nudge | −42 to −44 |
| stingers alone | −32 to −34 | set complete | −29 |
| Mythic reveal | −23 | Mythic idle loop | −37 |

Variants of a layer are level-matched (`synth.py` brings each to its family's median RMS), so the jitter changes the
detail, not the loudness.

## 7. Previews

`C:/Users/bitto/VOID/.claude/worktrees/mettle-draft/sound-previews/` (not committed), each mixed exactly as the game
plays it: `deal`, `deal_stinger_{epic,cursed,wildcard,earned,mastery}`, `hover_{common,rare,epic,mastery,cursed,wildcard,earned,mythic}`
(the Mythic one with 2.5 s of its shimmer), `hover_sweep_repeat` (nine hovers in a row, for repetition), `pick_<rarity>`
(pick, sweetener and burn-away), `burn_away`, `reroll` (with the new deal), `reroll_denied`, `queued_draft_nudge`,
`deck_slot`, `set_complete`, `mythic_reveal` (then a hover), `mythic_idle_loop` (8 s), and **`showcase.wav`**: a hand
with a Cursed card dealt, four hovers, the Epic kept and the others burning, a reroll, a hand with a Mythic, the reveal,
a hover on the Mythic, and the Mythic kept. They are at in-game level, so they play quiet, like Minecraft's own UI.

## 8. Taste calls to confirm

1. **The thunk:** wooden (bookshelf + book, now) or stonier (more lodestone, closer to obsidian)?
2. **Heartbeats in the reveal:** two quiet Warden heartbeats under the tremble. They add dread but some players will
   hear "Warden". Keep, or drop for the rumble alone?
3. **The burst:** respawn anchor + synth boom (now), or the Totem of Undying (iconic and bigger, but reads as "saved from
   death")?
4. **Common hover:** a single soft crackle (now), or silent so only rarer cards crackle?
5. **Cursed voice:** falling tritone on note-block bass and the ominous-bottle hiss (now): too Trial-Chambers? The
   alternative is the synth drone alone.
6. **One stinger per hand** (the rarest, now) or one per special card?
7. **Picks hush the "learned" chime** (now), or keep the chime after the thunk?
8. **An option:** card sounds follow only the UI volume slider (now); the nudge follows the `sounds` option. Should
   `mettle-client.properties` get a `cardSounds` switch?
