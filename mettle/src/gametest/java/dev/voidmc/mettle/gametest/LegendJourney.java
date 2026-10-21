package dev.voidmc.mettle.gametest;

import com.mojang.authlib.GameProfile;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.CardCooldowns;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.character.cards.legend.LegendCombat;
import dev.voidmc.mettle.character.cards.legend.LegendCraft;
import dev.voidmc.mettle.character.cards.legend.LegendSenses;
import dev.voidmc.mettle.character.cards.legend.LegendWild;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.client.legend.LegendClient;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.mixin.blades.BladesLivingAccess;
import dev.voidmc.mettle.mixin.legend.LegendFurnaceAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The sixteen drawn Mythics of build feel (docs/BUILD_FEEL.md §5.4), each granted, slotted two at a time and made to
 * work for real: blows struck, arrows loosed, keys held on the client, spawners and furnaces ticking. The arena is a
 * stone floor at y 279 high above the player (and a room cut at y −40 for the Deep Lantern); everything is put back.
 */
final class LegendJourney {
	private static final double FLOOR = 280;
	private static final String TAG = "mettle_legend";
	private static int cx;
	private static int cz;
	/** Hits on one tracked entity: {second arrow ? 1 : 0, damage taken, silent ? 1 : 0}. */
	private static final List<float[]> HITS = new ArrayList<>();
	private static UUID tracked;
	private static boolean listening;

