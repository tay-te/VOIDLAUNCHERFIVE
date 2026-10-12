package dev.voidmc.mettle.character.draft;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.CharacterState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The cursed cards (docs/DRAFT.md §4): two per released skill, each a strong effect with a real downside. Most are plain
 * attribute modifiers in their perk data (both halves); the ones here need code, and their skills' hooks ask these
 * methods. The numbers live here so the set can be tuned together.
 *
 * <p>The Hex set reaches these coded halves as it reaches the attribute ones (EffectsApplier.hexed): Hex 2 makes each
 * downside 25 % smaller, Hex 4 each upside 15 % stronger ({@link #down}, {@link #up}).
 */
public final class Cursed {
	public static final Identifier GREEDY_PICK = Mettle.id("mining/greedy_pick");
	public static final Identifier BLACK_LUNG = Mettle.id("mining/black_lung");
	public static final Identifier RECKLESS_EDGE = Mettle.id("blades/reckless_edge");
	public static final Identifier GLASS_DANCER = Mettle.id("blades/glass_dancer");
	public static final Identifier BLOODRAGE = Mettle.id("heavy_weapons/bloodrage");
	public static final Identifier IMMOVABLE = Mettle.id("heavy_weapons/immovable");
	public static final Identifier HEAVY_DRAW = Mettle.id("marksmanship/heavy_draw");
	public static final Identifier SNAP_SHOT = Mettle.id("marksmanship/snap_shot");
	public static final Identifier IRON_HIDE = Mettle.id("defense/iron_hide");
	public static final Identifier BASTION = Mettle.id("defense/bastion");
	public static final Identifier FEATHERWEIGHT = Mettle.id("athletics/featherweight");
	public static final Identifier BULLS_BACK = Mettle.id("athletics/bulls_back");
	public static final Identifier WOLFS_HUNGER = Mettle.id("survival/wolfs_hunger");
	public static final Identifier WILD_LUCK = Mettle.id("survival/wild_luck");
	public static final Identifier BRITTLE_EDGE = Mettle.id("smithing/brittle_edge");
	public static final Identifier SLAPDASH = Mettle.id("smithing/slapdash");
	/** Every cursed card (docs/DRAFT.md §4). */
	public static final java.util.List<Identifier> ALL = java.util.List.of(GREEDY_PICK, BLACK_LUNG, RECKLESS_EDGE, GLASS_DANCER, BLOODRAGE, IMMOVABLE,
		HEAVY_DRAW, SNAP_SHOT, IRON_HIDE, BASTION, FEATHERWEIGHT, BULLS_BACK, WOLFS_HUNGER, WILD_LUCK, BRITTLE_EDGE, SLAPDASH);

	/** Greedy Pick: the chance an ore crumbles to stone instead of dropping. */
	public static final float GREEDY_PICK_CRUMBLE = 1f / 8f;
	/** Heavy Draw: arrows hit 35 % harder, bows draw at three quarters speed (a third longer). */
	public static final float HEAVY_DRAW_DAMAGE = 1.35f;
	public static final float HEAVY_DRAW_SPEED = 0.75f;
	/** Snap Shot: bows draw twice as fast, arrows deal 30 % less. */
	public static final float SNAP_SHOT_SPEED = 2f;
	public static final float SNAP_SHOT_DAMAGE = 0.7f;
	/** Wolf's Hunger: hunger drains 40 % faster. */
	public static final float WOLFS_HUNGER_DRAIN = 1.4f;
	/** Brittle Edge: crafted weapons +2 damage, half the durability. */
	public static final double BRITTLE_EDGE_DAMAGE = 2.0;
	public static final double BRITTLE_EDGE_DURABILITY = -0.5;
	/** Slapdash: Smithing XP ×1.6, crafted pieces a quarter less durable. */
	public static final double SLAPDASH_XP = 1.6;
	public static final double SLAPDASH_DURABILITY = -0.25;

	private static final ResourceKey<Skill> SMITHING = ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.SKILL, Mettle.id("smithing"));

	private Cursed() {
	}

	private static boolean has(Player player, Identifier perk) {
		return dev.voidmc.mettle.character.Perks.has(player, perk);
	}

	/** The Hex set's tier for this player (docs/CARDS.md §6.3): 2 softens downsides, 4 strengthens upsides. Both sides. */
	public static int hex(Player player) {
		return dev.voidmc.mettle.character.cards.CardHooks.set(player, "hex");
	}

	/** A multiplier's upside (above 1), 15 % stronger at Hex 4. */
	public static double up(double factor, int hex) {
		return hex >= 4 ? 1 + (factor - 1) * HEX_UP : factor;
	}

	/** A multiplier's downside (its distance from 1, either way), 25 % smaller at Hex 2. */
	public static double down(double factor, int hex) {
		return hex >= 2 ? 1 + (factor - 1) * HEX_DOWN : factor;
	}

	/** Hex 4's upside and Hex 2's downside scales. */
	public static final double HEX_UP = 1.15;
	public static final double HEX_DOWN = 0.75;

	/** Bow draw speed multiplier from cursed cards (both sides: the client animates the draw from it). */
	public static float drawSpeed(Player player) {
		return drawSpeed(has(player, HEAVY_DRAW), has(player, SNAP_SHOT), hex(player));
	}

	public static float drawSpeed(boolean heavyDraw, boolean snapShot) {
		return drawSpeed(heavyDraw, snapShot, 0);
	}

	/** Heavy Draw slows the draw (a downside), Snap Shot quickens it (an upside). */
	public static float drawSpeed(boolean heavyDraw, boolean snapShot, int hex) {
		return (float) ((heavyDraw ? down(HEAVY_DRAW_SPEED, hex) : 1) * (snapShot ? up(SNAP_SHOT_SPEED, hex) : 1));
	}

	/** Arrow damage multiplier from cursed cards. */
	public static float arrowDamage(Player player) {
		return arrowDamage(has(player, HEAVY_DRAW), has(player, SNAP_SHOT), hex(player));
	}

	public static float arrowDamage(boolean heavyDraw, boolean snapShot) {
		return arrowDamage(heavyDraw, snapShot, 0);
	}

	/** Heavy Draw's harder arrows are its upside, Snap Shot's weaker ones its downside. */
	public static float arrowDamage(boolean heavyDraw, boolean snapShot, int hex) {
		return (float) ((heavyDraw ? up(HEAVY_DRAW_DAMAGE, hex) : 1) * (snapShot ? down(SNAP_SHOT_DAMAGE, hex) : 1));
	}

	/** Hunger drain multiplier (Wolf's Hunger: a downside). */
	public static float hunger(Player player) {
		return has(player, WOLFS_HUNGER) ? wolfsHunger(hex(player)) : 1f;
	}

	/** Wolf's Hunger's drain for a Hex tier. */
	public static float wolfsHunger(int hex) {
		return (float) down(WOLFS_HUNGER_DRAIN, hex);
	}

	/** Greedy Pick's chance an ore crumbles (a downside: a quarter less likely at Hex 2). */
	public static float crumbleChance(Player player) {
		return crumbleChance(hex(player));
	}

	public static float crumbleChance(int hex) {
		return hex >= 2 ? (float) (GREEDY_PICK_CRUMBLE * HEX_DOWN) : GREEDY_PICK_CRUMBLE;
	}

	/** Slapdash's Smithing XP multiplier for a Hex tier (an upside). */
	public static double slapdashXp(int hex) {
		return up(SLAPDASH_XP, hex);
	}

	/** Brittle Edge's damage on a crafted weapon (the upside), for the Hex tier its smith had. */
	public static double brittleEdgeDamage(int hex) {
		return hex >= 4 ? BRITTLE_EDGE_DAMAGE * HEX_UP : BRITTLE_EDGE_DAMAGE;
	}

	/** Brittle Edge's lost durability (the downside), for the Hex tier its smith had. */
	public static double brittleEdgeDurability(int hex) {
		return hex >= 2 ? BRITTLE_EDGE_DURABILITY * HEX_DOWN : BRITTLE_EDGE_DURABILITY;
	}

	/** Slapdash's lost durability (the downside), for the Hex tier its smith had. */
	public static double slapdashDurability(int hex) {
		return hex >= 2 ? SLAPDASH_DURABILITY * HEX_DOWN : SLAPDASH_DURABILITY;
	}

	/** XP multiplier from cursed cards (Slapdash: Smithing), before Hex (Progress has no player here; see {@link #cardXp}). */
	public static double xp(CharacterState state, ResourceKey<Skill> skill) {
		return skill.equals(SMITHING) && state.rank(ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.PERK, SLAPDASH)) > 0 ? SLAPDASH_XP : 1.0;
	}

	/**
	 * Slapdash held only through a slotted card (the record's perks don't show it): its XP multiplier, else 1; with Hex
	 * 4 also what makes the record's Slapdash 15 % stronger.
	 */
	public static double cardXp(Player player, CharacterState state, ResourceKey<Skill> skill) {
		if (!skill.equals(SMITHING)) {
			return 1.0;
		}
		ResourceKey<dev.voidmc.mettle.api.Perk> key = ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.PERK, SLAPDASH);
		double hexed = slapdashXp(hex(player));
		if (state.rank(key) > 0) {
			return hexed / SLAPDASH_XP;
		}
		return dev.voidmc.mettle.core.CardStore.get(player).activeRank(key) > 0 ? hexed : 1.0;
	}

	/** Greedy Pick: what an ore crumbles to (the stone it sat in). */
	public static Item crumble(BlockState ore) {
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ore.getBlock()).getPath();
		return path.startsWith("deepslate_") ? Items.COBBLED_DEEPSLATE : path.startsWith("nether_") ? Items.NETHERRACK : Items.COBBLESTONE;
	}
}
