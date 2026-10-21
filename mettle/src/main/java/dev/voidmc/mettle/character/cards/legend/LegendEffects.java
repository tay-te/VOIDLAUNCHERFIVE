package dev.voidmc.mettle.character.cards.legend;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * What the sixteen drawn Mythics of build feel do (docs/BUILD_FEEL.md §5.4), each only while its card is slotted
 * ({@code Mythics.active}). Server side; the client parts are in {@code client.legend.LegendClient}. The effects live by
 * kind: {@link LegendCombat} (Blades, Heavy Weapons, Marksmanship, the Iron Vow), {@link LegendSenses} (the Stonesong,
 * the Deep Lantern, the Long Watch), {@link LegendWild} (Athletics, Survival) and {@link LegendCraft} (Smithing).
 */
public final class LegendEffects {
	/** The bosses no Mythic finishes or scares (as Mythics.BOSSES). */
	private static final Set<EntityType<?>> BOSSES = Set.of(EntityTypes.ENDER_DRAGON, EntityTypes.WITHER, EntityTypes.WARDEN, EntityTypes.ELDER_GUARDIAN);

	private LegendEffects() {
	}

	public static void init() {
		LegendPayloads.init();
		LegendCombat.init();
		LegendSenses.init();
		LegendWild.init();
		LegendCraft.init();
	}

	static boolean boss(Entity entity) {
		return BOSSES.contains(entity.getType());
	}

	/** A hostile mob (never a player, never a boss). */
	static boolean hostile(Entity entity) {
		return entity instanceof Mob && !(entity instanceof Player) && !boss(entity)
			&& (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER);
	}

	/** Whether this player may build here: spawn protection, the world border, adventure mode (as the Heart's bore asks). */
	static boolean mayBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return player.mayInteract(level, pos) && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
			&& !level.getServer().isUnderSpawnProtection(level, pos, player);
	}

	static void chip(ServerPlayer player, String mythic, Component text) {
		SignaturePerks.feedback(player, Mettle.id("mythic/" + mythic), text);
	}
}
