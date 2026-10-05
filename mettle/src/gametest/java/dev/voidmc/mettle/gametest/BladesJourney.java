package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.BladesSkill;
import dev.voidmc.mettle.character.skill.blades.Aftermath;
import dev.voidmc.mettle.character.skill.blades.Bleeding;
import dev.voidmc.mettle.character.skill.blades.Parry;
import dev.voidmc.mettle.character.skill.blades.Whirlwind;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.mixin.blades.BladesLivingAccess;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;

/**
 * The Blades tree played for real on the server: every perk triggered through the game's own attack, damage, use and
 * kill paths, and its effect measured. Three careers (Duelist, Skirmisher, Whirlwind) with a respec between, then the
 * earned perks. Fights happen on a platform in the sky so terrain can't get in the way; the player is put back after.
 */
final class BladesJourney {
	private static final String LEARN = "execute as @p run mettle perk learn mettle:blades/";
	private static final String TAG = "blades_t";

	private BladesJourney() {
	}

	static void run(TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		double[] home = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return new double[] {p.getX(), p.getY(), p.getZ()};
		});
		server.runCommand("mettle background set mettle:ranger @a");
		server.runCommand("effect clear @a");
		server.runCommand("execute as @p at @s run fill ~-10 199 ~-10 ~10 199 ~10 minecraft:smooth_stone");
		server.runCommand("execute as @p at @s run fill ~-10 200 ~-10 ~10 206 ~10 minecraft:air");
		server.runCommand("execute as @p at @s run tp @s ~ 200 ~ 0 0");
		server.runCommand("mettle xp mettle:blades 30000 @a");
		for (int i = 0; i < 8; i++) {
			server.runCommand("execute as @p run mettle attribute agility");
		}
		hold(server, Items.IRON_SWORD);
		wait(world, 15);

		duelist(world, server);
		server.runCommand("execute as @p run mettle respec");
		for (int i = 0; i < 8; i++) {
			server.runCommand("execute as @p run mettle attribute agility");
		}
		skirmisher(world, server);
		server.runCommand("execute as @p run mettle respec");
		for (int i = 0; i < 8; i++) {
			server.runCommand("execute as @p run mettle attribute agility");
		}
		whirlwind(world, server);
		spearWhirlwind(world, server);
		earned(world, server);

		// Leave the character as found: bought perks refunded, earned Blades perks taken back (so they can't colour the
		// journeys after this one), and the player home.
		server.runCommand("execute as @p run mettle respec");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			Map<ResourceKey<Perk>, Integer> kept = new HashMap<>(state.perks());
			kept.keySet().removeIf(k -> k.identifier().getPath().startsWith("blades/"));
			CharacterStore.set(p, state.withPerks(kept));
			EffectsApplier.apply(p);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			p.setShiftKeyDown(false);
			p.setSprinting(false);
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		server.runCommand("kill @e[tag=" + TAG + "]");
		server.runCommand("kill @e[type=item]");
		server.runCommand("effect clear @a");
		server.runCommand("mettle background set mettle:ranger @a");
		server.runCommand("tp @p " + home[0] + " " + home[1] + " " + home[2]);
		wait(world, 5);
	}

	// ---------------------------------------------------------------- Path A, Duelist mastery

	private static void duelist(TestSingleplayerContext world, TestServerContext server) {
		// Fencer: blades swing faster, other things don't.
		double[] speeds = server.computeOnServer(s -> new double[] {player(s).getAttributeValue(Attributes.ATTACK_SPEED)});
		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "fencer");
		}
		server.runCommand(LEARN + "quick_draw");
		wait(world, 12);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (Perks.rank(p, BladesSkill.FENCER) != 3 || Perks.rank(p, BladesSkill.QUICK_DRAW) != 1) {
				return "roots not learned: " + CharacterStore.get(p).perks() + " level " + CharacterStore.get(p).level(dev.voidmc.mettle.character.skill.Combat.BLADES);
			}
			double speed = p.getAttributeValue(Attributes.ATTACK_SPEED);
			return near(speed, speeds[0] * 1.15) ? null : "Fencer III: swing speed " + speed + ", expected " + speeds[0] * 1.15;
		}));
		hold(server, Items.STICK);
		wait(world, 12);
		check(server.computeOnServer(s -> player(s).getAttribute(Attributes.ATTACK_SPEED).getModifier(Mettle.id("blades/fencer")) == null
			? null : "Fencer stayed on with a stick in hand"));
		hold(server, Items.IRON_SWORD);
		wait(world, 12);

		// Quick Draw I: the opener on a mob that isn't after you deals +20 %, the next hit doesn't.
		summon(server, "iron_golem", "^ ^ ^2", "qd");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			LivingEntity golem = find(p, "qd");
			float base = base(p);
			float first = hit(p, golem);
			float second = hit(p, golem);
			golem.discard();
			if (!near(first, base * 1.2f)) {
				return "Quick Draw: opener " + first + ", expected " + base * 1.2f;
			}
			return near(second, base) ? null : "Quick Draw: second hit " + second + ", expected " + base;
		}));

		// Measure: half a block more reach with a sword, and on a spear's own reach.
		server.runCommand(LEARN + "measure");
		server.runCommand(LEARN + "disarm");
		wait(world, 12);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			double reach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			if (!near(reach, 3.5)) {
				return "Measure: sword reach " + reach + ", expected 3.5";
			}
			ItemStack spear = new ItemStack(Items.IRON_SPEAR);
			float own = spear.get(DataComponents.ATTACK_RANGE).maxReach();
			float stretched = p.getAttackRangeWith(spear).maxReach();
			return near(stretched, own + 0.5f) ? null : "Measure: spear reach " + stretched + ", expected " + (own + 0.5f);
		}));

		// Disarm: critical hits knock a zombie's sword out of its hands, one time in five.
		summon(server, "husk", "^ ^ ^2", "disarm");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob zombie = (Mob) find(p, "disarm");
			zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			zombie.setTarget(p);
			for (int i = 0; i < 60 && !zombie.getMainHandItem().isEmpty(); i++) {
				zombie.setHealth(zombie.getMaxHealth());
				crit(p, zombie);
			}
			boolean dropped = !p.level().getEntitiesOfClass(ItemEntity.class, zombie.getBoundingBox().inflate(3),
				e -> e.getItem().is(Items.IRON_SWORD)).isEmpty();
			zombie.discard();
			return dropped ? null : "Disarm: the zombie kept its sword through 60 criticals";
		}));
		server.runCommand("kill @e[type=item]");

		// Single Combat: alone with one hostile, +20 % dealt and -20 % taken; a second hostile ends it.
		server.runCommand(LEARN + "single_combat");
		summon(server, "husk", "^ ^ ^2", "duel");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob zombie = (Mob) find(p, "duel");
			zombie.setTarget(p);
			float alone = hit(p, zombie);
			float aloneTaken = takeHit(p, zombie);
			zombie.setHealth(zombie.getMaxHealth());
			Mob second = summonNow(p, net.minecraft.world.entity.EntityTypes.HUSK, 0, 4);
			float crowded = hit(p, zombie);
			float crowdedTaken = takeHit(p, zombie);
			zombie.discard();
			second.discard();
			if (!near(alone, crowded * 1.2f)) {
				return "Single Combat: dealt " + alone + " alone vs " + crowded + " with two";
			}
			return near(aloneTaken, crowdedTaken * 0.8f) ? null : "Single Combat: took " + aloneTaken + " alone vs " + crowdedTaken + " with two";
		}));

		// Momentum: +10 % per quick hit on the same foe, up to +40 %.
		server.runCommand(LEARN + "momentum");
		summon(server, "iron_golem", "^ ^ ^2", "mom");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob golem = (Mob) find(p, "mom");
			golem.setTarget(p);
			float base = base(p);
			float[] expected = {1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.4f};
			for (int i = 0; i < expected.length; i++) {
				float dealt = hit(p, golem);
				if (!near(dealt, base * expected[i])) {
					golem.discard();
					return "Momentum: hit " + (i + 1) + " dealt " + dealt + ", expected " + base * expected[i];
				}
			}
			golem.discard();
			return null;
		}));

		// Duelist: a parry stops a zombie's hit from in front, staggers it, and the riposte is a critical.
		server.runCommand(LEARN + "duelist");
		summon(server, "husk", "^ ^ ^1.5", "parry");
		summon(server, "husk", "^ ^ ^-1.5", "behind");
		summon(server, "iron_golem", "^2 ^ ^2", "riposte");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, BladesSkill.DUELIST)) {
				return "Duelist not learned: " + CharacterStore.get(p).perks();
			}
			Mob zombie = (Mob) find(p, "parry");
			Mob behind = (Mob) find(p, "behind");
			Mob golem = (Mob) find(p, "riposte");
			golem.setTarget(p);
			ItemStack sword = p.getMainHandItem();
			p.gameMode.useItem(p, p.level(), sword, InteractionHand.MAIN_HAND);
			if (takeHit(p, behind) <= 0) {
				return "Duelist: a hit from behind was parried";
			}
			if (takeHit(p, zombie) != 0) {
				return "Duelist: the parried hit still hurt";
			}
			if (!Parry.staggered(zombie) || !zombie.hasEffect(MobEffects.SLOWNESS)) {
				return "Duelist: the attacker didn't stagger";
			}
			if (takeHit(p, zombie) != 0) {
				return "Duelist: a staggered zombie landed a hit";
			}
			if (!p.getCooldowns().isOnCooldown(sword)) {
				return "Duelist: no cooldown after a parry";
			}
			float base = base(p);
			float riposte = hit(p, golem);
			float next = hit(p, golem);
			zombie.discard();
			behind.discard();
			golem.discard();
			if (!near(riposte, base * 1.5f)) {
				return "Duelist: riposte dealt " + riposte + ", expected a critical " + base * 1.5f;
			}
			return near(next, base * 1.1f) ? null : "Duelist: the hit after the riposte dealt " + next + ", expected " + base * 1.1f;
		}));
	}

	// ---------------------------------------------------------------- Path B, Executioner mastery

	private static void skirmisher(TestSingleplayerContext world, TestServerContext server) {
		summon(server, "iron_golem", "^ ^ ^2", "sprint");
		Function<MinecraftServer, Boolean> sprintHit = s -> {
			ServerPlayer p = player(s);
			Mob golem = (Mob) find(p, "sprint");
			p.setSprinting(true);
			hit(p, golem);
			boolean still = p.isSprinting();
			p.setSprinting(false);
			return still;
		};
		if (server.computeOnServer(sprintHit::apply)) {
			throw new AssertionError("Light Feet: a sprint survived a hit before the perk");
		}
		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "quick_draw");
		}
		server.runCommand(LEARN + "light_feet");
		server.runCommand(LEARN + "wide_arc");
		wait(world, 12);
		if (!server.computeOnServer(sprintHit::apply)) {
			throw new AssertionError("Light Feet: the sprint broke");
		}
		server.runCommand("kill @e[tag=" + TAG + "]");

		// Wide Arc: a sweep deals 1 + 0.75 × the hit to the foe beside the target.
		summon(server, "iron_golem", "^ ^ ^2", "arc_a");
		summon(server, "iron_golem", "^1.5 ^ ^2", "arc_b");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob a = (Mob) find(p, "arc_a");
			Mob b = (Mob) find(p, "arc_b");
			a.setTarget(p);
			b.setTarget(p);
			double ratio = p.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO);
			float base = base(p);
			b.damageCooldownTime = 0;
			float before = b.getHealth();
			hit(p, a);
			float swept = before - b.getHealth();
			a.discard();
			b.discard();
			if (!near(ratio, 0.75)) {
				return "Wide Arc: sweep ratio " + ratio;
			}
			return near(swept, 1 + 0.75f * base) ? null : "Wide Arc: the swept golem took " + swept + ", expected " + (1 + 0.75f * base);
		}));

		// Bleed: a critical opens a wound that deals 4 more over 4 s.
		server.runCommand(LEARN + "bleed");
		summon(server, "iron_golem", "^ ^ ^2", "bleed");
		float[] afterCrit = new float[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob golem = (Mob) find(p, "bleed");
			golem.setTarget(p);
			crit(p, golem);
			afterCrit[0] = golem.getHealth();
			return Bleeding.wounds(golem) == 1 ? null : "Bleed: " + Bleeding.wounds(golem) + " wounds after a critical";
		}));
		wait(world, 110);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob golem = (Mob) find(p, "bleed");
			float bled = afterCrit[0] - golem.getHealth();
			int open = Bleeding.wounds(golem);
			golem.discard();
			return near(bled, 4f) && open == 0 ? null : "Bleed: bled " + bled + " (expected 4), " + open + " wounds still open";
		}));

		// Cut and Run: a kill gives Speed I and a fully charged swing that survives the swing packet.
		server.runCommand(LEARN + "cut_and_run");
		summon(server, "husk", "^ ^ ^2", "run");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob zombie = (Mob) find(p, "run");
			zombie.setHealth(1f);
			hit(p, zombie);
			p.resetAttackStrengthTicker();
			var speed = p.getEffect(MobEffects.SPEED);
			if (zombie.isAlive() || speed == null || speed.getAmplifier() != 0) {
				return "Cut and Run: no Speed I after a kill (" + speed + ")";
			}
			return p.getAttackStrengthScale(0.5f) >= 1f ? null : "Cut and Run: swing charge " + p.getAttackStrengthScale(0.5f);
		}));
		server.runCommand("effect clear @a");

		// Executioner: a hit that leaves a mob below a quarter kills it, with Speed II; a healthy mob just takes the hit.
		server.runCommand(LEARN + "executioner");
		summon(server, "husk", "^ ^ ^2", "exec");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, BladesSkill.EXECUTIONER)) {
				return "Executioner not learned: " + CharacterStore.get(p).perks();
			}
			Mob zombie = (Mob) find(p, "exec");
			zombie.setTarget(p);
			hit(p, zombie);
			if (!zombie.isAlive()) {
				return "Executioner: a healthy zombie died to one hit";
			}
			zombie.setHealth(9f);
			hit(p, zombie);
			var speed = p.getEffect(MobEffects.SPEED);
			if (zombie.isAlive()) {
				return "Executioner: zombie left at " + zombie.getHealth() + " wasn't executed";
			}
			return speed != null && speed.getAmplifier() == 1 ? null : "Executioner: no Speed II (" + speed + ")";
		}));
		server.runCommand("effect clear @a");
	}

	// ---------------------------------------------------------------- Whirlwind mastery

	private static void whirlwind(TestSingleplayerContext world, TestServerContext server) {
		server.runCommand(LEARN + "measure");
		server.runCommand(LEARN + "light_feet");
		server.runCommand(LEARN + "whirlwind");
		summon(server, "iron_golem", "^ ^ ^2", "aim");
		summon(server, "iron_golem", "^2 ^ ^", "w1");
		summon(server, "iron_golem", "^-2 ^ ^", "w2");
		summon(server, "iron_golem", "^ ^ ^-2", "w3");
		wait(world, 12);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, BladesSkill.WHIRLWIND)) {
				return "Whirlwind not learned: " + CharacterStore.get(p).perks();
			}
			Mob aim = (Mob) find(p, "aim");
			Mob[] around = {(Mob) find(p, "w1"), (Mob) find(p, "w2"), (Mob) find(p, "w3")};
			float base = base(p);
			p.setShiftKeyDown(true);
			for (Mob m : around) {
				m.damageCooldownTime = 0;
			}
			float aimed = hit(p, aim);
			String problem = null;
			for (Mob m : around) {
				float took = m.getMaxHealth() - m.getHealth();
				if (!near(took, aimed)) {
					problem = "Whirlwind: a golem behind took " + took + ", expected " + aimed + " (base " + base + ")";
				}
				m.damageCooldownTime = 0;
			}
			if (problem == null) {
				hit(p, aim);
				// The golem behind is out of the vanilla sweep's reach, so only a second spin could touch it.
				if (around[2].getMaxHealth() - around[2].getHealth() > aimed + 0.01f) {
					problem = "Whirlwind: spun again inside its cooldown";
				} else if (Whirlwind.cooldown(p) <= 0) {
					problem = "Whirlwind: no cooldown";
				}
			}
			p.setShiftKeyDown(false);
			aim.discard();
			for (Mob m : around) {
				m.discard();
			}
			return problem;
		}));
	}

	/** Spears stab instead of swinging; a sneaking stab spins too, once the cooldown has passed. */
	private static void spearWhirlwind(TestSingleplayerContext world, TestServerContext server) {
		hold(server, Items.IRON_SPEAR);
		summon(server, "iron_golem", "^ ^ ^2", "s_aim");
		summon(server, "iron_golem", "^ ^ ^-2", "s_back");
		wait(world, dev.voidmc.mettle.character.skill.blades.BladeMath.WHIRLWIND_COOLDOWN + 5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob aim = (Mob) find(p, "s_aim");
			Mob back = (Mob) find(p, "s_back");
			float base = base(p);
			p.setShiftKeyDown(true);
			((BladesLivingAccess) p).mettle$setAttackStrengthTicker(100);
			p.getMainHandItem().get(DataComponents.PIERCING_WEAPON).attack(p, EquipmentSlot.MAINHAND);
			p.setShiftKeyDown(false);
			float took = back.getMaxHealth() - back.getHealth();
			aim.discard();
			back.discard();
			return near(took, base) ? null : "Whirlwind: a sneaking spear stab spun for " + took + ", expected " + base;
		}));
		hold(server, Items.IRON_SWORD);
		wait(world, 3);
	}

	// ---------------------------------------------------------------- feats, synergies, teaching

	private static void earned(TestSingleplayerContext world, TestServerContext server) {
		// Unbroken: ten hostiles in a row with a blade, untouched. A scratch first resets the streak.
		server.runCommand("damage @p 1 minecraft:generic");
		for (int i = 0; i < 10; i++) {
			summon(server, "husk", "^ ^ ^2", "streak");
			server.runCommand("damage @e[tag=streak,limit=1] 1000 minecraft:player_attack by @p");
			if (i == 8) {
				check(server.computeOnServer(s -> Perks.has(player(s), BladesSkill.UNBROKEN) ? "Unbroken: earned after nine" : null));
			}
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return Perks.has(p, BladesSkill.UNBROKEN) ? null : "Unbroken: not earned, streak " + Aftermath.streak(p) + ", best "
				+ FeatCounters.get(p, Aftermath.STREAK);
		}));
		// Its gift: after 5 s without taking damage, the next blade hit is a critical; the one after isn't.
		summon(server, "iron_golem", "^ ^ ^2", "calm");
		wait(world, 105);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob golem = (Mob) find(p, "calm");
			golem.setTarget(p);
			float base = base(p);
			float first = hit(p, golem);
			float second = hit(p, golem);
			golem.discard();
			if (!near(first, base * 1.5f)) {
				return "Unbroken: first hit after a quiet spell dealt " + first + ", expected " + base * 1.5f;
			}
			return near(second, base) ? null : "Unbroken: second hit dealt " + second + ", expected " + base;
		}));

		// Giant Slayer: the 25th large mob felled with a blade earns it; then large mobs take +25 %.
		check(server.computeOnServer(s -> {
			FeatCounters.add(player(s), Aftermath.LARGE_KILLS, 24);
			return Perks.has(player(s), BladesSkill.GIANT_SLAYER) ? "Giant Slayer: earned at 24" : null;
		}));
		summon(server, "polar_bear", "^ ^ ^2", "giant");
		server.runCommand("damage @e[tag=giant,limit=1] 1000 minecraft:player_attack by @p");
		summon(server, "polar_bear", "^ ^ ^2", "giant2");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, BladesSkill.GIANT_SLAYER)) {
				return "Giant Slayer: not earned after 25 large kills (" + FeatCounters.get(p, Aftermath.LARGE_KILLS) + ")";
			}
			Mob bear = (Mob) find(p, "giant2");
			bear.setTarget(p);
			float base = base(p);
			float dealt = hit(p, bear);
			bear.discard();
			return near(dealt, base * 1.25f) ? null : "Giant Slayer: a polar bear took " + dealt + ", expected " + base * 1.25f;
		}));

		// Sword and Board, Master Strike: a shield block charges the swing, and a counter right after is a critical that
		// ignores the zombie's diamond chestplate.
		server.runCommand("mettle perk grant mettle:blades/sword_and_board @a");
		server.runCommand("mettle perk grant mettle:blades/master_strike @a");
		summon(server, "husk", "^ ^ ^1.5", "counter");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob zombie = (Mob) find(p, "counter");
			zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			zombie.setTarget(p);
			// A shield that raises instantly, so the test needn't hold the use key through ticks.
			ItemStack shield = new ItemStack(Items.SHIELD);
			BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
			shield.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(0f, blocks.disableCooldownScale(), blocks.damageReductions(),
				blocks.itemDamage(), blocks.bypassedBy(), blocks.blockSound(), blocks.disableSound()));
			p.setItemInHand(InteractionHand.OFF_HAND, shield);
			p.startUsingItem(InteractionHand.OFF_HAND);
			((BladesLivingAccess) p).mettle$setAttackStrengthTicker(0);
			float taken = takeHit(p, zombie);
			p.stopUsingItem();
			if (taken > 0) {
				return "Sword and Board: the shield didn't block (" + taken + " taken)";
			}
			if (p.getAttackStrengthScale(0.5f) < 1f) {
				return "Sword and Board: swing charge " + p.getAttackStrengthScale(0.5f) + " after a block";
			}
			float base = base(p);
			zombie.damageCooldownTime = 0;
			float before = zombie.getHealth();
			p.attack(zombie);
			float dealt = before - zombie.getHealth();
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			zombie.discard();
			return near(dealt, base * 1.5f) ? null : "Master Strike: counter dealt " + dealt + " through armour, expected " + base * 1.5f;
		}));

		// Weapon Master: switching from a blade to a heavy weapon makes the first full swing a critical, once in 5 s.
		server.runCommand("mettle perk grant mettle:blades/weapon_master @a");
		server.runCommand("damage @p 1 minecraft:generic");
		hold(server, Items.IRON_AXE);
		wait(world, 3);
		summon(server, "iron_golem", "^ ^ ^2", "switch");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob golem = (Mob) find(p, "switch");
			golem.setTarget(p);
			float base = base(p);
			float first = hit(p, golem);
			float second = hit(p, golem);
			if (!near(first, base * 1.5f)) {
				return "Weapon Master: first axe swing dealt " + first + ", expected a critical " + base * 1.5f;
			}
			return near(second, base) ? null : "Weapon Master: second axe swing dealt " + second + ", expected " + base;
		}));
		hold(server, Items.IRON_SWORD);
		wait(world, 3);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob golem = (Mob) find(p, "switch");
			float base = base(p);
			float back = hit(p, golem);
			golem.discard();
			return near(back, base) ? null : "Weapon Master: switching back inside 5 s still gave a critical (" + back + ")";
		}));
	}

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void hold(TestServerContext server, net.minecraft.world.item.Item item) {
		server.computeOnServer(s -> {
			player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			return null;
		});
	}

	private static void summon(TestServerContext server, String type, String where, String tag) {
		server.runCommand("execute as @p at @s run summon minecraft:" + type + " " + where + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"" + TAG
			+ "\",\"" + tag + "\"]}");
	}

	private static Mob summonNow(ServerPlayer p, net.minecraft.world.entity.EntityType<? extends Mob> type, double dx, double dz) {
		Mob mob = type.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		mob.setPos(p.getX() + dx, p.getY(), p.getZ() + dz);
		mob.setNoAi(true);
		mob.addTag(TAG);
		p.level().addFreshEntity(mob);
		return mob;
	}

	private static LivingEntity find(ServerPlayer p, String tag) {
		return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), e -> e.entityTags().contains(tag)).getFirst();
	}

	/** The swing's damage before any perk: the attack damage attribute, as a full, plain swing deals it. */
	private static float base(ServerPlayer p) {
		return (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	/** A full-strength swing from the ground, through the game's own attack; returns the health it took. */
	private static float hit(ServerPlayer p, LivingEntity target) {
		((BladesLivingAccess) p).mettle$setAttackStrengthTicker(100);
		target.damageCooldownTime = 0;
		float before = target.getHealth();
		p.attack(target);
		return before - target.getHealth();
	}

	/** The same swing while falling, which vanilla makes a critical. */
	private static float crit(ServerPlayer p, LivingEntity target) {
		p.fallDistance = 1.0;
		p.setOnGround(false);
		float dealt = hit(p, target);
		p.fallDistance = 0;
		p.setOnGround(true);
		return dealt;
	}

	/** {@code attacker} swings at the player; returns the health lost. */
	private static float takeHit(ServerPlayer p, Mob attacker) {
		p.setHealth(p.getMaxHealth());
		p.damageCooldownTime = 0;
		float before = p.getHealth();
		attacker.doHurtTarget(p.level(), p);
		return before - p.getHealth();
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 0.02;
	}

	/** Lets the server run {@code ticks} ticks (perk modifiers settle every 10, bleeds tick every 20). */
	private static void wait(TestSingleplayerContext world, int ticks) {
		int target = world.getServer().computeOnServer(s -> s.getTickCount()) + ticks;
		world.getServer().waitFor(s -> s.getTickCount() >= target, ticks + 200);
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
