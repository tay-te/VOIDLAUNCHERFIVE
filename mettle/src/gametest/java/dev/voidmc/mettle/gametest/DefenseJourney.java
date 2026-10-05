package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.DefenseSkill;
import dev.voidmc.mettle.character.skill.defense.Cooldowns;
import dev.voidmc.mettle.character.skill.defense.Daze;
import dev.voidmc.mettle.character.skill.defense.Ironclad;
import dev.voidmc.mettle.character.skill.defense.Wounds;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Defense tree played for real: each perk triggered on the server and checked. Three builds in turn (Shieldbearer
 * with Guardian, Ironclad with Juggernaut, a mixed one with Last Stand), then the feats, the synergies and the teaching.
 * Ends respecced, as a Ranger, at full health, with everything it spawned or granted gone.
 */
final class DefenseJourney {
	private static final String LEARN = "execute as @p run mettle perk learn mettle:defense/";
	private static final String TAG = "mettle_def";

	private DefenseJourney() {
	}

	static void run(TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("difficulty normal");
		server.runCommand("gamerule natural_health_regeneration false");
		server.runCommand("execute as @p run mettle respec");
		server.runCommand("effect clear @a");
		server.runCommand("mettle xp mettle:smithing 5000 @a");
		server.runCommand("mettle xp mettle:athletics 5000 @a");
		server.runCommand("mettle xp mettle:defense 30000 @a");
		vitality(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (CharacterStore.get(p).level(dev.voidmc.mettle.character.skill.Combat.DEFENSE) < 18) {
				return "Defense level " + CharacterStore.get(p).level(dev.voidmc.mettle.character.skill.Combat.DEFENSE);
			}
			// Both synergies unlock by themselves at Defense 10 with Smithing 10 and Athletics 10.
			if (!Perks.has(p, DefenseSkill.BATTLE_WORN) || !Perks.has(p, DefenseSkill.MARCHING_ORDER)) {
				return "synergies not unlocked: " + CharacterStore.get(p).perks();
			}
			p.getInventory().clearContent();
			p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			return null;
		}));
		shieldbearer(server);
		server.runCommand("execute as @p run mettle respec");
		vitality(server);
		ironclad(server);
		server.runCommand("execute as @p run mettle respec");
		vitality(server);
		lastStandAndFeats(server);
		cleanUp(server);
	}

	// ---------------------------------------------------------------- Path A and Guardian

	private static void shieldbearer(TestServerContext server) {
		double[] armour = new double[1];
		check(server.computeOnServer(s -> {
			armour[0] = player(s).getAttributeValue(Attributes.ARMOR);
			return null;
		}));
		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "thick_skin");
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			double now = p.getAttributeValue(Attributes.ARMOR);
			if (Perks.rank(p, DefenseSkill.THICK_SKIN) != 3 || now != armour[0] + 3) {
				return "Thick Skin III: armour " + now + ", expected " + (armour[0] + 3);
			}
			// Vanilla shields need a quarter second to come up.
			p.startUsingItem(InteractionHand.OFF_HAND);
			boolean up = p.isBlocking();
			p.stopUsingItem();
			return up ? "a shield blocked on its first tick without Shield Up" : null;
		}));

		// A hit from the side (110° off where the player looks) gets past a plain shield.
		Mob flanker = spawn(server, EntityTypes.ZOMBIE, 110, 2.0);
		server.runCommand(LEARN + "shield_up");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, DefenseSkill.SHIELD_UP)) {
				return "Shield Up not learned: " + CharacterStore.get(p).perks();
			}
			p.startUsingItem(InteractionHand.OFF_HAND);
			if (!p.isBlocking()) {
				return "Shield Up: the shield is not up on its first tick";
			}
			p.setDeltaMovement(Vec3.ZERO);
			p.knockback(1.0, 1.0, 0.0, p.damageSources().generic(), 1f);
			if (p.getDeltaMovement().lengthSqr() > 0) {
				return "Shield Up: knocked back while blocking " + p.getDeltaMovement();
			}
			float before = full(p);
			p.hurtServer(p.level(), p.damageSources().mobAttack(flanker), 4f);
			p.stopUsingItem();
			return p.getHealth() < before ? null : "a side hit was blocked without Bulwark";
		}));

		server.runCommand(LEARN + "shield_bash");
		server.runCommand(LEARN + "perfect_block");
		server.runCommand(LEARN + "bulwark");
		server.runCommand(LEARN + "guardian");
		Mob front = spawn(server, EntityTypes.ZOMBIE, 0, 2.0);
		Mob pig = spawn(server, EntityTypes.PIG, -40, 2.0);
		Mob villager = spawn(server, EntityTypes.VILLAGER, 60, 2.5);
		Mob taunted = spawn(server, EntityTypes.ZOMBIE, 180, 5.0);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			for (Identifier id : new Identifier[] {DefenseSkill.SHIELD_BASH, DefenseSkill.PERFECT_BLOCK, DefenseSkill.BULWARK, DefenseSkill.GUARDIAN}) {
				if (!Perks.has(p, id)) {
					return id + " not learned: " + CharacterStore.get(p).perks();
				}
			}
			// Bulwark: the same side hit is now turned.
			float before = full(p);
			p.startUsingItem(InteractionHand.OFF_HAND);
			p.hurtServer(level, p.damageSources().mobAttack(flanker), 4f);
			if (p.getHealth() < before) {
				return "Bulwark: a side hit got through, health " + p.getHealth() + " of " + before;
			}
			// Perfect Block: a hit in the first quarter second up comes back at half strength.
			p.stopUsingItem();
			p.startUsingItem(InteractionHand.OFF_HAND);
			p.damageCooldownTime = 0;
			float zombie = front.getHealth();
			p.hurtServer(level, p.damageSources().mobAttack(front), 8f);
			if (p.getHealth() < before) {
				return "Perfect Block: the frontal hit was not blocked";
			}
			if (front.getHealth() > zombie - 3f) {
				return "Perfect Block: attacker at " + front.getHealth() + " of " + zombie + ", expected about 4 reflected";
			}
			// Guardian: a villager beside the raised shield takes 40 % less, and its attacker turns on the player.
			villager.damageCooldownTime = 0;
			float folk = villager.getHealth();
			villager.hurtServer(level, villager.damageSources().mobAttack(taunted), 10f);
			if (Math.abs(villager.getHealth() - (folk - 6f)) > 0.01f) {
				return "Guardian: villager at " + villager.getHealth() + ", expected " + (folk - 6f);
			}
			if (taunted.getTarget() != p) {
				return "Guardian: the attacker did not turn on the guardian";
			}
			// Shield Bash: lower the shield and hit at once: shoved back and dazed.
			p.stopUsingItem();
			pig.hurtServer(level, p.damageSources().playerAttack(p), 1f);
			if (pig.getDeltaMovement().horizontalDistance() < 1.0 || !Daze.isDazed(pig) || !pig.hasEffect(MobEffects.SLOWNESS)) {
				return "Shield Bash: pig moving " + pig.getDeltaMovement() + ", dazed " + Daze.isDazed(pig);
			}
			return null;
		}));
		discard(server);
	}

	// ---------------------------------------------------------------- Path B and Juggernaut

	private static void ironclad(TestServerContext server) {
		for (String perk : new String[] {"mender", "second_wind", "hold_the_line", "unbowed", "juggernaut"}) {
			server.runCommand(LEARN + perk);
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (Identifier id : new Identifier[] {DefenseSkill.MENDER, DefenseSkill.SECOND_WIND, DefenseSkill.HOLD_THE_LINE, DefenseSkill.UNBOWED,
				DefenseSkill.JUGGERNAUT}) {
				if (!Perks.has(p, id)) {
					return id + " not learned: " + CharacterStore.get(p).perks();
				}
			}
			if (Perks.has(p, DefenseSkill.SHIELD_UP)) {
				return "respec kept Shield Up";
			}
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			return null;
		}));
		waitTicks(server, 12);
		Mob hitter = spawn(server, EntityTypes.ZOMBIE, 180, 3.0);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			// Juggernaut: in full armour nothing knocks you back, and a sprint bowls a mob over.
			if (p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0 || p.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE) < 1.0) {
				return "Juggernaut: knockback resistance " + p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			}
			// Spawned here, in the same tick, so nothing nudges it out of the way first.
			Mob target = spawnNow(p, EntityTypes.ZOMBIE, 0, 0.9);
			float zombie = target.getHealth();
			p.setSprinting(true);
			int bowled = Ironclad.juggernautCharge(p);
			p.setSprinting(false);
			if (bowled != 1 || target.getHealth() > zombie - 3f || !Daze.isDazed(target)) {
				return "Juggernaut: bowled " + bowled + ", zombie at " + target.getHealth() + " of " + zombie;
			}

			// Unbowed: in full armour poison lasts half as long; with a piece off it does not.
			p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
			int halved = p.getEffect(MobEffects.POISON).getDuration();
			p.removeAllEffects();
			ItemStack helmet = p.getItemBySlot(EquipmentSlot.HEAD);
			p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
			int whole = p.getEffect(MobEffects.WEAKNESS).getDuration();
			p.removeAllEffects();
			p.setItemSlot(EquipmentSlot.HEAD, helmet);
			if (halved != 100 || whole != 200) {
				return "Unbowed: poison " + halved + " (expected 100), weakness without a helmet " + whole + " (expected 200)";
			}

			// Mender: 300 points of wear leave about 210 on a chestplate (it would have broken at 240).
			ItemStack spare = new ItemStack(Items.IRON_CHESTPLATE);
			for (int i = 0; i < 300; i++) {
				spare.hurtAndBreak(1, p, EquipmentSlot.CHEST);
			}
			if (spare.isEmpty() || spare.getDamageValue() < 160 || spare.getDamageValue() > 235) {
				return "Mender: 300 wear became " + (spare.isEmpty() ? "a broken chestplate" : spare.getDamageValue());
			}

			// Second Wind: dropping below 4 hearts gives Regeneration II.
			Cooldowns.reset(p, Cooldowns.SECOND_WIND);
			p.setHealth(10f);
			p.damageCooldownTime = 0;
			p.hurtServer(level, p.damageSources().magic(), 4f);
			MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
			if (regen == null || regen.getAmplifier() != 1) {
				return "Second Wind: no Regeneration II at " + p.getHealth();
			}
			p.removeAllEffects();

			// Hold the Line: three hits standing still, +3 armour.
			full(p);
			p.setKnownMovement(Vec3.ZERO);
			double armour = p.getAttributeValue(Attributes.ARMOR);
			for (int i = 1; i <= 3; i++) {
				p.damageCooldownTime = 0;
				p.hurtServer(level, p.damageSources().mobAttack(hitter), i);
			}
			if (Ironclad.holdTheLineStacks(p) != 3 || p.getAttributeValue(Attributes.ARMOR) != armour + 3) {
				return "Hold the Line: " + Ironclad.holdTheLineStacks(p) + " stacks, armour " + p.getAttributeValue(Attributes.ARMOR) + " from " + armour;
			}

			// Battle-Worn (synergy): a chestplate two points from breaking takes five hits and is still there, at 1.
			ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
			chest.setDamageValue(chest.getMaxDamage() - 2);
			for (int i = 0; i < 5; i++) {
				full(p);
				p.damageCooldownTime = 0;
				p.hurtServer(level, p.damageSources().mobAttack(hitter), 8f);
			}
			chest = p.getItemBySlot(EquipmentSlot.CHEST);
			if (chest.isEmpty() || chest.getDamageValue() != chest.getMaxDamage() - 1) {
				return "Battle-Worn: chestplate " + (chest.isEmpty() ? "broke" : "at " + chest.getDamageValue() + " of " + chest.getMaxDamage());
			}
			return null;
		}));
		waitTicks(server, 12);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			AttributeModifier worn = p.getAttribute(Attributes.ARMOR).getModifier(Mettle.id("defense/battle_worn"));
			if (worn == null || worn.amount() != -3.0) {
				return "Battle-Worn: a worn iron chestplate should give half its 6 armour, modifier " + worn;
			}
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				p.setItemSlot(slot, ItemStack.EMPTY);
			}
			p.removeAllEffects();
			return null;
		}));
		discard(server);
	}

	// ---------------------------------------------------------------- Last Stand, the feats and the teaching

	private static void lastStandAndFeats(TestServerContext server) {
		for (String perk : new String[] {"shield_up", "mender", "last_stand"}) {
			server.runCommand(LEARN + perk);
		}
		Mob archer = spawn(server, EntityTypes.SKELETON, 0, 3.0);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			if (!Perks.has(p, DefenseSkill.LAST_STAND)) {
				return "Last Stand (any two path perks) not learned: " + CharacterStore.get(p).perks();
			}
			// Last Stand: a killing blow leaves one heart and Resistance IV.
			Cooldowns.reset(p, Cooldowns.LAST_STAND);
			full(p);
			p.damageCooldownTime = 0;
			p.hurtServer(level, p.damageSources().magic(), 100f);
			MobEffectInstance resistance = p.getEffect(MobEffects.RESISTANCE);
			if (!p.isAlive() || p.getHealth() != 2f || resistance == null || resistance.getAmplifier() != 3) {
				return "Last Stand: alive " + p.isAlive() + ", health " + p.getHealth() + ", resistance " + resistance;
			}
			if (Cooldowns.remaining(p, Cooldowns.LAST_STAND) <= 0) {
				return "Last Stand: no cooldown started";
			}
			p.removeAllEffects();

			// Pincushion (feat, 300 blocked projectiles): about half the arrows a shield turns are kept.
			FeatCounters.add(p, "block_projectile", 300);
			if (!Perks.has(p, DefenseSkill.PINCUSHION)) {
				return "Pincushion not earned at 300 blocks";
			}
			int arrowsBefore = p.getInventory().countItem(Items.ARROW);
			Vec3 at = archer.getEyePosition();
			for (int i = 0; i < 24; i++) {
				Arrow arrow = new Arrow(level, at.x, at.y, at.z, new ItemStack(Items.ARROW), null);
				arrow.setOwner(archer);
				level.addFreshEntity(arrow);
				p.stopUsingItem();
				p.startUsingItem(InteractionHand.OFF_HAND);
				p.damageCooldownTime = 0;
				p.hurtServer(level, p.damageSources().arrow(arrow, archer), 3f);
			}
			p.stopUsingItem();
			int kept = p.getInventory().countItem(Items.ARROW) - arrowsBefore;
			if (kept < 1 || kept > 23) {
				return "Pincushion: kept " + kept + " of 24 blocked arrows";
			}

			// Still Standing (feat): living through a 16-damage hit earns it...
			full(p);
			p.damageCooldownTime = 0;
			p.hurtServer(level, p.damageSources().magic(), 16f);
			if (!p.isAlive() || !Perks.has(p, DefenseSkill.STILL_STANDING)) {
				return "Still Standing not earned (alive " + p.isAlive() + ", max health " + p.getMaxHealth() + ")";
			}
			// ...and the first hit over 10 each minute is cut by a third; the next is not.
			Cooldowns.reset(p, Cooldowns.STILL_STANDING);
			float max = full(p);
			p.damageCooldownTime = 0;
			p.hurtServer(level, p.damageSources().magic(), 12f);
			float first = max - p.getHealth();
			full(p);
			p.damageCooldownTime = 0;
			p.hurtServer(level, p.damageSources().magic(), 12f);
			float second = max - p.getHealth();
			if (Math.abs(first - 8f) > 0.01f || Math.abs(second - 12f) > 0.01f) {
				return "Still Standing: hits of 12 took " + first + " then " + second + ", expected 8 then 12";
			}
			return null;
		}));

		// Roll with the Blow (teaching): a quarter of a hit arrives over the next four seconds.
		server.runCommand("mettle perk grant mettle:defense/roll_with_the_blow @a");
		float[] max = new float[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, DefenseSkill.ROLL_WITH_THE_BLOW)) {
				return "Roll with the Blow not granted";
			}
			max[0] = full(p);
			p.damageCooldownTime = 0;
			p.hurtServer(p.level(), p.damageSources().magic(), 8f);
			float now = max[0] - p.getHealth();
			if (Math.abs(now - 6f) > 0.01f || Math.abs(Wounds.pending(p) - 2f) > 0.01f) {
				return "Roll with the Blow: took " + now + " at once (expected 6), " + Wounds.pending(p) + " held back (expected 2)";
			}
			return null;
		}));
		server.waitFor(s -> Wounds.pending(player(s)) == 0f, 140);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float total = max[0] - p.getHealth();
			return Math.abs(total - 8f) < 0.01f ? null : "Roll with the Blow: " + total + " taken after four seconds, expected 8";
		}));
		discard(server);
	}

	// ---------------------------------------------------------------- helpers

	private static void cleanUp(TestServerContext server) {
		server.runCommand("execute as @p run mettle respec");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			// Earned perks survive a respec by design; take this journey's back so the tour leaves a plain Ranger.
			CharacterState state = CharacterStore.get(p);
			Map<net.minecraft.resources.ResourceKey<dev.voidmc.mettle.api.Perk>, Integer> kept = new HashMap<>(state.perks());
			kept.keySet().removeIf(k -> k.identifier().getPath().startsWith("defense/"));
			CharacterStore.set(p, state.withPerks(kept));
			EffectsApplier.apply(p);
			Cooldowns.reset(p, Cooldowns.LAST_STAND);
			Cooldowns.reset(p, Cooldowns.SECOND_WIND);
			Cooldowns.reset(p, Cooldowns.STILL_STANDING);
			p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			p.removeAllEffects();
			return null;
		}));
		server.runCommand("mettle background set mettle:ranger @a");
		server.runCommand("gamerule natural_health_regeneration true");
		server.runCommand("difficulty easy");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setHealth(p.getMaxHealth());
			return Perks.has(p, DefenseSkill.THICK_SKIN) || Perks.has(p, DefenseSkill.ROLL_WITH_THE_BLOW) ? "Defense perks left after clean-up" : null;
		}));
	}

	/** Raises Vitality to the mastery gate (14) with the character's free points. */
	private static void vitality(TestServerContext server) {
		for (int i = 0; i < 14; i++) {
			server.runCommand("execute as @p run mettle attribute vitality");
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			int v = CharacterStore.get(p).attribute(CharacterAttribute.VITALITY, CharacterStore.background(p.level().registryAccess(), CharacterStore.get(p)));
			return v >= 14 ? null : "Vitality only reached " + v;
		}));
	}

	/**
	 * A still mob {@code distance} blocks from the player, {@code degrees} round from where the player's head faces
	 * (0 straight ahead, 180 behind).
	 */
	private static Mob spawn(TestServerContext server, EntityType<? extends Mob> type, double degrees, double distance) {
		return server.computeOnServer(s -> spawnNow(player(s), type, degrees, distance));
	}

	private static Mob spawnNow(ServerPlayer p, EntityType<? extends Mob> type, double degrees, double distance) {
		double yaw = Math.toRadians(p.getYHeadRot() + degrees);
		Mob mob = type.create(p.level(), EntitySpawnReason.COMMAND);
		mob.setNoAi(true);
		mob.setSilent(true);
		mob.addTag(TAG);
		mob.snapTo(p.getX() - Math.sin(yaw) * distance, p.getY(), p.getZ() + Math.cos(yaw) * distance, 0f, 0f);
		p.level().addFreshEntity(mob);
		return mob;
	}

	private static void discard(TestServerContext server) {
		server.runCommand("kill @e[tag=" + TAG + "]");
		server.runCommand("kill @e[type=arrow]");
		server.runCommand("kill @e[type=item]");
	}

	private static void waitTicks(TestServerContext server, int ticks) {
		int target = server.computeOnServer(MinecraftServer::getTickCount) + ticks;
		server.waitFor(s -> s.getTickCount() >= target, ticks + 40);
	}

	/** Heals the player to full and returns the maximum. */
	private static float full(ServerPlayer p) {
		p.setHealth(p.getMaxHealth());
		return p.getMaxHealth();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError("Defense: " + problem);
		}
	}
}
