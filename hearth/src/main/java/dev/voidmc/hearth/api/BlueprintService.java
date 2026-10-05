package dev.voidmc.hearth.api;

import java.util.Collection;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The build module: archetypes, styles, and blueprints made from plot specs. Registered in {@link HearthServices}. */
public interface BlueprintService {
	/**
	 * The blueprint for a spec on a terrain. Deterministic (same spec and terrain, same blueprint, on any thread, in any
	 * session) and cached, so callers ask freely instead of holding on to blueprints.
	 *
	 * @throws IllegalArgumentException if the archetype is unknown
	 */
	Blueprint blueprint(PlotSpec spec, TerrainProbe terrain);

	@Nullable ArchetypeInfo archetype(Identifier id);

	Collection<ArchetypeInfo> archetypes();

	/** The style id a settlement of this culture builds in (variants within it are the archetype's business). */
	Identifier style(Culture culture);

	/**
	 * The goods one block of this state costs to place, as the blueprints' material bill reckons it (empty for free blocks:
	 * air, plants, natural ground), so repairs cost what building did; null where the build module does not say (callers
	 * keep their own table).
	 */
	default Goods @Nullable [] goodsOf(net.minecraft.world.level.block.state.BlockState state) {
		return null;
	}

	/**
	 * What a built plot puts up for a special day (docs/buildings/age.md): {@code kind} is {@code market} (awnings, wares
	 * before the front), {@code festival} (garlands, banners, lanterns, pots), {@code wedding}, {@code mourning} or
	 * {@code winter} (snow on the ledges, cold cultures only). World positions in placing order, supports first, only
	 * where the blueprint places air or nothing, never in a doorway; deterministic in (spec, kind, day). Place them in
	 * order and take them down after (each position back to air if it still holds the step's block). Empty where the
	 * build module does not dress (the default) or the kind does not apply.
	 */
	default java.util.List<Dressing> dressing(PlotSpec spec, TerrainProbe terrain, String kind, long day) {
		return java.util.List.of();
	}

	/** One block of a dressing; {@code tag} is block-entity data (a banner's pattern) or null. */
	record Dressing(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
		net.minecraft.nbt.@Nullable CompoundTag tag) {
	}
}
