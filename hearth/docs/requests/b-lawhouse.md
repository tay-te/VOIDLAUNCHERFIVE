# Requests from the lawhouse architect (b-lawhouse)

1. **plan**: `law/jail` and `law/gallows` are registered but nothing in `plan/growth` asks for them (Program has the
   pillory and courthouse; Gates has the watch house and toll gate). Suggest Program entries: jail beside the
   courthouse (CIVIC district, town+, law on), gallows at the edge of the market square or just outside the walls
   (town+, law on, after the jail).
2. **plan / catalogue**: `DefaultCatalogue` fallback sizes for the law ids are smaller than the registered ones
   (pillory 3–5 vs 5–7, gallows 5–7 vs 7–9, courthouse height 12–18 vs 28–36, watch house 5–9 vs 8–10, toll gate
   7–11 × 5–7 vs 11–13 × 6–8). Please align them with `build/archetype/law/*` INFO so fallback plots fit.
3. **law**: the jail's cells carry `CELL` POIs inside iron doors; prisoners need to be teleported/led in by the guard
   (doors are iron, opened only by the law code, not by hand).
