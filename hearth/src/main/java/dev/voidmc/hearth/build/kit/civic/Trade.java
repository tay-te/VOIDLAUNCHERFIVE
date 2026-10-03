package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Selling: counters with wares on them and the merchant's place behind, the customer's in front; a bar with its taps;
 * a coin counter with a grille. Each registers COUNTER (the merchant's cell, facing the customer) and MARKET_STALL (the
 * customer's cell, facing the counter), and puts the merchant's job site behind it.
 */
public final class Trade {
	private Trade() {
	}

	/**
	 * A counter of {@code n} blocks from (x, y, z) along {@code run}, its customer side toward {@code toCustomer}: slab
	 * tops with wares and barrels, the merchant's barrel at the far end (the job site, behind the counter). Registers
	 * COUNTER and MARKET_STALL at its middle.
	 */
	public static void counter(Draft d, int x, int y, int z, Direction run, int n, Direction toCustomer, JobType job) {
		Mat wood = d.mat(Role.FURNITURE);
		Direction toMerchant = toCustomer.getOpposite();
		for (int i = 0; i < n; i++) {
			int px = x + Dirs.dx(run) * i;
			int pz = z + Dirs.dz(run) * i;
			if (!d.isEmpty(px, y, pz)) {
				continue;
			}
			if (i == n - 1 && job != JobType.NONE) {
				Furniture.workstation(d, job, px, y, pz, toMerchant);
				continue;
			}
			double u = Noise.unit(d.seed() ^ 0xC0A7, px, y, pz);
			try (var s = d.stage(StageKind.INTERIOR)) {
				if (u < 0.25) {
					d.set(px, y, pz, States.facing(Blocks.BARREL, toCustomer));
				} else {
					d.set(px, y, pz, wood.slab(SlabType.TOP));
					wares(d, px, y + 1, pz);
				}
			}
		}
		int mid = Math.max(0, (n - 1) / 2 - (n >= 3 ? 0 : 0));
		int mx = x + Dirs.dx(run) * mid;
		int mz = z + Dirs.dz(run) * mid;
		d.poi(PoiType.COUNTER, mx + Dirs.dx(toMerchant), y, mz + Dirs.dz(toMerchant), toCustomer);
		d.poi(PoiType.MARKET_STALL, mx + Dirs.dx(toCustomer), y, mz + Dirs.dz(toCustomer), toMerchant);
	}

	/** Something for sale on a counter top: a pot of flowers, candles, a melon or pumpkin, a cake, a decorated pot. */
	public static void wares(Draft d, int x, int y, int z) {
		double u = Noise.unit(d.seed() ^ 0x3A7E5, x, y, z);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (u < 0.2) {
				Garden.pot(d, x, y, z);
			} else if (u < 0.32) {
				d.set(x, y, z, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + (int) (u * 10) % 4));
			} else if (u < 0.44) {
				d.set(x, y, z, Blocks.DECORATED_POT.defaultBlockState());
			} else if (u < 0.52) {
				d.set(x, y, z, Blocks.MELON.defaultBlockState());
			} else if (u < 0.6) {
				d.set(x, y, z, Blocks.PUMPKIN.defaultBlockState());
			} else if (u < 0.66) {
				d.set(x, y, z, Blocks.CAKE.defaultBlockState());
			} else if (u < 0.74) {
				d.set(x, y, z, Blocks.HAY_BLOCK.defaultBlockState());
			}
		}
	}

	/**
	 * A tap-room bar: a counter of {@code n} along {@code run} from (x, y, z), its drinkers' side toward
	 * {@code toDrinkers}, kegs (barrels lying on their side) racked on the wall behind the barman and a brewing stand,
	 * stools in front. Registers COUNTER, MARKET_STALL and the innkeeper's job site behind.
	 */
	public static void bar(Draft d, int x, int y, int z, Direction run, int n, Direction toDrinkers, int wallBehind) {
		Mat wood = d.mat(Role.FURNITURE);
		Direction toBarman = toDrinkers.getOpposite();
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < n; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y, pz, i == 0 || i == n - 1 ? wood.full() : wood.stairsOrSlab(toBarman, Half.TOP));
				if (i % 2 == 1) {
					d.set(px, y + 1, pz, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
				}
			}
		}
		// the kegs on the wall behind (the barman's lane between)
		for (int i = 0; i < n; i++) {
			int kx = x + Dirs.dx(run) * i + Dirs.dx(toBarman) * wallBehind;
			int kz = z + Dirs.dz(run) * i + Dirs.dz(toBarman) * wallBehind;
			if (!d.isEmpty(kx, y, kz)) {
				continue;
			}
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(kx, y, kz, States.facing(Blocks.BARREL, toDrinkers));
				if (i % 2 == 0 && d.isEmpty(kx, y + 1, kz)) {
					d.set(kx, y + 1, kz, States.facing(Blocks.BARREL, toDrinkers));
				}
			}
		}
		int mid = n / 2;
		int mx = x + Dirs.dx(run) * mid;
		int mz = z + Dirs.dz(run) * mid;
		Furniture.workstation(d, JobType.INNKEEPER, mx + Dirs.dx(toBarman) * wallBehind, y, mz + Dirs.dz(toBarman) * wallBehind, toDrinkers);
		d.poi(PoiType.COUNTER, mx + Dirs.dx(toBarman), y, mz + Dirs.dz(toBarman), toDrinkers);
		d.poi(PoiType.MARKET_STALL, mx + Dirs.dx(toDrinkers), y, mz + Dirs.dz(toDrinkers), toBarman);
		// stools along the drinkers' side
		for (int i = 1; i < n - 1; i += 2) {
			int sx = x + Dirs.dx(run) * i + Dirs.dx(toDrinkers);
			int sz = z + Dirs.dz(run) * i + Dirs.dz(toDrinkers);
			if (d.isEmpty(sx, y, sz) && i != mid) {
				try (var s = d.stage(StageKind.INTERIOR)) {
					d.set(sx, y, sz, wood.slab(SlabType.BOTTOM));
				}
				d.poi(PoiType.SEAT, sx, y, sz, toBarman);
			}
		}
	}

	/** A table with chairs round it at (x, y, z) (for a tap room or a hall), as many chairs as fit. */
	public static void tableWithChairs(Draft d, int x, int y, int z) {
		if (!d.isEmpty(x, y, z)) {
			return;
		}
		Furniture.table(d, x, y, z);
		for (Direction dir : Dirs.HORIZONTAL) {
			int cx = x + Dirs.dx(dir);
			int cz = z + Dirs.dz(dir);
			if (d.isEmpty(cx, y, cz) && d.isSolid(cx, y - 1, cz)) {
				Furniture.chair(d, cx, y, cz, dir);
			}
		}
	}
}
