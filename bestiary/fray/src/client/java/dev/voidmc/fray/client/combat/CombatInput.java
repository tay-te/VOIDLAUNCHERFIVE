package dev.voidmc.fray.client.combat;

import dev.voidmc.fray.client.Animator;
import dev.voidmc.fray.client.ClientClips;
import dev.voidmc.fray.client.ClientMotion;
import dev.voidmc.fray.combat.Weapons;
import dev.voidmc.fray.combat.action.ActionKind;
import dev.voidmc.fray.combat.action.Movesets;
import dev.voidmc.fray.combat.action.Moveset;
import dev.voidmc.fray.combat.action.WeaponFamily;
import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.fray.combat.math.HoldMath;
import dev.voidmc.fray.combat.math.LoneKey;
import dev.voidmc.fray.combat.net.CombatPayloads;
import dev.voidmc.fray.motion.Clip;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The local player's combat input (COMBAT.md §2): the attack button's tap and hold, Guard on the use key, Left Alt
 * dodge, C technique (sneak + C the Art), middle mouse lock-on. Each input is predicted at once (clip, stamina, the
 * dodge's movement) and sent to the server, which decides.
 */
public final class CombatInput {
	private static int seq;
	public static boolean attackHeld;
	public static long attackDown;
	private static boolean attackClick;
	private static boolean useClick;
	public static boolean guardHeld;
	private static boolean autoReleased;

	// The predicted dodge's movement.
	private static long dodgeTick = Long.MIN_VALUE / 2;
	private static double dodgeAngle;
	private static float dodgeSpeed;
	private static int dodgeTicks;

	/** Off: the local player's actions aren't predicted with clips (HUD screenshots). */
	public static boolean predictClips = true;
	/** Inputs sent (tests), and the technique / Art inputs among them. */
	public static int sent, techniquesSent;
	/** Dodges fired by the key (tests). */
	public static int dodgesFired;
	/** Pick-block calls let through to vanilla (tests). */
	public static int picksAllowed;

	/**
	 * The dodge on a modifier key (Left Alt) fires when the key is released by itself: any other key or mouse button
	 * in between (Alt + middle mouse is pick block) spoils it, as does holding it past half a second (the user's call,
	 * 2026-10-21). On any other key it fires on press.
	 */
	public static final LoneKey DODGE_GATE = new LoneKey(500);
	/** C in creative, where C + a number is the save/load toolbar: the technique fires on C's release, unless a number went down. */
	public static final LoneKey TECHNIQUE_GATE = new LoneKey(1000);
	private static boolean dodgeQueued, techniqueQueued;
	/** Left or right Alt, tracked from key events (vanilla's pick block keeps Alt + middle mouse). */
	private static boolean altHeld;

	private CombatInput() {
	}

	public static void send(byte type, int target, int param) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.getConnection() == null) {
			return;
		}
		long tick = mc.level == null ? 0 : mc.level.getGameTime();
		ClientPlayNetworking.send(new CombatPayloads.Input(type, ++seq, tick, p.getYRot(), p.getXRot(), target, param));
		sent++;
	}

	/** Fray takes the attack key unless it would mine: a sword never mines; tools and hands mine solid blocks. */
	public static boolean takeAttack(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || p.isSpectator() || !FrayCombatClient.serverHasFray()) {
			return false;
		}
		HitResult hr = mc.hitResult;
		if (hr == null || hr.getType() != HitResult.Type.BLOCK) {
			return true;
		}
		WeaponFamily fam = Weapons.family(p.getMainHandItem());
		if (fam == WeaponFamily.SWORD && !p.isCreative()) {
			return true;
		}
		BlockPos pos = ((BlockHitResult) hr).getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		// Grass, flowers and other blocks you walk through: the swing passes through.
		return state.getCollisionShape(mc.level, pos).isEmpty() && !state.isAir() && !p.isCreative() && fam != WeaponFamily.UNARMED;
	}

	public static void onAttackClick() {
		attackClick = true;
	}

	public static void onUseClick() {
		useClick = true;
	}

	private static int crosshairEntity(Minecraft mc) {
		if (LockOn.target >= 0) {
			return LockOn.target;
		}
		return mc.hitResult instanceof EntityHitResult e ? e.getEntity().getId() : -1;
	}

	private static boolean guardAllowed(Minecraft mc) {
		LocalPlayer p = mc.player;
		boolean shield = p.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) || p.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS);
		WeaponFamily fam = Weapons.family(p.getMainHandItem());
		if (!shield && (fam == WeaponFamily.UNARMED || fam == WeaponFamily.BOW)) {
			return false;
		}
		boolean fight = ClientCombat.self != null && (ClientCombat.self.flags() & CombatPayloads.SELF_IN_COMBAT) != 0 || LockOn.target >= 0;
		HitResult hr = mc.hitResult;
		if (hr instanceof BlockHitResult && hr.getType() == HitResult.Type.BLOCK && !fight) {
			return false;
		}
		return !(hr instanceof EntityHitResult e && !(e.getEntity() instanceof Enemy) && !fight);
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			attackHeld = guardHeld = false;
			return;
		}
		long now = mc.level.getGameTime();
		boolean screen = mc.gui.screen() != null;
		// Attack: a click Fray took starts the hold; the release decides light, heavy or charged.
		if (!attackHeld && attackClick && !screen) {
			attackHeld = true;
			autoReleased = false;
			attackDown = now;
			send(CombatPayloads.ATTACK_DOWN, crosshairEntity(mc), 0);
		}
		if (attackHeld) {
			int held = (int) (now - attackDown);
			Moveset ms = Movesets.of(Weapons.family(p.getMainHandItem()));
			if (!autoReleased && HoldMath.autoRelease(held, ms.chargeTicks(), 1.0)) {
				// The server releases a full charge itself; predict it.
				autoReleased = true;
				predictAttack(mc, held);
			}
			if (!mc.options.keyAttack.isDown() || screen) {
				attackHeld = false;
				if (!autoReleased) {
					send(CombatPayloads.ATTACK_UP, crosshairEntity(mc), held);
					predictAttack(mc, held);
				}
			}
		}
		attackClick = false;

		// Guard: the use key with a shield or a weapon in hand.
		boolean useDown = mc.options.keyUse.isDown() || useClick;
		if (!guardHeld && useDown && !screen && guardAllowed(mc)) {
			guardHeld = true;
			send(CombatPayloads.GUARD_DOWN, crosshairEntity(mc), 0);
			ClientCombat.predictedSpend += 6;
			ClientCombat.predictedAt = ClientCombat.ms();
		}
		if (guardHeld && (!mc.options.keyUse.isDown() || screen)) {
			guardHeld = false;
			send(CombatPayloads.GUARD_UP, -1, 0);
		}
		useClick = false;

		// The dodge and technique keys are read from key events (onKey); their click counts are only drained here.
		while (CombatKeys.dodge.consumeClick()) {
			// onKey decides.
		}
		while (CombatKeys.technique.consumeClick()) {
			// onKey decides.
		}
		if (screen || !mc.isWindowActive()) {
			DODGE_GATE.cancel();
			TECHNIQUE_GATE.cancel();
			dodgeQueued = techniqueQueued = false;
		}
		if (dodgeQueued) {
			dodgeQueued = false;
			dodgesFired++;
			dodge(mc, p, now);
		}
		if (techniqueQueued) {
			techniqueQueued = false;
			techniquesSent++;
			send(p.isShiftKeyDown() ? CombatPayloads.ART : CombatPayloads.TECHNIQUE, crosshairEntity(mc), 0);
		}
		while (CombatKeys.lockOn.consumeClick()) {
			if (!screen && !altDown(mc)) {
				LockOn.toggle(mc);
			}
		}
		LockOn.tick(mc);
		applyDodge(p, now);
		ClientCombat.prune();
	}

	/** Alt is held (key events, or the window's key state). */
	public static boolean altDown(Minecraft mc) {
		return altHeld || InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
	}

	private static boolean modifier(InputConstants.Key k) {
		if (k.getType() != InputConstants.Type.KEYBOARD) {
			return false;
		}
		int v = k.getValue();
		return v == InputConstants.KEY_LALT || v == InputConstants.KEY_RALT || v == InputConstants.KEY_LCONTROL || v == InputConstants.KEY_RCONTROL
			|| v == InputConstants.KEY_LSHIFT || v == InputConstants.KEY_RSHIFT || v == InputConstants.KEY_LGUI || v == InputConstants.KEY_RGUI;
	}

	/** A hotbar number (or F3, for F3 + C) is held: C is part of vanilla's chord, not the technique. */
	private static boolean chordHeld(Minecraft mc) {
		for (net.minecraft.client.KeyMapping k : mc.options.keyHotbarSlots) {
			if (k.isDown()) {
				return true;
			}
		}
		return mc.options.keyDebugModifier.isDown();
	}

	private static boolean hotbarKey(Minecraft mc, InputConstants.Key key) {
		for (net.minecraft.client.KeyMapping k : mc.options.keyHotbarSlots) {
			if (k.matches(key)) {
				return true;
			}
		}
		return false;
	}

	/** In creative, a technique key shared with the save/load toolbar activators waits for its release. */
	private static boolean deferTechnique(Minecraft mc) {
		return mc.player != null && mc.player.isCreative()
			&& (CombatKeys.technique.same(mc.options.keySaveHotbarActivator) || CombatKeys.technique.same(mc.options.keyLoadHotbarActivator));
	}

	/**
	 * Every key and mouse button press or release in the world, before vanilla handles it (from
	 * {@code KeyboardHandler.keyPress} and {@code MouseHandler.onButton}). Repeats are not passed in.
	 */
	public static void onKey(InputConstants.Key key, boolean press) {
		Minecraft mc = Minecraft.getInstance();
		if (key.getType() == InputConstants.Type.KEYBOARD && (key.getValue() == InputConstants.KEY_LALT || key.getValue() == InputConstants.KEY_RALT)) {
			altHeld = press;
		}
		if (mc.player == null || mc.gui.screen() != null || CombatKeys.dodge == null) {
			DODGE_GATE.cancel();
			TECHNIQUE_GATE.cancel();
			return;
		}
		long now = ClientCombat.ms();
		boolean isDodge = !CombatKeys.dodge.isUnbound() && CombatKeys.dodge.matches(key);
		boolean isTechnique = !CombatKeys.technique.isUnbound() && CombatKeys.technique.matches(key);
		if (press) {
			if (isDodge) {
				if (modifier(key)) {
					DODGE_GATE.press(now, false);
				} else {
					dodgeQueued = true;
				}
			} else {
				DODGE_GATE.other();
			}
			if (isTechnique) {
				boolean chord = chordHeld(mc);
				if (deferTechnique(mc)) {
					TECHNIQUE_GATE.press(now, chord);
				} else if (!chord) {
					techniqueQueued = true;
				}
			} else if (hotbarKey(mc, key) || mc.options.keyDebugModifier.matches(key)) {
				TECHNIQUE_GATE.other();
			}
		} else {
			if (isDodge && DODGE_GATE.held() && DODGE_GATE.release(now)) {
				dodgeQueued = true;
			}
			if (isTechnique && TECHNIQUE_GATE.held() && TECHNIQUE_GATE.release(now)) {
				techniqueQueued = true;
			}
		}
	}

	/**
	 * Middle mouse is vanilla's pick block (not lock-on) when Alt is held, or out of a fight with nothing locked and the
	 * crosshair on a block or on a creature that isn't hostile.
	 */
	public static boolean middleIsPick(Minecraft mc) {
		if (altDown(mc)) {
			return true;
		}
		boolean fight = ClientCombat.self != null && (ClientCombat.self.flags() & CombatPayloads.SELF_IN_COMBAT) != 0;
		if (fight || LockOn.target >= 0) {
			return false;
		}
		HitResult hr = mc.hitResult;
		if (hr == null || hr.getType() == HitResult.Type.MISS) {
			return false;
		}
		return hr.getType() == HitResult.Type.BLOCK || hr instanceof EntityHitResult e && !(e.getEntity() instanceof Enemy);
	}

	private static void dodge(Minecraft mc, LocalPlayer p, long now) {
		Input in = p.input.keyPresses;
		int dz = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		int dx = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		int param = 0;
		if (dz != 0 || dx != 0) {
			double a = Math.toDegrees(Math.atan2(dx, dz));
			param = 1 + (int) Math.floorMod(Math.round(a / 45.0), 8L);
		}
		send(CombatPayloads.DODGE, -1, param);
		if (!p.onGround() || ClientCombat.self != null && ClientCombat.self.windedLeft() > 0) {
			return;
		}
		boolean roll = param != 0;
		int cls = Weapons.armourClass(p);
		dodgeTick = now;
		// Compass 1 = forward, clockwise; the look's yaw turns it into the world.
		double rel = roll ? (param - 1) * 45.0 : 180.0;
		dodgeAngle = Math.toRadians(p.getYRot() + rel);
		dodgeTicks = roll ? 7 : 4;
		dodgeSpeed = (roll ? Movesets.DODGE_DISTANCE[cls] : Movesets.BACKSTEP.step()) / dodgeTicks;
		ClientCombat.predictedSpend += roll ? Movesets.DODGE_COST[cls] : Movesets.BACKSTEP.stamina();
		ClientCombat.predictedAt = ClientCombat.ms();
		predictClip(mc, roll ? ActionKind.DODGE : ActionKind.BACKSTEP, param, 0);
	}

	private static void applyDodge(LocalPlayer p, long now) {
		long k = now - dodgeTick;
		if (k >= 0 && k < dodgeTicks && p.onGround()) {
			// Rotations: yaw 0 faces +Z; the right-hand side is -X.
			double x = -Math.sin(dodgeAngle), z = Math.cos(dodgeAngle);
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(x * dodgeSpeed, v.y, z * dodgeSpeed);
		}
	}

	private static void predictAttack(Minecraft mc, int held) {
		LocalPlayer p = mc.player;
		Moveset ms = Movesets.of(Weapons.family(p.getMainHandItem()));
		HoldMath.Kind kind = HoldMath.classify(held);
		boolean winded = ClientCombat.self != null && ClientCombat.self.windedLeft() > 0;
		if (winded) {
			kind = HoldMath.Kind.LIGHT;
		}
		ActionKind ak;
		int windup;
		float cost;
		switch (kind) {
			case HEAVY -> {
				ak = ActionKind.HEAVY;
				windup = ms.heavy().phases().windup();
				cost = ms.heavy().stamina();
			}
			case CHARGED -> {
				ak = ActionKind.CHARGED;
				windup = ms.heavy().phases().windup();
				cost = HoldMath.chargedCost(ms.heavy().stamina(), ms.chargedStaminaMax(), HoldMath.charge(held, ms.chargeTicks(), 1));
			}
			default -> {
				ak = ActionKind.LIGHT;
				windup = ms.light(0).phases().windup();
				cost = ms.light(0).stamina();
			}
		}
		ClientCombat.predictedSpend += cost;
		ClientCombat.predictedAt = ClientCombat.ms();
		int variant = 0;
		if (ak == ActionKind.LIGHT) {
			long now = mc.level.getGameTime();
			lightCombo = now - lastLight <= 14 ? (lightCombo + 1) % ms.lights().size() : 0;
			lastLight = now;
			variant = lightCombo;
		}
		predictClip(mc, ak, variant, windup);
	}

	private static int lightCombo;
	private static long lastLight = Long.MIN_VALUE / 2;

	private static void predictClip(Minecraft mc, ActionKind kind, int variant, int windup) {
		Identifier id = ClientActionClips.clip(kind, variant);
		Clip clip = id == null ? null : ClientClips.get(id);
		ClientCombat.lastPredictedKind = kind.ordinal();
		if (clip != null && mc.player != null && predictClips) {
			ClientMotion.animator(mc.player.getId()).play(Animator.ACTION, clip, ClientMotion.now(0), ClientActionClips.speed(kind, windup, id));
		}
	}

	/** The server refused a predicted action: back to idle in 3 ticks, the stamina back. */
	public static void onAck(CombatPayloads.Ack ack) {
		Minecraft mc = Minecraft.getInstance();
		if (ack.accepted() || mc.player == null) {
			return;
		}
		ClientCombat.predictedSpend = 0;
		if (ack.kind() >= 0 && ack.kind() == ClientCombat.lastPredictedKind) {
			Animator a = ClientMotion.get(mc.player.getId());
			if (a != null) {
				a.stop(Animator.ACTION, ClientMotion.now(0), 3);
			}
			if (ack.kind() == ActionKind.DODGE.ordinal() || ack.kind() == ActionKind.BACKSTEP.ordinal()) {
				dodgeTick = Long.MIN_VALUE / 2;
			}
		}
	}

	/** Another entity's action (the server's word): its clip on our side. */
	public static void onActionPlay(CombatPayloads.ActionPlay play) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity e = mc.level.getEntity(play.entity());
		if (e == null || mc.player != null && e == mc.player) {
			return;
		}
		ActionKind kind = ActionKind.byIndex(play.kind());
		if (kind == null) {
			Animator a = ClientMotion.get(play.entity());
			if (a != null) {
				a.stop(Animator.ACTION, ClientMotion.now(0), 3);
			}
			return;
		}
		Identifier id = ClientActionClips.clip(kind, play.variant());
		Clip clip = id == null ? null : ClientClips.get(id);
		if (clip != null) {
			ClientMotion.animator(play.entity()).play(Animator.ACTION, clip, play.startTick(),
				id.equals(dev.voidmc.fray.combat.action.ActionClips.SWING_ALIAS) ? play.speed() : 1);
		}
	}

	/** The cost of the action under the finger (the stamina ghost): a held attack's heavy or charge, a held guard's block. */
	public static float ghostCost(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || !attackHeld) {
			return 0;
		}
		int held = (int) (mc.level.getGameTime() - attackDown);
		Moveset ms = Movesets.of(Weapons.family(p.getMainHandItem()));
		return switch (HoldMath.classify(held)) {
			case LIGHT -> 0;
			case HEAVY -> ms.heavy().stamina();
			case CHARGED -> HoldMath.chargedCost(ms.heavy().stamina(), ms.chargedStaminaMax(), HoldMath.charge(held, ms.chargeTicks(), 1));
		};
	}
}
