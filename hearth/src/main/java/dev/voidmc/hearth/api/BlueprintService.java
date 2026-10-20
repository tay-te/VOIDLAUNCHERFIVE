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

	/**
	 * Whether {@link #blueprint} would answer this spec at once (from the cache); if not, starts making it in the
	 * background so that it soon will. For callers on the server thread that can wait a few ticks rather than hold the
	 * tick while a large building is drawn and compiled (a castle takes up to a second). A service that makes nothing in
	 * the background answers true (callers then make it inline, as before). Never throws: an unknown archetype answers
	 * true, and {@link #blueprint} then throws as it always has.
	 */
	default boolean prepare(PlotSpec spec, TerrainProbe terrain) {
		return true;
	}

	/**
	 * The roles {@link #dressColours} answers for, in order: what a building's walls and roofs are most often made of
	 * (the wall, its frame, gable fill, plinth and masonry; the roof covering, its trim and sheet roofs).
	 */
	java.util.List<String> DRESS_ROLES = java.util.List.of("wall", "frame", "gable", "plinth", "masonry", "roof", "roof_trim", "sheet");

	/**
	 * The map colours (0xRRGGBB) of the materials a building on this spec is dressed in, one per {@link #DRESS_ROLES}
	 * entry (0 where it has none), without drawing it: the style's variant, region, local materials and tier as the
	 * blueprint would choose them (a thatched roof answers its thatch). For far views that know a building's shape
	 * from another of its kind and want its own colours. Cheap next to {@link #blueprint} (it reads the ground under the
	 * plot and the style, nothing else); null where the service does not say (the default) or the archetype is unknown.
	 */
	default int @Nullable [] dressColours(PlotSpec spec, TerrainProbe terrain) {
		return null;
	}

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
