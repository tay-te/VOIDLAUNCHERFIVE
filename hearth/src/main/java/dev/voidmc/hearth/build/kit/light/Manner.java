package dev.voidmc.hearth.build.kit.light;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * How a culture lights its buildings: whether its homes burn torches or lanterns on the walls, the colours of its
 * candles, how its halls hang their lights, what stands in its yards at night (a lamp post, a brazier, a stone lantern, a
 * tiki torch) and what it hides under a rug. Every surface culture hangs the iron lantern on an iron chain: its flame is
 * a candle's (a copper lantern burns green, a soul lantern blue, and the surface has no coloured light).
 *
 * <table>
 *   <caption>the manners</caption>
 *   <tr><th>culture</th><th>lantern</th><th>walls</th><th>candles</th><th>yard</th><th>hall chandelier</th></tr>
 *   <tr><td>temperate, alpine, moorland</td><td>iron</td><td>lantern on a bracket</td><td>tallow, red, white</td><td>lamp post</td><td>wheel of lanterns</td></tr>
 *   <tr><td>northern, tundra</td><td>iron</td><td>torches</td><td>tallow, brown</td><td>brazier</td><td>wheel of candles</td></tr>
 *   <tr><td>maritime</td><td>iron</td><td>lantern on a bracket</td><td>white, blue</td><td>lamp post</td><td>wheel of lanterns</td></tr>
 *   <tr><td>mediterranean</td><td>iron</td><td>lantern on a bracket</td><td>white, yellow</td><td>lamp post</td><td>wheel of lanterns</td></tr>
 *   <tr><td>desert, mesa</td><td>iron</td><td>lantern on a bracket</td><td>orange, yellow, red</td><td>brazier</td><td>wheel of lanterns</td></tr>
 *   <tr><td>steppe</td><td>iron</td><td>lantern on a bracket</td><td>yellow, brown</td><td>brazier</td><td>wheel of lanterns</td></tr>
 *   <tr><td>karst, wisteria</td><td>iron</td><td>lantern on a bracket</td><td>red, white / white, red, brown</td><td>stone lantern</td><td>lanterns in a row</td></tr>
 *   <tr><td>bayou</td><td>iron</td><td>lantern on a bracket</td><td>tallow, yellow</td><td>lamp post</td><td>wheel of lanterns</td></tr>
 *   <tr><td>tropical</td><td>iron</td><td>torches</td><td>yellow, orange, brown</td><td>tiki torch</td><td>wheel of lanterns</td></tr>
 * </table>
 *
 * A deep settlement's style may name another lantern in its {@code light} role (a soul lantern, an Expanse lamp); copper
 * there is always waxed (a blueprint must not weather out of true: repairs compare blocks).
 *
 * <p><b>The surface burns only what folk burn</b> (lanterns, candles, torches, fires; glowstone or shroomlight hidden
 * under a rug): Expanse's glowing blocks (amber, glowcaps, prismite, lumenwood) belong to the realms underground, so a
 * surface style naming an Expanse lamp gets the iron lantern. Only the deep settlements ({@code deep/...} archetypes and
 * the {@code deep} styles) light with the realm's glow: amber hidden in their floors.
 */
public final class Manner {
	public enum Yard {
		POST, BRAZIER, STONE_LANTERN, TIKI
	}

	public final Culture culture;
	/** The lantern hung and stood everywhere. */
	public final Block lantern;
	/** The chain lanterns hang from. */
	public final Block chain;
	/** Walls of homes carry torches rather than lanterns. */
	public final boolean torches;
	/** The torch, where torches are used. */
	public final Block torch;
	/** Candle colours, plainest first (tallow for a poor house, colours as the house rises). */
	public final Block[] candles;
	public final Yard yard;
	/** Halls hang a wheel of candles rather than of lanterns. */
	public final boolean candleWheel;
	/** Halls hang lanterns in rows rather than wheels (the eastern cultures). */
	public final boolean rows;
	/** What is hidden under a rug or set into a floor. */
	public final Block glow;
	/** An underground settlement (the realms may glow; the surface never does). */
	public final boolean deep;
	private final Draft d;

