# Magic: Alembic (brewing and spells)

**Alembic** (`alembic`, `dev.voidmc.alembic`) owns reagents, brewing stations, the brewing craft, brews (draughts,
tinctures, elixirs, oils, bombs, salves, inks), the absorbed vanilla potions and brewing recipes, and **spells**.
Spells are cast through **Arsenal's foci** (staffs, wands, tomes). Arsenal and Alembic are one family: Alembic makes
the oils, quench brines and inks Arsenal uses; Arsenal makes the foci Alembic casts through; the inscribing table
inscribes runes into weapons and spells into foci. Mettle owns the numbers that grow with the player: the **Alchemy**
skill (brewing) and the **Arcana** skill (spells).

The user's decision (2026-10-21): vanilla brewing is **replaced as a system** (no brewing-stand recipe chain), and
vanilla potions are **absorbed as content** (§7).

---

# Part A · Brewing

## 1. Pillars

1. **Brewing is a craft, like forging.** You follow a formula at a cauldron, keep the heat in its band, add
   reagents in order, count the turns. Doing it well makes a better brew. KCD II's alchemy is the model.
2. **Reagents come from the world.** Heather on the moor, edelweiss on the peaks, salt from the salt flats, venom
   from an asp, wight dust from a barrow. Where you go decides what you can brew.
3. **Brews matter in a fight.** Wards against the enemy's element, oils for its weakness, bombs for its weak spots,
   tinctures that turn a bad fight. Preparation is half of the hunter's game.
4. **Limited by your body, not a timer.** Toxicity (§6) caps how much you can drink.
5. **Nothing vanilla breaks.** Every potion item in the world still works; every brewing recipe from vanilla,
   datapacks and other mods becomes a formula.

## 2. Reagents

Each reagent has one to three **virtues** (what it does in a brew) and a **grade** (Common, Fine, Pristine) set when
it is harvested: by ripeness, by the tool (shears and a knife give better grades than a fist) and by Mettle's
Survival and Alchemy numbers.

