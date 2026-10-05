package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.defense.Cooldowns;
import dev.voidmc.mettle.character.skill.defense.Daze;
import dev.voidmc.mettle.character.skill.defense.Grit;
import dev.voidmc.mettle.character.skill.defense.Ironclad;
import dev.voidmc.mettle.character.skill.defense.Shieldbearer;
import dev.voidmc.mettle.character.skill.defense.Wounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Defense tree (TREES.md §7): its perks' behaviours. XP comes from {@link Combat}; the perk definitions are data in
 * {@code data/mettle/mettle/perk/defense/}, mixins in {@code mixin.defense}.
 *
 * <p>Shieldbearer (the shield, and who stands behind it) lives in {@link Shieldbearer}; Ironclad (armour, lasting the
 * fight) in {@link Ironclad}; the death-defying ones (Last Stand, the feats) in {@link Grit}; Roll with the Blow's held
 * back damage in {@link Wounds}. Thick Skin is a plain armour modifier in data; Armour Training and Marching Order are
 * weight perks that the burden module will read.
 */
public final class DefenseSkill {
	public static final Identifier THICK_SKIN = Mettle.id("defense/thick_skin");
	public static final Identifier ARMOUR_TRAINING = Mettle.id("defense/armour_training");
	public static final Identifier SHIELD_UP = Mettle.id("defense/shield_up");
	public static final Identifier SHIELD_BASH = Mettle.id("defense/shield_bash");
	public static final Identifier PERFECT_BLOCK = Mettle.id("defense/perfect_block");
	public static final Identifier BULWARK = Mettle.id("defense/bulwark");
	public static final Identifier MENDER = Mettle.id("defense/mender");
	public static final Identifier SECOND_WIND = Mettle.id("defense/second_wind");
	public static final Identifier HOLD_THE_LINE = Mettle.id("defense/hold_the_line");
	public static final Identifier UNBOWED = Mettle.id("defense/unbowed");
	public static final Identifier GUARDIAN = Mettle.id("defense/guardian");
	public static final Identifier LAST_STAND = Mettle.id("defense/last_stand");
	public static final Identifier JUGGERNAUT = Mettle.id("defense/juggernaut");
	public static final Identifier PINCUSHION = Mettle.id("defense/pincushion");
	public static final Identifier STILL_STANDING = Mettle.id("defense/still_standing");
	public static final Identifier ROLL_WITH_THE_BLOW = Mettle.id("defense/roll_with_the_blow");
	public static final Identifier MARCHING_ORDER = Mettle.id("defense/marching_order");
	public static final Identifier BATTLE_WORN = Mettle.id("defense/battle_worn");

	/** When each player last saw each perk's chip, so passive perks say so without spamming the action bar. */
	private static final Map<UUID, Map<Identifier, Long>> LAST_CHIP = new HashMap<>();

	private DefenseSkill() {
	}

	public static void init() {
		Cooldowns.init();
		Daze.init();
		ServerLivingEntityEvents.AFTER_DAMAGE.register(DefenseSkill::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player) || !Grit.lastStand(player, source));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Wounds.forget(player.getUUID());
				Ironclad.forget(player.getUUID());
			}
		});
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, from, to) -> {
			if (entity instanceof ServerPlayer player) {
				Ironclad.refresh(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Ironclad.refresh(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Ironclad.juggernautCharge(player);
				if (tick % 10 == 0) {
					Ironclad.refresh(player);
				}
				if (tick % 20 == 0) {
					Wounds.tick(player);
				}
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> forget(player.getUUID()));
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (target instanceof ServerPlayer defender) {
			Shieldbearer.afterBlock(defender, source, baseTaken, taken, blocked);
			Ironclad.afterHurt(defender, source, taken, blocked);
			Grit.afterHurt(defender, source, baseTaken, blocked);
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
			Shieldbearer.afterHit(attacker, target, source);
		}
	}

	/** A perk's chip on the action bar (or the overlay), at most once per {@code throttle} ticks for that perk. */
	public static void feedback(ServerPlayer player, Identifier perk, Component text, int throttle) {
		long now = player.level().getGameTime();
		Map<Identifier, Long> last = LAST_CHIP.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		Long previous = last.get(perk);
		if (throttle > 0 && previous != null && now - previous < throttle && now >= previous) {
			return;
		}
		last.put(perk, now);
		SignaturePerks.feedback(player, perk, text);
	}

	public static void feedback(ServerPlayer player, Identifier perk, Component text) {
		feedback(player, perk, text, 0);
	}

	/** Lang key of a perk's trigger chip: {@code mettle.feedback.defense.<id>}. */
	public static String chip(Identifier perk) {
		return "mettle.feedback." + perk.getPath().replace('/', '.');
	}

	public static void forget(UUID player) {
		LAST_CHIP.remove(player);
		Shieldbearer.forget(player);
		Ironclad.forget(player);
		Wounds.forget(player);
	}
}
