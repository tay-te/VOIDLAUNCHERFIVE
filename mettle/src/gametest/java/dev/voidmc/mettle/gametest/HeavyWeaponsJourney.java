package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.HeavyWeaponsSkill;
import dev.voidmc.mettle.character.skill.heavy.Stagger;
import dev.voidmc.mettle.character.skill.heavy.Stance;
import dev.voidmc.mettle.character.skill.heavy.Strikes;
import dev.voidmc.mettle.character.skill.heavy.ThrownAxe;
import dev.voidmc.mettle.character.skill.heavy.Woodcraft;
import dev.voidmc.mettle.client.heavy.HeavyWeaponsClient;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Heavy Weapons tree played for real: each perk triggered on the server and checked. Three builds in a row (the
 * Woodsman with Timber, the Crusher with Earthshaker, then Berserker), the feats, the synergy and the teaching. Ends
 * with a respec and the player back as a Ranger.
 */
final class HeavyWeaponsJourney {
	private static final String LEARN = "execute as @p run mettle perk learn mettle:heavy_weapons/";
	private static final String DUMMY = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:200f,Tags:[\"hw\",\"%s\"],"
		+ "attributes:[{id:\"minecraft:max_health\",base:200},{id:\"minecraft:armor\",base:%s}]}";

	private HeavyWeaponsJourney() {
	}

	static void run(TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("gamerule natural_health_regeneration false");
		server.runCommand("mettle respec @a");
		server.runCommand("mettle background set mettle:smith @a");
		server.runCommand("effect clear @a");
		server.runCommand("kill @e[type=item]");
		// A clear, flat yard round the player: dummies must not stand in a hill or a tree.
		server.runCommand("execute at @p run fill ~-8 ~ ~-8 ~10 ~8 ~8 minecraft:air");
		server.runCommand("execute at @p run fill ~-8 ~-1 ~-8 ~10 ~-1 ~8 minecraft:grass_block");
		Vec3[] home = new Vec3[1];
		ItemStack[] saved = new ItemStack[2];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			home[0] = p.position();
			saved[0] = p.getMainHandItem().copy();
			// A shield in the other hand would take the sneak-use (Throwing Axe stands aside for blocking).
			saved[1] = p.getOffhandItem().copy();
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			p.setHealth(p.getMaxHealth());
			return null;
		}));

		server.runCommand("mettle xp mettle:heavy_weapons 30000 @a");
		strength(server);
		check(server.computeOnServer(s -> {
			CharacterState state = CharacterStore.get(player(s));
			if (state.level(Combat.HEAVY_WEAPONS) != 20) {
				return "Heavy Weapons level " + state.level(Combat.HEAVY_WEAPONS) + ", expected 20";
			}
			return null;
		}));

		woodsman(world, server, home[0]);
		crusher(server);
		berserker(server);
		feats(server);
		stonebreaker(server);
		overheadBlow(server);

		// Log Hauler is a weight perk: its data ships now, the burden module gives it effect.
		check(server.computeOnServer(s -> Perks.registry(player(s)).containsKey(Perks.key(HeavyWeaponsSkill.LOG_HAULER)) ? null : "Log Hauler missing"));

		server.runCommand("mettle respec @a");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (Perks.has(p, HeavyWeaponsSkill.BERSERKER) || !Perks.has(p, HeavyWeaponsSkill.HEADHUNTER) || !Perks.has(p, HeavyWeaponsSkill.OVERHEAD_BLOW)) {
				return "respec: " + CharacterStore.get(p).perks();
			}
			p.setShiftKeyDown(false);
			p.setItemInHand(InteractionHand.MAIN_HAND, saved[0]);
			p.setItemInHand(InteractionHand.OFF_HAND, saved[1]);
			p.setHealth(p.getMaxHealth());
			p.teleportTo(home[0].x, home[0].y, home[0].z);
			return null;
		}));
		server.runCommand("kill @e[tag=hw]");
		server.runCommand("kill @e[type=item]");
		server.runCommand("effect clear @a");
		server.runCommand("gamerule natural_health_regeneration true");
		server.runCommand("mettle background set mettle:ranger @a");
	}

	// ---------------------------------------------------------------- Woodsman: roots, Cleave, Rhythm, Throwing Axe, Timber

	private static void woodsman(TestSingleplayerContext world, TestServerContext server, Vec3 home) {
		float[] base = new float[1];
		check(server.computeOnServer(s -> {
			base[0] = player(s).getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
			return null;
		}));
		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "heft");
			server.runCommand(LEARN + "feller");
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (Perks.rank(p, HeavyWeaponsSkill.HEFT) != 3 || Perks.rank(p, HeavyWeaponsSkill.FELLER) != 3) {
				return "roots not learned: " + CharacterStore.get(p).perks();
			}
			float speed = p.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
			return near(speed, base[0] * 1.45f) ? null : "Feller III: log speed " + speed + ", expected " + base[0] * 1.45f;
		}));

		// Heft III: a 10-point axe blow lands as 11.5.
		dummy(server, "hw_heft", 3, 0, 0);
		server.runCommand("damage @e[tag=hw_heft,limit=1] 10 minecraft:player_attack by @p");
		check(server.computeOnServer(s -> {
			float loss = 200 - mob(s, "hw_heft").getHealth();
			return near(loss, 11.5f) ? null : "Heft III: took " + loss + ", expected 11.5";
		}));

		server.runCommand(LEARN + "cleave");
		server.runCommand(LEARN + "rhythm_of_the_axe");
		server.runCommand(LEARN + "throwing_axe");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return Perks.has(p, HeavyWeaponsSkill.CLEAVE) && Perks.has(p, HeavyWeaponsSkill.RHYTHM_OF_THE_AXE) && Perks.has(p, HeavyWeaponsSkill.THROWING_AXE)
				? null : "Woodsman perks not learned: " + CharacterStore.get(p).perks();
		}));

		// Cleave: a real swing at one dummy spills 30 % onto the one beside it.
		dummy(server, "hw_cleave_a", 3, 0, -3);
		dummy(server, "hw_cleave_b", 3, 0, -2);
		charged(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			LivingEntity a = mob(s, "hw_cleave_a");
			LivingEntity b = mob(s, "hw_cleave_b");
			p.attack(a);
			float lossA = 200 - a.getHealth();
			float lossB = 200 - b.getHealth();
			if (lossA <= 0) {
				return "Cleave: the swing missed";
			}
			return near(lossB, lossA * 0.3f) ? null : "Cleave: neighbour took " + lossB + ", expected " + lossA * 0.3f;
		}));
		server.runCommand("kill @e[tag=hw]");

		// Rhythm of the Axe: two logs in quick succession make the third 30 % faster.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			BlockState log = Blocks.OAK_LOG.defaultBlockState();
			float before = p.getDestroySpeed(log);
			BlockPos at = p.blockPosition().offset(-4, 0, -4);
			for (int i = 0; i < 2; i++) {
				level.setBlock(at.above(i), log, Block.UPDATE_ALL);
			}
			p.gameMode.destroyBlock(at.above(1));
			p.gameMode.destroyBlock(at);
			float after = p.getDestroySpeed(log);
			return near(after, before * 1.30f) ? null : "Rhythm: speed " + after + " after two logs, expected " + before * 1.30f;
		}));

		throwingAxe(world, server, home);
		timber(server);
	}

	private static void throwingAxe(TestSingleplayerContext world, TestServerContext server, Vec3 home) {
		ensureRenderer(server);
		dummy(server, "hw_throw", 7, 0, 0);
		server.runCommand("execute as @p at @p run tp @s ~ ~ ~ facing entity @e[tag=hw_throw,limit=1] eyes");
		check(server.computeOnServer(s -> {
			ItemStack axe = new ItemStack(Items.IRON_AXE);
			axe.set(DataComponents.CUSTOM_NAME, Component.literal("Hurled"));
			player(s).setItemInHand(InteractionHand.MAIN_HAND, axe);
			return null;
		}));
		charged(server);
		float[] expected = new float[1];
		double[] xpBefore = new double[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			expected[0] = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.15f;
			xpBefore[0] = CharacterStore.get(p).xp(Combat.HEAVY_WEAPONS);
			p.setShiftKeyDown(true);
			p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			p.setShiftKeyDown(false);
			if (!p.getMainHandItem().isEmpty()) {
				return "Throwing Axe: the axe is still in hand";
			}
			return axes(s).size() == 1 ? null : "Throwing Axe: " + axes(s).size() + " axes in flight";
		}));
		server.waitFor(s -> axes(s).isEmpty() || axes(s).getFirst().tickCount >= 2, 40);
		screenshot(server, "mettle_heavy_throw_flight.png");
		server.waitFor(s -> mob(s, "hw_throw").getHealth() < 200, 60);
		server.waitFor(s -> !axes(s).isEmpty() && axes(s).getFirst().landed(), 100);
		screenshot(server, "mettle_heavy_throw_landed.png");
		server.runCommand("execute as @p at @e[type=mettle:thrown_axe,limit=1] run tp @s ~2.5 ~0.8 ~1.5 facing entity @e[type=mettle:thrown_axe,limit=1] feet");
		ticks(server, 5);
		screenshot(server, "mettle_heavy_throw_landed_side.png");
		check(server.computeOnServer(s -> {
			float loss = 200 - mob(s, "hw_throw").getHealth();
			if (!near(loss, expected[0])) {
				return "Throwing Axe: dummy took " + loss + ", expected " + expected[0];
			}
			if (CharacterStore.get(player(s)).xp(Combat.HEAVY_WEAPONS) <= xpBefore[0]) {
				return "Throwing Axe: no Heavy Weapons XP for the hit";
			}
			ThrownAxe axe = axes(s).getFirst();
			player(s).teleportTo(axe.getX(), axe.getY(), axe.getZ());
			return null;
		}));
		server.waitFor(s -> axes(s).isEmpty(), 60);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			int slot = -1;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack it = p.getInventory().getItem(i);
				if (it.is(Items.IRON_AXE) && "Hurled".equals(it.getHoverName().getString())) {
					slot = i;
				}
			}
			if (slot < 0) {
				return "Throwing Axe: the thrower didn't get the axe back";
			}
			ItemStack back = p.getInventory().getItem(slot);
			p.getInventory().setItem(slot, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.MAIN_HAND, back);
			p.teleportTo(home.x, home.y, home.z);
			return back.getDamageValue() == 1 ? null : "Throwing Axe: came back with " + back.getDamageValue() + " wear, expected 1";
		}));
		server.runCommand("kill @e[tag=hw]");
	}

	private static void timber(TestServerContext server) {
		server.runCommand(LEARN + "timber");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, HeavyWeaponsSkill.TIMBER)) {
				return "Timber not learned: " + CharacterStore.get(p).perks();
			}
			ServerLevel level = p.level();
			BlockPos base = p.blockPosition().offset(6, 0, -5);
			tree(level, base, 6);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			p.setShiftKeyDown(true);
			p.gameMode.destroyBlock(base);
			p.setShiftKeyDown(false);
			for (int i = 0; i < 6; i++) {
				if (!level.getBlockState(base.above(i)).isAir()) {
					return "Timber left a log at +" + i;
				}
			}
			int wear = p.getMainHandItem().getDamageValue();
			return wear == 6 ? null : "Timber: axe wore " + wear + ", expected 6 (one per log)";
		}));

		// A wall of logs the player placed is not a tree, even with leaves on it: Timber leaves it standing.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			BlockPos wall = p.blockPosition().offset(6, 0, 4);
			ItemStack logs = new ItemStack(Items.OAK_LOG, 3);
			p.setItemInHand(InteractionHand.MAIN_HAND, logs);
			for (int i = 0; i < 3; i++) {
				BlockPos below = wall.above(i - 1);
				p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
				if (!PlacedBlocks.isPlaced(level, wall.above(i))) {
					return "placed log " + i + " not remembered (" + level.getBlockState(wall.above(i)) + ")";
				}
			}
			for (int i = 0; i < 3; i++) {
				level.setBlock(wall.above(i).east(), leaves(), Block.UPDATE_ALL);
			}
			long counted = FeatCounters.get(p, Woodcraft.NATURAL_LOG_COUNTER);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			p.setShiftKeyDown(true);
			p.gameMode.destroyBlock(wall);
			p.setShiftKeyDown(false);
			if (!Woodcraft.isLog(level.getBlockState(wall.above(1))) || !Woodcraft.isLog(level.getBlockState(wall.above(2)))) {
				return "Timber felled a placed log wall";
			}
			return FeatCounters.get(p, Woodcraft.NATURAL_LOG_COUNTER) == counted ? null : "a placed log counted towards Old Growth";
		}));
		server.runCommand("kill @e[type=item]");
	}

	// ---------------------------------------------------------------- Crusher: Armour Crusher, Bone Breaker, Iron Grip, Stagger, Earthshaker

	private static void crusher(TestServerContext server) {
		server.runCommand("mettle respec @a");
		strength(server);
		for (String perk : new String[] {"armour_crusher", "bone_breaker", "iron_grip", "stagger", "earthshaker"}) {
			server.runCommand(LEARN + perk);
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			return Perks.has(p, HeavyWeaponsSkill.EARTHSHAKER) && Perks.has(p, HeavyWeaponsSkill.STAGGER) && !Perks.has(p, HeavyWeaponsSkill.TIMBER)
				? null : "Crusher build not learned: " + CharacterStore.get(p).perks();
		}));

		// Armour Crusher: against 20 armour a 10-point blow does 6, not 4 (the armour counts as 15).
		server.runCommand("execute at @p run summon minecraft:husk ~3 ~ ~ " + DUMMY.formatted("hw_armour", 20));
		server.runCommand("damage @e[tag=hw_armour,limit=1] 10 minecraft:player_attack by @p");
		check(server.computeOnServer(s -> {
			float loss = 200 - mob(s, "hw_armour").getHealth();
			return near(loss, 6f) ? null : "Armour Crusher: took " + loss + ", expected 6";
		}));
		server.runCommand("kill @e[tag=hw]");

		// Iron Grip: an axe in hand means no knockback and +2 toughness; an empty hand, neither.
		server.waitFor(s -> player(s).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0
			&& player(s).getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= 2.0, 20);
		check(server.computeOnServer(s -> {
			player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return null;
		}));
		server.waitFor(s -> player(s).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0, 20);
		check(server.computeOnServer(s -> {
			player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			return null;
		}));

		// Bone Breaker: a jump-crit slows the target by 40 % for three seconds.
		dummy(server, "hw_bone", 3, 0, 2);
		charged(server);
		check(server.computeOnServer(s -> {
			LivingEntity target = mob(s, "hw_bone");
			double speed = target.getAttributeValue(Attributes.MOVEMENT_SPEED);
			crit(player(s), target);
			double slowed = target.getAttributeValue(Attributes.MOVEMENT_SPEED);
			return near((float) slowed, (float) speed * 0.6f) ? null : "Bone Breaker: speed " + slowed + ", expected " + speed * 0.6;
		}));
		server.waitFor(s -> !mob(s, "hw_bone").getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Stagger.BONE_BREAKER_SLOW), 100);

		// Stagger: after a full swing the husk's blows miss while it is dazed, and its next one misses too.
		dummy(server, "hw_stagger", 3, 0, -2);
		charged(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob husk = (Mob) mob(s, "hw_stagger");
			p.attack(husk);
			float health = p.getHealth();
			if (husk.doHurtTarget(p.level(), p) || p.getHealth() != health) {
				return "Stagger: a dazed husk still landed its blow";
			}
			return null;
		}));
		server.waitFor(s -> !Stagger.isStaggered((Mob) mob(s, "hw_stagger")), 40);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob husk = (Mob) mob(s, "hw_stagger");
			if (husk.doHurtTarget(p.level(), p)) {
				return "Stagger: the husk's next attack didn't miss";
			}
			if (!husk.doHurtTarget(p.level(), p)) {
				return "Stagger: the husk could never hit again";
			}
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		server.runCommand("effect clear @a");

		// Stagger puts out a creeper's fuse.
		server.runCommand("execute at @p run summon minecraft:creeper ~3 ~ ~ {NoAI:1b,Silent:1b,Fuse:600,Tags:[\"hw\",\"hw_creeper\"]}");
		check(server.computeOnServer(s -> {
			((Creeper) mob(s, "hw_creeper")).setSwellDir(1);
			return null;
		}));
		server.waitFor(s -> ((Creeper) mob(s, "hw_creeper")).getSwelling(1f) > 0.05f, 60);
		charged(server);
		check(server.computeOnServer(s -> {
			Creeper creeper = (Creeper) mob(s, "hw_creeper");
			player(s).attack(creeper);
			return creeper.getSwelling(1f) < 0.01f && creeper.getSwellDir() < 0 ? null : "Stagger: the creeper is still fusing (" + creeper.getSwelling(1f) + ")";
		}));
		server.runCommand("kill @e[tag=hw]");

		// Earthshaker: a sneaking full swing throws back and dazes every foe within four blocks, then waits ten seconds.
		dummy(server, "hw_quake_a", 2, 0, 2);
		dummy(server, "hw_quake_b", -2, 0, -1);
		charged(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob near = (Mob) mob(s, "hw_quake_b");
			p.setShiftKeyDown(true);
			p.attack(mob(s, "hw_quake_a"));
			p.setShiftKeyDown(false);
			if (!Stagger.isStaggered(near) || near.getDeltaMovement().horizontalDistance() < 0.3) {
				return "Earthshaker: the second husk wasn't thrown back and dazed (" + near.getDeltaMovement() + ")";
			}
			return Strikes.earthshakerCooldown(p) > 150 ? null : "Earthshaker: no cooldown";
		}));
		server.runCommand("kill @e[tag=hw]");
	}

	// ---------------------------------------------------------------- Berserker

	private static void berserker(TestServerContext server) {
		server.runCommand("mettle respec @a");
		strength(server);
		server.runCommand(LEARN + "cleave");
		server.runCommand(LEARN + "armour_crusher");
		server.runCommand(LEARN + "berserker");
		dummy(server, "hw_rage", 3, 0, 0);
		charged(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, HeavyWeaponsSkill.BERSERKER)) {
				return "Berserker not learned: " + CharacterStore.get(p).perks();
			}
			LivingEntity target = mob(s, "hw_rage");
			float swing = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
			p.setHealth(6f);
			p.attack(target);
			float loss = 200 - target.getHealth();
			if (!near(loss, swing * 1.4f)) {
				return "Berserker: dealt " + loss + ", expected " + swing * 1.4f;
			}
			if (!near(p.getHealth(), 6f + loss * 0.2f)) {
				return "Berserker: healed to " + p.getHealth() + ", expected " + (6f + loss * 0.2f);
			}
			// The lifesteal lifted us out of the rage; back under 40 % for the rest.
			p.setHealth(6f);
			return null;
		}));
		server.waitFor(s -> player(s).getAttribute(Attributes.KNOCKBACK_RESISTANCE).hasModifier(Stance.BERSERKER_KNOCKBACK), 20);
		server.runCommand("effect give @p minecraft:slowness 10 1");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (p.hasEffect(MobEffects.SLOWNESS)) {
				return "Berserker: slowed while raging";
			}
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		server.waitFor(s -> !player(s).getAttribute(Attributes.KNOCKBACK_RESISTANCE).hasModifier(Stance.BERSERKER_KNOCKBACK), 20);
		server.runCommand("kill @e[tag=hw]");
	}

	// ---------------------------------------------------------------- feats, synergy, teaching

	private static void feats(TestServerContext server) {
		// Headhunter: the hundredth hostile felled with an axe earns it; then heads start to drop.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			FeatCounters.add(p, HeavyWeaponsSkill.HOSTILE_AXE_KILLS, 99 - FeatCounters.get(p, HeavyWeaponsSkill.HOSTILE_AXE_KILLS));
			return Perks.has(p, HeavyWeaponsSkill.HEADHUNTER) ? "Headhunter earned too early" : null;
		}));
		boolean head = false;
		for (int i = 0; i < 60 && !head; i++) {
			server.runCommand("execute at @p run summon minecraft:zombie ~3 ~ ~ {Tags:[\"hw\",\"hw_head\"]}");
			server.runCommand("damage @e[tag=hw_head,limit=1] 500 minecraft:player_attack by @p");
			if (i == 0) {
				check(server.computeOnServer(s -> Perks.has(player(s), HeavyWeaponsSkill.HEADHUNTER) ? null : "Headhunter not earned at 100 axe kills"));
			}
			head = server.computeOnServer(s -> !items(s, Items.ZOMBIE_HEAD).isEmpty());
		}
		if (!head) {
			throw new AssertionError("Headhunter: 59 zombies and not one head");
		}
		server.runCommand("kill @e[type=item]");
		server.runCommand("kill @e[tag=hw]");

		// Old Growth: the two-thousandth natural log earns it; after that chopping doesn't wear the axe.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			FeatCounters.add(p, Woodcraft.NATURAL_LOG_COUNTER, 1999 - FeatCounters.get(p, Woodcraft.NATURAL_LOG_COUNTER));
			BlockPos at = p.blockPosition().offset(-4, 0, 4);
			level.setBlock(at, Blocks.BIRCH_LOG.defaultBlockState(), Block.UPDATE_ALL);
			p.gameMode.destroyBlock(at);
			if (!Perks.has(p, HeavyWeaponsSkill.OLD_GROWTH)) {
				return "Old Growth not earned at 2000 natural logs";
			}
			int wear = p.getMainHandItem().getDamageValue();
			level.setBlock(at, Blocks.BIRCH_LOG.defaultBlockState(), Block.UPDATE_ALL);
			p.gameMode.destroyBlock(at);
			return p.getMainHandItem().getDamageValue() == wear ? null : "Old Growth: the axe still wore chopping";
		}));
		server.runCommand("kill @e[type=item]");
	}

	private static void stonebreaker(TestServerContext server) {
		server.runCommand("mettle xp mettle:mining 5000 @a");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, HeavyWeaponsSkill.STONEBREAKER)) {
				return "Stonebreaker not unlocked at Mining " + CharacterStore.get(p).level(dev.voidmc.mettle.character.skill.MiningSkill.MINING) + " and Heavy Weapons 20";
			}
			BlockState stone = Blocks.STONE.defaultBlockState();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			float pick = p.getDestroySpeed(stone);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
			float mace = p.getDestroySpeed(stone);
			if (!near(mace, pick)) {
				return "Stonebreaker: a mace digs stone at " + mace + ", an iron pickaxe at " + pick;
			}
			if (!p.hasCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()) || p.hasCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) {
				return "Stonebreaker: wrong tool tier";
			}
			ServerLevel level = p.level();
			BlockPos at = p.blockPosition().offset(-4, 0, 2);
			level.setBlock(at, Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_ALL);
			p.gameMode.destroyBlock(at);
			return !items(s, Items.RAW_IRON).isEmpty() ? null : "Stonebreaker: iron ore broken with a mace dropped nothing";
		}));
		server.runCommand("kill @e[type=item]");
	}

	private static void overheadBlow(TestServerContext server) {
		dummy(server, "hw_plain", 3, 0, -3);
		dummy(server, "hw_overhead", 3, 0, 3);
		float[] plain = new float[1];
		check(server.computeOnServer(s -> {
			player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			return null;
		}));
		charged(server);
		check(server.computeOnServer(s -> {
			LivingEntity target = mob(s, "hw_plain");
			crit(player(s), target);
			plain[0] = 200 - target.getHealth();
			return plain[0] > 0 ? null : "Overhead Blow: the plain crit missed";
		}));
		server.runCommand("mettle perk grant mettle:heavy_weapons/overhead_blow @a");
		charged(server);
		check(server.computeOnServer(s -> {
			LivingEntity target = mob(s, "hw_overhead");
			crit(player(s), target);
			float loss = 200 - target.getHealth();
			if (!near(loss, plain[0] * 2f / 1.5f)) {
				return "Overhead Blow: crit dealt " + loss + ", expected " + plain[0] * 2f / 1.5f;
			}
			return Stagger.isStaggered((Mob) target) ? null : "Overhead Blow: target not staggered";
		}));
		server.runCommand("kill @e[tag=hw]");
	}

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Strength to 14 (Smith starts at 6), the mastery gate. */
	private static void strength(TestServerContext server) {
		for (int i = 0; i < 8; i++) {
			server.runCommand("execute as @p run mettle attribute strength");
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			int strength = state.attribute(CharacterAttribute.STRENGTH, CharacterStore.background(p.level().registryAccess(), state));
			return strength >= 14 ? null : "Strength " + strength + ", expected 14";
		}));
	}

	private static void dummy(TestServerContext server, String tag, int dx, int dy, int dz) {
		server.runCommand("execute at @p run summon minecraft:husk ~" + dx + " ~" + dy + " ~" + dz + " " + DUMMY.formatted(tag, 0));
	}

	private static LivingEntity mob(MinecraftServer server, String tag) {
		ServerPlayer p = player(server);
		return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), e -> e.entityTags().contains(tag)).stream()
			.findFirst().orElseThrow(() -> new AssertionError("test mob " + tag + " missing"));
	}

	private static List<ThrownAxe> axes(MinecraftServer server) {
		ServerPlayer p = player(server);
		return p.level().getEntitiesOfClass(ThrownAxe.class, p.getBoundingBox().inflate(48), e -> true);
	}

	private static List<ItemEntity> items(MinecraftServer server, net.minecraft.world.item.Item item) {
		ServerPlayer p = player(server);
		return p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16), e -> e.getItem().is(item));
	}

	private static void ticks(TestServerContext server, int n) {
		int[] seen = {0};
		server.waitFor(s -> ++seen[0] > n, n + 20);
	}

	/** Waits until the next swing is fully charged, as a player would. */
	private static void charged(TestServerContext server) {
		server.waitFor(s -> player(s).getAttackStrengthScale(0.5f) >= 1f, 100);
	}

	/** A falling (critical) swing at the target, as after a jump. */
	private static void crit(ServerPlayer p, LivingEntity target) {
		p.setSprinting(false);
		p.fallDistance = 1.0;
		p.setOnGround(false);
		p.attack(target);
		p.setOnGround(true);
		p.fallDistance = 0;
	}

	private static BlockState leaves() {
		return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1).setValue(LeavesBlock.PERSISTENT, false);
	}

	/** A natural oak: a trunk {@code height} logs tall on dirt, with a crown of leaves round its top. */
	private static void tree(ServerLevel level, BlockPos base, int height) {
		level.setBlock(base.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
		for (int i = 0; i < height; i++) {
			level.setBlock(base.above(i), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
		}
		for (int dy = height - 2; dy <= height; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos p = base.offset(dx, dy, dz);
					if (level.getBlockState(p).isAir()) {
						level.setBlock(p, leaves(), Block.UPDATE_ALL);
					}
				}
			}
		}
	}

	/** Until the coordinator wires HeavyWeaponsClient into MettleClient, register the thrown axe's renderer here. */
	private static void ensureRenderer(TestServerContext server) {
		if (HeavyWeaponsClient.initialised()) {
			return;
		}
		onClient(server, () -> {
			Minecraft mc = Minecraft.getInstance();
			HeavyWeaponsClient.init();
			mc.getEntityRenderDispatcher().onResourceManagerReload(mc.getResourceManager());
		});
	}

	private static void screenshot(TestServerContext server, String name) {
		onClient(server, () -> {
			Minecraft mc = Minecraft.getInstance();
			Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {
			});
		});
	}

	/**
	 * Runs a task on the client thread. The journey only has the singleplayer context, and the gametest thread may not
	 * touch Minecraft.getInstance(), so the integrated server's thread hands the task to the client's queue.
	 */
	private static void onClient(TestServerContext server, Runnable task) {
		AtomicBoolean done = new AtomicBoolean();
		server.runOnServer(s -> Minecraft.getInstance().execute(() -> {
			task.run();
			done.set(true);
		}));
		server.waitFor(s -> done.get(), 200);
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < 0.02f;
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError("Heavy Weapons: " + problem);
		}
	}
}
