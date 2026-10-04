package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The court's tower: five square, standing half out of the front of the hall (or at its front corner), the way in at its
 * foot. Its ground stage is the vestibule (the portal in front, an arch behind into the hall); at the court's floor a
 * small room with the ringer's ladder, a doorway from the court room and, on the square, the loggia from which
 * sentences are proclaimed (an arch with a stone balustrade on a corbelled ledge); then the shaft with its stage courses,
 * slits and clocks, the belfry of paired lights with the bell, the cornice and the cap. The Bohemian court wears the
 * crown of the Prague bridge towers (a tall steep pyramid, a pinnacle at each corner of its foot, a gilded ball on a rod);
 * the others the cap of their tradition (an onion in the Alps, a spire in the north and the stone lands, a tiled hip over
 * a Mediterranean campanile, a cone on the steppe).
 */
public final class CourtTower {
	private CourtTower() {
	}

	/** The cap a court's tower wears. */
	public enum Cap {
		PRAGUE, ONION, SPIRE, PYRAMID, HIP, CONE, BATTLEMENT
	}

	public static Cap cap(Tradition t) {
		return switch (t) {
			case TIMBER -> Cap.PRAGUE;
			case ALPINE -> Cap.ONION;
			case STONE, LOG -> Cap.SPIRE;
			case MEDITERRANEAN -> Cap.HIP;
			case STEPPE -> Cap.CONE;
			case ADOBE -> Cap.BATTLEMENT;
			default -> Cap.PYRAMID;
		};
	}

	/** Rows a cap wants over the cornice (finial included). */
	static int room(Cap c) {
		return switch (c) {
			case PRAGUE -> 10;
			case ONION -> 8;
			case SPIRE -> 11;
			case PYRAMID, CONE -> 6;
			case HIP -> 4;
			case BATTLEMENT -> 2;
		};
	}

	/** What the tower built. */
	public record Built(int top, int belfry, int loggiaY) {
	}