	private LegendJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		current = context;
		listen();
		Saved saved = on(server, LegendJourney::save);
		try {
			setUp(context, server);
			mining(context, server);
			blades(server);
			heavy(context, server);
			marksmanship(context, server);
			defense(context, server);
			athletics(context, server);
			survival(context, server);
			smithing(context, server);
		} catch (Throwable t) {
			try {
				tearDown(context, server, saved);
			} catch (Throwable cleanup) {
				t.addSuppressed(cleanup);
			}
			throw t;
		}
		tearDown(context, server, saved);
	}

	// ---------------------------------------------------------------- the arena, and putting everything back

	private record Saved(CharacterState character, List<ItemStack> inventory, double[] at, float health, int food, int levels, GameType mode,
		boolean spawnMobs, boolean raining, boolean thundering) {
	}

	private static Saved save(ServerPlayer p) {
		List<ItemStack> inv = new ArrayList<>();
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			inv.add(p.getInventory().getItem(i).copy());
		}
		ServerLevel level = (ServerLevel) p.level();
		return new Saved(Cards.ensure(p), inv, new double[] {p.getX(), p.getY(), p.getZ()}, p.getHealth(), p.getFoodData().getFoodLevel(),
			p.experienceLevel, p.gameMode.getGameModeForPlayer(), level.getGameRules().get(GameRules.SPAWN_MOBS), level.isRaining(), level.isThundering());
	}

	private static void setUp(ClientGameTestContext context, TestServerContext server) {
		int[] c = on(server, p -> new int[] {p.getBlockX(), p.getBlockZ()});
		cx = c[0];
		cz = c[1];
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("effect clear @a");
		fill(server, -16, 279, -16, 16, 279, 16, "smooth_stone");
		fill(server, -16, 280, -16, 16, 290, 16, "air");
		context.runOnClient(mc -> mc.options.autoJump().set(false));
		check(on(server, p -> {
			Cards.clear(p);
			level(p, "mining", 8);
			level(p, "blades", 8);
			p.getInventory().clearContent();
			return null;
		}));
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5, 0);
	}

	private static void tearDown(ClientGameTestContext context, TestServerContext server, Saved saved) {
		for (Function<Options, KeyMapping> key : List.<Function<Options, KeyMapping>>of(o -> o.keyUp, o -> o.keySprint, o -> o.keyJump, o -> o.keyShift,
			o -> o.keyUse)) {
			context.getInput().releaseKey(key);
		}
		tracked = null;
		context.setScreen(() -> null);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			p.closeContainer();
			for (Entity e : new ArrayList<Entity>(level.getEntitiesOfClass(Entity.class, new AABB(cx - 40, -64, cz - 40, cx + 40, 320, cz + 40),
				e -> e.entityTags().contains(TAG) || e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof AbstractArrow
					|| e instanceof LightningBolt))) {
				e.discard();
			}
			Cards.clear(p);
			for (String id : IDS) {
				Cards.ledger(level.getServer()).unclaim(id);
			}
			CharacterStore.set(p, saved.character());
			EffectsApplier.apply(p);
			p.setGameMode(saved.mode());
			p.getInventory().clearContent();
			for (int i = 0; i < saved.inventory().size(); i++) {
				p.getInventory().setItem(i, saved.inventory().get(i));
			}
			p.setShiftKeyDown(false);
			p.setSprinting(false);
			p.stopUsingItem();
			p.experienceLevel = saved.levels();
			p.getFoodData().setFoodLevel(saved.food());
			p.setHealth(saved.health());
			p.removeAttached(LegendWild.BORNE);
			for (String cd : List.of(LegendSenses.STONESONG, LegendSenses.LONG_WATCH, LegendCombat.THUNDERHEAD, LegendCombat.DREAD_MAUL,
				LegendCombat.IRON_VOW, LegendWild.CATS_WAY, LegendCraft.MENDERS_HAND)) {
				CardCooldowns.reset(p, cd);
			}
			return null;
		}));
		server.runCommand("effect clear @a");
		server.runCommand("time set 1000");
		server.runCommand(saved.thundering() ? "weather thunder" : saved.raining() ? "weather rain" : "weather clear");
		server.runCommand("gamerule spawn_mobs " + saved.spawnMobs());
		server.runCommand("tp @p " + saved.at()[0] + " " + saved.at()[1] + " " + saved.at()[2]);
		fill(server, -16, 279, -16, 16, 290, 16, "air");
		context.waitTicks(5);
	}

	private static final List<String> IDS = List.of("stonesong", "deep_lantern", "misericorde", "red_harvest", "thunderhead", "dread_maul", "hunters_moon",
		"pinning_star", "iron_vow", "long_watch", "ferrymans_stride", "cats_way", "beastspeakers_torc", "greenway", "ever_ember", "menders_hand");

	/** Releases what is slotted, then grants and slots these two Mythics (one in the Mining deck, one in Blades). */
	private static void slot(TestServerContext server, String a, String b) {
		check(on(server, p -> {
			Cards.clear(p);
			for (String id : IDS) {
				Cards.ledger(p.level().getServer()).unclaim(id);
			}
			CardData first = Mythics.grant(p, Mettle.id(a), true).orElse(null);
			CardData second = Mythics.grant(p, Mettle.id(b), true).orElse(null);
			if (first == null || second == null) {
				return "could not grant " + a + " and " + b;
			}
			var x = Cards.slot(p, first.id(), -1, MiningSkill.MINING);
			var y = Cards.slot(p, second.id(), -1, Progress.skill("blades"));
			if (x.isPresent() || y.isPresent() || !Mythics.active(p, a) || !Mythics.active(p, b)) {
				return "could not slot " + a + " and " + b + ": " + x + " " + y;
			}
			p.setHealth(p.getMaxHealth());
			p.getFoodData().setFoodLevel(20);
			return null;
		}));
		// The client's card cache follows (Mythics.active is read there too).
		context().waitTicks(3);
	}

	private static ClientGameTestContext current;

	private static ClientGameTestContext context() {
		return current;
	}

	// ---------------------------------------------------------------- Mining: the Stonesong, the Deep Lantern

	private static void mining(ClientGameTestContext context, TestServerContext server) {
		slot(server, "stonesong", "deep_lantern");
		// A diamond vein six blocks off in the floor; an emerald one closer that a player placed, which never sings.
		BlockPos ore = new BlockPos(cx + 4, 279, cz + 5);
		BlockPos placed = new BlockPos(cx + 1, 279, cz + 2);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			level.setBlockAndUpdate(ore, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
			level.setBlockAndUpdate(placed, Blocks.EMERALD_ORE.defaultBlockState());
			PlacedBlocks.mark(level, placed);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			CardCooldowns.reset(p, LegendSenses.STONESONG);
			return null;
		}));
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5, 0);
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(55);
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(3);
		String song = on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			BlockPos hum = LegendSenses.lastHum(p);
			long cd = CardCooldowns.remaining(p, LegendSenses.STONESONG);
			PlacedBlocks.takePlaced(level, placed);
			level.setBlockAndUpdate(ore, Blocks.SMOOTH_STONE.defaultBlockState());
			level.setBlockAndUpdate(placed, Blocks.SMOOTH_STONE.defaultBlockState());
			if (!ore.equals(hum)) {
				return "the Stonesong: 2 s still and sneaking hummed from " + hum + ", expected the diamond at " + ore;
			}
			return cd > 1000 ? null : "the Stonesong: cooldown " + cd + " ticks, expected about 60 s";
		});
		check(song);

		// The Deep Lantern: a room cut at y −40, a zombie spawner three blocks off that spawns at once. Nothing comes
		// while the Lantern is slotted; with it gone, zombies do.
		BlockPos room = new BlockPos(cx, -41, cz);
		List<BlockState> before = new ArrayList<>();
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			for (BlockPos pos : BlockPos.betweenClosed(room.offset(-5, 0, -5), room.offset(5, 5, 5))) {
				before.add(level.getBlockState(pos));
			}
			for (BlockPos pos : BlockPos.betweenClosed(room.offset(-5, 0, -5), room.offset(5, 5, 5))) {
				boolean shell = pos.getY() == room.getY() || pos.getY() == room.getY() + 5 || Math.abs(pos.getX() - cx) == 5 || Math.abs(pos.getZ() - cz) == 5;
				level.setBlock(pos, shell ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
			}
			return null;
		}));
		server.runCommand("setblock " + (cx + 3) + " -40 " + cz + " minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\",Tags:[\"" + TAG
			+ "\"]},custom_spawn_rules:{block_light_limit:[0,15],sky_light_limit:[0,15]}},Delay:0,MinSpawnDelay:10,MaxSpawnDelay:20,SpawnRange:3}");
		tp(context, server, cx - 2.5, -40, cz + 0.5, 0);
		context.waitTicks(100);
		int withLantern = on(server, p -> zombiesNear(p, new BlockPos(cx + 3, -40, cz)));
		check(on(server, p -> {
			Cards.clear(p);
			return null;
		}));
		int without = 0;
		for (int i = 0; i < 20 && without == 0; i++) {
			context.waitTicks(10);
			without = on(server, p -> zombiesNear(p, new BlockPos(cx + 3, -40, cz)));
		}
		final int spawned = without;
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(room).inflate(12))) {
				m.discard();
			}
			level.removeBlockEntity(new BlockPos(cx + 3, -40, cz));
			int i = 0;
			for (BlockPos pos : BlockPos.betweenClosed(room.offset(-5, 0, -5), room.offset(5, 5, 5))) {
				level.setBlock(pos, before.get(i++), 2);
			}
			return null;
		}));
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5, 0);
		expect(withLantern == 0, "the Deep Lantern: " + withLantern + " zombies came from a spawner 3 blocks off below y 0");
		expect(spawned > 0, "the Deep Lantern test is void: with the Lantern gone the spawner still brought nothing");
	}

	private static int zombiesNear(ServerPlayer p, BlockPos spawner) {
		return p.level().getEntitiesOfClass(Mob.class, new AABB(spawner).inflate(8), m -> m.getType() == EntityTypes.ZOMBIE).size();
	}

	// ---------------------------------------------------------------- Blades: Misericorde, Red Harvest

	private static void blades(TestServerContext server) {
		slot(server, "misericorde", "red_harvest");
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			// Above 15 %: an ordinary hit.
			Mob healthy = mob(level, EntityTypes.HUSK, cx + 2.5, FLOOR, cz + 0.5, false, 100, 50);
			swing(p, healthy);
			boolean lived = healthy.isAlive();
			healthy.discard();
			if (!lived) {
				return "Misericorde ended a husk at half health";
			}
			// A boss below 15 % is never ended.
			Mob guardian = mob(level, EntityTypes.ELDER_GUARDIAN, cx + 2.5, FLOOR, cz + 0.5, false, 1000, 100);
			swing(p, guardian);
			boolean bossLived = guardian.isAlive();
			guardian.discard();
			if (!bossLived) {
				return "Misericorde ended an Elder Guardian";
			}
			// Below 15 % (10 of 100): the blow ends it though it deals far less.
			Mob low = mob(level, EntityTypes.HUSK, cx + 2.5, FLOOR, cz + 0.5, false, 100, 10);
			swing(p, low);
			boolean ended = !low.isAlive();
			low.discard();
			if (!ended) {
				return "Misericorde: a blade hit on a husk at 10 % left it on " + low.getHealth();
			}
			// Red Harvest: that kill readied the swing (through the arm's swing that follows a kill) and the next one sweeps,
			// even sprinting (vanilla never sweeps then).
			p.resetAttackStrengthTicker();
			float ready = p.getAttackStrengthScale(0.5f);
			if (ready < 0.99f || !LegendCombat.sweepReady(p)) {
				return "Red Harvest: after a blade kill the swing is at " + ready + ", sweep waiting " + LegendCombat.sweepReady(p);
			}
			Mob aimed = mob(level, EntityTypes.HUSK, cx + 2.5, FLOOR, cz + 0.5, false, 100, 100);
			Mob beside = mob(level, EntityTypes.HUSK, cx + 2.5, FLOOR, cz + 1.4, false, 100, 100);
			p.setSprinting(true);
			p.attack(aimed);
			p.setSprinting(false);
			float besideHealth = beside.getHealth();
			aimed.discard();
			beside.discard();
			if (besideHealth >= 100) {
				return "Red Harvest: the sprinting swing after a kill did not sweep the husk beside";
			}
			return LegendCombat.sweepReady(p) ? "Red Harvest: the sweep was not spent by the swing" : null;
		}));
	}

	/** A full swing (the attack strength charged), as the client would make it. */
	private static void swing(ServerPlayer p, Entity target) {
		((BladesLivingAccess) p).mettle$setAttackStrengthTicker(100);
		if (target instanceof LivingEntity living) {
			living.damageCooldownTime = 0;
		}
		p.attack(target);
	}

	// ---------------------------------------------------------------- Heavy Weapons: Thunderhead, the Dread Maul

	private static void heavy(ClientGameTestContext context, TestServerContext server) {
		slot(server, "thunderhead", "dread_maul");
		server.runCommand("weather thunder");
		// The storm comes in over seconds; for the test it is here at once.
		check(on(server, p -> {
			p.level().setRainLevel(1);
			p.level().setThunderLevel(1);
			return p.level().isThundering() ? null : "no thunderstorm";
		}));
		UUID[] struck = new UUID[1];
		float[] health = new float[1];
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			CardCooldowns.reset(p, LegendCombat.THUNDERHEAD);
			Mob target = mob(level, EntityTypes.HUSK, cx + 2.5, FLOOR, cz + 0.5, false, 300, 300);
			struck[0] = target.getUUID();
			health[0] = p.getHealth();
			swing(p, target);
			List<LightningBolt> bolts = level.getEntitiesOfClass(LightningBolt.class, target.getBoundingBox().inflate(3),
				b -> b.entityTags().contains(LegendCombat.BOLT));
			if (bolts.isEmpty()) {
				return "Thunderhead: a full heavy hit in a thunderstorm called no lightning";
			}
			if (bolts.getFirst().entityTags().contains(LegendCombat.NO_FIRE)) {
				return "Thunderhead: a bolt over ground the player may build on was made fireless";
			}
			return CardCooldowns.remaining(p, LegendCombat.THUNDERHEAD) > 300 ? null : "Thunderhead: no 20 s cooldown";
		}));
		context.waitTicks(15);
		check(on(server, p -> {
			Entity target = ((ServerLevel) p.level()).getEntity(struck[0]);
			if (p.getHealth() < health[0]) {
				return "Thunderhead: the bolt struck its caller (" + health[0] + " → " + p.getHealth() + ")";
			}
			// The axe deals about 9; the bolt 5 more.
			return target instanceof LivingEntity l && l.getHealth() < 300 - 11 ? null : "Thunderhead: the bolt did not strike the target (health " + (target instanceof LivingEntity l ? l.getHealth() : -1) + " of 300)";
		}));
		// Where the player may not build (adventure mode), the bolt lights nothing; and the cooldown holds a second call.
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			level.getEntitiesOfClass(LightningBolt.class, new AABB(cx - 16, 270, cz - 16, cx + 16, 300, cz + 16)).forEach(Entity::discard);
			fillIn(level, Blocks.FIRE, Blocks.AIR);
			Entity target = level.getEntity(struck[0]);
			if (!(target instanceof Mob mob)) {
				return "the husk is gone";
			}
			target.clearFire();
			swing(p, target);
			if (!level.getEntitiesOfClass(LightningBolt.class, target.getBoundingBox().inflate(3), b -> b.entityTags().contains(LegendCombat.BOLT)
				&& b.tickCount == 0).isEmpty()) {
				return "Thunderhead: a second bolt within the 20 s cooldown";
			}
			CardCooldowns.reset(p, LegendCombat.THUNDERHEAD);
			p.setGameMode(GameType.ADVENTURE);
			swing(p, mob);
			p.setGameMode(GameType.SURVIVAL);
			List<LightningBolt> bolts = level.getEntitiesOfClass(LightningBolt.class, target.getBoundingBox().inflate(3),
				b -> b.entityTags().contains(LegendCombat.NO_FIRE));
			return bolts.isEmpty() ? "Thunderhead: no fireless bolt where the player may not build" : null;
		}));
		context.waitTicks(15);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			StringBuilder where = new StringBuilder();
			for (BlockPos pos : BlockPos.betweenClosed(cx - 16, 279, cz - 16, cx + 16, 284, cz + 16)) {
				if (level.getBlockState(pos).is(Blocks.FIRE)) {
					where.append(" ").append(pos.getX() - cx).append(",").append(pos.getY()).append(",").append(pos.getZ() - cz);
				}
			}
			for (LightningBolt b : level.getEntitiesOfClass(LightningBolt.class, new AABB(cx - 16, 270, cz - 16, cx + 16, 300, cz + 16))) {
				where.append(" bolt ").append(b.entityTags()).append(" age ").append(b.tickCount);
			}
			// Only around the struck husk: a storm's own bolts may land elsewhere on the floor.
			Entity target = level.getEntity(struck[0]);
			int fires = 0;
			if (target != null) {
				for (BlockPos pos : BlockPos.betweenClosed(target.blockPosition().offset(-2, -1, -2), target.blockPosition().offset(2, 2, 2))) {
					fires += level.getBlockState(pos).is(Blocks.FIRE) ? 1 : 0;
				}
			}
			fillIn(level, Blocks.FIRE, Blocks.AIR);
			if (target != null) {
				where.append(" husk at ").append(target.getX() - cx).append(",").append(target.getY()).append(",").append(target.getZ() - cz);
				target.discard();
			}
			return fires == 0 ? null : "Thunderhead: " + fires + " fires lit where the player may not build:" + where;
		}));
		server.runCommand("weather clear");
		check(on(server, p -> {
			p.level().setRainLevel(0);
			p.level().setThunderLevel(0);
			return null;
		}));

		// The Dread Maul: an axe kill sends the husks around fleeing for 3 s.
		UUID[] near = new UUID[2];
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			fillIn(level, Blocks.FIRE, Blocks.AIR);
			CardCooldowns.reset(p, LegendCombat.DREAD_MAUL);
			Mob victim = mob(level, EntityTypes.HUSK, cx + 2.5, FLOOR, cz + 0.5, false, 20, 1);
			Mob a = mob(level, EntityTypes.HUSK, cx + 4.5, FLOOR, cz + 3.5, true, 20, 20);
			Mob b = mob(level, EntityTypes.HUSK, cx + 0.5, FLOOR, cz + 4.5, true, 20, 20);
			a.setTarget(p);
			b.setTarget(p);
			near[0] = a.getUUID();
			near[1] = b.getUUID();
			swing(p, victim);
			if (victim.isAlive()) {
				return "the Dread Maul: the axe did not kill the husk";
			}
			if (CardCooldowns.remaining(p, LegendCombat.DREAD_MAUL) <= 200) {
				return "the Dread Maul: no 15 s cooldown after a heavy kill with husks near";
			}
			return a.getTarget() == null && b.getTarget() == null ? null : "the Dread Maul: the husks near still target the player";
		}));
		double[] start = on(server, p -> new double[] {dist(p, near[0]), dist(p, near[1])});
		context.waitTicks(40);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			double a = dist(p, near[0]);
			double b = dist(p, near[1]);
			boolean calm = level.getEntity(near[0]) instanceof Mob ma && ma.getTarget() == null && level.getEntity(near[1]) instanceof Mob mb && mb.getTarget() == null;
			StringBuilder why = new StringBuilder();
			for (UUID id : near) {
				Entity e = level.getEntity(id);
				if (e instanceof Mob m) {
					why.append(" [afraid ").append(LegendCombat.afraid(m)).append(", noAi ").append(m.isNoAi()).append(", ticks ").append(m.tickCount)
						.append(", nav done ").append(m.getNavigation().isDone()).append(", away ")
						.append(m instanceof net.minecraft.world.entity.PathfinderMob pm ? net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(pm, 12, 7, p.position()) : null)
						.append("]");
				}
				if (e != null) {
					e.discard();
				}
			}
			if (!calm) {
				return "the Dread Maul: a fleeing husk took the player as target within 3 s";
			}
			return a > start[0] + 1 && b > start[1] + 1 ? null : "the Dread Maul: the husks did not flee (" + start[0] + " → " + a + ", " + start[1] + " → " + b + ")" + why;
		}));
	}

	private static double dist(ServerPlayer p, UUID id) {
		Entity e = ((ServerLevel) p.level()).getEntity(id);
		return e == null ? -1 : e.distanceTo(p);
	}

	/** Replaces one block with another across the arena; how many there were. */
	private static int fillIn(ServerLevel level, net.minecraft.world.level.block.Block from, net.minecraft.world.level.block.Block to) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(cx - 16, 279, cz - 16, cx + 16, 284, cz + 16)) {
			if (level.getBlockState(pos).is(from)) {
				level.setBlockAndUpdate(pos, to.defaultBlockState());
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- Marksmanship: Hunter's Moon, the Pinning Star

	private static void marksmanship(ClientGameTestContext context, TestServerContext server) {
		slot(server, "hunters_moon", "pinning_star");
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5, 0);
		// The Pinning Star, by day: an arrow in the legs roots a walking husk for 2 s; one in the head does not.
		UUID[] ids = new UUID[2];
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			Mob legs = mob(level, EntityTypes.HUSK, cx + 0.5, FLOOR, cz + 9.5, true, 100, 100);
			Mob head = mob(level, EntityTypes.HUSK, cx + 6.5, FLOOR, cz + 9.5, false, 100, 100);
			ids[0] = legs.getUUID();
			ids[1] = head.getUUID();
			loose(p, legs, 0.3);
			loose(p, head, 1.75);
			return null;
		}));
		context.waitTicks(8);
		double[] at = new double[3];
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			if (!(level.getEntity(ids[0]) instanceof Mob legs) || !(level.getEntity(ids[1]) instanceof Mob head)) {
				return "the husks are gone";
			}
			if (legs.getHealth() >= 100 || head.getHealth() >= 100) {
				return "the Pinning Star: the arrows missed (" + legs.getHealth() + ", " + head.getHealth() + ")";
			}
			if (!LegendCombat.pinned(legs) || legs.getAttributeValue(Attributes.MOVEMENT_SPEED) > 1e-6) {
				return "the Pinning Star: an arrow in the legs did not root the husk (speed " + legs.getAttributeValue(Attributes.MOVEMENT_SPEED) + ")";
			}
			if (LegendCombat.pinned(head)) {
				return "the Pinning Star: an arrow in the head rooted the husk";
			}
			legs.setTarget(p);
			at[0] = legs.getX();
			at[1] = legs.getZ();
			return null;
		}));
		context.waitTicks(20);
		check(on(server, p -> {
			Mob legs = (Mob) ((ServerLevel) p.level()).getEntity(ids[0]);
			double moved = Math.hypot(legs.getX() - at[0], legs.getZ() - at[1]);
			return moved < 0.2 ? null : "the Pinning Star: the rooted husk moved " + moved + " blocks";
		}));
		context.waitTicks(30);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			Mob legs = (Mob) level.getEntity(ids[0]);
			boolean free = !LegendCombat.pinned(legs) && legs.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.1;
			legs.discard();
			level.getEntity(ids[1]).discard();
			return free ? null : "the Pinning Star: the husk was still rooted after 2.5 s";
		}));

		// Hunter's Moon: by night under the open sky, a full-draw hit looses a silent second arrow for half the damage.
		server.runCommand("time set 18000");
		context.waitTicks(3);
		UUID[] target = new UUID[1];
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			Mob husk = mob(level, EntityTypes.HUSK, cx + 0.5, FLOOR, cz + 9.5, false, 200, 200);
			target[0] = husk.getUUID();
			track(husk);
			loose(p, husk, 1.0);
			return null;
		}));
		context.waitTicks(25);
		check(on(server, p -> {
			List<float[]> hits = List.copyOf(HITS);
			tracked = null;
			if (hits.size() != 2 || hits.get(0)[0] != 0 || hits.get(1)[0] != 1) {
				return "Hunter's Moon: by night the arrow brought " + describe(hits) + ", expected the arrow then the second arrow";
			}
			float first = hits.get(0)[1];
			float second = hits.get(1)[1];
			if (Math.abs(second - first / 2) > 1.01f) {
				return "Hunter's Moon: the second arrow dealt " + second + " after a first of " + first + ", expected half";
			}
			return hits.get(1)[2] == 1 ? null : "Hunter's Moon: the second arrow was not silent";
		}));
		// By day, none.
		server.runCommand("time set 1000");
		context.waitTicks(3);
		check(on(server, p -> {
			Mob husk = (Mob) ((ServerLevel) p.level()).getEntity(target[0]);
			husk.damageCooldownTime = 0;
			track(husk);
			loose(p, husk, 1.0);
			return null;
		}));
		context.waitTicks(25);
		check(on(server, p -> {
			List<float[]> hits = List.copyOf(HITS);
			tracked = null;
			((ServerLevel) p.level()).getEntity(target[0]).discard();
			return hits.size() == 1 ? null : "Hunter's Moon: by day the arrow brought " + describe(hits);
		}));
	}

	/** A full-draw arrow of the player's, from their eyes at {@code height} up the target. */
	private static void loose(ServerPlayer p, LivingEntity target, double height) {
		Arrow arrow = new Arrow(p.level(), p, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		arrow.setCritArrow(true);
		arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		Vec3 to = new Vec3(target.getX(), target.getY() + height, target.getZ()).subtract(arrow.position());
		arrow.shoot(to.x, to.y + to.horizontalDistance() * 0.015, to.z, 3.0f, 0f);
		p.level().addFreshEntity(arrow);
	}

	private static void listen() {
		if (listening) {
			return;
		}
		listening = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (tracked != null && entity.getUUID().equals(tracked) && source.getDirectEntity() instanceof AbstractArrow arrow) {
				HITS.add(new float[] {arrow.entityTags().contains(LegendCombat.SECOND_ARROW) ? 1 : 0, taken, arrow.isSilent() ? 1 : 0});
			}
		});
	}

	private static void track(Entity entity) {
		HITS.clear();
		tracked = entity.getUUID();
	}

	private static String describe(List<float[]> hits) {
		StringBuilder s = new StringBuilder();
		for (float[] h : hits) {
			s.append(h[0] == 1 ? " second " : " arrow ").append(h[1]);
		}
		return hits.size() + " hits:" + s;
	}

	// ---------------------------------------------------------------- Defense: the Iron Vow, the Long Watch

	/** A second player standing by: an ally (no teams), who can be hurt. */
	private static final class Ally extends FakePlayer {
		Ally(ServerLevel level) {
			super(level, new GameProfile(UUID.fromString("6d657474-6c65-4c65-8765-6e64616c6c79"), "Ally"));
		}

		@Override
		public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
			return false;
		}
	}

	private static void defense(ClientGameTestContext context, TestServerContext server) {
		slot(server, "iron_vow", "long_watch");
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5, 0);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			CardCooldowns.reset(p, LegendCombat.IRON_VOW);
			Ally ally = new Ally(level);
			ally.snapTo(cx + 3.5, FLOOR, cz + 0.5);
			ally.setHealth(4);
			Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			zombie.snapTo(cx + 4.5, FLOOR, cz + 0.5);
			// The same blow on the holder first, for what their own armour and perks leave of it.
			float full = p.getHealth();
			p.damageCooldownTime = 0;
			p.hurtServer(level, level.damageSources().mobAttack(zombie), 6f);
			float own = full - p.getHealth();
			p.setHealth(p.getMaxHealth());
			p.damageCooldownTime = 0;
			float before = p.getHealth();
			ally.hurtServer(level, level.damageSources().mobAttack(zombie), 6f);
			float after = p.getHealth();
			zombie.discard();
			if (ally.isDeadOrDying() || ally.getHealth() != 4) {
				return "the Iron Vow: the ally's killing blow was not taken (ally on " + ally.getHealth() + ")";
			}
			if (own <= 0 || Math.abs(before - after - own) > 0.01f) {
				return "the Iron Vow: the holder took " + (before - after) + " of the ally's blow, expected " + own + " (as if struck)";
			}
			if (CardCooldowns.remaining(p, LegendCombat.IRON_VOW) < 23000) {
				return "the Iron Vow: not once per in-game day (cooldown " + CardCooldowns.remaining(p, LegendCombat.IRON_VOW) + ")";
			}
			p.setHealth(p.getMaxHealth());
			return null;
		}));

		// The Long Watch: sneaking still with a shield raised for 3 s, the hostiles within 16 blocks glow for this client.
		UUID[] husk = new UUID[1];
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			CardCooldowns.reset(p, LegendSenses.LONG_WATCH);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
			Mob m = mob(level, EntityTypes.HUSK, cx + 0.5, FLOOR, cz + 10.5, false, 20, 20);
			husk[0] = m.getUUID();
			return null;
		}));
		context.waitTicks(3);
		context.getInput().holdKey(o -> o.keyShift);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(75);
		context.getInput().releaseKey(o -> o.keyUse);
		context.getInput().releaseKey(o -> o.keyShift);
		int id = on(server, p -> ((ServerLevel) p.level()).getEntity(husk[0]).getId());
		check(on(server, p -> {
			if (!LegendSenses.lastWatch(p).contains(id)) {
				return "the Long Watch: 3 s sneaking behind a shield marked " + LegendSenses.lastWatch(p) + ", not the husk " + id;
			}
			return CardCooldowns.remaining(p, LegendSenses.LONG_WATCH) > 400 ? null : "the Long Watch: no 30 s cooldown";
		}));
		check(context.computeOnClient(mc -> {
			Entity e = mc.level.getEntity(id);
			return e != null && LegendClient.watched(e) && mc.shouldEntityAppearGlowing(e) ? null : "the Long Watch: the husk doesn't glow on the client";
		}));
		check(on(server, p -> {
			((ServerLevel) p.level()).getEntity(husk[0]).discard();
			return null;
		}));
	}

	// ---------------------------------------------------------------- Athletics: the Ferryman's Stride, the Cat's Way

	private static void athletics(ClientGameTestContext context, TestServerContext server) {
		slot(server, "ferrymans_stride", "cats_way");
		// A still-water channel across the floor, 12 blocks wide, sprinted across from the west.
		fill(server, -6, 278, 9, 6, 278, 11, "smooth_stone");
		fill(server, -6, 279, 9, 6, 279, 11, "water");
		tp(context, server, cx - 9.5, FLOOR, cz + 10.5, -90);
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keySprint);
		int over = 0;
		String sank = null;
		for (int i = 0; i < 50; i++) {
			context.waitTick();
			double[] s = context.computeOnClient(mc -> new double[] {mc.player.getX(), mc.player.getY(), mc.player.isInWater() ? 1 : 0, mc.player.isSprinting() ? 1 : 0});
			if (s[0] > cx - 5.5 && s[0] < cx + 5.5) {
				over++;
				if (sank == null && (s[2] > 0 || s[1] < 279.49)) {
					sank = "the Ferryman's Stride: sprinting over still water the player went in (y " + s[1] + ", in water " + (s[2] > 0) + ", sprinting " + (s[3] > 0) + ")";
				}
			}
		}
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(5);
		fill(server, -6, 279, 9, 6, 279, 11, "smooth_stone");
		fill(server, -6, 278, 9, 6, 278, 11, "air");
		check(sank);
		expect(over >= 10, "the Ferryman's Stride: the sprint spent only " + over + " ticks over the water");

		// The Cat's Way: a sprint-jump along a three-high wall holds its height for up to 2 s.
		fill(server, -10, 280, -11, 10, 282, -11, "smooth_stone");
		check(on(server, p -> {
			CardCooldowns.reset(p, LegendWild.CATS_WAY);
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		tp(context, server, cx - 8.5, FLOOR, cz - 9.65, -90);
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keySprint);
		context.waitTicks(8);
		double ground = context.computeOnClient(mc -> mc.player.getY());
		context.getInput().holdKey(o -> o.keyJump);
		context.waitTick();
		context.getInput().releaseKey(o -> o.keyJump);
		context.waitTicks(24);
		double[] mid = context.computeOnClient(mc -> new double[] {mc.player.getY(), mc.player.onGround() ? 1 : 0, mc.player.getX()});
		long cd = on(server, p -> CardCooldowns.remaining(p, LegendWild.CATS_WAY));
		context.waitTicks(40);
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		double[] end = context.computeOnClient(mc -> new double[] {mc.player.getY(), mc.player.onGround() ? 1 : 0});
		float health = on(server, p -> p.getHealth());
		float max = on(server, p -> p.getMaxHealth());
		fill(server, -10, 280, -11, 10, 282, -11, "air");
		expect(mid[1] == 0 && mid[0] > ground + 0.9, "the Cat's Way: 25 ticks after a sprint-jump along the wall the player was at y " + mid[0]
			+ " (ground " + ground + ", on ground " + (mid[1] > 0) + ")");
		expect(cd > 60, "the Cat's Way: the server kept no 8 s cooldown (" + cd + ")");
		expect(end[1] > 0 && end[0] < ground + 0.01, "the Cat's Way: the run did not end on the ground (y " + end[0] + ")");
		expect(health >= max, "the Cat's Way: the run cost " + (max - health) + " health");
	}

	// ---------------------------------------------------------------- Survival: the Beastspeaker's Torc, the Greenway

	private static void survival(ClientGameTestContext context, TestServerContext server) {
		slot(server, "beastspeakers_torc", "greenway");
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5, 0);
		UUID[] beasts = new UUID[3];
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			Mob wolf = mob(level, EntityTypes.WOLF, cx + 2.5, FLOOR, cz + 0.5, true, 0, 8);
			Mob bear = mob(level, EntityTypes.POLAR_BEAR, cx - 2.5, FLOOR, cz + 0.5, true, 0, 30);
			beasts[0] = wolf.getUUID();
			beasts[1] = bear.getUUID();
			swing(p, wolf);
			swing(p, bear);
			return null;
		}));
		context.waitTicks(30);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			Mob wolf = (Mob) level.getEntity(beasts[0]);
			Mob bear = (Mob) level.getEntity(beasts[1]);
			boolean turned = wolf.getTarget() == p || bear.getTarget() == p || wolf instanceof net.minecraft.world.entity.NeutralMob n && n.isAngryAt(p, level);
			wolf.discard();
			bear.discard();
			return turned ? "the Beastspeaker's Torc: a struck wolf or polar bear turned on the player" : null;
		}));
		// A cow fed wheat follows the feeder.
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			Mob cow = mob(level, EntityTypes.COW, cx + 1.5, FLOOR, cz + 2.5, true, 0, 10);
			beasts[2] = cow.getUUID();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
			p.interactOn(cow, InteractionHand.MAIN_HAND, cow.position());
			// Empty-handed after, so it is the Torc that leads it, not the wheat.
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			LegendWild.Follow f = cow.getAttached(LegendWild.FOLLOWS);
			if (f == null || !f.player().equals(p.getUUID()) || f.until() - level.getGameTime() < 23900) {
				return "the Beastspeaker's Torc: the fed cow follows " + f;
			}
			return null;
		}));
		tp(context, server, cx + 12.5, FLOOR, cz + 0.5, 0);
		double far = on(server, p -> dist(p, beasts[2]));
		context.waitTicks(100);
		double close = on(server, p -> dist(p, beasts[2]));
		check(on(server, p -> {
			((ServerLevel) p.level()).getEntity(beasts[2]).discard();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return null;
		}));
		expect(close < 6 && close < far - 4, "the Beastspeaker's Torc: the fed cow stayed off (" + far + " → " + close + " blocks)");

		// The Greenway: walking east through berry bushes, a hedge of natural leaves and hanging vines, unslowed, unhurt.
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			int z = cz + 4;
			BlockState bush = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
			fill(level, -8, 279, 3, 8, 279, 5, Blocks.GRASS_BLOCK.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(cx - 5, 280, z), bush);
			level.setBlockAndUpdate(new BlockPos(cx - 4, 280, z), bush);
			level.setBlockAndUpdate(new BlockPos(cx - 2, 280, z + 1), Blocks.OAK_LOG.defaultBlockState());
			BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
			level.setBlockAndUpdate(new BlockPos(cx - 2, 280, z), leaves);
			level.setBlockAndUpdate(new BlockPos(cx - 2, 281, z), leaves.setValue(LeavesBlock.DISTANCE, 2));
			for (int y = 280; y <= 281; y++) {
				level.setBlockAndUpdate(new BlockPos(cx + 1, y, z + 1), Blocks.SMOOTH_STONE.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(cx + 1, y, z), Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true));
			}
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		tp(context, server, cx - 7.5, FLOOR, cz + 4.5, -90);
		context.getInput().holdKey(o -> o.keyUp);
		StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			context.waitTick();
			if (i % 6 == 0) {
				String step = context.computeOnClient(mc -> String.format(" %.2f/%.2f/%.2f%s%s", mc.player.getX() - cx, mc.player.getY(), mc.player.getZ() - cz,
					mc.player.isShiftKeyDown() ? "s" : "", mc.player.onGround() ? "g" : ""));
				trace.append(step);
			}
		}
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(3);
		double x = context.computeOnClient(mc -> mc.player.getX());
		String shapes = context.computeOnClient(mc -> {
			BlockPos leaf = new BlockPos(cx - 2, 280, cz + 4);
			BlockState st = mc.level.getBlockState(leaf);
			return "client " + st + " shape " + st.getCollisionShape(mc.level, leaf, net.minecraft.world.phys.shapes.CollisionContext.of(mc.player))
				+ " greenway " + Mythics.active(mc.player, "greenway") + " through " + LegendWild.throughLeaves(st, net.minecraft.world.phys.shapes.CollisionContext.of(mc.player), leaf);
		}) + on(server, p -> {
			BlockPos leaf = new BlockPos(cx - 2, 280, cz + 4);
			BlockState st = p.level().getBlockState(leaf);
			return "; server " + st + " shape " + st.getCollisionShape(p.level(), leaf, net.minecraft.world.phys.shapes.CollisionContext.of(p));
		});
		float hp = on(server, p -> p.getHealth());
		float maxHp = on(server, p -> p.getMaxHealth());
		boolean leavesStill = on(server, p -> p.level().getBlockState(new BlockPos(cx - 2, 280, cz + 4)).is(Blocks.OAK_LEAVES));
		check(on(server, p -> {
			fill((ServerLevel) p.level(), -8, 280, 3, 8, 282, 5, Blocks.AIR.defaultBlockState());
			fill((ServerLevel) p.level(), -8, 279, 3, 8, 279, 5, Blocks.SMOOTH_STONE.defaultBlockState());
			return null;
		}));
		expect(leavesStill, "the Greenway test is void: the leaves were gone");
		expect(x > cx + 3, "the Greenway: 3 s walking east through bushes, leaves and vines reached x " + (x - cx) + ", expected past +3; " + shapes + "; trace" + trace);
		expect(hp >= maxHp, "the Greenway: the berry bushes hurt (" + hp + " of " + maxHp + ")");

		// The Greenway's trail: a husk that loses sight of the player gives up within about 1.5 s (vanilla 3 s).
		UUID[] husk = new UUID[1];
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			fill(level, 7, 280, -6, 9, 282, -4, Blocks.SMOOTH_STONE.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(cx + 8, 280, cz - 5), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(cx + 8, 281, cz - 5), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(cx + 7, 281, cz - 5), Blocks.AIR.defaultBlockState());
			Mob m = mob(level, EntityTypes.HUSK, cx + 8.5, FLOOR, cz - 4.5, true, 20, 20);
			husk[0] = m.getUUID();
			return null;
		}));
		tp(context, server, cx + 2.5, FLOOR, cz - 4.5, -90);
		boolean seen = false;
		for (int i = 0; i < 20 && !seen; i++) {
			context.waitTicks(5);
			seen = on(server, p -> ((ServerLevel) p.level()).getEntity(husk[0]) instanceof Mob m && m.getTarget() == p);
		}
		expect(seen, "the Greenway test is void: the husk never took the player as target through its window");
		check(on(server, p -> {
			((ServerLevel) p.level()).setBlockAndUpdate(new BlockPos(cx + 7, 281, cz - 5), Blocks.SMOOTH_STONE.defaultBlockState());
			return null;
		}));
		int lost = -1;
		for (int i = 0; i < 80 && lost < 0; i++) {
			context.waitTick();
			if (on(server, p -> ((ServerLevel) p.level()).getEntity(husk[0]) instanceof Mob m && m.getTarget() == null)) {
				lost = i;
			}
		}
		check(on(server, p -> {
			((ServerLevel) p.level()).getEntity(husk[0]).discard();
			fill((ServerLevel) p.level(), 7, 280, -6, 9, 282, -4, Blocks.AIR.defaultBlockState());
			return null;
		}));
		expect(lost >= 0 && lost <= 45, "the Greenway: the husk lost the player's trail after " + lost + " ticks unseen, expected about 30 (vanilla 60)");
	}

	// ---------------------------------------------------------------- Smithing: the Ever-Ember, the Mender's Hand

	private static void smithing(ClientGameTestContext context, TestServerContext server) {
		slot(server, "ever_ember", "menders_hand");
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5, 0);
		BlockPos furnace = new BlockPos(cx + 2, 280, cz);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			level.setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
			AbstractFurnaceBlockEntity be = (AbstractFurnaceBlockEntity) level.getBlockEntity(furnace);
			p.openMenu(be);
			AbstractContainerMenu menu = p.containerMenu;
			menu.setCarried(new ItemStack(Items.RAW_IRON, 4));
			menu.clicked(0, 0, ContainerInput.PICKUP, p);
			menu.setCarried(new ItemStack(Items.COAL, 1));
			menu.clicked(1, 0, ContainerInput.PICKUP, p);
			p.closeContainer();
			if (!p.getUUID().equals(be.getAttached(LegendCraft.FILLED_BY))) {
				return "the Ever-Ember: filling the furnace did not make it the player's";
			}
			int total = ((LegendFurnaceAccess) be).mettle$cookingTotalTime();
			return total == 100 ? null : "the Ever-Ember: raw iron cooks in " + total + " ticks, expected 100 (half of 200)";
		}));
		context.waitTicks(215);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			AbstractFurnaceBlockEntity be = (AbstractFurnaceBlockEntity) level.getBlockEntity(furnace);
			int out = be.getItem(2).getCount();
			boolean coalLeft = be.getItem(1).isEmpty();
			be.clearContent();
			level.removeBlock(furnace, false);
			if (out < 2) {
				return "the Ever-Ember: " + out + " ingots in 10.75 s, expected 2 (twice as fast)";
			}
			return coalLeft ? null : "the Ever-Ember: the coal was never burnt";
		}));

		// The Mender's Hand: with no levels, a worn pickaxe and one ingot come out whole, its prior work unchanged; not twice.
		BlockPos anvil = new BlockPos(cx - 2, 280, cz);
		check(on(server, p -> {
			ServerLevel level = (ServerLevel) p.level();
			level.setBlockAndUpdate(anvil, Blocks.ANVIL.defaultBlockState());
			p.experienceLevel = 0;
			p.getInventory().clearContent();
			CardCooldowns.reset(p, LegendCraft.MENDERS_HAND);
			p.openMenu(level.getBlockState(anvil).getMenuProvider(level, anvil));
			if (!(p.containerMenu instanceof AnvilMenu menu)) {
				return "no anvil menu";
			}
			ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
			pick.setDamageValue(200);
			pick.set(DataComponents.REPAIR_COST, 3);
			menu.getSlot(0).set(pick);
			menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 2));
			ItemStack result = menu.getSlot(2).getItem();
			if (result.isEmpty() || result.getDamageValue() != 0 || result.getOrDefault(DataComponents.REPAIR_COST, -1) != 3 || menu.getCost() != 0) {
				return "the Mender's Hand: the anvil offered " + result + " damage " + result.getDamageValue() + ", prior work "
					+ result.getOrDefault(DataComponents.REPAIR_COST, -1) + ", for " + menu.getCost() + " levels";
			}
			menu.clicked(2, 0, ContainerInput.QUICK_MOVE, p);
			ItemStack got = ItemStack.EMPTY;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(Items.IRON_PICKAXE)) {
					got = p.getInventory().getItem(i);
				}
			}
			if (got.isEmpty() || got.getDamageValue() != 0 || p.experienceLevel != 0) {
				return "the Mender's Hand: with no levels the mended pickaxe could not be taken (" + got + ")";
			}
			if (CardCooldowns.remaining(p, LegendCraft.MENDERS_HAND) < 23000) {
				return "the Mender's Hand: not once per in-game day";
			}
			// Again the same day: vanilla's price and partial repair.
			ItemStack again = new ItemStack(Items.IRON_PICKAXE);
			again.setDamageValue(200);
			menu.getSlot(0).set(again);
			menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 1));
			ItemStack second = menu.getSlot(2).getItem();
			boolean vanilla = menu.getCost() > 0 && second.getDamageValue() > 0;
			p.closeContainer();
			p.getInventory().clearContent();
			level.removeBlock(anvil, false);
			return vanilla ? null : "the Mender's Hand: a second repair the same day was free again (" + menu.getCost() + " levels, damage "
				+ second.getDamageValue() + ")";
		}));
	}

	// ---------------------------------------------------------------- helpers

	private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z, boolean ai, float maxHealth, float health) {
		Mob m = type.create(level, EntitySpawnReason.COMMAND);
		m.snapTo(x, y, z, 0, 0);
		m.setNoAi(!ai);
		m.addTag(TAG);
		m.setPersistenceRequired();
		if (maxHealth > 0) {
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		}
		m.setHealth(health);
		level.addFreshEntity(m);
		return m;
	}

	private static void level(ServerPlayer p, String skill, int level) {
		MinecraftServer s = p.level().getServer();
		for (int i = 0; i < 12 && CharacterStore.get(p).level(Progress.skill(skill)) < level; i++) {
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:" + skill + " 3000 @a");
		}
	}

	private static void fill(TestServerContext server, int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		server.runCommand("fill " + (cx + x0) + " " + y0 + " " + (cz + z0) + " " + (cx + x1) + " " + y1 + " " + (cz + z1) + " minecraft:" + block);
	}

	private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (BlockPos pos : BlockPos.betweenClosed(cx + x0, y0, cz + z0, cx + x1, y1, cz + z1)) {
			level.setBlockAndUpdate(pos, state);
		}
	}

	private static void tp(ClientGameTestContext context, TestServerContext server, double x, double y, double z, float yaw) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " 0");
		context.waitTicks(5);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static <T> T on(TestServerContext server, Function<ServerPlayer, T> f) {
		return server.computeOnServer(s -> f.apply(player(s)));
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}

	private static void expect(boolean ok, String problem) {
		if (!ok) {
			throw new AssertionError(problem);
		}
	}
}
