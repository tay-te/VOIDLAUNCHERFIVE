package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import dev.voidmc.mettle.character.skill.marksmanship.Hits;
import dev.voidmc.mettle.character.skill.marksmanship.HuntersMark;
import dev.voidmc.mettle.client.marksmanship.MarksmanshipClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Marksmanship tree played for real on the server: bows drawn and released through the bow's own code, arrows that
 * fly and strike through the arrow's own code, and every perk's effect measured. It runs on a glass platform high in
 * the sky (nothing in the way) and puts the player, inventory and background back afterwards.
 *
 * <p>Three passes, because one character can't afford the whole tree: Sharpshooter with Deadeye, Quickshot with
 * Arrowstorm, then Volley; earned perks (feats, synergies, the teaching) last.
 */
final class MarksmanshipJourney {
	private static final String LEARN = "execute as @p run mettle perk learn mettle:marksmanship/";
	private static final double TOLERANCE = 0.09;

	/** The arena's origin: the player stands here, targets a few blocks along +x. */
	private static BlockPos origin;
	private static Vec3 home;
	private static final List<ItemStack> SAVED = new ArrayList<>();
	private static ClientGameTestContext client;

	private MarksmanshipJourney() {
	}

	static void run(TestSingleplayerContext world) {
		client = clientContext(world);
		TestServerContext server = world.getServer();
		server.runCommand("mettle background set mettle:ranger @a");
		server.runCommand("gamerule spawn_mobs false");
		setUp(server);
		try {
			server.runCommand("mettle xp mettle:marksmanship 30000 @a");
			sharpshooter(world);
			respec(server);
			quickshot(world);
			respec(server);
			volley(server);
			earned(world);
		} catch (Throwable t) {
			// Clean up, but never let a cleanup failure hide what went wrong.
			try {
				client.getInput().releaseKey(o -> o.keyUse);
				tearDown(server);
			} catch (Throwable cleanup) {
				t.addSuppressed(cleanup);
			}
			throw t;
		}
		client.getInput().releaseKey(o -> o.keyUse);
		tearDown(server);
	}

	// ---------------------------------------------------------------- pass 1: roots, Sharpshooter, Deadeye

