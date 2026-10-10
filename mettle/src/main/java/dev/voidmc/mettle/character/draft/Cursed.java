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

	/** Bow draw speed multiplier from cursed cards (both sides: the client animates the draw from it). */
	public static float drawSpeed(Player player) {
		return drawSpeed(has(player, HEAVY_DRAW), has(player, SNAP_SHOT));
	}

	public static float drawSpeed(boolean heavyDraw, boolean snapShot) {
		return (heavyDraw ? HEAVY_DRAW_SPEED : 1f) * (snapShot ? SNAP_SHOT_SPEED : 1f);
	}

	/** Arrow damage multiplier from cursed cards. */
	public static float arrowDamage(Player player) {
		return arrowDamage(has(player, HEAVY_DRAW), has(player, SNAP_SHOT));
	}

	public static float arrowDamage(boolean heavyDraw, boolean snapShot) {
		return (heavyDraw ? HEAVY_DRAW_DAMAGE : 1f) * (snapShot ? SNAP_SHOT_DAMAGE : 1f);
	}

	/** Hunger drain multiplier (Wolf's Hunger). */
	public static float hunger(Player player) {
		return has(player, WOLFS_HUNGER) ? WOLFS_HUNGER_DRAIN : 1f;
	}

	/** XP multiplier from cursed cards (Slapdash: Smithing). */
	public static double xp(CharacterState state, ResourceKey<Skill> skill) {
		return skill.equals(SMITHING) && state.rank(ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.PERK, SLAPDASH)) > 0 ? SLAPDASH_XP : 1.0;
	}

	/** Greedy Pick: what an ore crumbles to (the stone it sat in). */
	public static Item crumble(BlockState ore) {
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ore.getBlock()).getPath();
		return path.startsWith("deepslate_") ? Items.COBBLED_DEEPSLATE : path.startsWith("nether_") ? Items.NETHERRACK : Items.COBBLESTONE;
	}
}
