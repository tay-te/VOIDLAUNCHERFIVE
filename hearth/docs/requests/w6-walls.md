# Requests from the walls lane (wave 6: art review items 3, 12, 18)

The walls lane fixed the shared kits: log ends only at the four corners, no beam ends or dark lintels on log walls, window
trim as thin boards (`Carving.dripCaps`, `Carving.trim`, `Carving.board`), horns one block and off cottages, and quoins
that tooth only in a tonal accent on buildings of some standing (`Walls.quoined(d, body, k)`, `Walls.tonal(wall, accent)`).
`Openings`, `Storeys.stoneStorey`, `landmark/Tower.shaft` and `noble/Tower.shell` got one-line edits to use them. What is
left lives in other lanes' files:

1. **landmark (GreatHall.dragons)**: the dragon-head barge boards run four blocks past the apex (neck of two, head,
   snout); the review calls the great hall's horns oversized. One block of neck, the head right over it.
2. **crowns**: the tundra guardhouse crown reads as antlers (review, civic). Same rule: one block past the ridge.
3. **defense / lawhouse / landscape quoins**: `Watchtower` (`corner && (y & 1) == 1`), `Gatehouse`, `lawhouse/Masonry`
   (toothed), `landscape/Towers.walls` and `industry/Furnace` still tooth or alternate in whatever accent the style has.
   Gate the toothing on `Walls.tonal(body.main(Form.FULL), quoin.main(Form.FULL))` and drop quoins where
   `Walls.quoined` says no, as `noble/Tower.shell` now does.
4. **palettes**: on dark log walls the northern and tundra `shutter` (pale oak 2 : dark oak 1) and lattice windows (the
   `shutter` trapdoors set in the wall, e.g. the tundra paper mill) are white squares: the last of the polka dots there.
   Shutters in the frame's or the logs' own timber read better; white only in the "birch trim" variant. Tundra pale oak
   logs also show white end grain at corners (fine as a corner column, but a darker log reads calmer).
5. **masonry window heads (Openings, whoever owns it next)**: on plaster and stone walls the lintel is a full block of
   `wall_accent` (`c = -1..width` on stone): brick heads on white render (temperate market hall s0) still dot the front.
   Same tonal rule would help: a contrasting accent heads only the opening's own width, or uses the wall's stair.