	private static void sharpshooter(TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		// Vanilla first: a 10-tick draw and an arrow recipe.
		double vanilla = on(server, p -> speed(shoot(p, 10).getFirst()));
		expect(Math.abs(vanilla - 1.25) < TOLERANCE, "vanilla 10-tick draw flew at " + vanilla + ", expected 1.25");
		check(on(server, p -> craftArrows(p) == 4 ? null : "arrows crafted without Fletcher: " + craftArrows(p)));

		agility(server);
		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "steady_aim");
			server.runCommand(LEARN + "retrieval");
		}
		for (String perk : new String[] {"long_shot", "hunters_mark", "steady_breath", "deadeye"}) {
			server.runCommand(LEARN + perk);
		}
		held(server, MarksmanshipSkill.DEADEYE, MarksmanshipSkill.STEADY_BREATH, MarksmanshipSkill.HUNTERS_MARK, MarksmanshipSkill.LONG_SHOT);
		check(on(server, p -> Perks.rank(p, MarksmanshipSkill.STEADY_AIM) == 3 && Perks.rank(p, MarksmanshipSkill.RETRIEVAL) == 3 ? null
			: "roots not at rank 3"));

		// Steady Aim III: the same 10 ticks count as 13.
		double aimed = on(server, p -> speed(shoot(p, 10).getFirst()));
		expect(Math.abs(aimed - 1.7225) < TOLERANCE, "Steady Aim III 10-tick draw flew at " + aimed + ", expected 1.72");

		// Steady Breath: sneaking at full draw, 30 % faster and dead straight; standing, ordinary.
		check(on(server, p -> {
			p.setShiftKeyDown(true);
			AbstractArrow arrow = shoot(p, 20).getFirst();
			p.setShiftKeyDown(false);
			double v = speed(arrow);
			double straight = arrow.getDeltaMovement().normalize().dot(p.getLookAngle());
			if (Math.abs(v - 3.9) > TOLERANCE || straight < 0.99999) {
				return "Steady Breath: speed " + v + " (3.9), alignment " + straight;
			}
			double plain = speed(shoot(p, 20).getFirst());
			return Math.abs(plain - 3.0) < TOLERANCE ? null : "standing full draw flew at " + plain + ", expected 3.0";
		}));

		// Long Shot and Deadeye on a sturdy pig: body shot close (4), body shot from 42 blocks (+25 % = 5), headshot (×2, crit).
		check(on(server, p -> {
			LivingEntity pig = spawn(p, EntityTypes.PIG, 4, 0, 100);
			float a = strike(p, pig, 0.3);
			p.teleportTo(origin.getX() + 0.5 - 38, origin.getY(), origin.getZ() + 0.5);
			float b = strike(p, pig, 0.3);
			p.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
			float c = strike(p, pig, 0.75);
			if (a != 4f) {
				return "close body shot dealt " + a + ", expected 4";
			}
			if (b != 5f) {
				return "Long Shot from ~42 blocks dealt " + b + ", expected 5";
			}
			if (c < 8f || c > 14f) {
				return "Deadeye headshot dealt " + c + ", expected 8 to 14";
			}
			return HuntersMark.marked(p, pig) ? null : "Hunter's Mark: pig not marked on the server";
		}));
		// Hunter's Mark reaches only the shooter's client.
		int pigId = on(server, p -> mobs(p).getFirst().getId());
		client.waitFor(mc -> mc.level != null && mc.level.getEntity(pigId) instanceof net.minecraft.world.entity.Entity pig && mc.shouldEntityAppearGlowing(pig), 40);
		client.runOnClient(mc -> {
			mc.player.setYRot(-90f);
			mc.player.setXRot(20f);
		});
		client.waitTicks(3);
		client.takeScreenshot("mettle_marksmanship_hunters_mark");
		expect(client.computeOnClient(mc -> mc.level.getEntity(pigId) instanceof net.minecraft.world.entity.Entity pig && MarksmanshipClient.marked(pig)
			&& mc.shouldEntityAppearGlowing(pig) && !pig.isCurrentlyGlowing()), "Hunter's Mark: the client draws no personal outline on the pig");

		// Deadeye: a headshot kill gives the arrow back.
		check(on(server, p -> {
			LivingEntity pig = spawn(p, EntityTypes.PIG, 4, -3, 5);
			strike(p, pig, 0.75);
			if (pig.isAlive()) {
				return "Deadeye: a 5-health pig survived a headshot";
			}
			int back = arrowsAround(p, pig.position());
			return back >= 1 ? null : "Deadeye: the headshot kill gave no arrow back";
		}));

		// Retrieval III: 30 arrows into one pig; about 45 % come back when it dies.
		check(on(server, p -> {
			LivingEntity pig = spawn(p, EntityTypes.PIG, 4, 3, 1000);
			for (int i = 0; i < 30; i++) {
				strike(p, pig, 0.3);
			}
			int lodged = Hits.lodged(pig);
			if (lodged < 3 || lodged > 27) {
				return "Retrieval: " + lodged + " of 30 arrows lodged, expected about 13";
			}
			Vec3 at = pig.position();
			pig.kill(p.level());
			int back = arrowsAround(p, at);
			return back == lodged ? null : "Retrieval: " + lodged + " lodged but " + back + " dropped";
		}));
		clearArena(server);
	}

	// ---------------------------------------------------------------- pass 2: Quickshot and Arrowstorm

	private static void quickshot(TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		agility(server);
		for (String perk : new String[] {"long_shot", "pinning_shot", "quick_nock", "fletcher", "double_nock", "quiverwise", "arrowstorm"}) {
			server.runCommand(LEARN + perk);
		}
		held(server, MarksmanshipSkill.PINNING_SHOT, MarksmanshipSkill.FLETCHER, MarksmanshipSkill.DOUBLE_NOCK, MarksmanshipSkill.QUIVERWISE,
			MarksmanshipSkill.ARROWSTORM);

		// Quiverwise: crossbows load in 15 ticks instead of 25. Fletcher: 6 arrows a craft.
		check(on(server, p -> {
			int charge = CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), p);
			if (charge != 15) {
				return "Quiverwise: crossbow charge " + charge + " ticks, expected 15";
			}
			int made = craftArrows(p);
			return made == 6 ? null : "Fletcher: a craft made " + made + ", expected 6";
		}));

		// Quick Nock: a draw begun within a second of the last shot is 40 % faster; later, it isn't.
		on(server, p -> shoot(p, 20));
		waitTicks(server, 10);
		double quick = on(server, p -> speed(shoot(p, 8).getFirst()));
		waitTicks(server, 40);
		double slow = on(server, p -> speed(shoot(p, 8).getFirst()));
		expect(Math.abs(quick - 1.4336) < TOLERANCE, "Quick Nock: 8-tick draw flew at " + quick + ", expected 1.43");
		expect(Math.abs(slow - 0.96) < TOLERANCE, "without Quick Nock an 8-tick draw flew at " + slow + ", expected 0.96");

		// Double Nock: of five shots in a row exactly one looses a second arrow, which can't be picked up.
		check(on(server, p -> {
			int doubled = 0;
			for (int i = 0; i < 5; i++) {
				List<AbstractArrow> arrows = shoot(p, 20);
				if (arrows.size() == 2) {
					doubled++;
					if (arrows.get(1).pickup != AbstractArrow.Pickup.CREATIVE_ONLY) {
						return "Double Nock's twin can be picked up";
					}
				} else if (arrows.size() != 1) {
					return "a shot loosed " + arrows.size() + " arrows";
				}
			}
			return doubled == 1 ? null : "Double Nock doubled " + doubled + " of 5 shots";
		}));

		// Quiverwise: 32 shots from 64 arrows spend about 24.
		check(on(server, p -> {
			stock(p, 64);
			for (int i = 0; i < 32; i++) {
				shoot(p, 20);
			}
			int spent = 64 - p.getInventory().countItem(Items.ARROW);
			return spent >= 14 && spent <= 30 ? null : "Quiverwise: 32 shots spent " + spent + " arrows, expected about 24";
		}));
		clearArena(server);

		// Pinning Shot: a fully drawn hit slows the pig.
		int pinned = on(server, p -> {
			stock(p, 64);
			LivingEntity pig = spawn(p, EntityTypes.PIG, 5, 0, 100);
			p.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
			shoot(p, 20);
			return pig.getId();
		});
		server.waitFor(s -> pinnedPig(s, pinned), 40);
		check(on(server, p -> pinnedPig(p.level().getServer(), pinned) ? null : "Pinning Shot: the pig was not slowed"));
		clearArena(server);

		// Arrowstorm: hold the use key with a bow; storm arrows fly every half second after full draw, without letting go.
		on(server, p -> {
			stock(p, 64);
			return null;
		});
		client.getInput().holdKey(o -> o.keyUse);
		server.waitFor(s -> ownArrows(s).size() >= 2, 120);
		int storm = on(server, p -> p.isUsingItem() ? ownArrows(p.level().getServer()).size() : -1);
		client.getInput().releaseKey(o -> o.keyUse);
		waitTicks(server, 5);
		expect(storm >= 2, "Arrowstorm: " + storm + " arrows while holding (-1: the bow was released), expected at least 2");
		clearArena(server);
	}

	// ---------------------------------------------------------------- pass 3: Volley

	private static void volley(TestServerContext server) {
		agility(server);
		for (String perk : new String[] {"long_shot", "hunters_mark", "volley"}) {
			server.runCommand(LEARN + perk);
		}
		held(server, MarksmanshipSkill.VOLLEY);
		check(on(server, p -> {
			stock(p, 20);
			p.setShiftKeyDown(true);
			List<AbstractArrow> arrows = shoot(p, 20);
			p.setShiftKeyDown(false);
			int spent = 20 - p.getInventory().countItem(Items.ARROW);
			if (arrows.size() != 3 || spent != 3) {
				return "Volley loosed " + arrows.size() + " arrows and spent " + spent + ", expected 3 and 3";
			}
			double fan = arrows.get(1).getDeltaMovement().normalize().dot(arrows.get(2).getDeltaMovement().normalize());
			return fan < 0.995 ? null : "Volley's arrows are not fanned (" + fan + ")";
		}));
		clearArena(server);
	}

	// ---------------------------------------------------------------- earned: feats, synergies, the teaching

	private static void earned(TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		// Sixty Paces: a hit from 66 blocks earns it; then arrows fly 20 % faster.
		check(on(server, p -> {
			LivingEntity pig = spawn(p, EntityTypes.PIG, 4, 0, 100);
			p.teleportTo(origin.getX() + 0.5 - 62, origin.getY(), origin.getZ() + 0.5);
			strike(p, pig, 0.3);
			p.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
			if (!Perks.has(p, MarksmanshipSkill.SIXTY_PACES)) {
				return "Sixty Paces not earned by a 66-block hit";
			}
			double v = speed(shoot(p, 20).getFirst());
			return Math.abs(v - 3.6) < TOLERANCE ? null : "Sixty Paces: full draw flew at " + v + ", expected 3.6";
		}));

		// Wingshot: the 25th blaze shot down earns it; then fliers take +50 %.
		check(on(server, p -> {
			FeatCounters.add(p, MarksmanshipSkill.WINGSHOT_COUNTER, 24);
			LivingEntity doomed = spawn(p, EntityTypes.BLAZE, 4, 3, 1);
			strike(p, doomed, 0.8);
			if (!Perks.has(p, MarksmanshipSkill.WINGSHOT)) {
				return "Wingshot not earned by the 25th blaze (" + FeatCounters.get(p, MarksmanshipSkill.WINGSHOT_COUNTER) + ")";
			}
			LivingEntity blaze = spawn(p, EntityTypes.BLAZE, 4, -3, 100);
			float dealt = strike(p, blaze, 0.8);
			return dealt == 6f ? null : "Wingshot: a 4-damage arrow dealt " + dealt + " to a blaze, expected 6";
		}));
		clearArena(server);

		// Huntsman (Marksmanship 10 + Survival 10): a chicken shot dead gives the arrow back and one more raw chicken.
		server.runCommand("mettle xp mettle:survival 5000 @a");
		check(on(server, p -> {
			if (!Perks.has(p, MarksmanshipSkill.HUNTSMAN)) {
				return "Huntsman not unlocked at Marksmanship 20 and Survival 10";
			}
			LivingEntity hen = spawn(p, EntityTypes.CHICKEN, 4, 0, -1);
			Vec3 at = hen.position();
			strike(p, hen, 0.3);
			if (hen.isAlive()) {
				return "Huntsman: the chicken survived";
			}
			int meat = itemsAround(p, at, Items.CHICKEN);
			int expected = SignaturePerks.has(p, SignaturePerks.FIELD_DRESSING) ? 3 : 2;
			if (meat != expected) {
				return "Huntsman: " + meat + " raw chicken, expected " + expected;
			}
			return arrowsAround(p, at) == 1 ? null : "Huntsman: " + arrowsAround(p, at) + " arrows back, expected 1";
		}));
		clearArena(server);

		// Leaping Shot (Marksmanship 10 + Athletics 10): a half-drawn shot in mid-air is critical; on the ground it isn't.
		server.runCommand("mettle xp mettle:athletics 5000 @a");
		check(on(server, p -> {
			if (!Perks.has(p, MarksmanshipSkill.LEAPING_SHOT)) {
				return "Leaping Shot not unlocked at Marksmanship 20 and Athletics 10";
			}
			p.setOnGround(true);
			boolean grounded = shoot(p, 5).getFirst().isCritArrow();
			p.setOnGround(false);
			boolean leaping = shoot(p, 5).getFirst().isCritArrow();
			p.setOnGround(true);
			return !grounded && leaping ? null : "Leaping Shot: crit on the ground " + grounded + ", in the air " + leaping;
		}));

		// Steppe Draw (taught): full power at 15 ticks, and no spread from the saddle.
		server.runCommand("mettle perk grant mettle:marksmanship/steppe_draw @a");
		check(on(server, p -> {
			double v = speed(shoot(p, 15).getFirst());
			if (Math.abs(v - 3.6) > TOLERANCE) {
				return "Steppe Draw: a 15-tick draw flew at " + v + ", expected full power (3.6 with Sixty Paces)";
			}
			LivingEntity horse = spawn(p, EntityTypes.HORSE, 0, 2, -1);
			p.startRiding(horse, true, false);
			double straight = shoot(p, 20).getFirst().getDeltaMovement().normalize().dot(p.getLookAngle());
			p.stopRiding();
			return straight > 0.99999 ? null : "Steppe Draw: a shot from the saddle strayed (" + straight + ")";
		}));
		clearArena(server);
	}

	// ---------------------------------------------------------------- the arena

	private static void setUp(TestServerContext server) {
		on(server, p -> {
			home = p.position();
			SAVED.clear();
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				SAVED.add(p.getInventory().getItem(i).copy());
			}
			p.getInventory().clearContent();
			origin = new BlockPos(p.getBlockX(), 220, p.getBlockZ());
			ServerLevel level = p.level();
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-66, -1, -5), origin.offset(8, -1, 5))) {
				level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 2);
			}
			p.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
			p.setYRot(-90f);
			p.setXRot(0f);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
			stock(p, 64);
			return null;
		});
		waitTicks(server, 10);
	}

	private static void tearDown(TestServerContext server) {
		clearArena(server);
		respec(server);
		server.runCommand("mettle background set mettle:ranger @a");
		on(server, p -> {
			p.setShiftKeyDown(false);
			p.stopUsingItem();
			p.teleportTo(home.x, home.y, home.z);
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-66, -1, -5), origin.offset(8, -1, 5))) {
				p.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
			p.getInventory().clearContent();
			for (int i = 0; i < SAVED.size(); i++) {
				p.getInventory().setItem(i, SAVED.get(i));
			}
			return null;
		});
	}

	private static void clearArena(TestServerContext server) {
		on(server, p -> {
			AABB box = new AABB(origin).inflate(120);
			p.level().getEntitiesOfClass(AbstractArrow.class, box, a -> true).forEach(AbstractArrow::discard);
			p.level().getEntitiesOfClass(ItemEntity.class, box, e -> true).forEach(ItemEntity::discard);
			p.level().getEntitiesOfClass(Mob.class, box, m -> m.entityTags().contains("mettle_mm")).forEach(Mob::discard);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
			return null;
		});
	}

	private static void respec(TestServerContext server) {
		server.runCommand("execute as @p run mettle respec");
	}

	/** Agility up to the mastery gate (Ranger starts at 6). Extra attempts just fail. */
	private static void agility(TestServerContext server) {
		for (int i = 0; i < 8; i++) {
			server.runCommand("execute as @p run mettle attribute agility");
		}
	}

	// ---------------------------------------------------------------- shooting

	/** Draws the held bow for {@code ticks} and lets go, through BowItem's own code. Returns the arrows it loosed. */
	private static List<AbstractArrow> shoot(ServerPlayer p, int ticks) {
		if (!p.getMainHandItem().is(Items.BOW)) {
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		}
		if (p.getInventory().countItem(Items.ARROW) < 4) {
			stock(p, 64);
		}
		Set<Integer> before = new HashSet<>();
		for (AbstractArrow a : ownArrows(p.level().getServer())) {
			before.add(a.getId());
		}
		ItemStack bow = p.getMainHandItem();
		p.startUsingItem(InteractionHand.MAIN_HAND);
		bow.releaseUsing(p.level(), p, bow.getUseDuration(p) - ticks);
		p.stopUsingItem();
		List<AbstractArrow> loosed = new ArrayList<>();
		for (AbstractArrow a : ownArrows(p.level().getServer())) {
			if (!before.contains(a.getId())) {
				loosed.add(a);
			}
		}
		loosed.sort((x, y) -> Integer.compare(x.getId(), y.getId()));
		if (loosed.isEmpty()) {
			throw new AssertionError("a " + ticks + "-tick draw loosed nothing");
		}
		return loosed;
	}

	private static double speed(AbstractArrow arrow) {
		return arrow.getDeltaMovement().length();
	}

	/**
	 * An arrow of the player's flies into {@code target} at {@code height} above its feet at speed 2 (4 damage, no
	 * crit), ticking through the arrow's own movement and hit code. Returns the health it took.
	 */
	private static float strike(ServerPlayer p, LivingEntity target, double height) {
		ServerLevel level = p.level();
		target.damageCooldownTime = 0;
		float before = target.getHealth();
		Arrow arrow = new Arrow(level, target.getX() - 1.5, target.getY() + height, target.getZ(), new ItemStack(Items.ARROW), null);
		arrow.setOwner(p);
		arrow.setDeltaMovement(2, 0, 0);
		level.addFreshEntity(arrow);
		for (int i = 0; i < 4 && !arrow.isRemoved(); i++) {
			arrow.tick();
		}
		arrow.discard();
		return before - target.getHealth();
	}

	private static List<AbstractArrow> ownArrows(MinecraftServer s) {
		ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
		return p.level().getEntitiesOfClass(AbstractArrow.class, p.getBoundingBox().inflate(150), a -> a.getOwner() == p);
	}

	private static boolean pinnedPig(MinecraftServer s, int id) {
		ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
		return p.level().getEntity(id) instanceof LivingEntity pig && pig.hasEffect(MobEffects.SLOWNESS);
	}

	// ---------------------------------------------------------------- small helpers

	/** A still, tagged mob {@code dx} along the firing line and {@code dz} across it; health −1 leaves it as it spawns. */
	private static LivingEntity spawn(ServerPlayer p, EntityType<? extends Mob> type, int dx, int dz, float health) {
		Mob mob = type.create(p.level(), EntitySpawnReason.COMMAND);
		if (mob == null) {
			throw new AssertionError("could not create " + type);
		}
		mob.setNoAi(true);
		mob.addTag("mettle_mm");
		mob.snapTo(origin.getX() + dx + 0.5, origin.getY(), origin.getZ() + dz + 0.5, 90f, 0f);
		if (health > 0) {
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(health, 1));
			mob.setHealth(health);
		}
		p.level().addFreshEntity(mob);
		return mob;
	}

	private static List<Mob> mobs(ServerPlayer p) {
		return p.level().getEntitiesOfClass(Mob.class, new AABB(origin).inflate(120), m -> m.entityTags().contains("mettle_mm"));
	}

	private static void stock(ServerPlayer p, int arrows) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(Items.ARROW)) {
				p.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		p.getInventory().add(new ItemStack(Items.ARROW, arrows));
	}

	private static int arrowsAround(ServerPlayer p, Vec3 at) {
		return itemsAround(p, at, Items.ARROW);
	}

	private static int itemsAround(ServerPlayer p, Vec3 at, net.minecraft.world.item.Item item) {
		return p.level().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(1.5), e -> e.getItem().is(item))
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	/** Lays flint, stick and feather in a crafting grid and reads the result slot. */
	private static int craftArrows(ServerPlayer p) {
		// An id no open screen has, so the result-slot packet the server sends is ignored by the client.
		CraftingMenu menu = new CraftingMenu(99, p.getInventory(), ContainerLevelAccess.create(p.level(), p.blockPosition()));
		menu.getSlot(1).set(new ItemStack(Items.FLINT));
		menu.getSlot(4).set(new ItemStack(Items.STICK));
		menu.getSlot(7).set(new ItemStack(Items.FEATHER));
		return menu.getSlot(0).getItem().is(Items.ARROW) ? menu.getSlot(0).getItem().getCount() : 0;
	}

	private static void held(TestServerContext server, Identifier... perks) {
		check(on(server, p -> {
			for (Identifier perk : perks) {
				if (!Perks.has(p, perk)) {
					return "not learned: " + perk + " (perks " + dev.voidmc.mettle.core.CharacterStore.get(p).perks().keySet() + ", agility points "
						+ dev.voidmc.mettle.core.CharacterStore.get(p).spent() + ")";
				}
			}
			return null;
		}));
	}

	private static void waitTicks(TestServerContext server, int ticks) {
		int target = server.computeOnServer(MinecraftServer::getTickCount) + ticks;
		server.waitFor(s -> s.getTickCount() >= target, ticks + 20);
	}

	/** The tour's client context (MettleTour passes only the world; see docs/requests/marksmanship.md). */
	private static ClientGameTestContext clientContext(TestSingleplayerContext world) {
		try {
			java.lang.reflect.Field field = world.getClass().getDeclaredField("context");
			field.setAccessible(true);
			return (ClientGameTestContext) field.get(world);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Marksmanship: no client context in " + world.getClass(), e);
		}
	}

	private static <T> T on(TestServerContext server, Function<ServerPlayer, T> action) {
		return server.computeOnServer(s -> action.apply(s.getPlayerList().getPlayers().getFirst()));
	}

	private static void expect(boolean ok, String problem) {
		if (!ok) {
			throw new AssertionError("Marksmanship: " + problem);
		}
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError("Marksmanship: " + problem);
		}
	}
}
