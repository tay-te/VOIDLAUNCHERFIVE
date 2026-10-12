package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import dev.voidmc.mettle.character.skill.survival.Camp;
import dev.voidmc.mettle.character.skill.survival.Discovery;
import dev.voidmc.mettle.character.skill.survival.Foraging;
import dev.voidmc.mettle.character.skill.survival.GeneralFeats;
import dev.voidmc.mettle.character.skill.survival.Hunting;
import dev.voidmc.mettle.character.skill.survival.Landmarks;
import dev.voidmc.mettle.character.skill.survival.Tracker;
import dev.voidmc.mettle.character.skill.survival.Weatherwise;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.core.MettleAttributes;
import dev.voidmc.mettle.mixin.survival.SurvivalFoodDataAccess;
import dev.voidmc.mettle.character.skill.survival.CampfireCooks;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Survival tree and the general feats played for real on the server: animals killed through the damage command,
 * wild plants (placed with setBlock, so they count as natural) broken and picked through the player's own game mode,
 * food cooked in a furnace and on a campfire, a real village found and discovered, and the feats earned through their
 * own counters. Three careers (Hunter + Tracker, Forager + Living off the Land, Lay of the Land) with a respec between.
 * Everything happens on a grass platform in the sky (and in that village); the player is put back after.
 */
final class SurvivalJourney {
	private static final String LEARN = "execute as @p run mettle perk learn mettle:survival/";
	private static final String TAG = "survival_t";
	private static final int Y = 200;

