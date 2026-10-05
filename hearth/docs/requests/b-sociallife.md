# Requests from the social-life architect (stage B)

What the social-life buildings (`docs/buildings/sociallife.md`) need from files they do not own.

## For the plan module

1. **Sizes and beds (registered infos differ from `DefaultCatalogue`).** Please read them from the registered
   `ArchetypeInfo`, or bring the placeholders in line:
   - heights: tavern 9–16 (catalogue 8–12), hostel 12–16 (8–12: two storeys plus a pitched roof, and a raised house's
     posts), caravanserai 10–14 (7–11), thieves' den 7–10 (5–8), tilt yard 7–10 (3–7: the lord's canopy and the
     pavilions' pennants), infirmary 9–14 (7–11), almshouse 14–16 (7–11), orphanage 14–16 (8–12), cemetery 6–14 (1–8:
     the mortuary chapel's bell-cote), festival ground 6–16 (1–6: maypole and stage roof);
   - beds: hostel 6 (catalogue 8; it sleeps 6–14 by its plot), caravanserai 8 (10; 12–17 typically).
2. **Placement.** The caravanserai wants a level plot (it levels the whole courtyard and embanks water); the tilt yard a
   long flat field, its gate on the short street side; the thieves' den a back lane where its neighbours are poor.
3. **`banner` param.** The tilt yard and festival ground choose the day's colours by seed; a settlement-wide banner
   param (as the civic architect asked) would make the lists fly the town's colours.

## For the social / npc / economy / law modules

4. **Points promised** (tested in every culture by `SocialLifeTest.everyBuildingRegistersWhatItsFolkUse`):
   - tavern: COUNTER, MARKET_STALL, INNKEEPER and ENTERTAINER JOB_SITEs, STAGE, GAMING_TABLE, HEARTH, SEAT, DOOR;
   - hostel: BED, COUNTER, INNKEEPER JOB_SITE, TABLE, SEAT, HEARTH, DOOR (HITCHING_POST at the rail where it fits);
   - caravanserai: BED, COUNTER, MARKET_STALL, INNKEEPER and TRADER JOB_SITEs, HITCHING_POST (the arcade piers and every
     stable cell), STORAGE, HEARTH, TABLE, MEETING_POINT (the fountain), DOOR;
   - thieves' den: FENCE JOB_SITE, COUNTER, MARKET_STALL, STRONGBOX (also STORAGE), HEARTH, DOOR, GAMING_TABLE where it fits;
   - playhouse: STAGE, ENTERTAINER JOB_SITE, SEAT, STORAGE, MEETING_POINT, DOOR;
   - tilt yard: SEAT, THRONE (the lord's seat in the box), MEETING_POINT, STORAGE, DOOR;
   - infirmary: SICKBED, HEALER JOB_SITE, STORAGE, ALTAR, HEARTH, DOOR (cots are not beds: no housing);
   - almshouse: BED, STORAGE (alms box), TABLE, SEAT, HEARTH, ALTAR, DOOR;
   - orphanage: BED, LECTERN (the matron), TABLE, SEAT, HEARTH, STORAGE, DOOR;
   - cemetery: PRIEST JOB_SITE, SEAT, DOOR, ALTAR with a chapel;
   - festival ground: STAGE, MEETING_POINT, TABLE, SEAT.
5. **The den's strongbox** carries both STRONGBOX and STORAGE at the same cell; the economy's fence logic may key on
   `commerce/thieves_den` or the FENCE job site, both present.

## For the build module (shared)

6. **`Carving.hornsAtGables` floats** over steep (1.5–2) gable roofs in the log cultures (the horns sit a block or two
   off the ridge); `Shell` no longer calls it. The tavern still does.
7. **`Lighting.sconce` puts its bracket a block above the lantern**: on a gallery under a floor (an arcade) the bracket
   replaces the floor board above and the floor becomes unwalkable; I hang lanterns from the boards instead. A note in
   §13 of the guide would save the next architect the hunt.

## Integration (hearth-integrate-4)

- §1 done: `DefaultCatalogue` = registered infos.
- §2 partly: `Program` already sites the caravanserai at a gateway, the tilt yard at the edge in the castle district and the den hidden in the poor quarter; level ground is not enforced.
- §3 done: the festival ground carries the town's `banner` too, and the tilt yard and the festival ground fly its colours when it has one.
- §4, §5 nothing to wire.
- §6 done: `Carving.hornsAtGables` puts no horns on a roof falling two or more a row off its ridge.
- §7 done: `Lighting.sconce` hangs the lantern from a full block already drawn above it instead of replacing it with a bracket (noted in BUILDING_GUIDE §13).
