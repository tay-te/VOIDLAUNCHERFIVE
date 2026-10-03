package dev.voidmc.hearth.sim.gen;

import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jspecify.annotations.Nullable;

/**
 * Keeps trees, boulders, pillars, lakes and the like off ground a settlement's history (or a live settlement) planned
 * to build on, so a house is never cut through by an oak. Only surface decoration steps are guarded, and only at or
 * above the natural ground: ores, caves and everything underground generate as always.
 *
 * <p>{@code FeaturePlacerMixin} calls {@link #enter} as each top-level placed feature starts (so features nested inside
 * selectors inherit its step) and {@link #blocks} before every feature placement.
 */
public final class WorldgenGuard {
	/** How far below the probe's ground a feature origin still counts as on the surface. */
	private static final int SURFACE_DEPTH = 4;
	private static final ThreadLocal<@Nullable SimWorld> ACTIVE = new ThreadLocal<>();
	private static volatile @Nullable Map<PlacedFeature, Integer> steps;
	private static volatile @Nullable ServerLevel stepsLevel;

	private WorldgenGuard() {
	}

	private static boolean guarded(int step) {
		return step == GenerationStep.Decoration.LAKES.ordinal() || step == GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal()
			|| step == GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal() || step == GenerationStep.Decoration.FLUID_SPRINGS.ordinal()
			|| step == GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
	}

	/** A top-level placed feature begins in this thread. */
	public static void enter(WorldGenLevel level, PlacedFeature feature) {
		ACTIVE.set(null);
		if (!(level instanceof WorldGenRegion region)) {
			return;
		}
		SimWorld world = SimServer.world(region.getLevel());
		if (world == null || !world.atlas().enabled()) {
			return;
		}
		Integer step = steps(region.getLevel()).get(feature);
		if (step != null && guarded(step)) {
			ACTIVE.set(world);
		}
	}

	public static void exit() {
		ACTIVE.remove();
	}

	/** Whether a feature about to be placed at {@code pos} would land on planned ground. */
	public static boolean blocks(BlockPos pos) {
		SimWorld world = ACTIVE.get();
		if (world == null || !world.isPlanned(pos.getX(), pos.getZ())) {
			return false;
		}
		if (pos.getY() < world.probe().groundY(pos.getX(), pos.getZ()) - SURFACE_DEPTH) {
			return false;
		}
		SimServer s = SimServer.current();
		if (s != null) {
			s.stats().guardSkips.increment();
		}
		return true;
	}

	/** Which decoration step each placed feature runs in, read once from the biomes (after biome modifications). */
	private static Map<PlacedFeature, Integer> steps(ServerLevel level) {
		Map<PlacedFeature, Integer> m = steps;
		if (m == null || stepsLevel != level) {
			synchronized (WorldgenGuard.class) {
				m = steps;
				if (m == null || stepsLevel != level) {
					m = new IdentityHashMap<>();
					for (Holder<Biome> biome : level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()) {
						List<HolderSet<PlacedFeature>> features = biome.value().getGenerationSettings().features();
						for (int i = 0; i < features.size(); i++) {
							for (Holder<PlacedFeature> f : features.get(i)) {
								m.putIfAbsent(f.value(), i);
							}
						}
					}
					steps = m;
					stepsLevel = level;
				}
			}
		}
		return m;
	}

	/** Forgets the step table (a new server). */
	public static void reset() {
		steps = null;
		stepsLevel = null;
	}
}
