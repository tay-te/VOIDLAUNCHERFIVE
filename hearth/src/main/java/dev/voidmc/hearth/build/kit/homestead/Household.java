package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Who lives in a home, and the things that say so (docs/LIVING_WORLD.md §8.1): the same cottage reads differently for a
 * smith, a fisher, a scholar or a merchant. Homes read two spec params:
 *
 * <ul>
 *   <li>{@code station} (string, a {@link Station} name: poor, peasant, artisan, merchant, clergy, scholar, official,
 *   gentry, lord): the household's place;</li>
 *   <li>{@code household} (compound): {@code job} (string, a {@link JobType} name), the household's trade.</li>
 * </ul>
 *
 * Without them a home is dressed for an ordinary household of its culture (nothing is added). {@link #dress} adds the
 * trade's and the station's things after the rooms are furnished, only where a room can spare the floor (every cell it
 * takes is claimed through {@link Rooms.Room#claim}, so doors, beds and stores stay reachable): tongs and an anvil by a
 * smith's hearth, nets and a creel at a fisher's, books stacked at a scholar's or a priest's, carpets, candles and a
 * strongbox at a merchant's, hay and a basket of the harvest at a farmer's.
 */
public final class Household {
	private Household() {
	}

	/** A household: its station (null when the spec does not say) and its trade. */
	public record Who(@Nullable Station station, JobType job) {
		/** The station the spec gave, or the one the trade brings. */
		public Station rank() {
			return this.station != null ? this.station : this.job == JobType.NONE ? Station.PEASANT : Station.of(this.job);
		}

		public boolean known() {
			return this.station != null || this.job != JobType.NONE;
		}
	}

	/** Reads {@code station} and {@code household.job} from the spec. */
	public static Who of(Draft d) {
		Station station = null;
		String s = d.param("station", "");
		if (!s.isEmpty()) {
			for (Station st : Station.values()) {
				if (st.getSerializedName().equalsIgnoreCase(s)) {
					station = st;
				}
			}
		}
		JobType job = JobType.NONE;
		String j = d.params().getCompoundOrEmpty("household").getStringOr("job", "");
		if (!j.isEmpty()) {
			try {
				job = JobType.valueOf(j.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				job = JobType.NONE;
			}
		}
		return new Who(station, job);
	}

	/**
	 * Dresses a furnished home for its household: the living room (kitchen or hall) and the room the family sleeps in
	 * (may be the same room); {@code hearth} is the fire's cell (or null).
	 */
	public static void dress(Draft d, Rooms.Room living, Rooms.Room sleeping, int @Nullable [] hearth) {
		Who who = of(d);
		if (!who.known()) {
			return;
		}
		switch (who.job()) {
			case SMITH, SMELTER, MINTER, GUARD -> {
				// the anvil in a corner by the fire, tongs (a chain) hung beside the hearth, a grindstone
				place(d, living, hearth, Blocks.CHIPPED_ANVIL.defaultBlockState(), true);
				if (hearth != null) {
					tongs(d, hearth, living.y);
				}
			}
			case FISHER, BOATWRIGHT, SALTER -> {
				place(d, living, null, Blocks.COMPOSTER.defaultBlockState(), false);
				place(d, living, null, States.facing(Blocks.BARREL, Direction.UP), false);
				net(d, living);
			}
			case FARMER, HERDER, BEEKEEPER -> {
				place(d, living, null, Blocks.HAY_BLOCK.defaultBlockState(), false);
				place(d, living, null, who.job() == JobType.BEEKEEPER ? Blocks.HONEYCOMB_BLOCK.defaultBlockState() : Blocks.CARVED_PUMPKIN.defaultBlockState(),
					false);
			}
			case FORESTER, CARPENTER, HUNTER, CHARCOAL_BURNER -> {
				place(d, living, hearth, d.mat(Role.FRAME).log(Direction.Axis.X).at(living.x0, living.y, living.z0), true);
				place(d, living, null, States.facing(Blocks.BARREL, Direction.UP), false);
			}
			case SCHOLAR, PRIEST, JUDGE, HEALER, PAPERMAKER -> {
				books(d, living, who.job() == JobType.HEALER ? 1 : 2);
				books(d, sleeping, 1);
				if (who.job() == JobType.HEALER) {
					place(d, living, null, Blocks.BREWING_STAND.defaultBlockState(), false);
				}
			}
			case MERCHANT, TRADER, COURIER, INNKEEPER -> merchant(d, living, sleeping);
			case TAILOR, DYER -> place(d, living, null, States.facing(Blocks.LOOM, Dirs.FRONT), false);
			case BAKER, BREWER, BUTCHER, CHANDLER -> {
				place(d, living, null, States.facing(Blocks.BARREL, Direction.UP), false);
				place(d, living, null, States.facing(Blocks.BARREL, Direction.UP), false);
			}
			case MASON, QUARRIER, MINER, KILN_WORKER, CLAY_DIGGER -> place(d, living, null, Blocks.STONECUTTER.defaultBlockState(), false);
			default -> {
			}
		}
		// the station's finish on top of the trade
		switch (who.rank()) {
			case MERCHANT, GENTRY, LORD, OFFICIAL -> {
				if (who.job() != JobType.MERCHANT && who.job() != JobType.TRADER) {
					merchant(d, living, sleeping);
				}
			}
			case CLERGY, SCHOLAR -> {
				if (who.job() != JobType.SCHOLAR && who.job() != JobType.PRIEST) {
					books(d, living, 1);
				}
			}
			default -> {
			}
		}
	}

	/** Carpets on the open floor, candles on the tables, a strongbox in a corner of the bedroom. */
	private static void merchant(Draft d, Rooms.Room living, Rooms.Room sleeping) {
		carpet(d, living);
		if (sleeping != living) {
			carpet(d, sleeping);
		}
		int[] c = corner(d, sleeping, null);
		if (c != null) {
			Direction into = into(d, sleeping, c);
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(c[0], sleeping.y, c[1], States.facing(Blocks.CHEST, into),
					BlockEntities.container(List.of(new Object[]{"minecraft:gold_ingot", 3}, new Object[]{"minecraft:emerald", 4})));
			}
			d.poi(PoiType.STRONGBOX, c[0] + Dirs.dx(into), sleeping.y, c[1] + Dirs.dz(into), into.getOpposite());
		}
	}

	/** A bookshelf two high in a corner (or one under a low ceiling), {@code n} of them. */
	private static void books(Draft d, Rooms.Room r, int n) {
		for (int i = 0; i < n; i++) {
			int[] c = corner(d, r, null);
			if (c == null) {
				return;
			}
			Furniture.bookshelf(d, c[0], r.y, c[1]);
			if (r.height >= 3 && d.isEmpty(c[0], r.y + 1, c[1])) {
				d.inStage(StageKind.INTERIOR, () -> d.set(c[0], r.y + 1, c[1], Noise.unit(d.seed(), c[0], r.y, c[1]) < 0.5
					? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState()));
			}
		}
	}

	/** Carpet over the room's open floor (cells with nothing on them and a floor under). */
	private static void carpet(Draft d, Rooms.Room r) {
		Block a = d.mat(Role.CLOTH).palette().main().block(dev.voidmc.hearth.build.style.Form.CARPET);
		if (a == null) {
			a = Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED);
		}
		Block b = a;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = r.z0 + 1; z <= r.z1 - 1; z++) {
				for (int x = r.x0 + 1; x <= r.x1 - 1; x++) {
					if (d.isAbsent(x, r.y, z) && d.isSolid(x, r.y - 1, z)) {
						d.set(x, r.y, z, b.defaultBlockState());
					}
				}
			}
		}
	}

	/** A fishing net (cobweb) hung high in a corner of the room, against the walls. */
	private static void net(Draft d, Rooms.Room r) {
		int y = r.y + r.height - 1;
		for (int[] c : new int[][]{{r.x0, r.z0}, {r.x1, r.z0}, {r.x0, r.z1}, {r.x1, r.z1}}) {
			if (d.isEmpty(c[0], y, c[1]) && d.isEmpty(c[0], y - 1, c[1])) {
				d.inStage(StageKind.INTERIOR, () -> d.set(c[0], y, c[1], Blocks.COBWEB.defaultBlockState()));
				return;
			}
		}
	}

	/** A smith's tongs: an iron chain hanging beside the hearth's mantel. */
	private static void tongs(Draft d, int[] hearth, int y) {
		for (Direction dir : Dirs.HORIZONTAL) {
			int x = hearth[0] + Dirs.dx(dir);
			int z = hearth[1] + Dirs.dz(dir);
			if (d.isEmpty(x, y + 1, z) && d.isSolid(x, y + 2, z) && d.isEmpty(x, y, z)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(x, y + 1, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y)));
				return;
			}
		}
	}

	/** Places a block in a free corner of the room (near {@code near} when given). */
	private static boolean place(Draft d, Rooms.Room r, int @Nullable [] near, BlockState state, boolean close) {
		int[] c = corner(d, r, close ? near : null);
		if (c == null) {
			return false;
		}
		BlockState st = state;
		if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, into(d, r, c));
		}
		BlockState fin = st;
		d.inStage(StageKind.INTERIOR, () -> d.set(c[0], r.y, c[1], fin));
		return true;
	}

	/**
	 * A free cell of the room against its walls (corners first, then along a wall; nearest {@code near} when given) that
	 * the room can spare: claimed, so the room stays walkable.
	 */
	public static int @Nullable [] corner(Draft d, Rooms.Room r, int @Nullable [] near) {
		List<int[]> cand = new ArrayList<>();
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = r.x0; x <= r.x1; x++) {
				int walls = 0;
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (!r.inside(nx, nz) && d.isSolid(nx, r.y, nz)) {
						walls++;
					}
				}
				if (walls == 0 || !d.isEmpty(x, r.y, z) || !d.isEmpty(x, r.y + 1, z) || !d.isSolid(x, r.y - 1, z)) {
					continue;
				}
				cand.add(new int[]{x, z, walls});
			}
		}
		long seed = d.seed();
		cand.sort((a, b) -> {
			if (near != null) {
				int da = Math.abs(a[0] - near[0]) + Math.abs(a[1] - near[1]);
				int db = Math.abs(b[0] - near[0]) + Math.abs(b[1] - near[1]);
				if (da != db) {
					return Integer.compare(da, db);
				}
			}
			if (a[2] != b[2]) {
				return Integer.compare(b[2], a[2]);
			}
			return Double.compare(Noise.unit(seed ^ 0x4053, a[0], 0, a[1]), Noise.unit(seed ^ 0x4053, b[0], 0, b[1]));
		});
		for (int[] c : cand) {
			if (r.claim(c[0], c[1])) {
				return new int[]{c[0], c[1]};
			}
		}
		return null;
	}

	/** The direction from a wall cell into the room (away from its first wall). */
	private static Direction into(Draft d, Rooms.Room r, int[] c) {
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = c[0] + Dirs.dx(dir);
			int nz = c[1] + Dirs.dz(dir);
			if (!r.inside(nx, nz) && d.isSolid(nx, r.y, nz)) {
				return dir.getOpposite();
			}
		}
		return Dirs.FRONT;
	}
}
