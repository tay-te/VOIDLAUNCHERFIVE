package dev.voidmc.hearth.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Material;
import dev.voidmc.hearth.build.style.Palette;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

/** Styles load for every culture and every role can be cut in the forms the kit asks of it; materials resolve. */
class StylesTest {
	static {
		BuildTestSupport.init();
	}

	/** The forms the kit cuts each role in (at least one material of the role must have each). */
	private static final Map<Role, Form[]> NEEDS = Map.ofEntries(
		Map.entry(Role.ROOF, new Form[]{Form.STAIRS, Form.SLAB, Form.FULL}),
		Map.entry(Role.FRAME, new Form[]{Form.LOG}),
		Map.entry(Role.BEAM, new Form[]{Form.LOG}),
		Map.entry(Role.DOOR, new Form[]{Form.DOOR}),
		Map.entry(Role.SHUTTER, new Form[]{Form.TRAPDOOR}),
		Map.entry(Role.GLASS, new Form[]{Form.PANE}),
		Map.entry(Role.FURNITURE, new Form[]{Form.STAIRS, Form.SLAB, Form.FENCE, Form.PRESSURE_PLATE, Form.TRAPDOOR}),
		Map.entry(Role.RAILING, new Form[]{Form.FENCE}),
		Map.entry(Role.CLOTH, new Form[]{Form.BED, Form.CARPET}),
		Map.entry(Role.FLOOR, new Form[]{Form.FULL, Form.SLAB}),
		Map.entry(Role.STILT, new Form[]{Form.LOG}),
		Map.entry(Role.CHIMNEY, new Form[]{Form.FULL}),
		Map.entry(Role.WALL, new Form[]{Form.FULL}),
		Map.entry(Role.FOUNDATION, new Form[]{Form.FULL}),
		Map.entry(Role.WINDOW, new Form[]{Form.STAIRS})
	);

	@Test
	void everyCultureHasItsStyle() {
		StyleRegistry styles = StyleRegistry.builtin();
		for (Culture c : Culture.values()) {
			Style s = styles.get(StyleRegistry.styleId(c));
			assertNotNull(s, "no style for " + c);
			assertEquals(c, s.culture());
		}
	}

	@Test
	void everyRoleCutsTheFormsTheKitNeeds() {
		StyleRegistry styles = StyleRegistry.builtin();
		List<String> missing = new ArrayList<>();
		for (Culture c : Culture.values()) {
			Style s = styles.get(StyleRegistry.styleId(c));
			for (int v = -1; v < s.variants().size(); v++) {
				boolean thatch = s.habits(v).roof().thatch();
				String where = c.getSerializedName() + (v >= 0 ? " (" + s.variants().get(v).name() + ")" : "");
				for (int tier = 0; tier <= 4; tier++) {
					for (var e : NEEDS.entrySet()) {
						Role role = thatch && e.getKey() == Role.ROOF ? Role.THATCH : e.getKey();
						Palette p = s.palette(role, tier, v);
						for (Form f : e.getValue()) {
							if (!p.has(f)) {
								missing.add(where + " tier " + tier + " " + role.key() + ":" + f);
							}
						}
					}
					// every role resolves to some block at all (a misspelt id resolves to nothing)
					for (Role role : Role.values()) {
						Palette p = s.palette(role, tier, v);
						boolean any = false;
						for (Form f : Form.values()) {
							any |= p.has(f);
						}
						if (!any) {
							missing.add(where + " tier " + tier + " " + role.key() + " resolves to nothing");
						}
					}
				}
			}
		}
		assertTrue(missing.isEmpty(), "missing forms:\n  " + String.join("\n  ", missing));
	}

	@Test
	void materialsResolveFamiliesAndFallBack() {
		Material spruce = Material.of("wood:minecraft:spruce");
		assertSame(Blocks.SPRUCE_PLANKS, spruce.block(Form.FULL));
		assertSame(Blocks.SPRUCE_STAIRS, spruce.block(Form.STAIRS));
		assertSame(Blocks.SPRUCE_LOG, spruce.block(Form.LOG));
		assertSame(Blocks.STRIPPED_SPRUCE_LOG, spruce.block(Form.STRIPPED_LOG));
		assertSame(Blocks.SPRUCE_TRAPDOOR, spruce.block(Form.TRAPDOOR));
		Material bricks = Material.of("minecraft:stone_bricks");
		assertSame(Blocks.STONE_BRICK_STAIRS, bricks.block(Form.STAIRS));
		assertSame(Blocks.STONE_BRICK_WALL, bricks.block(Form.WALL));
		Material tiles = Material.of("minecraft:deepslate_tiles");
		assertSame(Blocks.DEEPSLATE_TILE_SLAB, tiles.block(Form.SLAB));
		Material quartz = Material.of("minecraft:quartz_block");
		assertSame(Blocks.QUARTZ_STAIRS, quartz.block(Form.STAIRS));
		// an Expanse wood falls back to its vanilla stand-in when Expanse is not installed (as in this test)
		Material wisteria = Material.of("wood:expanse:wisteria|wood:minecraft:cherry");
		assertSame(Blocks.CHERRY_PLANKS, wisteria.block(Form.FULL));
		assertTrue(!wisteria.primary());
		Material red = Material.of("dye:red");
		assertSame(Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED), red.block(Form.BED));
		assertSame(Blocks.WOOL_STAIRS.pick(net.minecraft.world.item.DyeColor.RED), red.block(Form.STAIRS));
		assertTrue(!Material.of("minecraft:terracotta").has(Form.STAIRS));
	}

	@Test
	void palettesMixByWeightAndChooseOnePerBuilding() {
		Palette p = new Palette(List.of(new Palette.Entry(Material.of("minecraft:cobblestone"), 3, 0, 0, 0, 0),
			new Palette.Entry(Material.of("minecraft:mossy_cobblestone"), 1, 0, 0, 0, 0)));
		int cobble = 0;
		int mossy = 0;
		for (int x = 0; x < 40; x++) {
			for (int z = 0; z < 40; z++) {
				var b = p.pick(Form.FULL, 99L, x, 0, z, 0, 0, 0, 0);
				if (b == Blocks.COBBLESTONE) {
					cobble++;
				} else if (b == Blocks.MOSSY_COBBLESTONE) {
					mossy++;
				}
			}
		}
		assertEquals(1600, cobble + mossy);
		assertTrue(cobble > 2 * mossy && mossy > 150, "a 3:1 palette mixed " + cobble + ":" + mossy);
		java.util.Set<Object> chosen = new java.util.HashSet<>();
		for (long seed = 0; seed < 40; seed++) {
			chosen.add(p.chooseOne(seed).main().block(Form.FULL));
		}
		assertEquals(2, chosen.size(), "per-building choice never picked one of the materials");
	}
}