	private SurvivalJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		double[] home = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return new double[] {p.getX(), p.getY(), p.getZ()};
		});
		// Picked flowers and berries end up in the pack; the inventory goes back as it was.
		java.util.List<ItemStack> inventory = server.computeOnServer(s -> {
			java.util.List<ItemStack> copy = new java.util.ArrayList<>();
			for (int i = 0; i < player(s).getInventory().getContainerSize(); i++) {
				copy.add(player(s).getInventory().getItem(i).copy());
			}
			return copy;
		});
		server.runCommand("mettle background set mettle:ranger @a");
		server.runCommand("effect clear @a");
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("execute as @p at @s run fill ~-12 " + (Y - 1) + " ~-12 ~12 " + (Y - 1) + " ~12 minecraft:grass_block");
		server.runCommand("execute as @p at @s run fill ~-12 " + Y + " ~-12 ~12 " + (Y + 6) + " ~12 minecraft:air");
		server.runCommand("execute as @p at @s run tp @s ~ " + Y + " ~ 0 0");
		server.runCommand("kill @e[type=item]");
		server.runCommand("execute as @p run mettle respec");
		server.runCommand("mettle xp mettle:survival 30000 @a");
		server.runCommand("mettle xp mettle:athletics 5000 @a");
		vitality(server);
		context.waitTicks(10);
		// Whatever this spot is, it is known before XP is measured.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Discovery.check(p);
			p.removeAllEffects();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return null;
		}));

		xpSources(context, server);
		hunter(context, server);
		server.runCommand("execute as @p run mettle respec");
		vitality(server);
		forager(context, server);
		server.runCommand("execute as @p run mettle respec");
		vitality(server);
		layOfTheLand(context, server);
		server.runCommand("tp @p " + home[0] + " " + (Y + 1) + " " + home[2] + " 0 0");
		context.waitTicks(20);
		poultice(context, server);
		generalFeats(context, server);

		// Leave the character as found: bought perks refunded, earned Survival and general perks taken back (so they can't
		// colour the journeys after this one), the world's weather and the player home.
		server.runCommand("execute as @p run mettle respec");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			Map<ResourceKey<Perk>, Integer> kept = new HashMap<>(state.perks());
			kept.keySet().removeIf(k -> k.identifier().getPath().startsWith("survival/") || k.identifier().getPath().startsWith("general/"));
			CharacterStore.set(p, state.withPerks(kept));
			EffectsApplier.apply(p);
			Discovery.reset(p);
			p.getAbilities().flying = false;
			p.getAbilities().mayfly = false;
			p.onUpdateAbilities();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setShiftKeyDown(false);
			p.setHealth(p.getMaxHealth());
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(5f);
			for (int i = 0; i < inventory.size(); i++) {
				p.getInventory().setItem(i, inventory.get(i));
			}
			return null;
		}));
		server.runCommand("kill @e[tag=" + TAG + "]");
		server.runCommand("kill @e[type=item]");
		server.runCommand("effect clear @a");
		server.runCommand("weather clear");
		server.runCommand("mettle background set mettle:ranger @a");
		server.runCommand("tp @p " + home[0] + " " + home[1] + " " + home[2]);
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- XP (BALANCE.md §3)

	private static void xpSources(ClientGameTestContext context, TestServerContext server) {
		// An animal killed by the player: XP_ANIMAL.
		double[] before = {xp(server)};
		summon(server, "pig", "^ ^ ^3", "pig1");
		server.runCommand("damage @e[tag=pig1,limit=1] 100 minecraft:player_attack by @p");
		check(gained(server, before[0], SurvivalSkillXp.ANIMAL, "a pig the player killed"));

		// Mostly someone else's kill: nothing.
		before[0] = xp(server);
		summon(server, "pig", "^ ^ ^3", "pig2");
		server.runCommand("damage @e[tag=pig2,limit=1] 1 minecraft:player_attack by @p");
		server.runCommand("damage @e[tag=pig2,limit=1] 100 minecraft:generic");
		check(gained(server, before[0], 0, "a pig that died mostly of something else"));

		// Wild flowers (natural: placed with setBlock) picked by breaking: XP_PLANT each.
		before[0] = xp(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (int i = 0; i < 5; i++) {
				BlockPos at = p.blockPosition().offset(-4 + i * 2, 0, 5);
				p.level().setBlock(at, Blocks.POPPY.defaultBlockState(), 3);
				p.gameMode.destroyBlock(at);
			}
			return null;
		}));
		check(gained(server, before[0], 5 * SurvivalSkillXp.PLANT, "five wild poppies"));

		// Berries picked off a ripe wild bush: XP_PLANT.
		before[0] = xp(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos at = p.blockPosition().offset(0, 0, 6);
			p.level().setBlock(at, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3), 3);
			p.gameMode.useItemOn(p, p.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit(at));
			return p.level().getBlockState(at).getValue(SweetBerryBushBlock.AGE) == 1 ? null : "berries weren't picked";
		}));
		check(gained(server, before[0], SurvivalSkillXp.PLANT, "berries picked"));

		// A flower the player planted teaches nothing when picked.
		before[0] = xp(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos ground = p.blockPosition().offset(3, -1, 3);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DANDELION));
			p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit(ground));
			if (!p.level().getBlockState(ground.above()).is(Blocks.DANDELION)) {
				return "couldn't plant a dandelion";
			}
			if (!dev.voidmc.mettle.character.PlacedBlocks.isPlaced(p.level(), ground.above())) {
				return "a planted dandelion wasn't remembered as planted";
			}
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.gameMode.destroyBlock(ground.above());
			return null;
		}));
		check(gained(server, before[0], 0, "a planted dandelion"));

		// Food taken out of a furnace: XP_COOKED per item, by shift-click.
		before[0] = xp(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos at = p.blockPosition().offset(-3, 0, 0);
			p.level().setBlock(at, Blocks.FURNACE.defaultBlockState(), 3);
			AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) p.level().getBlockEntity(at);
			furnace.setItem(2, new ItemStack(Items.COOKED_BEEF, 3));
			int had = p.getInventory().countItem(Items.COOKED_BEEF);
			p.openMenu(furnace);
			p.containerMenu.quickMoveStack(p, 2);
			p.closeContainer();
			p.level().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
			int beef = p.getInventory().countItem(Items.COOKED_BEEF) - had;
			p.getInventory().clearOrCountMatchingItems(st -> st.is(Items.COOKED_BEEF), false, beef, p.inventoryMenu.getCraftSlots());
			return beef == 3 ? null : "furnace: " + beef + " cooked beef taken, expected 3";
		}));
		check(gained(server, before[0], 3 * SurvivalSkillXp.COOKED, "three cooked beef from a furnace"));

		// Food the player put on a campfire: XP_COOKED when it pops off cooked.
		before[0] = xp(server);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos at = p.blockPosition().offset(3, 0, 0);
			p.level().setBlock(at, Blocks.CAMPFIRE.defaultBlockState(), 3);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF));
			p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit(at));
			if (!p.getMainHandItem().isEmpty()) {
				return "the campfire didn't take the beef";
			}
			((CampfireCooks) p.level().getBlockEntity(at)).mettle$finishCooking();
			return null;
		}));
		context.waitTicks(4);
		check(gained(server, before[0], SurvivalSkillXp.COOKED, "beef cooked on a campfire"));
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.level().setBlock(p.blockPosition().offset(3, 0, 0), Blocks.AIR.defaultBlockState(), 3);
			return null;
		}));
		server.runCommand("kill @e[type=item]");
	}

	// ---------------------------------------------------------------- roots, Hunter path, Tracker

	private static void hunter(ClientGameTestContext context, TestServerContext server) {
		// Berry bushes scratch and slow a player without Pathfinder (the control for the Forager's run).
		float scratched = berryWalk(context, server);
		if (scratched <= 0) {
			throw new AssertionError("berry bushes didn't hurt a player without Pathfinder; the Pathfinder check would prove nothing");
		}

		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "gatherer");
			server.runCommand(LEARN + "field_cook");
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (Perks.rank(p, SurvivalSkill.GATHERER) != 3 || Perks.rank(p, SurvivalSkill.FIELD_COOK) != 3) {
				return "roots not learned: " + CharacterStore.get(p).perks();
			}
			// Gatherer III: forty wild poppies give about 52; never more than 80, almost never exactly 40.
			int dropped = 0;
			for (int i = 0; i < 40; i++) {
				BlockPos at = p.blockPosition().offset(-10 + (i % 20), 0, i < 20 ? 8 : 9);
				p.level().setBlock(at, Blocks.POPPY.defaultBlockState(), 3);
				p.gameMode.destroyBlock(at);
			}
			dropped = count(p, Items.POPPY, 16);
			if (dropped <= 40 || dropped > 80) {
				return "Gatherer III: 40 poppies dropped " + dropped;
			}
			// Field Cook III: cooked beef (8) fills 10.
			p.getFoodData().setFoodLevel(0);
			p.getFoodData().setSaturation(0);
			new ItemStack(Items.COOKED_BEEF).finishUsingItem(p.level(), p);
			int food = p.getFoodData().getFoodLevel();
			// Raw beef isn't cooked: plain 3.
			p.getFoodData().setFoodLevel(0);
			new ItemStack(Items.BEEF).finishUsingItem(p.level(), p);
			int raw = p.getFoodData().getFoodLevel();
			p.getFoodData().setFoodLevel(20);
			if (food != 10) {
				return "Field Cook III: cooked beef filled " + food + ", expected 10";
			}
			return raw == 3 ? null : "Field Cook touched raw beef: " + raw;
		}));
		server.runCommand("kill @e[type=item]");

		// Quiet Approach: a rabbit doesn't bolt from a sneaking hunter 6 blocks off, but does once they're within 4.
		server.runCommand(LEARN + "quiet_approach");
		server.runCommand(LEARN + "skinner");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, SurvivalSkill.QUIET_APPROACH) || !Perks.has(p, SurvivalSkill.SKINNER)) {
				return "Hunter tier II not learned: " + CharacterStore.get(p).perks();
			}
			Rabbit rabbit = EntityTypes.RABBIT.create(p.level(), EntitySpawnReason.COMMAND);
			rabbit.setPos(p.getX(), p.getY(), p.getZ() + 6);
			rabbit.addTag(TAG);
			p.level().addFreshEntity(rabbit);
			AvoidEntityGoal<Player> goal = new AvoidEntityGoal<>(rabbit, Player.class, 8f, 1.0, 1.0);
			// Whether the goal picked the player as the thing to run from (fleeing itself also needs a path, which a
			// fresh rabbit on a sky platform may not find).
			java.lang.reflect.Field toAvoid;
			try {
				toAvoid = AvoidEntityGoal.class.getDeclaredField("toAvoid");
				toAvoid.setAccessible(true);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
			Function<Boolean, Boolean> spooked = sneaking -> {
				p.setShiftKeyDown(sneaking);
				boolean fled = goal.canUse();
				try {
					return toAvoid.get(goal) == p || fled;
				} catch (IllegalAccessException e) {
					throw new AssertionError(e);
				}
			};
			boolean fledStanding = spooked.apply(false);
			boolean fledSneaking = spooked.apply(true);
			rabbit.setPos(p.getX(), p.getY(), p.getZ() + 3);
			boolean fledClose = spooked.apply(true);
			boolean calmClose = Hunting.staysCalm(rabbit, p);
			p.setShiftKeyDown(false);
			rabbit.discard();
			if (!fledStanding) {
				return "Quiet Approach control: a rabbit didn't mind a standing player at 6 blocks";
			}
			if (fledSneaking) {
				return "Quiet Approach: the rabbit fled a sneaking hunter 6 blocks away";
			}
			if (!fledClose) {
				return "Quiet Approach: the rabbit ignored a sneaking hunter at 3 blocks";
			}
			return calmClose ? "Quiet Approach: the rabbit stayed calm at 3 blocks" : null;
		}));

		// Skinner: a chicken always drops one raw chicken and a sheep one wool; Skinner adds one of each (Field Dressing,
		// the Ranger's, rolls the loot once more without Skinner's extra).
		summon(server, "chicken", "^ ^ ^3", "hen");
		server.runCommand("damage @e[tag=hen,limit=1] 100 minecraft:player_attack by @p");
		summon(server, "sheep", "^3 ^ ^", "ewe");
		server.runCommand("damage @e[tag=ewe,limit=1] 100 minecraft:player_attack by @p");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			int extra = dev.voidmc.mettle.character.signature.SignaturePerks.has(p, dev.voidmc.mettle.character.signature.SignaturePerks.FIELD_DRESSING) ? 1 : 0;
			int chicken = count(p, Items.CHICKEN, 8);
			int wool = count(p, Items.WOOL.white(), 8);
			if (chicken != 2 + extra) {
				return "Skinner: " + chicken + " raw chicken, expected " + (2 + extra);
			}
			return wool == 2 + extra ? null : "Skinner: " + wool + " wool, expected " + (2 + extra);
		}));
		server.runCommand("kill @e[type=item]");

		// Stalker: a sneak attack on an unaware pig kills it outright; the same tap standing up doesn't.
		server.runCommand(LEARN + "stalker");
		server.runCommand(LEARN + "sixth_sense");
		summon(server, "pig", "^ ^ ^3", "stalked");
		summon(server, "pig", "^3 ^ ^", "noticed");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, SurvivalSkill.STALKER) || !Perks.has(p, SurvivalSkill.SIXTH_SENSE)) {
				return "Hunter tier III not learned: " + CharacterStore.get(p).perks();
			}
			// Sneaking is the client's to say, so set and use it inside one server step.
			p.setShiftKeyDown(true);
			LivingEntity stalked = find(p, "stalked");
			if (!(stalked instanceof net.minecraft.world.entity.animal.Animal a)) {
				return "Stalker: no pig to stalk";
			}
			float modified = Hunting.damage(stalked, p.damageSources().playerAttack(p), 1f);
			if (modified < stalked.getHealth()) {
				return "Stalker: an unaware pig's damage stayed " + modified + " (noticed " + Hunting.noticed(a, p) + ", hurt by "
					+ a.getLastHurtByMob() + ", target " + a.getTarget() + ")";
			}
			stalked.hurtServer(p.level(), p.damageSources().playerAttack(p), 1f);
			p.setShiftKeyDown(false);
			find(p, "noticed").hurtServer(p.level(), p.damageSources().playerAttack(p), 1f);
			return null;
		}));
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			boolean stalkedAlive = !p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8), e -> e.isAlive() && e.entityTags().contains("stalked")).isEmpty();
			LivingEntity noticed = find(p, "noticed");
			if (stalkedAlive) {
				return "Stalker: the sneak attack didn't kill the pig";
			}
			if (noticed == null || noticed.getHealth() != noticed.getMaxHealth() - 1) {
				return "Stalker fired without sneaking";
			}
			// Sixth Sense: a zombie 8 blocks off picks the player as its target.
			Mob zombie = EntityTypes.ZOMBIE.create(p.level(), EntitySpawnReason.COMMAND);
			zombie.setPos(p.getX() + 8, p.getY(), p.getZ());
			zombie.setNoAi(true);
			zombie.addTag(TAG);
			p.level().addFreshEntity(zombie);
			long before = Hunting.lastSensed(p);
			zombie.setTarget(p);
			long after = Hunting.lastSensed(p);
			zombie.discard();
			noticed.discard();
			return after != before && after == p.level().getGameTime() ? null : "Sixth Sense: no heartbeat when a zombie took aim";
		}));
		server.runCommand("kill @e[type=item]");

		// Tracker (needs two Hunter perks). A whole tree costs 31 points and Survival 20 gives 20, so a tracker gives up
		// the roots and half the path: respec, then two Hunter perks and the mastery.
		server.runCommand("execute as @p run mettle respec");
		vitality(server);
		server.runCommand(LEARN + "quiet_approach");
		server.runCommand(LEARN + "skinner");
		server.runCommand(LEARN + "tracker");
		// Sneak still for two seconds and the cow 30 blocks off leaves a trail.
		summon(server, "cow", "~ ~ ~30", "tracked");
		check(server.computeOnServer(s -> Perks.has(player(s), SurvivalSkill.TRACKER) ? null : "Tracker not learned: " + CharacterStore.get(player(s)).perks()));
		// Really crouching, from the client's own sneak key.
		context.getInput().holdKeyFor(options -> options.keyShift, 70);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Tracker.Trail trail = Tracker.trail(p);
			LivingEntity cow = find(p, "tracked");
			if (trail == null) {
				return "Tracker: no trail after sneaking still";
			}
			if (cow == null || !trail.animal().equals(cow.getUUID())) {
				return "Tracker: trailing the wrong animal";
			}
			return trail.prints() >= 10 ? null : "Tracker: only " + trail.prints() + " prints over 30 blocks";
		}));
		server.runCommand("kill @e[tag=tracked]");

		// Big Game (feat): every big-game kill counts; at 30 it doubles meat and hide.
		summon(server, "goat", "^ ^ ^3", "goat");
		check(server.computeOnServer(s -> {
			FeatCounters.add(player(s), SurvivalSkill.BIG_GAME_KILLS, -FeatCounters.get(player(s), SurvivalSkill.BIG_GAME_KILLS));
			return null;
		}));
		server.runCommand("damage @e[tag=goat,limit=1] 100 minecraft:player_attack by @p");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			long kills = FeatCounters.get(p, SurvivalSkill.BIG_GAME_KILLS);
			if (kills != 1) {
				return "Big Game: a goat kill counted " + kills;
			}
			FeatCounters.add(p, SurvivalSkill.BIG_GAME_KILLS, 29);
			return Perks.has(p, SurvivalSkill.BIG_GAME) ? null : "Big Game not earned at 30 kills";
		}));
		summon(server, "hoglin", "^ ^ ^3", "hoglin");
		server.runCommand("damage @e[tag=hoglin,limit=1] 100 minecraft:player_attack by @p");
		check(server.computeOnServer(s -> {
			int[] added = Hunting.lastDressing(player(s));
			// A hoglin drops 2–4 porkchops: Big Game copies them all and Skinner adds one.
			return added != null && added[0] >= 3 ? null : "Big Game: a hoglin kill added " + (added == null ? "nothing" : added[0] + " meat");
		}));
		server.runCommand("kill @e[type=item]");
	}

	// ---------------------------------------------------------------- Forager path, Living off the Land

	private static void forager(ClientGameTestContext context, TestServerContext server) {
		server.runCommand(LEARN + "iron_stomach");
		server.runCommand(LEARN + "campfire_rest");
		server.runCommand(LEARN + "pathfinder");
		server.runCommand(LEARN + "self_sufficient");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (var id : new Identifier[] {SurvivalSkill.IRON_STOMACH, SurvivalSkill.CAMPFIRE_REST, SurvivalSkill.PATHFINDER, SurvivalSkill.SELF_SUFFICIENT}) {
				if (!Perks.has(p, id)) {
					return "Forager perk not learned: " + id + " " + CharacterStore.get(p).perks();
				}
			}
			// Iron Stomach: rotten flesh (80 % Hunger each) and a spider eye (always Poison) do nothing.
			p.removeAllEffects();
			for (int i = 0; i < 5; i++) {
				new ItemStack(Items.ROTTEN_FLESH).finishUsingItem(p.level(), p);
			}
			new ItemStack(Items.SPIDER_EYE).finishUsingItem(p.level(), p);
			new ItemStack(Items.CHICKEN).finishUsingItem(p.level(), p);
			if (p.hasEffect(MobEffects.HUNGER) || p.hasEffect(MobEffects.POISON)) {
				return "Iron Stomach: got " + p.getActiveEffects();
			}
			// Self-Sufficient: 2 exhaustion (two jumps' worth of sprinting) counts as 1.5.
			SurvivalFoodDataAccess food = (SurvivalFoodDataAccess) p.getFoodData();
			food.mettle$setExhaustion(0f);
			p.causeFoodExhaustion(2f);
			float exhaustion = food.mettle$exhaustion();
			food.mettle$setExhaustion(0f);
			return Math.abs(exhaustion - 1.5f) < 1e-4 ? null : "Self-Sufficient: 2 exhaustion became " + exhaustion + ", expected 1.5";
		}));

		// Campfire Rest: standing still by a lit campfire heals a heart every 4 s (hunger too low for natural healing).
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.removeAllEffects();
			p.level().setBlock(p.blockPosition().offset(2, 0, 0), Blocks.CAMPFIRE.defaultBlockState(), 3);
			p.getFoodData().setFoodLevel(10);
			p.setHealth(10f);
			return null;
		}));
		context.waitTicks(110);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float health = p.getHealth();
			p.level().setBlock(p.blockPosition().offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 3);
			p.getFoodData().setFoodLevel(20);
			return health >= 12f ? null : "Campfire Rest: health " + health + " after 5 s by the fire, expected 12";
		}));

		// Pathfinder: 15 % faster on a dirt path, not on grass; berry bushes don't scratch.
		BlockPos path = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos under = p.blockPosition().below();
			p.level().setBlock(under, Blocks.DIRT_PATH.defaultBlockState(), 3);
			return under;
		});
		// A dirt path is 1/16 lower than grass: give the player time to settle onto it before the 5-tick check sees it.
		for (int i = 0; i < 40 && !server.computeOnServer(s -> player(s).getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Camp.PATHFINDER_SPEED)); i++) {
			context.waitTick();
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			boolean on = p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Camp.PATHFINDER_SPEED);
			p.level().setBlock(path, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
			return on ? null : "Pathfinder: no speed on a dirt path (onGround " + p.onGround() + ", on " + p.level().getBlockState(p.getOnPos()) + " at " + p.position() + ", perk " + Perks.has(p, dev.voidmc.mettle.character.skill.SurvivalSkill.PATHFINDER) + ", flying " + p.getAbilities().flying + ")";
		}));
		server.runCommand("execute as @p at @s run tp @s ~ " + Y + " ~");
		context.waitTicks(12);
		check(server.computeOnServer(s -> player(s).getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Camp.PATHFINDER_SPEED)
			? "Pathfinder: speed stayed on off the path" : null));
		float scratched = berryWalk(context, server);
		if (scratched > 0) {
			throw new AssertionError("Pathfinder: berry bushes still hurt (" + scratched + ")");
		}

		// Living off the Land (needs two Forager perks): grass turns up finds, wild food fills double.
		server.runCommand(LEARN + "living_off_the_land");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, SurvivalSkill.LIVING_OFF_THE_LAND)) {
				return "Living off the Land not learned: " + CharacterStore.get(p).perks();
			}
			int before = Foraging.finds(p);
			for (int i = 0; i < 60; i++) {
				BlockPos at = p.blockPosition().offset(-10 + (i % 20), 0, 7 + i / 20);
				p.level().setBlock(at, Blocks.SHORT_GRASS.defaultBlockState(), 3);
				p.gameMode.destroyBlock(at);
			}
			if (Foraging.finds(p) == before) {
				return "Living off the Land: 60 tufts of grass turned up nothing";
			}
			p.getFoodData().setFoodLevel(0);
			p.getFoodData().setSaturation(0);
			new ItemStack(Items.SWEET_BERRIES).finishUsingItem(p.level(), p);
			int food = p.getFoodData().getFoodLevel();
			p.getFoodData().setFoodLevel(20);
			return food == 4 ? null : "Living off the Land: sweet berries filled " + food + ", expected 4";
		}));
		server.runCommand("kill @e[type=item]");
	}

	/** Walks the player south through four ripe berry bushes; returns the health they lost. */
	private static float berryWalk(ClientGameTestContext context, TestServerContext server) {
		float[] start = new float[1];
		double[] at = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.removeAllEffects();
			p.setHealth(p.getMaxHealth());
			start[0] = p.getHealth();
			for (int i = 1; i <= 4; i++) {
				p.level().setBlock(p.blockPosition().offset(0, 0, i), Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3), 3);
			}
			return new double[] {p.getX(), p.getY(), p.getZ()};
		});
		server.runCommand("tp @p " + at[0] + " " + at[1] + " " + at[2] + " 0 0");
		context.waitTicks(3);
		context.getInput().holdKeyFor(options -> options.keyUp, 30);
		float lost = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float l = start[0] - p.getHealth();
			for (int i = 1; i <= 4; i++) {
				p.level().setBlock(BlockPos.containing(at[0], at[1], at[2]).offset(0, 0, i), Blocks.AIR.defaultBlockState(), 3);
			}
			p.setHealth(p.getMaxHealth());
			return l;
		});
		server.runCommand("tp @p " + at[0] + " " + at[1] + " " + at[2] + " 0 0");
		server.runCommand("kill @e[type=item]");
		context.waitTicks(3);
		return lost;
	}

	// ---------------------------------------------------------------- discovery, Lay of the Land, Trailblazer

	private static void layOfTheLand(ClientGameTestContext context, TestServerContext server) {
		server.runCommand(LEARN + "skinner");
		server.runCommand(LEARN + "iron_stomach");
		server.runCommand(LEARN + "lay_of_the_land");
		double[] before = new double[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, SurvivalSkill.LAY_OF_THE_LAND)) {
				return "Lay of the Land not learned: " + CharacterStore.get(p).perks();
			}
			if (!Perks.has(p, SurvivalSkill.TRAILBLAZER)) {
				return "Trailblazer not unlocked at Survival 20 and Athletics 10";
			}
			Discovery.reset(p);
			p.removeAllEffects();
			before[0] = CharacterStore.get(p).xp(SurvivalSkill.SURVIVAL);
			return null;
		}));
		// A biome seen "for the first time": XP_BIOME, the biome tally, and Trailblazer's second wind (no particles).
		context.waitTicks(Discovery.INTERVAL + 5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Identifier biome = p.level().getBiome(p.blockPosition()).unwrapKey().orElseThrow().identifier();
			if (!Discovery.journal(p).biomes().contains(biome)) {
				return "Discovery: the biome underfoot wasn't recorded";
			}
			double gained = CharacterStore.get(p).xp(SurvivalSkill.SURVIVAL) - before[0];
			double expected = SurvivalSkillXp.BIOME * multiplier(p);
			if (Math.abs(gained - expected) > 0.01) {
				return "Discovery: a new biome gave " + gained + " XP, expected " + expected;
			}
			var speed = p.getEffect(MobEffects.SPEED);
			return speed != null && !speed.isVisible() ? null : "Trailblazer: no quiet Speed after a discovery (" + speed + ")";
		}));

		// A real village: found with the game's own search, and stood in.
		BlockPos[] village = new BlockPos[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			HolderSet<Structure> villages = structures.getOrThrow(StructureTags.VILLAGE);
			var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, villages, p.blockPosition(), 100, false);
			if (found == null) {
				// VOID Hearth replaces villages with its own settlements (-Phearth): another surface landmark stands in.
				HolderSet<Structure> surface = HolderSet.direct(java.util.stream.Stream.of(
					net.minecraft.world.level.levelgen.structure.BuiltinStructures.PILLAGER_OUTPOST,
					net.minecraft.world.level.levelgen.structure.BuiltinStructures.DESERT_PYRAMID,
					net.minecraft.world.level.levelgen.structure.BuiltinStructures.JUNGLE_TEMPLE,
					net.minecraft.world.level.levelgen.structure.BuiltinStructures.SWAMP_HUT,
					net.minecraft.world.level.levelgen.structure.BuiltinStructures.IGLOO).map(structures::getOrThrow).toList());
				found = level.getChunkSource().getGenerator().findNearestMapStructure(level, surface, p.blockPosition(), 100, false);
			}
			if (found == null) {
				return "no village (or other surface landmark) to visit in this world";
			}
			village[0] = found.getFirst();
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			return null;
		}));
		server.runCommand("tp @p " + village[0].getX() + " 250 " + village[0].getZ());
		context.waitTicks(60);
		String[] place = new String[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			ChunkPos chunk = new ChunkPos(village[0].getX() >> 4, village[0].getZ() >> 4);
			StructureStart start = null;
			for (StructureStart st : level.getChunk(chunk.x(), chunk.z()).getAllStarts().values()) {
				if (st.isValid()) {
					start = st;
				}
			}
			if (start == null) {
				return "the village's start chunk has no start";
			}
			BoundingBox box = start.getPieces().getFirst().getBoundingBox();
			int x = box.getCenter().getX();
			int z = box.getCenter().getZ();
			// Somewhere to stand inside the first piece: the highest two blocks of air in it.
			int y = box.maxY();
			while (y > box.minY() && !(level.getBlockState(new BlockPos(x, y, z)).isAir() && level.getBlockState(new BlockPos(x, y + 1, z)).isAir())) {
				y--;
			}
			p.teleportTo(x + 0.5, y, z + 0.5);
			Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
			place[0] = id + "@" + start.getChunkPos().x() + "," + start.getChunkPos().z();
			before[0] = CharacterStore.get(p).xp(SurvivalSkill.SURVIVAL);
			return null;
		}));
		context.waitTicks(Discovery.INTERVAL + 10);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Discovery.Journal journal = Discovery.journal(p);
			if (!journal.places().contains(place[0])) {
				return "Discovery: standing in " + place[0] + " wasn't recorded (" + journal.places() + ")";
			}
			double gained = CharacterStore.get(p).xp(SurvivalSkill.SURVIVAL) - before[0];
			double structure = SurvivalSkillXp.STRUCTURE * multiplier(p);
			// The village may also be a new biome.
			if (Math.abs(gained - structure) > 0.01 && Math.abs(gained - structure - SurvivalSkillXp.BIOME * multiplier(p)) > 0.01) {
				return "Discovery: the village gave " + gained + " XP, expected " + structure;
			}
			if (FeatCounters.get(p, SurvivalSkill.LANDMARK_KINDS) != journal.kinds().size() || journal.kinds().isEmpty()) {
				return "Seen It All: tally " + FeatCounters.get(p, SurvivalSkill.LANDMARK_KINDS) + " for kinds " + journal.kinds();
			}
			// Lay of the Land: the next landmark, of a kind not yet found, is marked.
			if (journal.marker().isEmpty()) {
				return "Lay of the Land: nothing marked after finding a village (nearest unfound: "
					+ Landmarks.nearest(p.level(), p.blockPosition(), journal.kinds()) + ")";
			}
			return journal.kinds().contains(journal.marker().get().kind()) ? "Lay of the Land: marked a kind already found" : null;
		}));
		context.waitFor(mc -> mc.player != null && mc.player.connection.getWaypointManager().hasWaypoints(), 40);
		context.takeScreenshot("mettle_survival_lay_of_the_land");

		// Reaching the marked place takes the marker down.
		BlockPos marked = server.computeOnServer(s -> Discovery.journal(player(s)).marker().orElseThrow().pos());
		server.runCommand("tp @p " + marked.getX() + " 250 " + marked.getZ());
		context.waitTicks(Discovery.INTERVAL + 10);
		check(server.computeOnServer(s -> {
			Discovery.Journal journal = Discovery.journal(player(s));
			return journal.marker().map(m -> m.pos().equals(marked)).orElse(false) ? "Lay of the Land: still marked after reaching it" : null;
		}));
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().flying = false;
			p.getAbilities().mayfly = false;
			p.onUpdateAbilities();
			return null;
		}));
	}

	// ---------------------------------------------------------------- the Poultice teaching

	private static void poultice(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("mettle perk grant mettle:survival/poultice @p");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, SurvivalSkill.POULTICE)) {
				return "Poultice not granted";
			}
			p.removeAllEffects();
			p.getFoodData().setFoodLevel(10);
			p.setHealth(6f);
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 400));
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POPPY, 5));
			p.setShiftKeyDown(true);
			p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			if (p.hasEffect(MobEffects.POISON)) {
				return "Poultice: still poisoned";
			}
			if (p.getMainHandItem().getCount() != 2) {
				return "Poultice: used " + (5 - p.getMainHandItem().getCount()) + " herbs, expected 3";
			}
			// A second within the cooldown does nothing.
			p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POPPY, 5));
			p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			p.setShiftKeyDown(false);
			return p.getMainHandItem().getCount() == 5 ? null : "Poultice: worked again inside its cooldown";
		}));
		context.waitTicks(170);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float health = p.getHealth();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.getFoodData().setFoodLevel(20);
			return health >= 14f ? null : "Poultice: health " + health + " after 8 s, expected 14";
		}));
	}

	// ---------------------------------------------------------------- general feats and Weatherwise

	private static void generalFeats(ClientGameTestContext context, TestServerContext server) {
		// Nine Lives: half a heart or less, and alive. (General feats earned earlier in the tour are taken back first, so
		// each is seen being earned and changing what it changes.)
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			Map<ResourceKey<Perk>, Integer> kept = new HashMap<>(state.perks());
			kept.keySet().removeIf(k -> k.identifier().getPath().startsWith("general/") || k.identifier().equals(SurvivalSkill.WEATHERWISE));
			CharacterStore.set(p, state.withPerks(kept));
			EffectsApplier.apply(p);
			p.removeAllEffects();
			p.setHealth(1.8f);
			return null;
		}));
		long[] nearDeaths = {server.computeOnServer(s -> FeatCounters.get(player(s), SurvivalSkill.NEAR_DEATHS))};
		server.runCommand("damage @p 1 minecraft:generic");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!p.isAlive() || p.getHealth() > 1f) {
				return "Nine Lives: the setup hit left " + p.getHealth();
			}
			if (FeatCounters.get(p, SurvivalSkill.NEAR_DEATHS) != nearDeaths[0] + 1) {
				return "Nine Lives: a close call wasn't counted";
			}
			FeatCounters.add(p, SurvivalSkill.NEAR_DEATHS, 9);
			return Perks.has(p, SurvivalSkill.NINE_LIVES) ? null : "Nine Lives not earned";
		}));
		context.waitTicks(8);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			boolean fast = p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(GeneralFeats.NINE_LIVES_SPEED);
			p.setHealth(p.getMaxHealth());
			return fast ? null : "Nine Lives: no speed below 3 hearts";
		}));
		context.waitTicks(8);
		check(server.computeOnServer(s -> player(s).getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(GeneralFeats.NINE_LIVES_SPEED)
			? "Nine Lives: speed stayed at full health" : null));

		// Seen It All: eight kinds of landmark, one more attribute point.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			int points = Perks.attributePoints(p);
			FeatCounters.add(p, SurvivalSkill.LANDMARK_KINDS, 8 - FeatCounters.get(p, SurvivalSkill.LANDMARK_KINDS));
			if (!Perks.has(p, SurvivalSkill.SEEN_IT_ALL)) {
				return "Seen It All not earned at 8 kinds";
			}
			return Perks.attributePoints(p) == points + 1 ? null : "Seen It All: attribute points " + points + " → " + Perks.attributePoints(p);
		}));

		// Pack Horse: a kilometre under a crushing load, +8 kg.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			double capacity = p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			FeatCounters.add(p, SurvivalSkill.OVERBURDENED_METRES, 1000);
			double after = p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			return Perks.has(p, SurvivalSkill.PACK_HORSE) && after == capacity + 8 ? null : "Pack Horse: capacity " + capacity + " → " + after;
		}));

		// Seasoned: thirty days alive, by vanilla's own statistic; +1 heart.
		double[] maxHealth = new double[1];
		int[] lifeBefore = new int[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			CharacterStore.set(p, state.withPerks(without(state, SurvivalSkill.SEASONED)));
			EffectsApplier.apply(p);
			maxHealth[0] = p.getMaxHealth();
			lifeBefore[0] = p.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
			p.getStats().setValue(p, Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH), 720_000);
			return null;
		}));
		context.waitTicks(110);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, SurvivalSkill.SEASONED)) {
				return "Seasoned not earned after 30 days alive (tally " + FeatCounters.get(p, SurvivalSkill.LIFE_TICKS) + ")";
			}
			// The life goes back to its real length, so Seasoned isn't simply earned again after the cleanup.
			p.getStats().setValue(p, Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH), lifeBefore[0]);
			return p.getMaxHealth() == maxHealth[0] + 2 ? null : "Seasoned: max health " + maxHealth[0] + " → " + p.getMaxHealth();
		}));

		// Jack of All Trades: level 5 in all eight wave-1 skills; then skills below 10 learn 25 % faster. The XP it takes is
		// handed back afterwards, so the journeys after this one find the skills where they were.
		Map<ResourceKey<dev.voidmc.mettle.api.Skill>, Double> xpBefore = server.computeOnServer(s -> CharacterStore.get(player(s)).xp());
		// Earlier journeys may have earned it already; start from scratch so the tally is what grants it.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			CharacterStore.set(p, state.withPerks(without(state, SurvivalSkill.JACK_OF_ALL_TRADES)));
			FeatCounters.add(p, SurvivalSkill.SKILLS_AT_FIVE, -FeatCounters.get(p, SurvivalSkill.SKILLS_AT_FIVE));
			return Perks.has(p, SurvivalSkill.JACK_OF_ALL_TRADES) ? "Jack of All Trades couldn't be taken back" : null;
		}));
		for (String skill : new String[] {"blades", "heavy_weapons", "marksmanship", "defense", "athletics", "mining", "survival", "smithing"}) {
			server.runCommand("mettle xp mettle:" + skill + " 800 @a");
		}
		double[] plain = new double[1];
		ResourceKey<dev.voidmc.mettle.api.Skill> alchemy = Progress.skill("alchemy");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			plain[0] = Progress.multiplier(state, CharacterStore.background(p.level().registryAccess(), state), alchemy);
			return null;
		}));
		context.waitTicks(110);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (!Perks.has(p, SurvivalSkill.JACK_OF_ALL_TRADES)) {
				return "Jack of All Trades not earned (tally " + FeatCounters.get(p, SurvivalSkill.SKILLS_AT_FIVE) + ")";
			}
			CharacterState state = CharacterStore.get(p);
			var background = CharacterStore.background(p.level().registryAccess(), state);
			double low = Progress.multiplier(state, background, alchemy);
			double high = Progress.multiplier(state, background, SurvivalSkill.SURVIVAL);
			double highPlain = Progress.multiplier(state.withPerks(without(state, SurvivalSkill.JACK_OF_ALL_TRADES)), background, SurvivalSkill.SURVIVAL);
			if (Math.abs(low - plain[0] * 1.25) > 1e-9) {
				return "Jack of All Trades: alchemy multiplier " + plain[0] + " → " + low;
			}
			return Math.abs(high - highPlain) < 1e-9 ? null : "Jack of All Trades sped up a skill above 10";
		}));
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			for (var skill : state.xp().keySet()) {
				state = state.withXp(skill, xpBefore.getOrDefault(skill, 0.0));
			}
			CharacterStore.set(p, state);
			EffectsApplier.apply(p);
			return null;
		}));

		// Weatherwise: 40 biomes; then the overworld's rain two minutes out rumbles.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			FeatCounters.add(p, SurvivalSkill.BIOMES_VISITED, 40);
			if (!Perks.has(p, SurvivalSkill.WEATHERWISE)) {
				return "Weatherwise not earned at 40 biomes";
			}
			WeatherData weather = s.overworld().getWeatherData();
			weather.setClearWeatherTime(0);
			weather.setRaining(false);
			weather.setThundering(false);
			weather.setThunderTime(100_000);
			weather.setRainTime(2400 + 30);
			return null;
		}));
		server.runCommand("gamerule advance_weather true");
		long[] start = {server.computeOnServer(s -> s.overworld().getGameTime())};
		context.waitTicks(80);
		check(server.computeOnServer(s -> Weatherwise.warnedAt(player(s)) >= start[0] ? null : "Weatherwise: no warning two minutes before rain"));
		server.runCommand("weather clear");
	}

	// ---------------------------------------------------------------- helpers

	/** BALANCE.md §3's Survival numbers, spelled out so the journey checks against the design, not the code. */
	private static final class SurvivalSkillXp {
		static final double ANIMAL = dev.voidmc.mettle.character.skill.survival.SurvivalRules.XP_ANIMAL;
		static final double PLANT = dev.voidmc.mettle.character.skill.survival.SurvivalRules.XP_PLANT;
		static final double COOKED = dev.voidmc.mettle.character.skill.survival.SurvivalRules.XP_COOKED;
		static final double BIOME = dev.voidmc.mettle.character.skill.survival.SurvivalRules.XP_BIOME;
		static final double STRUCTURE = dev.voidmc.mettle.character.skill.survival.SurvivalRules.XP_STRUCTURE;
	}

	private static Map<ResourceKey<Perk>, Integer> without(CharacterState state, Identifier perk) {
		Map<ResourceKey<Perk>, Integer> perks = new HashMap<>(state.perks());
		perks.remove(Perks.key(perk));
		return perks;
	}

	private static void vitality(TestServerContext server) {
		// Ranger starts at Vitality 4; masteries need 14.
		for (int i = 0; i < 10; i++) {
			server.runCommand("execute as @p run mettle attribute vitality");
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static double xp(TestServerContext server) {
		return server.computeOnServer(s -> CharacterStore.get(player(s)).xp(SurvivalSkill.SURVIVAL));
	}

	private static double multiplier(ServerPlayer p) {
		CharacterState state = CharacterStore.get(p);
		return Progress.multiplier(state, CharacterStore.background(p.level().registryAccess(), state), SurvivalSkill.SURVIVAL);
	}

	private static String gained(TestServerContext server, double before, double base, String what) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			double gained = CharacterStore.get(p).xp(SurvivalSkill.SURVIVAL) - before;
			double expected = base * multiplier(p);
			return Math.abs(gained - expected) < 0.01 ? null : "Survival XP for " + what + ": " + gained + ", expected " + expected;
		});
	}

	private static void summon(TestServerContext server, String type, String where, String tag) {
		server.runCommand("execute as @p at @s run summon minecraft:" + type + " " + where + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"" + TAG
			+ "\",\"" + tag + "\"]}");
	}

	private static LivingEntity find(ServerPlayer p, String tag) {
		return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40), e -> e.isAlive() && e.entityTags().contains(tag)).stream()
			.findFirst().orElse(null);
	}

	private static int count(ServerPlayer p, Item item, double range) {
		return p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(range), e -> e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static BlockHitResult hit(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
