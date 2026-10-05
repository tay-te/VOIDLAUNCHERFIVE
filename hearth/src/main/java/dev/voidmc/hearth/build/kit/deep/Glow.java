package dev.voidmc.hearth.build.kit.deep;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The light and the living things of a realm, as its deep town uses them (LIVING_WORLD.md §8.2: "lit by glowcaps and
 * amber"). Each realm's character gives the town its own glow: glowcaps and shroomlight in the wilds, glow berries and
 * azalea in the lush realms, amber and honey in the amber hollows, prismite and amethyst in the crystal realms, sea
 * lanterns and pickles by the meres and tide pools, magma and blackstone in the ember and brimstone realms, glowing
 * roots under the forests, bone and amber in the fossil sands.
 *
 * <p><b>Light the rule counts is always vanilla</b> ({@link #lamp}: shroomlight, sea lanterns, froglights; plus the
 * lanterns, candles and glow berries the archetypes hang): Expanse's glowing blocks fall back to vanilla stand-ins where
 * Expanse is absent (the tests, the previews), and those stand-ins glow differently (amber 10 in the game against its
 * stand-in shroomlight's 15, glowcaps 10 against a brown mushroom's 1), so a room lit by amber could pass the check
 * and be dark in the game, or the reverse. The Expanse glow is the accent ({@link #accent}, {@link #plant}): set in the
 * stone, grown in the planters, never counted on.
 */
public final class Glow {
	/** What the town lights with and grows, for one realm character. */
	public final String character;
	/** A vanilla glowing block for lamp heads and light set in stone. */
	public final Block lamp;
	/** The realm's own glow set in the masonry as the accent (an Expanse block, with a vanilla stand-in). */
	public final Block accent;
	/** A second accent: a non-glowing mineral of the realm (amethyst, dripstone, bone, honeycomb...). */
	public final Block mineral;
	/** What planters are filled with. */
	public final Block soil;
	/** Plants for planters and beds, the realm's own first. */
	public final Block[] plants;

	private Glow(String character, Block lamp, Block accent, Block mineral, Block soil, Block... plants) {
		this.character = character;
		this.lamp = lamp;
		this.accent = accent;
		this.mineral = mineral;
		this.soil = soil;
		this.plants = plants;
	}

	public static Glow of(Draft d) {
		return of(DeepSite.of(d).character());
	}

	public static Glow of(String character) {
		Block glowcap = States.block("expanse:glowcap", Blocks.BROWN_MUSHROOM);
		Block amber = States.block("expanse:amber", Blocks.SHROOMLIGHT);
		return switch (character) {
			case "lush" -> new Glow(character, Blocks.VERDANT_FROGLIGHT, Blocks.SHROOMLIGHT, Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, Blocks.AZALEA,
				Blocks.FLOWERING_AZALEA, glowcap, Blocks.FERN, Blocks.MOSS_CARPET);
			case "dripstone" -> new Glow(character, Blocks.OCHRE_FROGLIGHT, amber, Blocks.DRIPSTONE_BLOCK, Blocks.ROOTED_DIRT, glowcap,
				Blocks.BROWN_MUSHROOM, Blocks.DEAD_BUSH);
			case "crystal" -> new Glow(character, Blocks.PEARLESCENT_FROGLIGHT, States.block("expanse:prismite_block", Blocks.AMETHYST_BLOCK),
				Blocks.AMETHYST_BLOCK, Blocks.MOSS_BLOCK, glowcap, Blocks.SMALL_AMETHYST_BUD, Blocks.BROWN_MUSHROOM);
			case "ember", "brimstone" -> new Glow(character, Blocks.SHROOMLIGHT, Blocks.MAGMA_BLOCK, Blocks.BLACKSTONE, Blocks.SOUL_SOIL, Blocks.CRIMSON_ROOTS,
				Blocks.CRIMSON_FUNGUS, Blocks.RED_MUSHROOM);
			case "mere", "tidal" -> new Glow(character, Blocks.SEA_LANTERN, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE, Blocks.MOSS_BLOCK, glowcap, Blocks.FERN,
				Blocks.BROWN_MUSHROOM);
			case "frozen" -> new Glow(character, Blocks.SEA_LANTERN, Blocks.SEA_LANTERN, Blocks.PACKED_ICE, Blocks.MOSS_BLOCK, glowcap, Blocks.BROWN_MUSHROOM,
				Blocks.FERN);
			case "roots" -> new Glow(character, Blocks.SHROOMLIGHT, amber, Blocks.ROOTED_DIRT, Blocks.ROOTED_DIRT, glowcap, Blocks.FERN, Blocks.AZALEA,
				Blocks.BROWN_MUSHROOM);
			case "fossil" -> new Glow(character, Blocks.OCHRE_FROGLIGHT, amber, Blocks.BONE_BLOCK, Blocks.COARSE_DIRT, Blocks.DEAD_BUSH, glowcap,
				Blocks.BROWN_MUSHROOM);
			case "amber" -> new Glow(character, Blocks.SHROOMLIGHT, amber, Blocks.HONEYCOMB_BLOCK, Blocks.ROOTED_DIRT, glowcap, Blocks.BROWN_MUSHROOM, Blocks.FERN);
			default -> new Glow("wilds", Blocks.SHROOMLIGHT, Blocks.SHROOMLIGHT, Blocks.MUSHROOM_STEM, Blocks.MOSS_BLOCK, glowcap, glowcap,
				Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM);
		};
	}

	/** A plant for a spot, the realm's own most often. */
	public Block plant(long seed, int x, int y, int z) {
		double u = Noise.unit(seed ^ 0x6105, x, y, z);
		int i = u < 0.45 ? 0 : 1 + (int) ((u - 0.45) / 0.55 * (this.plants.length - 1));
		return this.plants[Math.min(this.plants.length - 1, i)];
	}

	/** Whether the realm's soil carries plants (soul soil and coarse dirt carry only their own). */
	public static boolean grows(Block soil, Block plant) {
		if (plant == Blocks.CRIMSON_ROOTS || plant == Blocks.CRIMSON_FUNGUS) {
			return soil == Blocks.SOUL_SOIL || soil == Blocks.CRIMSON_NYLIUM;
		}
		if (plant == Blocks.SMALL_AMETHYST_BUD) {
			return true;
		}
		return soil != Blocks.SOUL_SOIL;
	}

	/** The state a plant stands in (amethyst buds grow upward). */
	public static BlockState plantState(Block plant) {
		BlockState s = plant.defaultBlockState();
		if (s.hasProperty(BlockStateProperties.FACING)) {
			s = s.setValue(BlockStateProperties.FACING, net.minecraft.core.Direction.UP);
		}
		return s;
	}

	/** Glow berries on a vine hanging {@code length} down from under (x, y + 1, z), berries on most of it. */
	public static void vine(Draft d, int x, int y, int z, int length) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < length; k++) {
				boolean tip = k == length - 1;
				boolean berries = Noise.unit(d.seed() ^ 0xBE11, x, y - k, z) < 0.7 || tip;
				BlockState v = (tip ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().trySetValue(BlockStateProperties.BERRIES, berries);
				d.set(x, y - k, z, v);
			}
		}
	}
}