| Reagent | Source | Virtues |
|---|---|---|
| Nether wart | Nether, vanilla | Base (the wort) |
| Heather | Expanse heather moor | Calm, Vigour |
| Edelweiss | Expanse peaks | Ward (frost), Vigour |
| Lavender, fireweed, reindeer lichen | Expanse (Mettle's herb list) | Calm · Ember · Ward (frost) |
| Poppy | vanilla | Sleep, Forgetting (Mettle's draughts) |
| Lily of the valley | vanilla | Venom |
| Cornflower, blue orchid, allium | vanilla | Clarity · Tide · Arcana |
| Dandelion, oxeye daisy, azure bluet | vanilla | Swift · Mending · Clarity |
| Wither rose | vanilla | Blight |
| Sweet berries, glow berries | vanilla | Mending · Shade |
| Red and brown mushrooms | vanilla | Venom · Earth |
| Cattail, Spanish moss | Expanse bayou | Tide · Shade |
| Glowcap | Expanse underground (Glowcap Wilds) | Shade, Arcana |
| Amber resin | Expanse Amber Hollows | Ward, Mending |
| Rime crystal | Bestiary (rime wraiths), Rime Hollows | Rime |
| Ember core, ember ash | Bestiary (cinder hounds), Ember Deeps | Ember |
| Brimstone, sulfur, potent sulfur | vanilla 26.x sulfur, Expanse sulfur seeps and brimstone vaults | Ember, Blast |
| Salt crust | Expanse salt flats | Preserve, Ward |
| Spider eye, fermented spider eye | vanilla | Venom · Reverse |
| Venom gland | Bestiary (asps, spiders, scorpions) | Venom (strong) |
| Grave dust, wight dust | Bestiary (undead, barrow wights) | Blight · Rime, Blight |
| Bear tallow | Bestiary (bears) | Oil |
| Spore sac | Bestiary (sporelings) | Venom, Shade |
| Rabbit's foot, phantom membrane | vanilla | Swift · Feather |
| Ghast tear, glistering melon | vanilla | Mending (strong) · Mending |
| Blaze powder, breeze rod | vanilla | Ember · Storm |
| Magma cream, slime ball | vanilla | Ward (fire) · Ooze |
| Golden carrot, pufferfish, turtle scute | vanilla | Shade (sight) · Tide · Ward |
| Honey, honeycomb | vanilla | Mending, Salve base |
| Glowstone, redstone | vanilla | Potency · Duration |
| Gunpowder, dragon's breath | vanilla | Burst (splash) · Linger |
| Amethyst shard, prismite | vanilla · Expanse underground | Arcana · Arcana (strong) |
| Quintessence | bottled experience at the still (§3) | Essence (elixirs, glyph ink) |

## 3. Stations

| Station | Block | Does | Notes |
|---|---|---|---|
| **Mortar and pestle** | new, small | grinds reagents (most need it); a ground reagent dissolves in one turn | grinding is a few clicks, no game |
| **Cauldron** | vanilla `minecraft:cauldron` | **decoctions**: the brewing craft (§4), over a heat source below (campfire, fire, magma block, lava) | vanilla's water, lava and powder-snow uses stay; a Hub screen opens when it holds a base and sits over heat |
| **Still** (the alembic) | new, copper | distils a decoction into a **tincture**; makes **spirits** (a stronger base) from a mash of crops; bottles **Quintessence** from experience orbs | the mod's namesake |
| **Bottling stand** | vanilla `minecraft:brewing_stand`, repurposed | turns a brew into splash (gunpowder) or lingering (dragon's breath), fills vials, flasks and phials, re-bottles vanilla potions into flasks | keeps blaze-powder fuel (`minecraft:brewing_fuel`); it no longer brews (ROADMAP decision D11) |
| **Athanor** | new, a slow furnace beside the still (multiblock bonus like Arsenal's great forge) | **elixirs** and fine inks over in-game days, while you play | the late-game station |
| **Inscribing table** | Arsenal's (was the enchanting table) | inscribes spells into foci with glyph ink | §13 |

## 4. The brewing craft

A **formula** is a short list of steps, shown on the Hub cauldron page:

```
Frost-ward Draught (formula, Fair difficulty)
  1. Base: water, heat to a simmer             keep the needle in the green band
  2. Add ground edelweiss                      then 1 turn of the hourglass
  3. Add rime crystal (whole)                  steep 2 turns
  4. Bring to the boil                         1 turn, needle in the red band
  5. Bottle                                    3 doses
```

| Part | Rule |
|---|---|
| **Heat** | a gauge you keep in a band with fuel and the bellows (Arsenal's forge uses the same language); Mettle's Alchemy widens the band (`brew_window`) |
| **Turns** | an hourglass; a turn is 3 s; step timing is judged in quarter turns |
| **Order** | reagents in the wrong order spoil the step |
| **Score** | 100 points: each step is worth its share; reagent grades add (Fine +5, Pristine +10 each, capped at +20) |
| **Quality** | Crude under 40 · Fair 40–69 · Fine 70–89 · **Masterwork** 90+ (and Alchemy 12, Mettle's gate). The same four names as Arsenal's parts |
| **Failure** | under 15 points: a **murky brew** (a mild random effect and double toxicity); it still counts as practice |
| **Experiment** | put any base and up to three reagents in and brew: if their virtues match a formula you don't know you **learn it** (and get a Crude brew); if not, a murky brew and a hint ("it wanted something of Rime") |
| **Learning formulas** | from books (Hearth apothecaries and scholars, Expanse chests), from experiments, and from absorbed vanilla recipes (known from the start, §7.3) |

The cauldron page is static chrome in the Hub; the moving needle and hourglass are drawn natively (Hearth's
minigame rule: no fast-moving parts in Ultralight). Figma first.

**Quality does this:**

| Quality | Duration | Potency | Toxicity |
|---|---|---|---|
| Crude | ×0.75 | as written | ×1.3 |
| Fair | ×1.0 | as written | ×1.0 |
| Fine | ×1.25 | as written | ×0.9 |
| Masterwork | ×1.5 | one level higher where the effect has levels | ×0.75 |

Durations are written with vanilla's `minecraft:potion_duration_scale` component (verified in 26.3), so everything
that reads potions shows the right time.

## 5. Brews

Every brew is a **vanilla potion item** (`minecraft:potion`, `splash_potion`, `lingering_potion`) with vanilla
`potion_contents` plus Alembic's `alembic:brew` component (formula, quality, brewer, doses, toxicity). Witches,
cauldrons, dispensers, tipped arrows and other mods that read potions all keep working. Oils and bombs are Alembic
items.

| Category | Base | Doses | Duration (Fair) | Toxicity | Examples |
|---|---|---|---|---|---|
| **Draught** | water or wort | vial 1, flask 3 | 3 min (utility), 60 s (combat) | 10 (healing 15) | Healing, Mending, Swiftness, Cat's Eye, Gills, Hare's, Feather, Fortune, Shade, Ember-ward, Frost-ward, Storm-ward, Venom-ward (antidote), Blight-ward, Ox (Mettle's +20 kg) |
| **Tincture** | spirit (the still) | vial 1 | 45 s | 25 | Bull's (strength), Iron Skin (physical resist +0.15), Stamina (regen +40 %), Second Wind (+50 stamina at once), Mana (regen +60 %), Clarity (cleanses statuses, build-up −30 %), Bastion (turtle master) |
| **Elixir** | Quintessence (the athanor) | 1 | 20 min | 35 | Vigour (+4 max health), the Deep (night sight and regeneration below y 0), Insight (skill XP, Mettle's number), Warding (all resists +0.1) |
| **Oil** (coating) | tallow or plant oil | a phial coats a weapon for 20 hits or 8 arrows | until used | — | Venom oil, Kindling oil, Frost resin, Arc oil, Hallowed oil, Rend oil (Bleed) |
| **Bomb** | a clay pot + gunpowder or a splash bottle | 1 | — | — | Fire pot, Frost pot, Smoke pot (breaks line of sight: a rogue's exit), Holy water (radiant), Oil flask (Oiled), Mire pot (slowness), Gust pot, Venom pot |
| **Salve** | honey or wax | 1 | 6 s | 5 | Bandage salve (empties the Bleed meter, heals over 6 s), Burn salve |
| **Ink** | lapis + iron gall + a virtue | — | — | — | Iron-gall ink (weapon runes), Ember, Rime, Storm, Lumen (gold), Hex (bone ash) inks; **Glyph ink** (lapis + Quintessence) for spells |

Oils go on weapons through Fray's `fray:coating` component, so they work on vanilla swords too; Arsenal's coating
attachment adds charges (CONTRACTS.md §12). Bombs are thrown like any throwable (COMBAT.md §4.2). Mettle's belt
radial is how brews and bombs are used mid-fight.

**Stacking.** The same effect never stacks: a stronger or longer one replaces it. Different brews do stack, limited
by toxicity.

## 6. Toxicity

| | Base (Mettle's Alchemy raises the cap) |
|---|---|
| Pool | 0–100 (`fray:max_toxicity`; up to ~160 with Alchemy) |
| Added | per brew (§5 table), times quality |
| Falls | 1 a second in combat, 3 a second out of it |
| 70+ | **Queasy**: a slight sway (off with the screen-shake option), no other harm |
| 100+ | **Poisoned**: Poison I for 6 s and you can't drink for 10 s |
| PvP | toxicity ×1.5 and no instant healing while fighting players (COMBAT.md §14) |

## 7. Absorbing vanilla brewing

### 7.1 What 26.3 has (verified)

Potions are a built-in registry (`Potions`: 47 entries incl. the long and strong variants). Brewing is
**data-driven**: 280 recipes of type `minecraft:brewing` (`data/minecraft/recipe/brewing/*.json`: input potion,
reagent, output), run by `BrewingStandBlockEntity` with `minecraft:brewing_fuel`. Potion items carry
`minecraft:potion_contents` (a potion and/or custom effects) and optionally `potion_duration_scale`.

### 7.2 Every vanilla potion becomes a brew

A vanilla potion stack (from loot, witches, trades, other mods, old saves) keeps its components and **works as a
Fair brew of its kind**: drinking applies its vanilla effects, adds the category's toxicity, and the tooltip names it
as a brew. Nothing is rewritten; the bottling stand can re-bottle it into a flask.

| Vanilla potion | Brew | Category | Notes |
|---|---|---|---|
| Water, Mundane, Thick, Awkward | bases: water, sludge, sludge, **wort** | base | the wort is the Nether base |
| Night Vision | **Cat's Eye** | draught | |
| Invisibility | **Shade Draught** | draught | creatures' sight ×0.1 (Fray perception) |
| Leaping | **Hare's Draught** | draught | |
| Fire Resistance | **Ember-ward** | draught | fire resist; Fray adds fire build-up −50 % |
| Swiftness | **Swiftness** | draught | |
| Slowness | **Mire** | bomb (splash) | the drinkable one still drinks |
| Turtle Master | **Bastion** | tincture | |
| Water Breathing | **Gills** | draught | |
| Healing | **Healing** | draught | instant, as vanilla (harms undead, as vanilla) |
| Harming | **Bane** | bomb | heals undead, as vanilla |
| Poison | **Venom** | bomb and oil | |
| Regeneration | **Mending** | draught | |
| Strength | **Bull's Tincture** | tincture | |
| Weakness | **Enfeebling** | bomb | |
| Luck | **Fortune** | draught | |
| Slow Falling | **Feather** | draught | |
| Wind Charged, Weaving, Oozing, Infested | **Gust**, **Cobweb**, **Ooze**, **Silverfish** pots | bombs | the omen potions as curses you throw |

Long and Strong variants keep their vanilla values; Alembic's own brews express the same with quality.

### 7.3 Every brewing recipe becomes a formula

On every data reload Alembic reads all `minecraft:brewing` recipes, vanilla's, datapacks' and other mods'. Each
(input → reagent → output) that has no hand-written formula becomes a **generated formula**: the input's base, the
reagent, standard steps (simmer, add, boil two turns, bottle). Splash and lingering recipes fold into the bottling
stand. Players know the vanilla formulas from the start (they were never secret). So a mod that adds a potion and a
brewing recipe for it is brewable in the cauldron with no work from anyone.

### 7.4 Other vanilla touch points

Witches throw Alembic bombs (Bestiary) and drop reagents. Tipped arrows keep working (crafted as vanilla; coating
arrows with oils is Arsenal's fletching table). Beacons, conduits and suspicious stew are untouched. Mettle's
**Herbalist** background (Herb Lore: brews last 50 % longer) moves onto Alembic brews; its kit trades the brewing stand
for a mortar and a cauldron. Mettle's Draughts of Forgetting and Unbinding gain Alembic formulas (their crafting
recipes stay as the fallback without Alembic).

## 8. Alchemy (Mettle's numbers)

Mettle owns the skill and its deck. Alembic reports XP (`CharacterService.award(player, mettle:alchemy, ...)`) for
brews made (by formula tier and quality), experiments, and reagents ground; Mettle's existing anti-farm stamping
(the brewer is recorded) carries over. Alembic reads balance keys: `brew_window` (heat band width), `brew_potency`,
`brew_duration`, `brew_yield` (extra doses), `brew_toxicity`, plus `fray:max_toxicity`. Deck themes for Mettle to
write: distilling, potency, poisons and oils, bombs, tolerance.

---

# Part B · Spells

## 9. Mana and casting

| | Base (Mettle's Arcana scales it) |
|---|---|
| Mana pool | 40 for everyone; Arcana up to ~200 at the endgame (ceiling 320); foci add |
| Regen | 2 a second in combat, ×3 after 5 s out of combat; Arcana raises it (about 5 mid-game, 9 at the endgame) |
| Casting | Guard (right mouse) casts the selected spell; Technique (C) casts the focus's second spell; hold C + scroll chooses (COMBAT.md §4.3) |
| Cast times | quick 6 ticks, standard 12, heavy 20+, channels while held; ×`fray:cast_speed` |
| Moving | ×0.6 while casting; dodging cancels a cast |
| Interrupts | a flinch, stagger, daze or Silence breaks the cast (heavy casts have hyper-armour) |
| Cooldowns | per spell, ×Mettle's `spell_cooldown` (Echo applies; ceiling 40 % less) |
| Aim | spells fly where you look; lock-on aims for you (PvE) |

Spells use Fray's pipeline: damage kinds, build-up, reactions, resistances; a spell is an action with a server
timeline like a swing.

## 10. Schools

| School | Kinds | Statuses | Shines against | Focus affinity (stones and cores) | Mettle keywords (suggested) |
|---|---|---|---|---|---|
| **Pyromancy** | fire | Burn | fungal, arthropods, beasts | blaze rod, ember core | Ember |
| **Cryomancy** | frost | Chill, Frozen | ember, aquatic | heart of the sea, rime crystal | Tide |
| **Stormcraft** | shock | Shock | aquatic, constructs | breeze rod, copper | Gale |
| **Hexcraft** | blight, poison, arcane (hexes) | Blight, Poison, Hex, Silence | humans, beasts, casters | echo shard, wither rose, bone | Hex |
| **Lumen** | radiant | Sanctify; heals and wards | undead, occult | gold, amber | Warden, Kin |
| **Kinesis** | arcane | Daze, poise | everything a little; control and movement | amethyst, prismite | Echo, Keen |

A focus whose stone or core matches the school gives that school +20 % spell power.

## 11. Spells

Base numbers at spell power 1.0 (a fair mid-game focus). Cast in ticks, cooldown in seconds.

| School | Spell | Kind | Mana | Cast | CD | Does |
|---|---|---|---|---|---|---|
| Pyromancy | **Kindle** | cantrip | 2 | 8 | 0 | a bolt: 5 fire, Burn build-up 20 |
| | **Fire Lance** | bolt | 10 | 14 | 3 | 16 fire, pierces one, Burn 40 |
| | **Flame Wave** | area | 22 | 16 | 8 | a 5-block 90° cone: 12 fire, Burn 35 |
| | **Ember Ward** | ward | 15 | 10 | 20 | 12 s: fire resist +0.5, attackers take 3 fire |
| | **Ignite** | control | 8 | 6 | 6 | sets off every Oiled or Burning target within 8 blocks (Conflagration) |
| | **Wildfire** | ultimate | 60 | 24 | 60 | a 6-block circle burns 6 s: 8 fire a second |
| Cryomancy | **Frost Shard** | cantrip | 2 | 8 | 0 | 4 frost, Chill 25 |
| | **Ice Spear** | bolt | 12 | 16 | 4 | 18 frost, pierces, Chill 50 |
| | **Rime Field** | control | 20 | 14 | 12 | a 4-block field 5 s: Chill 15 a second, −30 % speed; Wet targets freeze |
| | **Glacial Ward** | ward | 18 | 10 | 25 | absorbs 25 % of max health for 8 s; when it breaks, Chills attackers |
| | **Frostwalk** | utility | 10 | 6 | 20 | 20 s: water freezes under your feet (vanilla Frost Walker's behaviour) |
| | **Winter's Grasp** | ultimate | 60 | 24 | 75 | 8 blocks: every Chill meter fills (Frozen; bosses +40 %) |
| Stormcraft | **Spark** | cantrip | 2 | 6 | 0 | 3 shock, jumps to one more at half, Shock 20 |
| | **Chain Lightning** | bolt | 14 | 14 | 5 | 12 shock, chains to 3 (−25 % each, ×1.5 on Wet) |
| | **Thunderclap** | area | 20 | 12 | 10 | 3 blocks around you: 10 shock, knockback 1.2, Daze |
| | **Gale Step** | utility | 12 | 0 | 6 | a 6-block dash with 5 i-frame ticks |
| | **Static Ward** | ward | 15 | 8 | 18 | the next 3 melee attackers are Shocked (10 s) |
| | **Call the Storm** | ultimate | 70 | 28 | 90 | 8 s: a strike a second on a target within 16 blocks (Wet first): 18 shock; stronger in rain |
| Hexcraft | **Rot** | cantrip | 2 | 8 | 0 | 3 blight, Blight 25 |
| | **Hex** | curse | 10 | 10 | 6 | Hex for 8 s (+10 % damage taken, −25 % healing) |
| | **Venom Cloud** | area | 18 | 14 | 12 | a 3-block cloud 6 s: Poison 20 a second |
| | **Drain** | channel | 6/s | — | 0 | 8 blight a second to one target; heals you 2 a second (the leech ceiling) |
| | **Silence** | control | 12 | 8 | 15 | Silenced 3 s; on a boss, breaks a purple cast |
| | **Doom** | ultimate | 50 | 20 | 60 | marks a target 10 s; then 30 % of the damage it took is dealt again as blight |
| Lumen | **Mote** | cantrip | 2 | 8 | 0 | 4 radiant (undead ×1.5) |
| | **Smite** | bolt | 12 | 12 | 4 | 14 radiant from above (needs only sight of the target), Sanctifies undead |
| | **Mending Light** | heal | 20 | 16 | 10 | you or an ally: 25 % of max health over 4 s |
| | **Aegis** | ward | 25 | 12 | 20 | a ward on an ally or you: 30 % of max health for 10 s |
| | **Consecrate** | area | 25 | 18 | 30 | a 5-block circle 8 s: allies regenerate 1 a second, undead in it take 4 radiant a second |
| | **Dawnbreak** | ultimate | 70 | 24 | 90 | 10 blocks: 30 radiant to undead and occult (8 to others), cleanses allies, Staggers undead |
| Kinesis | **Force Bolt** | cantrip | 2 | 6 | 0 | 4 arcane, high poise damage |
| | **Arcane Missiles** | channel | 5/s | — | 0 | 3 homing missiles a second, 3 arcane each |
| | **Repulse** | control | 15 | 8 | 8 | a 4-block cone thrown back 5 blocks, Daze |
| | **Blink** | utility | 15 | 0 | 8 | teleport 8 blocks where you look (needs a clear line), 3 i-frame ticks |
| | **Arcane Shield** | ward | 20 | 6 | 15 | 6 s: a shield in front of you stops projectiles |
| | **Gravity Well** | ultimate | 60 | 20 | 75 | a 6-block well pulls creatures in for 4 s, then slams: 20 arcane, Staggers commons |

**Categories**: cantrips (cheap, spammable: the mage's light attack), bolts, areas, controls, wards (absorb shields),
utilities, channels, ultimates (one per school, long tells of their own: 20+ ticks). A mage's sustained damage with
cantrips is about 0.85× a matched melee build's; bursts and areas are where they win.

**Spells and the world**: fire spells and Call the Storm don't burn blocks unless the server turns on
`spell_griefing`; Blink and Gale Step can't pass walls; spells used on Hearth folk are assault like any blow.

## 12. Foci (Arsenal items)

| Focus | Hands | Spell sockets | Power | Cast speed | Mana | Parts (Arsenal) |
|---|---|---|---|---|---|---|
| **Staff** | two | 2–3 (Masterwork +1) | ×1.25 | ×1.0 | +40 pool, +1 regen | head (a focus stone), shaft (wood, bone), ferrule (metal) |
| **Wand** | off hand | 1–2 | ×0.85 | ×1.3 | +10 pool | core (blaze, breeze or end rod; bone), handle |
| **Tome** | off hand | 2–4 pages | ×1.0 | ×1.0 | +30 pool, +2 regen | cover (leather, hide), pages (paper and ink), clasp (metal) |

Focus quality (Crude to Masterwork) scales power ±15–20 % like a blade's. Runes for foci (ENCHANTING.md §5.4:
Amplify, Conduit, Quickening, school runes) go in rune sockets beside the spell sockets.

## 13. Learning and attuning spells

| | Rule |
|---|---|
| **Learning** | a spell is **known** per player: from **grimoires** (Expanse landmark hidden chests, 20 %, a school by region: frost in the Frostwatch citadel, fire in the Ember Deeps' camps), from **Hearth teachers** (scholars: Kinesis; priests: Lumen; hermits: Hex; monks: Frost and Storm), from **scrolls** (cast once, or study to learn), and ultimates only from **bosses' lore pages** |
| **Attuning** | at the inscribing table, a known spell goes into a free spell socket of a focus for one **glyph ink**; swapping costs another ink; no experience |
| **Gates** | none to learn; casting power grows with Arcana; whether ultimates need Arcana 12 to inscribe is Mettle's call |

## 14. Reactions (Fray mechanics, `fray:reaction` data)

Any source triggers them: a spell, a rune, an oil, a bomb, rain.

| Reaction | When | Effect |
|---|---|---|
| **Electrocute** | a Shock proc on a Wet target | stun doubled (1.2 s), jumps to every Wet target within 4 blocks |
| **Flash freeze** | frost on a Wet target with a Chill stack | Frozen at once |
| **Conflagration** | a Burn proc on an Oiled target | burst 6, Burn twice as long, spreads to Oiled targets within 2 |
| **Thermal shock** | frost on a Burning target, or fire on a Frozen one | ends both: an 8 blunt burst and Exposed 4 s |
| **Shatter** | a blunt hit on a Frozen target | ×1.5, ends Frozen, double poise damage |
| **Toxic fumes** | fire touching a poison cloud | a 3-block burst: 6 fire and Poison 30 to all inside |
| **Purge** | radiant on a Blighted or undead target | radiant ×2, Blight removed |
| **Unravel** | arcane on a Hexed target | strips wards (a Warded elite's, an Aegis) and one buff |
| **Arc** | shock on a target in iron or better armour | jumps to one more target |
| **Steam** | fire on a Wet target | Wet ends; a 2-block steam cloud blinds for 1 s and breaks line of sight |

## 15. Arcana (Mettle's numbers)

Alembic reports Arcana XP: 1 per point of spell damage (as melee skills do), 0.5 per point healed or warded on others,
a little per utility cast (tapering like any repeated source), and a lump for learning a spell. Alembic reads
`spell_cost`, `spell_cooldown` and the attributes `fray:max_mana`, `mana_regen`, `spell_power`, `cast_speed`. The
deck (paths per school or by role: destruction, control, warding) and any new keyword ("Arcane"?) are Mettle's to
write. The **Scholar** background (Wits, Scholarship) is the natural mage start; Mettle may add a **Hedge-mage**
background.

## 16. Mage builds

| Build | Skills | Kit | How it plays |
|---|---|---|---|
| **Pyromancer** | Arcana, Alchemy | staff (blaze head), oil flasks | soak a group in oil, Ignite; glass cannon, kites with Gale Step from a Storm wand |
| **Frost warden** | Arcana, Defense | mace + tome (heavy armour) | Rime Field and Ice Spear lock a fight down; Glacial Ward takes a heavy the tome can't block |
| **Stormcaller** | Arcana, Athletics | wand + sabre, Arc oil | rain or water pots, then Chain Lightning; Gale Step through the pack |
| **Hexer** | Arcana, Stealth | tome + dagger, Venom oil | Hex and Doom from the dark, Drain to stay up, a backstab to finish |
| **Lumen knight** | Arcana, Defense | mace + tome, holy water | wards and heals for the party, Smite and Consecrate against the dead |
| **Spellblade** | Blades, Arcana | longsword + wand | light combos weaving cantrips; runes on the blade start the reactions the wand finishes |
| **Arcanist** | Arcana (Kinesis), Wits | staff (amethyst) | Repulse, Blink, Gravity Well: control and position; the hardest to pin down |
