package dev.voidmc.hearth.npc.embody;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Where a folk nobody manages (a spawn egg, {@code /summon}) goes to live: the nearest settlement within 160 blocks, or
 * whatever fallback is registered (the dev sandbox's wanderers' camp).
 */
public final class StrayHomes {
	private static @Nullable BiFunction<ServerLevel, BlockPos, @Nullable Settlement> fallback;

	private StrayHomes() {
	}

	public static void setFallback(BiFunction<ServerLevel, BlockPos, @Nullable Settlement> f) {
		fallback = f;
	}

	public static @Nullable Settlement find(ServerLevel level, BlockPos pos) {
		HearthWorld world = HearthWorld.of(level);
		if (world != null) {
			Settlement s = world.nearest(pos, 160);
			if (s != null) {
				return s;
			}
		}
		var f = fallback;
		return f == null ? null : f.apply(level, pos);
	}
}
