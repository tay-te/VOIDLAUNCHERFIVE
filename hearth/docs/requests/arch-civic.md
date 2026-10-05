# Requests from the civic and commercial architect

What the civic and commercial buildings (`docs/buildings/civic.md`) need from files they do not own.

## For the plan module

1. **One coat of arms per settlement: pass `banner` to every plot.** The town hall, guardhouse, chapel towers and
   flagpoles hang the settlement's banner. Without a param each building seeds its own arms from the culture's cloth
   colours, so two buildings of the same town may fly different flags. A settlement-wide string, chosen once when the
   settlement is founded, in the form `"field;colour:pattern;colour:pattern…"` (e.g. `"blue;yellow:cross;white:border"`,
   colours and patterns by their vanilla names), passed in every civic and commercial plot's params, makes them agree.
   **Done** (integration 3): `banner` is chosen once from the town's heart (`PlanContext.banner()`) and passed to every civic, commercial, defensive, law, noble and landmark plot and the plaza.
2. **Heights.** The archetypes announce their own heights in `ArchetypeInfo` (chapel 12–30, town hall 14–32, market
   hall 12–24, library 12–20, bathhouse 8–16, school 9–15, inn 11–17, bank 9–16, guardhouse 8–18). `DefaultCatalogue`
   still carries lower placeholder maxima (chapel 18, town hall 18, market hall 14…); where the planner uses the
   catalogue rather than the registered info, towers and spires come out short or are left off. Please read heights
   from the registered `ArchetypeInfo`, or raise the catalogue's to match.
   **Done** (integration 3): the planner uses the registered infos, and the default catalogue now matches them.
3. **Inn beds.** The inn sleeps 3 to 6 by its plot (guest rooms along the street, two beds to a room where it is wide
   enough; 2 on the narrowest raised plots), 4 typically, and announces 4. `DefaultCatalogue` says 6. The guardhouse
   sleeps 2 (catalogue: 0).
   **Done** (integration 3): the catalogue has inn 4 beds and guardhouse 2.
4. **Commerce near the core.** The market hall's cupola and the inn's second storey want the core's full height; the
   commerce height rule (`min + (max - min) × t`) gives them that at the core and less beyond, and they adapt, but a
   market hall beyond the inner ring loses its cupola.
   **Done** (integration 3): the market hall and inn keep at least three quarters of their height range wherever they stand (`Build.keepsItsHeight`).
5. **The chapel on the rise.** The chapel looks its best with the land falling away from its front (it builds a
   raised base and steps); a site flag or a preference for the highest central plot would put it there.
   No change (integration 3): the chapel is already placed `RISE`.

## For the npc / economy modules

6. **Points promised** (tested for every culture in `build/civic/CivicArchetypesTest`): market hall COUNTER,
   MARKET_STALL, MERCHANT JOB_SITE, MEETING_POINT; inn COUNTER, MARKET_STALL, INNKEEPER JOB_SITE, HEARTH, BED, SEAT,
   TABLE; bank COUNTER, MARKET_STALL, MINTER JOB_SITE; chapel ALTAR, SEAT, PRIEST JOB_SITE; town hall MEETING_POINT,
   NOTICE_BOARD, LECTERN, SEAT; library SCHOLAR JOB_SITE, LECTERN, SEAT, TABLE, STORAGE; school LECTERN, SEAT;
   bathhouse SEAT, MEETING_POINT; guardhouse GUARD_POST, GUARD JOB_SITE, BED.
   Noted.
7. **The notice board** is placed as `hearth:notice_board` when that block is registered, else an oak sign on a fence
   reading "Notices"; the NOTICE_BOARD POI is on the cell in front of it either way.
   Noted.

## For the lead

8. Nothing in the shared build machinery was changed. The family adds `build/kit/civic/*` (its own kit) and a lang
   fragment `lang/en_us/build_civic.json` (archetype names, the inn names, "Notices").
   Noted.
