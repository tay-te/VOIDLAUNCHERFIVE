package dev.voidmc.hearth.social.life;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Where an event is held: the altar of the chapel for a wedding, the inn's tables for the feast, the stage or the square
 * for a festival, the stalls and square on market day, the cemetery for a funeral, the throne for the lord's court.
 * From blueprint POIs ({@link Settlement#pois}); the settlement's heart when it has none of them.
 */
public final class Venues {
	private Venues() {
	}

	/**
	 * @param anchor where the event is centred (dressings go round it)
	 * @param focus  what the gathering faces (the altar, the stage, the throne)
	 * @param spots  places folk stand at or round
	 * @param ring   how far round each spot folk spread
	 */
	public record Venue(BlockPos anchor, BlockPos focus, List<BlockPos> spots, int ring) {
	}

	public static Venue of(Settlement s, TownEvent e) {
		BlockPos center = s.center();
		return switch (e.kind()) {
			case MARKET -> {
				List<BlockPos> stalls = positions(s.pois(PoiType.MARKET_STALL));
				BlockPos heart = square(s);
				List<BlockPos> spots = new ArrayList<>(stalls);
				spots.add(heart);
				yield new Venue(heart, heart, spots, 3);
			}
			case FESTIVAL -> {
				List<Poi> stages = s.pois(PoiType.STAGE);
				BlockPos heart = square(s);
				BlockPos focus = stages.isEmpty() ? heart : nearest(positions(stages), heart);
				List<BlockPos> spots = new ArrayList<>();
				spots.add(focus);
				spots.add(heart);
				yield new Venue(heart, focus, spots, 5);
			}
			case WEDDING -> {
				List<Poi> altars = s.pois(PoiType.ALTAR);
				if (altars.isEmpty()) {
					BlockPos heart = square(s);
					yield new Venue(heart, heart, List.of(heart), 4);
				}
				BlockPos altar = nearest(positions(altars), center);
				PlotView chapel = plotAt(s, altar);
				List<BlockPos> seats = chapel == null ? List.of() : positions(inPlot(s.pois(PoiType.SEAT), chapel));
				List<BlockPos> spots = seats.isEmpty() ? List.of(altar) : seats;
				yield new Venue(altar, altar, spots, seats.isEmpty() ? 4 : 1);
			}
			case FEAST -> {
				List<BlockPos> tables = new ArrayList<>();
				for (PlotView p : built(s, "commerce/inn", "commerce/tavern", "landscape/festival_ground")) {
					tables.addAll(positions(inPlot(s.pois(PoiType.TABLE), p)));
				}
				BlockPos heart = square(s);
				if (tables.isEmpty()) {
					yield new Venue(heart, heart, List.of(heart), 4);
				}
				BlockPos first = nearest(tables, heart);
				yield new Venue(first, first, tables, 1);
			}
			case FUNERAL -> {
				List<PlotView> cemeteries = built(s, "civic/cemetery");
				if (!cemeteries.isEmpty()) {
					PlotView c = cemeteries.get(0);
					BlockPos mid = middle(c.spec());
					yield new Venue(mid, mid, List.of(mid), 4);
				}
				List<Poi> altars = s.pois(PoiType.ALTAR);
				BlockPos at = altars.isEmpty() ? square(s) : nearest(positions(altars), center);
				yield new Venue(at, at, List.of(at), 4);
			}
			case COURT -> {
				List<Poi> thrones = s.pois(PoiType.THRONE);
				if (!thrones.isEmpty()) {
					BlockPos throne = nearest(positions(thrones), center);
					PlotView hall = plotAt(s, throne);
					List<BlockPos> spots = new ArrayList<>();
					spots.add(throne);
					if (hall != null) {
						spots.addAll(positions(inPlot(s.pois(PoiType.SEAT), hall)));
					}
					yield new Venue(throne, throne, spots, 2);
				}
				int seat = Officials.seat(s, Office.LORD);
				PlotView p = seat < 0 ? null : s.plot(seat);
				BlockPos at = p == null ? square(s) : middle(p.spec());
				yield new Venue(at, at, List.of(at), 3);
			}
		};
	}

	/** The town square: a meeting point in a plaza, else the meeting point nearest the heart, else the heart itself. */
	public static BlockPos square(Settlement s) {
		List<Poi> meets = s.pois(PoiType.MEETING_POINT);
		if (meets.isEmpty()) {
			return s.center();
		}
		for (PlotView p : built(s, "landscape/plaza", "landscape/festival_ground")) {
			List<Poi> inside = inPlot(meets, p);
			if (!inside.isEmpty()) {
				return inside.get(0).pos();
			}
		}
		return nearest(positions(meets), s.center());
	}

	/**
	 * Where one resident stands at a venue: one of its spots (chosen by who they are), moved out round it on a ring so a
	 * crowd spreads, onto standable ground. Reads blocks only in loaded chunks; falls back to the spot itself.
	 */
	public static BlockPos standFor(ServerLevel level, Venue v, UUID resident, int salt) {
		long h = TownCalendar.mix(resident.getMostSignificantBits() * 31 + resident.getLeastSignificantBits() + salt);
		BlockPos spot = v.spots().get((int) Math.floorMod(h, (long) v.spots().size()));
		if (v.ring() <= 1) {
			BlockPos ok = standable(level, spot);
			if (ok != null) {
				return ok;
			}
		}
		for (int attempt = 0; attempt < 8; attempt++) {
			long a = TownCalendar.mix(h + attempt);
			double angle = (a & 0xFFFF) / 65536.0 * Math.PI * 2;
			int r = 1 + (int) Math.floorMod(a >>> 16, (long) Math.max(1, v.ring()));
			BlockPos p = spot.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
			BlockPos ok = standable(level, p);
			if (ok != null) {
				return ok;
			}
		}
		return spot;
	}

	/** A block a folk can stand in at or within two blocks above or below {@code p}, or null. */
	public static @Nullable BlockPos standable(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return null;
		}
		for (int dy : new int[] {0, 1, -1, 2, -2}) {
			BlockPos at = p.above(dy);
			BlockState feet = level.getBlockState(at);
			BlockState head = level.getBlockState(at.above());
			BlockState below = level.getBlockState(at.below());
			if (feet.getCollisionShape(level, at).isEmpty() && head.getCollisionShape(level, at.above()).isEmpty() && feet.getFluidState().isEmpty()
				&& below.isFaceSturdy(level, at.below(), Direction.UP)) {
				return at;
			}
		}
		return null;
	}

	public static List<PlotView> built(Settlement s, String... paths) {
		List<PlotView> out = new ArrayList<>();
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.BUILT) {
				continue;
			}
			String path = p.spec().archetype().getPath();
			for (String want : paths) {
				if (path.equals(want)) {
					out.add(p);
					break;
				}
			}
		}
		return out;
	}

	/** Whether a position lies in a plot's footprint (and its height). */
	public static boolean inside(PlotSpec spec, BlockPos pos) {
		BlockPos o = spec.origin();
		return pos.getX() >= o.getX() && pos.getX() < o.getX() + spec.sizeX() && pos.getZ() >= o.getZ() && pos.getZ() < o.getZ() + spec.sizeZ()
			&& pos.getY() >= o.getY() - 8 && pos.getY() <= o.getY() + spec.maxHeight();
	}

	public static List<Poi> inPlot(List<Poi> pois, PlotView plot) {
		List<Poi> out = new ArrayList<>();
		for (Poi p : pois) {
			if (inside(plot.spec(), p.pos())) {
				out.add(p);
			}
		}
		return out;
	}

	public static @Nullable PlotView plotAt(Settlement s, BlockPos pos) {
		for (PlotView p : s.plots()) {
			if (p.state() == PlotState.BUILT && inside(p.spec(), pos) && !p.spec().archetype().getPath().startsWith("infra/")) {
				return p;
			}
		}
		return null;
	}

	public static BlockPos middle(PlotSpec spec) {
		return spec.origin().offset(spec.sizeX() / 2, 0, spec.sizeZ() / 2);
	}

	public static List<BlockPos> positions(List<Poi> pois) {
		List<BlockPos> out = new ArrayList<>(pois.size());
		for (Poi p : pois) {
			out.add(p.pos());
		}
		return out;
	}

	public static BlockPos nearest(List<BlockPos> list, BlockPos to) {
		BlockPos best = list.get(0);
		double bd = Double.MAX_VALUE;
		for (BlockPos p : list) {
			double d = p.distSqr(to);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}
}
