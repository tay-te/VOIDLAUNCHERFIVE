package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.BandModifier;
import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.BurdenService;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.skill.AthleticsSkill;
import dev.voidmc.mettle.character.skill.athletics.AthleticsRules;
import dev.voidmc.mettle.character.skill.athletics.AthleticsState;
import dev.voidmc.mettle.character.skill.athletics.AthleticsXp;
import dev.voidmc.mettle.character.skill.athletics.Movement;
import dev.voidmc.mettle.character.skill.athletics.Trail;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.core.MettleAttributes;
import dev.voidmc.mettle.mixin.athletics.AthleticsFoodDataAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Athletics tree played for real: the client's own keys sprint, jump, swim and scramble on a course in the sky, and
 * the results are measured on both sides. XP runs through the real stat path. The burden module isn't in yet, so a
 * stand-in burden service (installed here and removed after) reports whatever load band a check needs; the Porter
 * perks are checked against it through the same {@code MettleServices} hooks the real one will call.
 *
 * <p>The roots, then three careers with a respec before each (Freerunner path, Porter path, Marathon), then the earned
 * perks and the minute cap. The character, counters and journal are put back exactly as found.
 */
final class AthleticsJourney {
	private static final String LEARN = "execute as @p run mettle perk learn mettle:athletics/";
	private static final double FLOOR = 200;

	private static int cx;
	private static int cz;

