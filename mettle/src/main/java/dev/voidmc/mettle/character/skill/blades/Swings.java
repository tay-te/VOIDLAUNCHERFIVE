package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.BladesSkill;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.mixin.blades.BladesLivingAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The melee swing being resolved right now in {@code Player#attack} (server thread only), so the damage hooks know
 * which foe is the one you aimed at, and whether the swing was a critical. Forced criticals live here: the riposte after
 * a parry (Duelist), Unbroken, Weapon Master and Master Strike; so do the on-crit perks, Disarm and Bleed.
 */
public final class Swings {
	private static @Nullable Swing current;

	private static final class Swing {
		final ServerPlayer player;
		final Entity target;
		final float strength;
		boolean critical;
		boolean ignoresArmour;
		float damage;

		Swing(ServerPlayer player, Entity target, float strength) {
			this.player = player;
			this.target = target;
			this.strength = strength;
		}
	}

	private Swings() {
	}

	public static void begin(ServerPlayer player, Entity target) {
		current = new Swing(player, target, player.getAttackStrengthScale(0.5f));
	}

	/** Whether {@code target} is the foe this player aimed at, rather than one caught by a sweep or a spin. */
	public static boolean primary(ServerPlayer player, Entity target) {
		Swing s = current;
		return s == null || s.player != player || s.target == target;
	}

	/**
	 * Called where vanilla decides a full swing is a critical (only full swings get this far). Returns the decision
	 * after the forced criticals; each forced one is spent when used.
	 */
	public static boolean critical(ServerPlayer player, Entity target, boolean natural) {
		Swing s = current;
		Combat.Weapon weapon = Combat.melee(player.getMainHandItem());
		if (s == null || s.player != player || !(target instanceof LivingEntity) || weapon == null) {
			return natural;
		}
		BladeState state = BladeState.of(player);
		long now = player.level().getGameTime();
		boolean blade = weapon == Combat.Weapon.BLADE;
		boolean crit = natural;
		if (blade && now <= state.counterUntil && Perks.has(player, BladesSkill.MASTER_STRIKE)) {
			state.counterUntil = -1;
			s.ignoresArmour = true;
			crit = true;
			BladesSkill.chip(player, BladesSkill.MASTER_STRIKE, "master_strike");
		}
		if (blade && Perks.has(player, BladesSkill.UNBROKEN) && BladeMath.unbrokenReady(now, state.lastHurt, state.unbrokenUsed)) {
			state.unbrokenUsed = now;
			if (!crit) {
				crit = true;
				BladesSkill.chip(player, BladesSkill.UNBROKEN, "unbroken");
			}
		}
		if (!crit && blade && now <= state.riposteUntil && Perks.has(player, BladesSkill.DUELIST)) {
			state.riposteUntil = -1;
			crit = true;
			BladesSkill.chip(player, BladesSkill.DUELIST, "duelist.critical");
		}
		if (!crit && state.weaponMasterArmed == weapon && Perks.has(player, BladesSkill.WEAPON_MASTER)) {
			state.weaponMasterArmed = null;
			state.weaponMasterReady = now + BladeMath.WEAPON_MASTER_COOLDOWN;
			crit = true;
			BladesSkill.chip(player, BladesSkill.WEAPON_MASTER, "weapon_master");
		}
		s.critical = crit;
		return crit;
	}

	public static void damage(ServerPlayer player, float damage) {
		if (current != null && current.player == player) {
			current.damage = damage;
		}
	}

	/** The swing connected: the on-crit perks. */
	public static void landed(ServerPlayer player, Entity target) {
		Swing s = current;
		if (s == null || s.player != player || !s.critical || !(target instanceof LivingEntity living) || !living.isAlive()
			|| Combat.melee(player.getMainHandItem()) != Combat.Weapon.BLADE) {
			return;
		}
		if (living instanceof Mob mob && Perks.has(player, BladesSkill.DISARM) && player.getRandom().nextFloat() < 0.2f) {
			disarm(player, mob);
		}
		if (Perks.has(player, BladesSkill.BLEED)) {
			int wounds = Bleeding.open(player, living);
			BladesSkill.chip(player, BladesSkill.BLEED, "bleed", wounds);
		}
	}

	public static void end(ServerPlayer player, Entity target) {
		Swing s = current;
		current = null;
		if (s != null && s.player == player) {
			Whirlwind.spin(player, target, s.strength, s.damage);
		}
	}

	/** Master Strike: the counter's armour is ignored for the foe it was aimed at. */
	public static boolean ignoresArmour(LivingEntity target, DamageSource source) {
		Swing s = current;
		return s != null && s.ignoresArmour && s.target == target && source.getEntity() == s.player;
	}

	private static void disarm(ServerPlayer player, Mob mob) {
		ItemStack weapon = mob.getMainHandItem();
		if (weapon.isEmpty()) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.spawnAtLocation((ServerLevel) mob.level(), weapon.copy());
		mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
		BladesSkill.chip(player, BladesSkill.DISARM, "disarm");
	}

	/** Fully charges the next swing (Cut and Run, Sword and Board), surviving the swing packet that follows a kill. */
	public static void charge(ServerPlayer player) {
		((BladesLivingAccess) player).mettle$setAttackStrengthTicker((int) Math.ceil(player.getCurrentItemAttackStrengthDelay()) + 1);
		BladeState.of(player).keepChargeUntil = player.level().getGameTime() + 10;
	}

	public static boolean keepCharge(Player player) {
		if (!(player instanceof ServerPlayer sp)) {
			return false;
		}
		BladeState state = BladeState.of(sp);
		if (state.keepChargeUntil >= sp.level().getGameTime()) {
			state.keepChargeUntil = -1;
			return true;
		}
		return false;
	}

	public static void clear() {
		current = null;
	}

	/** How charged this player's swing being resolved now is (0–1), or 0 outside a swing (cards: Worldsplitter). */
	public static float strength(ServerPlayer player) {
		Swing s = current;
		return s != null && s.player == player ? s.strength : 0f;
	}

	/** Whether this player's swing being resolved now is a critical (cards: Keen, the Keen set). */
	public static boolean critical(ServerPlayer player) {
		Swing s = current;
		return s != null && s.player == player && s.critical;
	}

	/** Whether {@code target} is caught by this player's swing without being the foe it was aimed at (a sweep). */
	public static boolean swept(ServerPlayer player, Entity target) {
		Swing s = current;
		return s != null && s.player == player && s.target != target;
	}
}
