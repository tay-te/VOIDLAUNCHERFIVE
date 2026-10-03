package dev.voidmc.hearth.build.style;

import java.util.EnumMap;
import java.util.Map;

/** Vanilla materials for any role a style leaves out, so a minimal data-pack style still builds. */
final class StyleDefaults {
	private static final Map<Role, Palette> DEFAULTS = new EnumMap<>(Role.class);

	static {
		put(Role.FOUNDATION, "minecraft:cobblestone");
		put(Role.PLINTH, "minecraft:stone_bricks");
		put(Role.WALL, "minecraft:white_terracotta");
		put(Role.WALL_ACCENT, "minecraft:stone_bricks");
		put(Role.FRAME, "wood:minecraft:oak");
		put(Role.BEAM, "wood:minecraft:oak");
		put(Role.FLOOR, "wood:minecraft:spruce");
		put(Role.CEILING, "wood:minecraft:spruce");
		put(Role.ROOF, "wood:minecraft:spruce");
		put(Role.ROOF_TRIM, "wood:minecraft:dark_oak");
		put(Role.GABLE, "wood:minecraft:spruce");
		put(Role.WINDOW, "wood:minecraft:spruce");
		put(Role.GLASS, "minecraft:glass_pane");
		put(Role.DOOR, "wood:minecraft:spruce");
		put(Role.SHUTTER, "wood:minecraft:spruce");
		put(Role.FENCE, "wood:minecraft:oak");
		put(Role.RAILING, "wood:minecraft:spruce");
		put(Role.PATH, "minecraft:dirt_path");
		put(Role.PAVING, "minecraft:stone_bricks");
		put(Role.LIGHT, "minecraft:lantern");
		put(Role.CHIMNEY, "minecraft:bricks");
		put(Role.FURNITURE, "wood:minecraft:spruce");
		put(Role.CLOTH, "dye:red");
		put(Role.ACCENT, "minecraft:chiseled_stone_bricks");
		put(Role.GARDEN, "minecraft:poppy");
		put(Role.CROPS, "minecraft:wheat");
		put(Role.HEDGE, "minecraft:oak_leaves");
		put(Role.TREE_LOG, "minecraft:oak_log");
		put(Role.TREE_LEAVES, "minecraft:oak_leaves");
		put(Role.GROUND, "minecraft:grass_block");
		put(Role.FILL, "minecraft:dirt");
		put(Role.STILT, "wood:minecraft:spruce");
		put(Role.THATCH, "minecraft:hay_block");
	}

	private StyleDefaults() {
	}

	private static void put(Role role, String spec) {
		DEFAULTS.put(role, Palette.of(Material.of(spec)));
	}

	static Palette palette(Role role) {
		return DEFAULTS.get(role);
	}
}
