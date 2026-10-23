package dev.voidmc.bestiary.danger;

import dev.voidmc.bestiary.api.DangerModifier;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The danger map (BESTIARY.md §6.1): world-anchored, computed when a creature spawns (and saved with it). Distance from
 * world spawn, depth, night on the surface, the dimension, then any {@link DangerModifier} (Hearth's territory and
 * roads, Expanse's ruins and lairs), clamped to 1-5.
 */
public final class Danger {
	private static final List<DangerModifier> MODIFIERS = new CopyOnWriteArrayList<>();

	private Danger() {
	}

	public static void addModifier(DangerModifier m) {
		MODIFIERS.add(m);
	}

	public static float at(ServerLevel level, BlockPos pos) {
		DangerConfig c = DangerConfig.get();
		if (c.override > 0) {
			return DangerMath.clamp(c.override);
		}
		float d;
		if (level.dimension() == Level.NETHER) {
			d = c.netherMin;
		} else if (level.dimension() == Level.END) {
			d = c.endMin;
		} else {
			BlockPos spawn = level.getRespawnData().pos();
			double dist = Math.hypot(pos.getX() - spawn.getX(), pos.getZ() - spawn.getZ());
			d = DangerMath.distance(dist, c.rings, c.ringBlend);
			int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
			d += DangerMath.depth(surface - pos.getY(), pos.getY(), c.tunnelLow, c.tunnelHigh);
			if (level.isDarkOutside() && level.canSeeSky(pos.above())) {
				d += c.night;
			}
		}
		for (DangerModifier m : MODIFIERS) {
			d += m.modify(level, pos, d);
		}
		if (c.mythic) {
			d += 1;
		}
		return DangerMath.clamp(d);
	}
}
