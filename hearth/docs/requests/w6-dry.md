# Requests from the dry-lands pass (wave 6, art review items 8, 14, 21)

## 1. civic/Adobe: raised corners on the inn and the other commerce roofs

**Seen**: `kit/civic/Adobe.roof` raises every corner of a desert or mesa public roof two courses in dressed stone (and,
with `crest`, a stepped crest over the front). The inn (`archetype/commerce/Inn.java`, via `Storeys.roof`) is drawn
through it, so a desert inn still reads as a little fort (art review item 8 lists inns with the homes).
**Asked**: a mode without raised corners (a plain coping, corners eased round as `homestead.Adobe.Parapet.SHOULDERED`)
for commerce, housing and work; keep raised corners and crests for civic prominence, fort, noble and law.

## 2. Shop-front awnings in four colours

**Seen**: the smithy's street awning (`archetype/work/Smithy.java`, `front`) and `civic/Adobe.awning` stripe or pick the
cloth palette cell by cell (red, blue, yellow, white), the same fairground look item 14 found on the roofs.
**Asked**: one colour per awning, or two in regular stripes; `Life.mutedCloth` (townhome kit) picks a worn colour.

## Done here, outside the lane (tiny edits)

- `kit/trade/Hall.java`: `crown()` and the terrace room's parapet use `Adobe.Parapet.SHOULDERED` instead of corner
  pinnacles; the terrace awning is `Life.awning` (one canvas colour).
- `kit/civic/Halls.java`: `roofTerrace`'s awning is `Life.awning`.
