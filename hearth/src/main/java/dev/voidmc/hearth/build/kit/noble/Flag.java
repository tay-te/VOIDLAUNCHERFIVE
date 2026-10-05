package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The lord's colours: a flag of the household's cloth flying from a pole on the highest roof (laid in wool so it reads
 * from far off and on VOID LOD's horizon), and banners hung on walls and over gates.
 */
public final class Flag {
	private Flag() {
	}

	/**
	 * A pole from standing level y up {@code height} at (x, z) with a flag at its head flying toward {@code fly}: two
	 * rows of wool {@code length} long in the cloth's first colour, the lower row's tail in the second (a stripe), and a
	 * banner of the arms on the pole's head. Stays under {@code maxTop}. Returns the highest row used.
	 */
	public static int pole(Draft d, Dress m, int x, int y, int z, int height, Direction fly, int length, int maxTop) {
		int top = Math.min(y + height, maxTop - 1);
		if (top < y + 2) {
			return y - 1;
		}
		var entries = m.cloth.palette().entries();
		Block main = entries.get(0).material().block(Form.FULL);
		Block second = entries.size() > 1 ? entries.get(1).material().block(Form.FULL) : main;
		if (main == null) {
			main = Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.RED);
		}
		if (second == null) {
			second = main;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int yy = y; yy <= top; yy++) {
				d.set(x, yy, z, m.frame.has(Form.FENCE) ? m.frame.fence().at(x, yy, z) : Blocks.IRON_BARS.defaultBlockState());
			}
			int fy = top - 1;
			for (int k = 1; k <= length; k++) {
				int fx = x + Dirs.dx(fly) * k;
				int fz = z + Dirs.dz(fly) * k;
				if (!Mason.ok(d, fx, fy, fz) || !Mason.ok(d, fx, fy - 1, fz)) {
					break;
				}
				d.set(fx, fy, fz, main.defaultBlockState());
				d.set(fx, fy - 1, fz, (k == length ? second : main).defaultBlockState());
			}
			if (top + 1 <= maxTop) {
				d.set(x, top + 1, z, dev.voidmc.hearth.build.kit.landmark.Details.rod());
			}
		}
		return Math.min(top + 1, maxTop);
	}

	/** A banner hanging on the wall behind (x, y, z), facing {@code out}, in the cloth's colours (the lord's arms). */
	public static void banner(Draft d, Dress m, int x, int y, int z, Direction out, int salt) {
		if (!Mason.ok(d, x, y, z) || !d.isEmpty(x, y, z)) {
			return;
		}
		Block b = Mason.wallBanner(m, salt);
		if (b == null) {
			return;
		}
		var entries = m.cloth.palette().entries();
		Block wool = entries.get(Math.floorMod(salt + 1, entries.size())).material().block(Form.FULL);
		String colour = wool == null ? "white" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(wool).getPath().replace("_wool", "");
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, States.facing(b, out), BlockEntities.banner(List.of(new String[]{"cross", colour}, new String[]{"border", colour})));
		}
	}
}