	public Manner(Draft d) {
		this.d = d;
		Culture c = d.style().culture() == null ? Culture.TEMPERATE : d.style().culture();
		this.culture = c;
		this.deep = deep(d);
		// the surface burns a candle's warm flame: the iron lantern everywhere (a copper lantern's flame is green, a soul
		// lantern's blue); a deep settlement may hang what its style names (a soul lantern, an Expanse lamp)
		Block styleLantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		if (!this.deep || !styleLantern.defaultBlockState().hasProperty(BlockStateProperties.HANGING)) {
			styleLantern = Blocks.LANTERN;
		}
		this.lantern = styleLantern;
		this.chain = Blocks.IRON_CHAIN;
		this.torches = switch (c) {
			case NORTHERN, TUNDRA, TROPICAL -> true;
			default -> false;
		};
		this.torch = Blocks.TORCH;
		this.candles = switch (c) {
			case TEMPERATE, ALPINE -> candles(null, DyeColor.RED, DyeColor.WHITE);
			case MOORLAND -> candles(null, DyeColor.WHITE, DyeColor.PURPLE);
			case NORTHERN, TUNDRA -> candles(null, DyeColor.BROWN, DyeColor.WHITE);
			case MARITIME -> candles(DyeColor.WHITE, DyeColor.BLUE, DyeColor.LIGHT_BLUE);
			case MEDITERRANEAN -> candles(DyeColor.WHITE, DyeColor.YELLOW, DyeColor.BLUE);
			case DESERT -> candles(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.RED);
			case MESA -> candles(DyeColor.ORANGE, DyeColor.RED, DyeColor.WHITE);
			case STEPPE -> candles(DyeColor.YELLOW, DyeColor.BROWN, DyeColor.ORANGE);
			case KARST -> candles(DyeColor.RED, DyeColor.WHITE, DyeColor.YELLOW);
			case WISTERIA -> candles(DyeColor.WHITE, DyeColor.RED, DyeColor.BROWN);
			case BAYOU -> candles(null, DyeColor.YELLOW, DyeColor.GREEN);
			case TROPICAL -> candles(DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.BROWN);
		};
		this.yard = switch (c) {
			case NORTHERN, TUNDRA, DESERT, MESA, STEPPE -> Yard.BRAZIER;
			case KARST, WISTERIA -> Yard.STONE_LANTERN;
			case TROPICAL -> Yard.TIKI;
			default -> Yard.POST;
		};
		this.candleWheel = c == Culture.NORTHERN || c == Culture.TUNDRA;
		this.rows = c == Culture.KARST || c == Culture.WISTERIA;
		this.glow = this.deep ? States.block("expanse:amber", Blocks.SHROOMLIGHT) : switch (c) {
			case DESERT, MESA, STEPPE, MEDITERRANEAN, TROPICAL, BAYOU, KARST, WISTERIA -> Blocks.SHROOMLIGHT;
			default -> Blocks.GLOWSTONE;
		};
	}

	/** Whether a draft is an underground settlement's: a {@code deep/} archetype, or a {@code deep} style. */
	public static boolean deep(Draft d) {
		return d.spec().archetype().getPath().startsWith("deep/") || d.style().id().getPath().startsWith("deep");
	}

	private static Block[] candles(DyeColor plain, DyeColor a, DyeColor b) {
		Block first = plain == null ? Blocks.CANDLE : Blocks.DYED_CANDLE.pick(plain);
		return new Block[]{first, Blocks.DYED_CANDLE.pick(a), Blocks.DYED_CANDLE.pick(b)};
	}

	/**
	 * The candle for a spot: the plain one in a poor house (tier 0–1), colours mixed in by position as the house rises.
	 */
	public Block candle(int x, int y, int z) {
		if (this.d.tier() <= 1) {
			return this.candles[0];
		}
		double u = dev.voidmc.hearth.build.draft.Noise.unit(this.d.seed() ^ 0xCA4D1E, x, y, z);
		double plain = this.d.tier() >= 3 ? 0.25 : 0.5;
		return u < plain ? this.candles[0] : u < plain + (1 - plain) / 2 ? this.candles[1] : this.candles[2];
	}

	/** Candles on one spot: more in finer houses. */
	public int candleCount(int x, int y, int z, int need) {
		int base = Math.clamp(1 + this.d.tier() / 2 + (int) (dev.voidmc.hearth.build.draft.Noise.unit(this.d.seed() ^ 0x5EC0, x, y, z) * 2), 1, 4);
		return Math.clamp(Math.max(base, need), 1, 4);
	}

	/** Wood for brackets and wheel arms (the frame's fence, or the furniture's). */
	public Mat bracketWood() {
		Mat frame = this.d.mat(Role.FRAME);
		return frame.has(Form.FENCE) ? frame : this.d.mat(Role.FURNITURE);
	}

	/** Stone for brazier stands and stone lanterns. */
	public Mat stone() {
		Mat f = this.d.mat(Role.FOUNDATION);
		return f.has(Form.WALL) ? f : this.d.mat(Role.PLINTH);
	}
}