	private AthleticsJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("mettle background set mettle:ranger @a");
		double[] home = on(server, p -> new double[] {p.getX(), p.getY(), p.getZ()});
		CharacterState before = on(server, CharacterStore::get);
		Trail.Journal journalBefore = on(server, Trail::journal);
		Map<String, Long> countersBefore = on(server, p -> p.getAttachedOrElse(FeatCounters.COUNTERS, Map.of()));
		BurdenService realBurden = MettleServices.burden();
		FakeBurden burden = new FakeBurden();
		try {
			MettleServices.setBurden(burden);
			setUp(context, server);
			xp(server, burden);
			server.runCommand("mettle xp mettle:athletics 30000 @a");
			vitality(server);
			roots(context, server);
			// A full tree is 31 points and level 20 gives 20, so each career starts from a respec.
			respec(server);
			freerunnerPath(context, server);
			respec(server);
			porterPath(context, server, burden);
			respec(server);
			marathon(context, server, burden);
			earned(context, server, burden);
		} catch (Throwable t) {
			try {
				tearDown(context, server, realBurden, before, journalBefore, countersBefore, home);
			} catch (Throwable cleanup) {
				t.addSuppressed(cleanup);
			}
			throw t;
		}
		tearDown(context, server, realBurden, before, journalBefore, countersBefore, home);
	}

	// ---------------------------------------------------------------- the course

	/**
	 * A 41 × 41 floor at y 199 around the player, with: a sprint lane down the middle (south), a raised step east
	 * (Vaulter), a three-high wall west (Freerunner) and a one-wide water channel along the east edge (Swimmer).
	 */
	private static void setUp(ClientGameTestContext context, TestServerContext server) {
		int[] c = on(server, p -> new int[] {p.getBlockX(), p.getBlockZ()});
		cx = c[0];
		cz = c[1];
		server.runCommand("effect clear @a");
		fill(server, -20, 199, -20, 20, 199, 20, "smooth_stone");
		fill(server, -20, 200, -20, 20, 211, 20, "air");
		// Vaulter's step: one block up, running south from z -8.
		fill(server, 10, 200, -8, 14, 200, 19, "smooth_stone");
		// Freerunner's wall: three blocks high and ten deep, from z -6.
		fill(server, -14, 200, -6, -10, 202, 3, "smooth_stone");
		// Swimmer's channel: water two deep under a roof, x +18.
		fill(server, 16, 199, -19, 20, 203, 19, "smooth_stone");
		fill(server, 18, 200, -18, 18, 201, 18, "water");
		context.runOnClient(mc -> mc.options.autoJump().set(false));
		feed(server, 20);
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5);
	}

	private static void fill(TestServerContext server, int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		server.runCommand("fill " + (cx + x0) + " " + y0 + " " + (cz + z0) + " " + (cx + x1) + " " + y1 + " " + (cz + z1) + " minecraft:" + block);
	}

	private static void tearDown(ClientGameTestContext context, TestServerContext server, BurdenService realBurden, CharacterState before,
		Trail.Journal journal, Map<String, Long> counters, double[] home) {
		for (Function<net.minecraft.client.Options, net.minecraft.client.KeyMapping> key : List.<Function<net.minecraft.client.Options, net.minecraft.client.KeyMapping>>of(
			o -> o.keyUp, o -> o.keySprint, o -> o.keyJump, o -> o.keyShift)) {
			context.getInput().releaseKey(key);
		}
		MettleServices.setBurden(realBurden);
		check(on(server, p -> {
			CharacterStore.set(p, before);
			p.setAttached(Trail.JOURNAL, journal);
			p.setAttached(FeatCounters.COUNTERS, counters);
			EffectsApplier.apply(p);
			Movement.refresh(p);
			p.setShiftKeyDown(false);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.getFoodData().setFoodLevel(20);
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		server.runCommand("effect clear @a");
		server.runCommand("tp @p " + home[0] + " " + home[1] + " " + home[2]);
		fill(server, -20, 199, -20, 20, 211, 20, "air");
		fill(server, -1, 229, -1, 1, 229, 1, "air");
		server.runCommand("mettle background set mettle:ranger @a");
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- XP

	/** The real stat path: one point each for the sprint, swim and climb distances in AthleticsRules; load doubles and triples it; no XP riding. */
	private static void xp(TestServerContext server, FakeBurden burden) {
		check(on(server, p -> {
			AthleticsState state = AthleticsState.of(p);
			state.pendingXp = 0;
			double mult = multiplier(p);
			double start = xpOf(p);
			p.awardStat(Stats.SPRINT_ONE_CM, (int) AthleticsRules.SPRINT_CM_PER_XP);
			p.awardStat(Stats.SWIM_ONE_CM, (int) AthleticsRules.SWIM_CM_PER_XP);
			p.awardStat(Stats.CLIMB_ONE_CM, (int) AthleticsRules.CLIMB_CM_PER_XP);
			double light = xpOf(p) - start;
			burden.band = LoadBand.BURDENED;
			p.awardStat(Stats.SPRINT_ONE_CM, (int) AthleticsRules.SPRINT_CM_PER_XP);
			double burdened = xpOf(p) - start - light;
			burden.band = LoadBand.OVERBURDENED;
			p.awardStat(Stats.SPRINT_ONE_CM, (int) AthleticsRules.SPRINT_CM_PER_XP);
			double over = xpOf(p) - start - light - burdened;
			burden.band = LoadBand.LIGHT;
			if (!near(light, 3 * mult)) {
				return "XP for one point each of sprint, swim and climb: " + light + ", expected " + 3 * mult;
			}
			if (!near(burdened, 2 * mult) || !near(over, 3 * mult)) {
				return "load multiplier: Burdened " + burdened + " (expected " + 2 * mult + "), Overburdened " + over + " (expected " + 3 * mult + ")";
			}
			return null;
		}));
		server.runCommand("execute at @p run summon minecraft:oak_boat ~ ~ ~ {Tags:[\"athletics_boat\"]}");
		server.runCommand("ride @p mount @e[tag=athletics_boat,limit=1]");
		check(on(server, p -> {
			if (!p.isPassenger()) {
				return "could not seat the player in a boat";
			}
			double start = xpOf(p);
			p.awardStat(Stats.SPRINT_ONE_CM, 8000);
			return xpOf(p) == start ? null : "Athletics XP while riding a boat";
		}));
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[tag=athletics_boat]");
		server.runCommand("kill @e[type=item]");
	}

	// ---------------------------------------------------------------- roots

	private static void roots(ClientGameTestContext context, TestServerContext server) {
		double capacity = on(server, p -> p.getAttributeValue(MettleAttributes.CARRY_CAPACITY));
		double plain = sprintSpeed(context, server);
		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "pack_mule");
			server.runCommand(LEARN + "runner");
		}
		check(on(server, p -> {
			if (Perks.rank(p, AthleticsSkill.PACK_MULE) != 3 || Perks.rank(p, AthleticsSkill.RUNNER) != 3) {
				return "roots not learned: " + CharacterStore.get(p).perks();
			}
			double now = p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			return near(now, capacity + 12) ? null : "Pack Mule III: capacity " + now + ", expected " + (capacity + 12);
		}));
		double runner = sprintSpeed(context, server);
		expect(Math.abs(runner / plain - 1.09) < 0.02, "Runner III: sprint " + runner + " b/t against " + plain + ", expected ×1.09");
	}

	// ---------------------------------------------------------------- Path B, Freerunner mastery

	private static void freerunnerPath(ClientGameTestContext context, TestServerContext server) {
		double swim = swimSpeed(context, server);
		int fallHurt = fall(context, server, 5.5);
		expect(fallHurt > 0, "a 5.5 block fall did no harm without Soft Landing");
		double plainStep = vault(context, server);
		expect(plainStep < FLOOR + 0.5, "without Vaulter the sprint went up the step (y " + plainStep + ")");
		double[] plainJump = sprintJump(context, server);
		double plainScramble = scramble(context, server);
		expect(plainScramble < FLOOR + 2, "without Freerunner the wall was climbed (max y " + plainScramble + ")");

		server.runCommand(LEARN + "swimmer");
		server.runCommand(LEARN + "soft_landing");
		check(on(server, p -> {
			double oxygen = p.getAttributeValue(Attributes.OXYGEN_BONUS);
			double safe = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
			if (!Perks.has(p, AthleticsSkill.SWIMMER) || !Perks.has(p, AthleticsSkill.SOFT_LANDING)) {
				return "Freerunner tier II not learned: " + CharacterStore.get(p).perks();
			}
			return near(oxygen, 0.5) && near(safe, 6) ? null : "Swimmer/Soft Landing: oxygen bonus " + oxygen + " (0.5), safe fall " + safe + " (6)";
		}));
		double swimmer = swimSpeed(context, server);
		expect(Math.abs(swimmer / swim - 1.3) < 0.06, "Swimmer: swam " + swimmer + " b/t against " + swim + ", expected ×1.3");
		int softHurt = fall(context, server, 5.5);
		expect(softHurt == 0, "Soft Landing: a 5.5 block fall still took " + softHurt + " health");
		// Three blocks more fall the same as before.
		int softHigher = fall(context, server, 8.5);
		// Both falls sit near a damage step, so float rounding may move one by a point; it must still hurt about the same.
		expect(softHigher > 0 && Math.abs(softHigher - fallHurt) <= 1, "Soft Landing: an 8.5 block fall took " + softHigher + " health, expected about " + fallHurt + " as 5.5 did without it");

		server.runCommand(LEARN + "vaulter");
		server.runCommand(LEARN + "spring");
		check(on(server, p -> Perks.has(p, AthleticsSkill.VAULTER) && Perks.has(p, AthleticsSkill.SPRING) ? null
			: "Freerunner tier III not learned: " + CharacterStore.get(p).perks()));
		double vaulted = vault(context, server);
		expect(vaulted > FLOOR + 0.9, "Vaulter: the sprint stopped at the step (y " + vaulted + ")");
		double[] spring = sprintJump(context, server);
		expect(Math.abs(plainJump[0] - 1.25) < 0.1, "a plain sprint-jump rose " + plainJump[0] + ", expected 1.25");
		expect(Math.abs(spring[0] - 1.75) < 0.1, "Spring: the sprint-jump rose " + spring[0] + ", expected 1.75");
		expect(spring[1] - plainJump[1] > 0.7, "Spring: the sprint-jump carried " + spring[1] + " blocks against " + plainJump[1] + ", expected about a block more");

		server.runCommand(LEARN + "freerunner");
		check(on(server, p -> Perks.has(p, AthleticsSkill.FREERUNNER) ? null : "Freerunner mastery not learned: " + CharacterStore.get(p).perks()));
		int scramblesBefore = on(server, p -> AthleticsState.of(p).scrambles);
		double top = scramble(context, server);
		expect(top > FLOOR + 2.99, "Freerunner: the scramble reached y " + top + ", expected the top of the 3-block wall");
		check(on(server, p -> {
			if (p.getY() < FLOOR + 2.99) {
				return "Freerunner: the server has the player at y " + p.getY() + ", not on the wall";
			}
			int scrambles = AthleticsState.of(p).scrambles - scramblesBefore;
			return scrambles == 1 ? null : "Freerunner: the server accepted " + scrambles + " scrambles, expected 1";
		}));
	}

	// ---------------------------------------------------------------- Path A, Ox mastery

	private static void porterPath(ClientGameTestContext context, TestServerContext server, FakeBurden burden) {
		double capacity = on(server, p -> p.getAttributeValue(MettleAttributes.CARRY_CAPACITY));
		server.runCommand(LEARN + "strong_back");
		server.runCommand(LEARN + "steady_pace");
		check(on(server, p -> {
			if (!Perks.has(p, AthleticsSkill.STRONG_BACK) || !Perks.has(p, AthleticsSkill.STEADY_PACE)) {
				return "Porter tier II not learned: " + CharacterStore.get(p).perks();
			}
			burden.ratio = 0.5f;
			BandRules rules = rules(p);
			if (!rules.equals(new BandRules(0.85f, 1.0f, 1.25f, 1.0f, true))) {
				return "Strong Back: bands " + rules + ", expected Burdened at 0.85";
			}
			// Steady Pace: inside the Burdened band, burden's penalty is off and Athletics' own −5 % stands in.
			burden.ratio = 0.9f;
			burden.band = LoadBand.BURDENED;
			BandRules steady = rules(p);
			Movement.refresh(p);
			AttributeModifier slow = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Movement.STEADY_SPEED);
			burden.band = LoadBand.LIGHT;
			burden.ratio = 0.5f;
			Movement.refresh(p);
			if (steady.penaltyScale() != 0f) {
				return "Steady Pace: penalty scale " + steady.penaltyScale() + " while Burdened, expected 0";
			}
			return slow != null && near(slow.amount(), -0.05) ? null : "Steady Pace: speed modifier " + slow + ", expected -0.05";
		}));

		check(on(server, p -> {
			burden.band = LoadBand.OVERBURDENED;
			boolean sprint = Movement.canSprint(p, false);
			burden.band = LoadBand.LIGHT;
			return sprint ? "Overburdened sprint allowed without Hauler's Stride" : null;
		}));
		double light = sprintSpeed(context, server);
		server.runCommand(LEARN + "beast_of_burden");
		server.runCommand(LEARN + "haulers_stride");
		check(on(server, p -> {
			if (!Perks.has(p, AthleticsSkill.BEAST_OF_BURDEN) || !Perks.has(p, AthleticsSkill.HAULERS_STRIDE)) {
				return "Porter tier III not learned: " + CharacterStore.get(p).perks();
			}
			double now = p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			if (!near(now, capacity + 22)) {
				return "Beast of Burden: capacity " + now + ", expected " + (capacity + 22);
			}
			if (rules(p).canBePinned()) {
				return "Beast of Burden: still pinnable";
			}
			burden.band = LoadBand.OVERBURDENED;
			boolean sprint = Movement.canSprint(p, false);
			burden.band = LoadBand.PINNED;
			boolean pinned = Movement.canSprint(p, false);
			burden.band = LoadBand.LIGHT;
			return sprint && !pinned ? null : "Hauler's Stride: sprint gate Overburdened " + sprint + " (true), Pinned " + pinned + " (false)";
		}));
		// Hauler's Stride for real: Overburdened, the client sprints at half the sprint bonus.
		burden.band = LoadBand.OVERBURDENED;
		double hauling = sprintSpeed(context, server);
		burden.band = LoadBand.LIGHT;
		expect(Math.abs(hauling / light - 1.15 / 1.3) < 0.02, "Hauler's Stride: sprint " + hauling + " b/t against " + light + ", expected ×" + 1.15 / 1.3);

		server.runCommand(LEARN + "ox");
		check(on(server, p -> {
			if (!Perks.has(p, AthleticsSkill.OX)) {
				return "Ox not learned: " + CharacterStore.get(p).perks();
			}
			double now = p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			if (!near(now, capacity + 60)) {
				return "Ox: capacity " + now + ", expected " + (capacity + 60);
			}
			burden.ratio = 1.1f;
			BandRules rules = rules(p);
			burden.ratio = 0.5f;
			return rules.equals(new BandRules(0.85f, 1.0f, 1.25f, 0.5f, false)) ? null : "Ox: bands " + rules + ", expected half the penalty";
		}));
		oxCarry(server);
	}

	/** Ox: sneak-use an empty hand on a chest to carry it off with its contents; placing it puts them back. */
	private static void oxCarry(TestServerContext server) {
		check(on(server, p -> {
			var level = p.level();
			BlockPos at = new BlockPos(cx - 5, 200, cz + 12);
			level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
			if (!(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
				return "Ox: no chest to lift";
			}
			chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
			chest.setItem(5, new ItemStack(Items.IRON_INGOT, 10));
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setShiftKeyDown(true);
			InteractionResult result = p.gameMode.useItemOn(p, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
			p.setShiftKeyDown(false);
			ItemStack carried = p.getMainHandItem();
			ItemContainerContents contents = carried.get(DataComponents.CONTAINER);
			if (!result.consumesAction() || !level.getBlockState(at).isAir() || !carried.is(Items.CHEST) || contents == null) {
				return "Ox: lifting gave " + result + ", block " + level.getBlockState(at) + ", hand " + carried;
			}
			if (contents.nonEmptyItemCopyStream().count() != 2 || carried.getMaxStackSize() != 1) {
				return "Ox: the carried chest holds " + contents + " and stacks to " + carried.getMaxStackSize();
			}
			BlockPos to = at.east(3);
			carried.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(to.below()), Direction.UP, to.below(), false)));
			if (!(level.getBlockEntity(to) instanceof ChestBlockEntity placed)) {
				return "Ox: the carried chest wouldn't go down again";
			}
			boolean back = placed.getItem(0).is(Items.COBBLESTONE) && placed.getItem(0).getCount() == 64 && placed.getItem(5).getCount() == 10;
			placed.clearContent();
			level.removeBlock(to, false);
			return back ? null : "Ox: the chest came down without its contents";
		}));
	}

	// ---------------------------------------------------------------- Marathon, Courier's Stride

	private static void marathon(ClientGameTestContext context, TestServerContext server, FakeBurden burden) {
		server.runCommand(LEARN + "strong_back");
		server.runCommand(LEARN + "steady_pace");
		expect(!sprintsHungry(context, server), "sprinting at 4 food without Marathon");
		float plain = on(server, AthleticsJourney::sprintHunger);
		expect(Math.abs(plain - 1.0f) < 1e-4, "10 m sprinted cost " + plain + " exhaustion, expected 1.0");
		server.runCommand(LEARN + "marathon");
		check(on(server, p -> Perks.has(p, AthleticsSkill.MARATHON) ? null : "Marathon not learned: " + CharacterStore.get(p).perks()));
		expect(sprintsHungry(context, server), "Marathon: no sprinting at 4 food");
		float marathon = on(server, AthleticsJourney::sprintHunger);
		expect(Math.abs(marathon - 0.5f) < 1e-4, "Marathon: 10 m sprinted cost " + marathon + " exhaustion, expected 0.5");

		// Courier's Stride: 20 s of unbroken sprinting (in circles, by turning a little every tick), all of it real movement.
		server.runCommand("mettle perk grant mettle:athletics/couriers_stride @a");
		feed(server, 20);
		double xpBefore = on(server, AthleticsJourney::xpOf);
		double room = on(server, p -> AthleticsState.of(p).meter.remaining(p.level().getGameTime()));
		// Turning right from facing south circles around (cx - 8.5, cz + 10.5), clear of everything built.
		tp(context, server, cx - 4.5, FLOOR, cz + 10.5);
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keySprint);
		float exhaustionAt = 0;
		for (int i = 0; i < 440; i++) {
			context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 4f));
			context.waitTick();
			if (i == 300) {
				boolean early = on(server, p -> AthleticsState.of(p).stride);
				expect(!early, "Courier's Stride came after only 15 s");
			}
			if (i == 420) {
				exhaustionAt = on(server, p -> exhaustion(p));
			}
		}
		float exhaustionEnd = on(server, p -> exhaustion(p));
		boolean clientStride = context.computeOnClient(mc -> AthleticsState.of(mc.player).stride && mc.player.isSprinting());
		String stride = on(server, p -> {
			if (!AthleticsState.of(p).stride) {
				return "Courier's Stride: no stride after 22 s of sprinting (" + AthleticsState.of(p).sprintTicks + " ticks)";
			}
			AttributeModifier speed = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Movement.SPRINT_SPEED);
			return speed != null && near(speed.amount(), 0.15) ? null : "Courier's Stride: sprint modifier " + speed + ", expected 0.15";
		});
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		check(stride);
		expect(clientStride, "Courier's Stride: the client never found its stride");
		expect(exhaustionEnd == exhaustionAt, "Courier's Stride: sprinting still cost " + (exhaustionEnd - exhaustionAt) + " exhaustion");
		check(on(server, p -> AthleticsState.of(p).stride ? "Courier's Stride outlasted the sprint" : null));
		// The run itself paid Athletics XP through the stat path (about 120 m sprinted).
		double gained = on(server, AthleticsJourney::xpOf) - xpBefore;
		expect(room < 2 || gained >= 2, "sprinting 20 s gave " + gained + " Athletics XP (room under the cap " + room + ")");
	}

	// ---------------------------------------------------------------- earned perks, the cap

	private static void earned(ClientGameTestContext context, TestServerContext server, FakeBurden burden) {
		// Ironlung: 2 km swum under water.
		tp(context, server, cx + 18.5, FLOOR, cz - 10.5);
		check(on(server, p -> {
			if (!p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
				return "Ironlung: the player's eyes aren't under water";
			}
			p.awardStat(Stats.SWIM_ONE_CM, (int) AthleticsRules.IRONLUNG_CM);
			if (!Perks.has(p, AthleticsSkill.IRONLUNG)) {
				return "Ironlung not earned after 2 km under water (counter " + FeatCounters.get(p, AthleticsXp.IRONLUNG_COUNTER) + ")";
			}
			double oxygen = p.getAttributeValue(Attributes.OXYGEN_BONUS);
			double mining = p.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
			return near(oxygen, 1.0) && near(mining, 1.0) ? null : "Ironlung: oxygen bonus " + oxygen + " (1.0), under-water mining " + mining + " (1.0)";
		}));

		// Mountain Goat: a peak above y 220 counts once; ten far-apart peaks earn it.
		fill(server, -1, 229, -1, 1, 229, 1, "smooth_stone");
		long peaks = on(server, p -> FeatCounters.get(p, Trail.PEAKS_COUNTER));
		tp(context, server, cx + 0.5, 230, cz + 0.5);
		context.waitTicks(45);
		tp(context, server, cx + 1.5, 230, cz + 0.5);
		context.waitTicks(45);
		check(on(server, p -> {
			long now = FeatCounters.get(p, Trail.PEAKS_COUNTER);
			if (now != peaks + 1) {
				return "Mountain Goat: " + (now - peaks) + " peaks counted on one summit, expected 1";
			}
			double safe = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
			FeatCounters.add(p, Trail.PEAKS_COUNTER, AthleticsRules.PEAKS - now);
			double after = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
			return Perks.has(p, AthleticsSkill.MOUNTAIN_GOAT) && near(after, safe + 3) ? null : "Mountain Goat: safe fall " + safe + " → " + after;
		}));
		tp(context, server, cx + 0.5, FLOOR, cz + 0.5);

		// The cap: AthleticsRules.XP_CAP_PER_MINUTE a minute, then nothing until the next minute.
		check(on(server, p -> {
			AthleticsState state = AthleticsState.of(p);
			state.pendingXp = 0;
			long now = p.level().getGameTime();
			double room = state.meter.remaining(now);
			double start = xpOf(p);
			p.awardStat(Stats.SPRINT_ONE_CM, (int) AthleticsRules.SPRINT_CM_PER_XP * 500);
			double paid = xpOf(p) - start;
			p.awardStat(Stats.SPRINT_ONE_CM, (int) AthleticsRules.SPRINT_CM_PER_XP * 5);
			double more = xpOf(p) - start - paid;
			if (!near(paid, room * multiplier(p))) {
				return "cap: 500 points of sprinting paid " + paid + ", expected " + room * multiplier(p) + " (room " + room + ")";
			}
			return more == 0 ? null : "cap: " + more + " XP paid past the minute's cap";
		}));
	}

	// ---------------------------------------------------------------- measured on the client

	/** Sprints south down the middle lane; the speed over 20 ticks once up to pace, in blocks a tick. */
	private static double sprintSpeed(ClientGameTestContext context, TestServerContext server) {
		tp(context, server, cx + 0.5, FLOOR, cz - 17.5);
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keySprint);
		context.waitTicks(12);
		Vec3 a = context.computeOnClient(mc -> mc.player.position());
		boolean sprinting = context.computeOnClient(mc -> mc.player.isSprinting());
		context.waitTicks(20);
		Vec3 b = context.computeOnClient(mc -> mc.player.position());
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		expect(sprinting, "the client didn't sprint");
		return a.subtract(b).horizontalDistance() / 20;
	}

	/** Walks forward under water down the channel; blocks a tick over 30 ticks. */
	private static double swimSpeed(ClientGameTestContext context, TestServerContext server) {
		tp(context, server, cx + 18.5, FLOOR, cz - 17.5);
		context.getInput().holdKey(o -> o.keyUp);
		context.waitTicks(15);
		Vec3 a = context.computeOnClient(mc -> mc.player.position());
		context.waitTicks(30);
		Vec3 b = context.computeOnClient(mc -> mc.player.position());
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(5);
		return a.subtract(b).horizontalDistance() / 30;
	}

	/** Drops the player onto the floor from {@code height}; the health it cost. */
	private static int fall(ClientGameTestContext context, TestServerContext server, double height) {
		check(on(server, p -> {
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		float max = on(server, Player::getMaxHealth);
		tp(context, server, cx + 5.5, FLOOR + height, cz + 5.5);
		context.waitTicks(40);
		float health = on(server, Player::getHealth);
		check(on(server, p -> {
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		return Math.round(max - health);
	}

	/** Sprints at the one-block step; the client's height after (and checks the step height while sprinting). */
	private static double vault(ClientGameTestContext context, TestServerContext server) {
		tp(context, server, cx + 12.5, FLOOR, cz - 17.5);
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keySprint);
		context.waitTicks(10);
		double[] step = context.computeOnClient(mc -> new double[] {mc.player.getAttributeValue(Attributes.STEP_HEIGHT),
			Perks.has(mc.player, AthleticsSkill.VAULTER) ? 1 : 0});
		context.waitTicks(45);
		double y = context.computeOnClient(mc -> mc.player.getY());
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		double expected = step[1] > 0 ? 1.1 : 0.6;
		expect(near(step[0], expected), "step height while sprinting " + step[0] + ", expected " + expected);
		return y;
	}

	/** One sprint-jump down the lane: {rise, horizontal carry from take-off to landing}. */
	private static double[] sprintJump(ClientGameTestContext context, TestServerContext server) {
		tp(context, server, cx + 0.5, FLOOR, cz - 17.5);
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keySprint);
		context.waitTicks(15);
		Vec3 takeoff = context.computeOnClient(mc -> mc.player.position());
		context.getInput().holdKey(o -> o.keyJump);
		context.waitTick();
		context.getInput().releaseKey(o -> o.keyJump);
		double top = takeoff.y;
		Vec3 landing = null;
		for (int i = 0; i < 40 && landing == null; i++) {
			context.waitTick();
			Vec3 at = context.computeOnClient(mc -> mc.player.position());
			top = Math.max(top, at.y);
			if (i > 2 && context.computeOnClient(mc -> mc.player.onGround())) {
				landing = at;
			}
		}
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		expect(landing != null, "the sprint-jump never landed");
		return new double[] {top - takeoff.y, landing.subtract(takeoff).horizontalDistance()};
	}

	/** Walks into the three-high wall holding jump; the highest the client got (stops once on top). */
	private static double scramble(ClientGameTestContext context, TestServerContext server) {
		tp(context, server, cx - 11.5, FLOOR, cz - 9.5);
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keyJump);
		double top = FLOOR;
		for (int i = 0; i < 60; i++) {
			context.waitTick();
			double[] at = context.computeOnClient(mc -> new double[] {mc.player.getY(), mc.player.onGround() ? 1 : 0});
			top = Math.max(top, at[0]);
			// Up and standing on the wall: stop before walking off its far side.
			if (at[0] > FLOOR + 2.9 && at[1] > 0) {
				break;
			}
		}
		context.getInput().releaseKey(o -> o.keyJump);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		return top;
	}

	/** Tries to sprint at 4 food; whether the client sprinted. */
	private static boolean sprintsHungry(ClientGameTestContext context, TestServerContext server) {
		feed(server, 4);
		context.waitTicks(5);
		tp(context, server, cx + 0.5, FLOOR, cz - 17.5);
		context.getInput().holdKey(o -> o.keyUp);
		context.getInput().holdKey(o -> o.keySprint);
		context.waitTicks(15);
		boolean sprinting = context.computeOnClient(mc -> mc.player.isSprinting());
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		feed(server, 20);
		return sprinting;
	}

	// ---------------------------------------------------------------- server helpers

	/** Exhaustion from 10 m of sprinting on the ground, through vanilla's own bookkeeping. */
	private static float sprintHunger(ServerPlayer p) {
		boolean was = p.isSprinting();
		p.setSprinting(true);
		float before = exhaustion(p);
		p.checkMovementStatistics(10, 0, 0);
		float cost = exhaustion(p) - before;
		p.setSprinting(was);
		return cost;
	}

	private static float exhaustion(ServerPlayer p) {
		return ((AthleticsFoodDataAccess) p.getFoodData()).mettle$exhaustion();
	}

	private static BandRules rules(ServerPlayer p) {
		BandRules rules = BandRules.DEFAULT;
		for (BandModifier m : MettleServices.bandModifiers()) {
			rules = m.adjust(p, rules);
		}
		return rules;
	}

	private static double xpOf(ServerPlayer p) {
		return CharacterStore.get(p).xp(AthleticsSkill.ATHLETICS);
	}

	private static double multiplier(ServerPlayer p) {
		CharacterState state = CharacterStore.get(p);
		return Progress.multiplier(state, CharacterStore.background(p.level().registryAccess(), state), AthleticsSkill.ATHLETICS);
	}

	private static void vitality(TestServerContext server) {
		for (int i = 0; i < 12; i++) {
			server.runCommand("execute as @p run mettle attribute vitality");
		}
	}

	private static void respec(TestServerContext server) {
		server.runCommand("execute as @p run mettle respec");
		vitality(server);
	}

	private static void feed(TestServerContext server, int food) {
		check(on(server, p -> {
			p.getFoodData().setFoodLevel(food);
			p.getFoodData().setSaturation(food >= 20 ? 5f : 0f);
			return null;
		}));
	}

	private static void tp(ClientGameTestContext context, TestServerContext server, double x, double y, double z) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 0");
		context.waitTicks(5);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static <T> T on(TestServerContext server, Function<ServerPlayer, T> f) {
		return server.computeOnServer(s -> f.apply(player(s)));
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-4;
	}

	private static void expect(boolean ok, String problem) {
		if (!ok) {
			throw new AssertionError(problem);
		}
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}

	/**
	 * Stands in for the burden module (not merged yet): reports the band a check sets, a load at {@code ratio} of
	 * capacity, and folds the registered band modifiers the way the real one does. Both sides read it (single player).
	 */
	private static final class FakeBurden implements BurdenService {
		volatile LoadBand band = LoadBand.LIGHT;
		volatile float ratio = 0.5f;

		@Override
		public float weight(ItemInstance stack) {
			return 0f;
		}

		@Override
		public float weight(Player player, ItemInstance stack) {
			return 0f;
		}

		@Override
		public float load(Player player) {
			return ratio * capacity(player);
		}

		@Override
		public float capacity(Player player) {
			return (float) player.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
		}

		@Override
		public LoadBand band(Player player) {
			return band;
		}

		@Override
		public BandRules rules(Player player) {
			BandRules rules = BandRules.DEFAULT;
			for (BandModifier m : MettleServices.bandModifiers()) {
				rules = m.adjust(player, rules);
			}
			return rules;
		}
	}
}