	/**
	 * The tower at (x0, z0) (its front face toward {@link Dirs#FRONT}), from standing level {@code base}; the court's
	 * floor at {@code fy}; the hall's ridge at {@code ridge}. Faces in {@code blind} get no slits or clocks (against the
	 * roof). The portal in front is the caller's (it registers the entrance).
	 */
	public static Built build(Draft d, Tradition t, int x0, int z0, int base, int fy, int ridge, int maxH, int tier, Set<Direction> blind) {
		Stone body = Masonry.walling(d);
		Stone dress = Stone.dressing(d);
		Stone foot = Masonry.plinth(d);
		Tower tw = new Tower(x0, z0, 5, body, dress, foot, false);
		Cap cap = cap(t);
		int top = maxH - 1;
		int bh = 5;
		int yb = Math.max(fy + 5, Math.min(Math.max(ridge - 1, fy + 5), top - room(cap) - bh));
		int yc = yb + bh;
		Side front = tw.face(Dirs.FRONT);
		Side back = tw.face(Dirs.BACK);
		Groundwork.footings(d, tw.x0, tw.z0, tw.x1, tw.z1, base - 1);
		tw.plinth(d, base, base, true, front.outX(front.middle(), 1), front.outZ(front.middle(), 1));
		tw.shaft(d, base + 1, yc - 1);
		d.clearBox(tw.x0 + 1, base, tw.z0 + 1, tw.x1 - 1, yb - 1, tw.z1 - 1);
		tw.floor(d, base - 1, d.mat(Role.PAVING).full());
		tw.floor(d, fy - 1, d.mat(Role.FLOOR).full());
		// a stage course half way up a tall shaft, the corbel table and a course under the belfry
		int mid = yb - fy >= 12 ? fy + (yb - fy) / 2 : -1;
		if (mid > 0) {
			tw.course(d, mid, false);
		}
		tw.band(d, yb - 1);
		tw.course(d, yb, false);
		// the vestibule opens into the hall through an arch; the tower room opens into the court through a doorway
		try (var st = d.stage(StageKind.WALLS)) {
			Details.arch(d, back, 1, 3, base, 3, dress, null);
			Details.arch(d, back, 2, 1, fy, 3, dress, null);
		}
		// the loggia of proclamations on the square: an arch, a balustrade on a corbelled ledge before it
		loggia(d, front, fy, dress);
		// slits up the shaft, clocks under the belfry where the faces are free
		Paint glass = d.mat(Role.GLASS).pane();
		int clockY = yb - 2;
		for (Side f : tw.faces()) {
			if (blind.contains(f.out())) {
				continue;
			}
			int lo = fy + 4;
			int hi = (mid > 0 ? mid : yb) - 1;
			if (f.out() != Dirs.FRONT && hi - lo >= 2) {
				Details.slit(d, f, f.middle(), lo, 2, dress, glass);
			}
			if (clockY - 1 > (mid > 0 ? mid : fy + 4) && f.out() != Dirs.BACK) {
				Towers.clock(d, f, 2, clockY, false);
			}
		}
		// the town's arms over the portal
		Signs.Arms arms = Signs.arms(d);
		int bz = front.outZ(2, 1);
		int bannerY = base + 4;
		if ((bz >= 0 || bannerY >= 3) && bannerY < fy - 2 && d.isEmpty(front.outX(2, 1), bannerY, bz)) {
			Signs.wallBanner(d, front.outX(2, 1), bannerY, bz, Dirs.FRONT, arms.field(), arms.layers());
		}
		// the belfry: paired lights in every face, the bell under its beam, the cornice the cap stands on
		tw.openings(d, yb + 1, 3, Tower.Opening.BIFORA, true, null);
		tw.floor(d, yb, dress.full());
		tw.hollow(d, yb + 1, yc - 1);
		tw.band(d, yc - 1);
		tw.cornice(d, yc);
		tw.bells(d, yc - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		// the ringer's ladder from the tower room to the belfry, a lantern to climb by
		Stairs.ladder(d, tw.x0 + 1, tw.z0 + 1, fy, yb, Dirs.BACK);
		Lighting.chandelier(d, tw.x0 + 3, tw.z0 + 3, yb, Math.max(fy + 2, yb - 2));
		int capTop = cap == Cap.PRAGUE ? prague(d, tw, yc, top, dress)
			: Towers.cap(d, tw.x0, tw.z0, 5, yc, switch (cap) {
				case ONION -> Towers.Cap.ONION;
				case SPIRE -> Towers.Cap.SPIRE;
				case HIP -> Towers.Cap.HIP;
				case CONE -> Towers.Cap.CONE;
				case BATTLEMENT -> Towers.Cap.BATTLEMENT;
				default -> Towers.Cap.PYRAMID;
			}, top);
		return new Built(capTop, yb, fy);
	}

	/** Whether the tower is rendered like the walls (the plaster and render traditions) rather than bare masonry. */
	static boolean render(Draft d, Tradition t) {
		boolean plaster = t == Tradition.TIMBER || t == Tradition.ALPINE || t == Tradition.MEDITERRANEAN || t == Tradition.STEPPE;
		return plaster && Stone.stony(d, Role.WALL) || Stone.masonryWalls(d) && Stone.stony(d, Role.WALL);
	}

	/**
	 * The loggia on face f at the tower room's floor: an arch three wide and three high opening the room to the square, a
	 * balustrade of the dressing a block out on a ledge carried on corbels, a VIEWPOINT in the arch.
	 */
	static void loggia(Draft d, Side f, int fy, Stone dress) {
		int ox = f.outX(2, 1);
		int oz = f.outZ(2, 1);
		boolean out = Walls.inPlot(d, ox, oz) || oz == -1 && fy - 2 >= 3;
		try (var st = d.stage(StageKind.WALLS)) {
			Details.arch(d, f, 1, 3, fy, 3, dress, null);
			if (!out) {
				// no room for a ledge: the balustrade stands in the arch
				for (int i = 1; i <= 3; i++) {
					d.set(f.x(i), fy, f.z(i), dress.wallOrFull());
				}
				return;
			}
			for (int i = 1; i <= 3; i++) {
				int x = f.outX(i, 1);
				int z = f.outZ(i, 1);
				d.set(x, fy - 1, z, dress.slabOrNothing(SlabType.TOP).orElse(dress.full()));
				d.set(x, fy, z, dress.wallOrFull());
				if (i != 2) {
					d.set(x, fy - 2, z, dress.stairsOrSlab(f.out().getOpposite(), Half.TOP));
				}
			}
		}
		d.poi(PoiType.VIEWPOINT, f.x(2), fy, f.z(2), f.out());
	}

	/**
	 * The crown of the Prague bridge towers: a tall steep pyramid of the roof's covering laid in rings that draw in a
	 * block a side, each ring a core of full blocks capped by stairs facing up its slope (slate or tile; copper over
	 * thatch and shingle towns), a pinnacle of the dressing on each corner of its foot, a gilded ball on a rod at the apex.
	 * Returns the top.
	 */
	static int prague(Draft d, Tower tw, int yc, int maxTop, Stone dress) {
		Stone skin = d.habits().roof().thatch() || !d.mat(Role.ROOF).has(Form.STAIRS) ? Stone.copper(d) : Stone.of(d, Role.ROOF);
		int room = maxTop - yc;
		// rows per ring, outer to inner: the broad ring briefly, the slender top longest
		int[] rows = room >= 10 ? new int[]{2, 3, 1} : room >= 8 ? new int[]{2, 2, 1} : new int[]{1, 1, 1};
		int y = yc + 1;
		try (var st = d.stage(StageKind.ROOF)) {
			for (int ring = 0; ring < 3; ring++) {
				int a = ring;
				int x0 = tw.x0 + a;
				int z0 = tw.z0 + a;
				int x1 = tw.x1 - a;
				int z1 = tw.z1 - a;
				for (int k = 0; k < rows[ring]; k++, y++) {
					boolean capRow = k == rows[ring] - 1 && ring < 2;
					for (int z = z0; z <= z1; z++) {
						for (int x = x0; x <= x1; x++) {
							boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
							if (capRow && edge) {
								Direction in = x == x0 ? Dirs.RIGHT : x == x1 ? Dirs.LEFT : z == z0 ? Dirs.BACK : Dirs.FRONT;
								if ((x == x0 || x == x1) && (z == z0 || z == z1)) {
									in = z == z0 ? Dirs.BACK : Dirs.FRONT;
								}
								d.set(x, y, z, skin.stairsOrSlab(in, Half.BOTTOM));
							} else {
								d.set(x, y, z, skin.full());
							}
						}
					}
				}
			}
		}
		int top = y - 1;
		// a pinnacle on each corner of the cornice, at the pyramid's foot
		int ph = Math.max(1, Math.min(3, room / 4));
		try (var st = d.stage(StageKind.ROOF)) {
			for (int[] c : new int[][]{{tw.x0, tw.z0}, {tw.x1, tw.z0}, {tw.x0, tw.z1}, {tw.x1, tw.z1}}) {
				top = Math.max(top, Details.pinnacle(d, c[0], yc + 1, c[1], ph, dress, true) - 1);
			}
		}
		if (y + 1 < d.maxHeight()) {
			int fy = y;
			d.inStage(StageKind.ROOF, () -> Details.finial(d, tw.cx, fy, tw.cz, true));
			top = Math.max(top, fy + 1);
		}
		return top;
	}
}
