
## Agreed with VOID LOD, 2026-10-19: the "planned, not built" mark

LodFootprints already writes `cols[i] = wall | 0xFF00_0000`. The wall word's high byte carries the state: `0xFF` = built
(unchanged), `0xFE` (`wall | 0xFE00_0000`) = planned or under construction (a live town's unfinished plot); any other
value counts as built. Old LOD jars mask the high byte. Do NOT use "bit 24 set" (it is set for every building today).
