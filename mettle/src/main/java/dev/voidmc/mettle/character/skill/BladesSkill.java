package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.blades.Aftermath;
import dev.voidmc.mettle.character.skill.blades.BladeGear;
import dev.voidmc.mettle.character.skill.blades.BladeHits;
import dev.voidmc.mettle.character.skill.blades.BladeState;
import dev.voidmc.mettle.character.skill.blades.Bleeding;
import dev.voidmc.mettle.character.skill.blades.Parry;
import dev.voidmc.mettle.character.skill.blades.Swings;
import dev.voidmc.mettle.character.skill.blades.Whirlwind;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;

/**
 * The Blades tree (TREES.md §4). XP comes from {@link Combat}; the perk definitions are data in
 * {@code data/mettle/mettle/perk/blades/}; the behaviours live in {@code skill.blades} and are reached from the
 * {@code mixin.blades} hooks through the entry points below. Everything is decided on the server; Light Feet and
 * Measure also run on the client so its own prediction agrees.
 */
public final class BladesSkill {
	public static final Identifier FENCER = Mettle.id("blades/fencer");
	public static final Identifier QUICK_DRAW = Mettle.id("blades/quick_draw");
	public static final Identifier MEASURE = Mettle.id("blades/measure");
	public static final Identifier DISARM = Mettle.id("blades/disarm");
	public static final Identifier MOMENTUM = Mettle.id("blades/momentum");
	public static final Identifier SINGLE_COMBAT = Mettle.id("blades/single_combat");
	public static final Identifier LIGHT_FEET = Mettle.id("blades/light_feet");
	public static final Identifier WIDE_ARC = Mettle.id("blades/wide_arc");
	public static final Identifier BLEED = Mettle.id("blades/bleed");
	public static final Identifier CUT_AND_RUN = Mettle.id("blades/cut_and_run");
	public static final Identifier DUELIST = Mettle.id("blades/duelist");
	public static final Identifier WHIRLWIND = Mettle.id("blades/whirlwind");
	public static final Identifier EXECUTIONER = Mettle.id("blades/executioner");
	public static final Identifier UNBROKEN = Mettle.id("blades/unbroken");
	public static final Identifier GIANT_SLAYER = Mettle.id("blades/giant_slayer");
	public static final Identifier SWORD_AND_BOARD = Mettle.id("blades/sword_and_board");
	public static final Identifier WEAPON_MASTER = Mettle.id("blades/weapon_master");
	public static final Identifier MASTER_STRIKE = Mettle.id("blades/master_strike");

	private BladesSkill() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Parry::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Aftermath::afterDamage);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(Aftermath::afterKill);
		// Swords have no use of their own, so the parry rides along and lets vanilla carry on (an off-hand shield still rises).
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) {
				Parry.start(sp);
			}
			return InteractionResult.PASS;
		});
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, from, to) -> {
			if (entity instanceof ServerPlayer player && slot == EquipmentSlot.MAINHAND) {
				BladeGear.refresh(player);
				BladeGear.switched(player, to);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			if (tick % 20 == 0) {
				Bleeding.tick(server);
			}
			if (tick % 10 == 0) {
				Parry.expire(server.overworld().getGameTime());
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					BladeGear.refresh(player);
					Whirlwind.announce(player);
				}
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> BladeState.forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BladeState.clear();
			Bleeding.clear();
			Parry.clear();
			Swings.clear();
		});
	}

	/** A feedback chip, {@code mettle.feedback.blades.<key>}. */
	public static void chip(ServerPlayer player, Identifier perk, String key, Object... args) {
		feedback(player, perk, Component.translatable("mettle.feedback.blades." + key, args));
	}

	public static void feedback(ServerPlayer player, Identifier perk, Component text) {
		SignaturePerks.feedback(player, perk, text);
	}

	// ---------------------------------------------------------------- mixin entry points

	/** LivingEntity#hurtServer's incoming damage, after the Blades perks of whoever hits or is hit. */
	public static float damage(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0 || Aftermath.executing()) {
			return damage;
		}
		if (target instanceof ServerPlayer defender) {
			damage = BladeHits.taken(defender, source, damage);
		}
		if (source.getEntity() instanceof ServerPlayer player && player != target && Combat.weapon(source, player) == Combat.Weapon.BLADE) {
			damage = BladeHits.dealt(player, target, damage, Swings.primary(player, target));
		}
		return damage;
	}

	public static void beginSwing(Player player, Entity target) {
		if (player instanceof ServerPlayer sp) {
			Swings.begin(sp, target);
		}
	}

	public static boolean critical(Player player, Entity target, boolean natural) {
		return player instanceof ServerPlayer sp ? Swings.critical(sp, target, natural) : natural;
	}

	public static void swingDamage(Player player, float damage) {
		if (player instanceof ServerPlayer sp) {
			Swings.damage(sp, damage);
		}
	}

	public static void landed(Player player, Entity target) {
		if (player instanceof ServerPlayer sp) {
			Swings.landed(sp, target);
		}
	}

	public static void endSwing(Player player, Entity target) {
		if (player instanceof ServerPlayer sp) {
			Swings.end(sp, target);
		}
	}

	/** A spear stab finished (spears don't go through Player#attack): Whirlwind, with the stab's own damage. */
	public static void stabbed(LivingEntity attacker, float charge) {
		if (attacker instanceof ServerPlayer player) {
			Whirlwind.spin(player, null, charge, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2f + charge * charge * 0.8f));
		}
	}

	public static boolean ignoresArmour(LivingEntity target, DamageSource source) {
		return Swings.ignoresArmour(target, source);
	}

	/** Light Feet, from Player#causeExtraKnockback; {@code sprint} is the sprint-stop call (the other is the slow-down). */
	public static boolean keepsSprint(Player player, boolean sprint) {
		if (!BladeGear.keepsSprint(player)) {
			return false;
		}
		if (sprint && player instanceof ServerPlayer sp) {
			BladeGear.keptSprint(sp);
		}
		return true;
	}

	public static boolean keepCharge(Player player) {
		return Swings.keepCharge(player);
	}

	public static AttackRange reach(LivingEntity entity, ItemStack weapon, AttackRange range) {
		return BladeGear.reach(entity, weapon, range);
	}
}
