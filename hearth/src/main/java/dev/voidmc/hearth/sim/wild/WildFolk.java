package dev.voidmc.hearth.sim.wild;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementKind;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * What posts' folk do beyond their routines (docs/LIVING_WORLD_2.md §7.3):
 * <ul>
 *   <li><b>The toll stop</b> ({@link TollStop}, priority 150): a player who has not paid today comes up the road to the
 *   gate; the toll keeper steps into the passage, calls a halt and opens the conversation: pay the toll (2–10 bits), talk
 *   your way through (a speech check), or go round. Refused, the post's guards stand in the passage a while. Crossing the
 *   gate's line within 40 blocks without paying, seen by its folk, is toll evasion (reported to the law as trespass, the
 *   toll's worth).</li>
 *   <li><b>The fog bell</b>: in rain, snow and storm the hospice's bell is rung, three strokes every fifteen seconds, to
 *   guide travellers on the pass.</li>
 *   <li><b>Words</b> (order 150–179): the hospice's brothers give a meal and dress wounds once a day; a hermit tells of far
 *   places; every post's folk say what their place is.</li>
 * </ul>
 * Costs: the gate watch runs every 10 ticks over embodied toll posts and the players near them; the bell every 20 ticks
 * over embodied hospices. Nothing runs for a post nobody is near.
 */
public final class WildFolk {
	/** The gate's zone: a player within this of the gate on the road is stopped. */
	static final int STOP_RANGE = 12;
	static final int WATCH_RANGE = 40;
	private static boolean behavioursRegistered;
	private static final TollStop TOLL_STOP = new TollStop();
	private static final BarTheWay BAR = new BarTheWay();
	/** Toll posts' gates barred by refusal: settlement id → game time until. */
	static final Map<Long, Long> BARRED = new HashMap<>();
	/** Per player: which side of each gate (settlement id) they were last on, and when they were last stopped. */
	private static final Map<UUID, Map<Long, Integer>> SIDES = new HashMap<>();
	private static final Map<UUID, Long> STOPPED = new HashMap<>();
	private static final Map<Long, BlockPos> BELLS = new HashMap<>();
	private static int bellStroke;
	/** Whether the toll stop wants this folk now (for the tour's diagnosis). */
	public static boolean wantsToll(NpcService.FolkHandle folk) {
		return TOLL_STOP.wants(folk);
	}

	/** Toll stops begun this session (the tour and the stats). */
	public static int tollStops;

	private WildFolk() {
	}

	static void init() {
		NpcService npc = HearthServices.npc();
		if (npc != null) {
			addBehaviours(npc);
		}
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			NpcService late = HearthServices.npc();
			if (late != null) {
				addBehaviours(late);
			}
		});
		DialogueProvider.register(new WildTalk());
		ServerTickEvents.END_SERVER_TICK.register(WildFolk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BARRED.clear();
			SIDES.clear();
			STOPPED.clear();
			BELLS.clear();
		});
	}

	private static synchronized void addBehaviours(NpcService npc) {
		if (behavioursRegistered) {
			return;
		}
		behavioursRegistered = true;
		npc.registerBehaviour(TOLL_STOP);
		npc.registerBehaviour(BAR);
	}

	// ---------------------------------------------------------------- the gate

	/** A toll post's gate: its middle on the road and the way the road runs through it. */
	record Gate(BlockPos at, Direction along) {
		/** Which side of the gate's line a point is on (-1, 0, 1), and how far along the line from the road. */
		int side(double x, double z) {
			double v = (x - this.at.getX() - 0.5) * this.along.getStepX() + (z - this.at.getZ() - 0.5) * this.along.getStepZ();
			return v > 1.5 ? 1 : v < -1.5 ? -1 : 0;
		}

		double across(double x, double z) {
			Direction r = this.along.getClockWise();
			return Math.abs((x - this.at.getX() - 0.5) * r.getStepX() + (z - this.at.getZ() - 0.5) * r.getStepZ());
		}
	}

	static @Nullable Gate gate(Settlement s) {
		PlotView p = PostRules.main(s, "law/toll_gate");
		if (p == null) {
			return null;
		}
		PlotSpec spec = p.spec();
		int x = spec.origin().getX() + spec.sizeX() / 2;
		int z = spec.origin().getZ() + spec.sizeZ() / 2;
		return new Gate(new BlockPos(x, spec.floorY(), z), spec.facing());
	}

	/** What the toll is at this gate: 2–10 bits, by the post (and dearer at a king's bridge). */
	static long toll(Settlement s) {
		PostData d = PostData.of(s);
		long seed = d == null ? s.id() : d.site.seed();
		return 2 + Math.floorMod(seed >>> 11, 9L);
	}

	static long day(Settlement s) {
		HearthWorldDay w = HearthWorldDay.of(s);
		return w == null ? 0 : w.day();
	}

	/** The sim day of a settlement's world. */
	record HearthWorldDay(SimWorld world) {
		static @Nullable HearthWorldDay of(Settlement s) {
			SimServer sim = SimServer.current();
			if (sim == null) {
				return null;
			}
			for (SimWorld w : sim.worlds()) {
				if (w.settlement(s.id()) == s) {
					return new HearthWorldDay(w);
				}
			}
			return null;
		}

		long day() {
			return this.world.day();
		}
	}

	static boolean paid(Settlement s, ServerPlayer player) {
		PostData d = PostData.of(s);
		return d != null && d.paid(player.getUUID(), day(s));
	}

	static boolean exempt(ServerPlayer player) {
		return player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
	}

	/** The odds of talking one's way through: charm and speech, against a keeper who has heard every tale. */
	static double persuadeOdds(ServerPlayer player, Settlement s) {
		StandingService standing = HearthServices.standing();
		float charm = standing == null ? 0 : standing.charisma(player);
		int speech = HearthServices.playerSkills().level(player, dev.voidmc.hearth.api.PlayerSkills.SPEECH);
		int rep = s.reputation(player.getUUID());
		return Mth.clamp(0.2 + 0.3 * charm + 0.03 * speech + rep / 400.0, 0.05, 0.85);
	}

	/** The keeper steps into the passage, calls a halt and opens the conversation. */
	static final class TollStop implements NpcService.FolkBehaviour {
		private final Map<UUID, UUID> target = new HashMap<>();

		@Override
		public Identifier id() {
			return Hearth.id("wild/toll_stop");
		}

		@Override
		public int priority() {
			return 150;
		}

		@Override
		public boolean wants(NpcService.FolkHandle folk) {
			if (folk.resident().job != JobType.TOLLKEEPER || folk.settlement().kind() != SettlementKind.POST || !folk.awake()) {
				return false;
			}
			return traveller(folk) != null;
		}

		private @Nullable ServerPlayer traveller(NpcService.FolkHandle folk) {
			Gate g = gate(folk.settlement());
			if (g == null || !(folk.entity().level() instanceof ServerLevel level)) {
				return null;
			}
			long now = folk.gameTime();
			for (ServerPlayer p : level.players()) {
				if (exempt(p) || paid(folk.settlement(), p)) {
					continue;
				}
				double d = p.distanceToSqr(g.at.getX() + 0.5, g.at.getY(), g.at.getZ() + 0.5);
				Long last = STOPPED.get(p.getUUID());
				if (d < STOP_RANGE * STOP_RANGE && g.across(p.getX(), p.getZ()) < 5 && (last == null || now - last > 1200)) {
					return p;
				}
			}
			return null;
		}

		@Override
		public void start(NpcService.FolkHandle folk) {
			ServerPlayer p = traveller(folk);
			Gate g = gate(folk.settlement());
			if (p == null || g == null) {
				return;
			}
			this.target.put(folk.resident().id, p.getUUID());
			tollStops++;
			STOPPED.put(p.getUUID(), folk.gameTime());
			folk.moveTo(g.at, 1.0);
			EconomyService eco = HearthServices.economy();
			long toll = toll(folk.settlement());
			Component coins = eco == null ? Component.literal(toll + " bits") : eco.format(toll);
			folk.say(Component.translatable("hearth.wild.toll.halt." + Math.floorMod(p.getId() + folk.resident().id.hashCode(), 3), coins), p);
		}

		@Override
		public boolean tick(NpcService.FolkHandle folk) {
			UUID id = this.target.get(folk.resident().id);
			if (id == null || !(folk.entity().level() instanceof ServerLevel level)) {
				return false;
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null || paid(folk.settlement(), p) || p.distanceToSqr(folk.entity()) > 24 * 24) {
				return false;
			}
			folk.lookAt(p);
			// face to face, or the keeper at his post in the passage with the traveller on the road before it
			double d2 = p.distanceToSqr(folk.entity());
			if (folk.gameTime() % 10 == 0 && (d2 < 8 * 8 || folk.arrived() && d2 < STOP_RANGE * STOP_RANGE)) {
				var talking = dev.voidmc.hearth.npc.NpcModule.conversations().talkingTo(p);
				if (talking == null && folk.entity() instanceof dev.voidmc.hearth.npc.entity.Folk f) {
					dev.voidmc.hearth.npc.NpcModule.conversations().open(p, f);
					return false;
				}
			}
			return folk.gameTime() - STOPPED.getOrDefault(p.getUUID(), 0L) < 400;
		}

		@Override
		public void stop(NpcService.FolkHandle folk) {
			this.target.remove(folk.resident().id);
		}
	}

	/** Refused at the gate: the post's guards stand in the passage a while. */
	static final class BarTheWay implements NpcService.FolkBehaviour {
		@Override
		public Identifier id() {
			return Hearth.id("wild/bar_the_way");
		}

		@Override
		public int priority() {
			return 151;
		}

		@Override
		public boolean wants(NpcService.FolkHandle folk) {
			if (folk.resident().job != JobType.GUARD || folk.settlement().kind() != SettlementKind.POST || !folk.awake()) {
				return false;
			}
			Long until = BARRED.get(folk.settlement().id());
			return until != null && until > folk.gameTime() && gate(folk.settlement()) != null;
		}

		@Override
		public void start(NpcService.FolkHandle folk) {
			Gate g = gate(folk.settlement());
			if (g != null) {
				Direction r = g.along.getClockWise();
				int k = (int) Math.floorMod(folk.resident().id.getLeastSignificantBits(), 3L) - 1;
				folk.moveTo(g.at.offset(r.getStepX() * k, 0, r.getStepZ() * k), 1.1);
			}
		}

		@Override
		public boolean tick(NpcService.FolkHandle folk) {
			Long until = BARRED.get(folk.settlement().id());
			return until != null && until > folk.gameTime();
		}

		@Override
		public void stop(NpcService.FolkHandle folk) {
			folk.stop();
		}
	}

	// ---------------------------------------------------------------- the server tick

	private static void tick(MinecraftServer server) {
		SimServer sim = SimServer.current();
		if (sim == null || server.getTickCount() % 10 != 0) {
			return;
		}
		long t0 = System.nanoTime();
		try {
			boolean bell = server.getTickCount() % 20 == 0;
			for (SimWorld w : sim.worlds()) {
				for (SimSettlement s : w.settlements()) {
					if (!s.embodied() || s.kind() != SettlementKind.POST) {
						continue;
					}
					String type = s.postType();
					if (type.equals(PostType.TOLL_GATE.getSerializedName())) {
						watch(w, s);
					} else if (bell && type.equals(PostType.HOSPICE.getSerializedName())) {
						fogBell(w, s);
					}
				}
			}
			if (bell) {
				bellStroke++;
			}
		} catch (RuntimeException e) {
			Hearth.LOG.error("hearth wild: the posts' watch failed", e);
		}
		Wild.TIMER.add(System.nanoTime() - t0);
	}

	/** Who crosses the gate's line without paying, and whether the post's folk see it. */
	private static void watch(SimWorld w, SimSettlement s) {
		Gate g = gate(s);
		if (g == null) {
			return;
		}
		ServerLevel level = w.level();
		for (ServerPlayer p : level.players()) {
			if (exempt(p) || p.distanceToSqr(g.at.getX(), g.at.getY(), g.at.getZ()) > WATCH_RANGE * WATCH_RANGE) {
				continue;
			}
			int side = g.side(p.getX(), p.getZ());
			Map<Long, Integer> sides = SIDES.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
			Integer was = sides.get(s.id());
			if (side != 0) {
				sides.put(s.id(), side);
			}
			if (was == null || side == 0 || was == side || paid(s, p)) {
				continue;
			}
			// over the line unpaid: an offence if the post's folk see it
			NpcService npc = HearthServices.npc();
			boolean seen = false;
			if (npc != null) {
				for (NpcService.FolkHandle f : npc.folkNear(level, p.position(), 32)) {
					if (f.settlement().id() == s.id() && f.awake() && f.canSee(p)) {
						seen = true;
						f.say(Component.translatable("hearth.wild.toll.evaded." + Math.floorMod(p.getId(), 3)), null);
						break;
					}
				}
			}
			if (seen) {
				LawService law = HearthServices.law();
				if (law != null) {
					law.report(new LawService.Crime(LawService.Offence.TRESPASS, p.getUUID(), s.id(), p.blockPosition(), null, toll(s), true,
						level.getGameTime()));
				}
				// the toll is owed still: reckoned paid for the day, so one crossing is one offence
				PostData d = PostData.of(s);
				if (d != null) {
					d.tolls.put(p.getUUID(), w.day());
					s.markDirty();
				}
			}
		}
	}

	/** Rings the hospice's bell in foul weather: three strokes every fifteen seconds. */
	private static void fogBell(SimWorld w, SimSettlement s) {
		ServerLevel level = w.level();
		if (!level.isRaining()) {
			return;
		}
		int phase = bellStroke % 15;
		if (phase > 2) {
			return;
		}
		BlockPos bell = BELLS.computeIfAbsent(s.id(), id -> findBell(level, s));
		level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4.0F, 0.8F + phase * 0.03F);
	}

	/** Where a hospice's bell was rung from (null until it has been): for the tour. */
	public static @Nullable BlockPos bellOf(SimSettlement s) {
		return BELLS.get(s.id());
	}

	/** The bell of the hospice's chapel (searched once in its plot), or a point over its middle. */
	private static BlockPos findBell(ServerLevel level, SimSettlement s) {
		PlotView p = PostRules.main(s, "civic/hospice");
		BlockPos centre = s.center().above(10);
		if (p == null) {
			return centre;
		}
		PlotSpec spec = p.spec();
		centre = new BlockPos(spec.origin().getX() + spec.sizeX() / 2, spec.floorY() + 10, spec.origin().getZ() + spec.sizeZ() / 2);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = spec.floorY(); y <= spec.floorY() + spec.maxHeight(); y++) {
			for (int x = spec.origin().getX(); x < spec.origin().getX() + spec.sizeX(); x++) {
				for (int z = spec.origin().getZ(); z < spec.origin().getZ() + spec.sizeZ(); z++) {
					m.set(x, y, z);
					if (level.isLoaded(m) && level.getBlockState(m).is(Blocks.BELL)) {
						return m.immutable();
					}
				}
			}
		}
		return centre;
	}

	// ---------------------------------------------------------------- words

	/** The posts' conversation options (order 150–179). */
	static final class WildTalk implements DialogueProvider {
		private final Map<UUID, String> replies = new HashMap<>();
		/** Per player, the sim day the brothers last fed and tended them. */
		private final Map<UUID, Long> tended = new HashMap<>();

		@Override
		public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
			PostData d = settlement.kind() == SettlementKind.POST ? PostData.of(settlement) : null;
			if (d == null) {
				return;
			}
			PostType type = d.site.type();
			out.add(new Option("wild:about", Component.translatable("hearth.wild.ask." + type.getSerializedName()), 150));
			DialogueProvider.describe("wild:about", DialogueProvider.Info.of("talk"));
			if (type == PostType.TOLL_GATE && resident.job == JobType.TOLLKEEPER) {
				if (paid(settlement, player)) {
					out.add(new Option("wild:toll/thanks", Component.translatable("hearth.wild.toll.paid_already"), 151));
					DialogueProvider.describe("wild:toll/thanks", DialogueProvider.Info.of("talk"));
				} else {
					EconomyService eco = HearthServices.economy();
					long toll = toll(settlement);
					Component coins = eco == null ? Component.literal(toll + " bits") : eco.format(toll);
					boolean can = eco != null && eco.balance(player) >= toll;
					out.add(new Option("wild:toll/pay", Component.translatable("hearth.wild.toll.pay", coins), 152, !can, null,
						can ? null : Component.translatable("hearth.wild.toll.short")));
					DialogueProvider.describe("wild:toll/pay", new DialogueProvider.Info("service", -1, toll, null));
					int odds = (int) Math.round(persuadeOdds(player, settlement) * 100);
					out.add(new Option("wild:toll/talk", Component.translatable("hearth.wild.toll.talk"), 153, false,
						Component.translatable("hearth.wild.odds", odds), null));
					DialogueProvider.describe("wild:toll/talk", new DialogueProvider.Info("persuade", odds, -1, dev.voidmc.hearth.api.PlayerSkills.SPEECH));
					out.add(new Option("wild:toll/round", Component.translatable("hearth.wild.toll.round"), 154));
					DialogueProvider.describe("wild:toll/round", DialogueProvider.Info.of("leave"));
				}
			}
			if (type == PostType.HOSPICE || type == PostType.MONASTERY) {
				out.add(new Option("wild:tend", Component.translatable("hearth.wild.tend"), 160));
				DialogueProvider.describe("wild:tend", DialogueProvider.Info.of("service"));
			}
			if (type == PostType.HERMITAGE) {
				out.add(new Option("wild:rumour", Component.translatable("hearth.wild.rumour"), 161));
				DialogueProvider.describe("wild:rumour", DialogueProvider.Info.of("talk"));
			}
		}

		@Override
		public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
			PostData d = PostData.of(settlement);
			if (d == null || !optionId.startsWith("wild:")) {
				return false;
			}
			long day = day(settlement);
			switch (optionId) {
				case "wild:about" -> this.replies.put(player.getUUID(), "hearth.wild.about." + d.site.type().getSerializedName()
					+ "." + Math.floorMod(resident.id.hashCode() + day, 3));
				case "wild:toll/thanks" -> this.replies.put(player.getUUID(), "hearth.wild.toll.go_on");
				case "wild:toll/pay" -> {
					EconomyService eco = HearthServices.economy();
					long toll = toll(settlement);
					if (eco == null || !eco.charge(player, toll)) {
						return false;
					}
					d.tolls.put(player.getUUID(), day);
					d.tollsTaken += toll;
					settlement.deposit(toll);
					settlement.markDirty();
					this.replies.put(player.getUUID(), "hearth.wild.toll.paid." + Math.floorMod(player.getId(), 3));
				}
				case "wild:toll/talk" -> {
					if (player.getRandom().nextDouble() < persuadeOdds(player, settlement)) {
						d.tolls.put(player.getUUID(), day);
						settlement.markDirty();
						this.replies.put(player.getUUID(), "hearth.wild.toll.talked");
					} else {
						BARRED.put(settlement.id(), player.level().getGameTime() + 600);
						this.replies.put(player.getUUID(), "hearth.wild.toll.refused");
					}
				}
				case "wild:toll/round" -> {
					BARRED.put(settlement.id(), player.level().getGameTime() + 600);
					this.replies.put(player.getUUID(), "hearth.wild.toll.go_round");
				}
				case "wild:tend" -> {
					Long last = this.tended.get(player.getUUID());
					if (last != null && last == day) {
						this.replies.put(player.getUUID(), "hearth.wild.tend.again");
					} else {
						this.tended.put(player.getUUID(), day);
						player.getFoodData().eat(6, 0.6F);
						player.heal(8.0F);
						this.replies.put(player.getUUID(), "hearth.wild.tend.given");
					}
				}
				case "wild:rumour" -> this.replies.put(player.getUUID(), "rumour");
				default -> {
					return false;
				}
			}
			return true;
		}

		@Override
		public @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
			String key = this.replies.remove(player.getUUID());
			if (key == null) {
				return null;
			}
			if (key.equals("rumour")) {
				return rumour(player, settlement);
			}
			if (key.startsWith("hearth.wild.toll.go_on") || key.startsWith("hearth.wild.toll.paid")) {
				return Component.translatable(key);
			}
			return Component.translatable(key);
		}

		/** A far place the hermit has heard of: a town 1 500 blocks and more away, which way and how far in leagues. */
		private static Component rumour(ServerPlayer player, Settlement settlement) {
			HearthWorldDay w = HearthWorldDay.of(settlement);
			if (w == null) {
				return Component.translatable("hearth.wild.rumour.none");
			}
			List<dev.voidmc.hearth.api.HearthWorld.SiteInfo> sites = w.world().knownSites(settlement.center(), 4000);
			dev.voidmc.hearth.api.HearthWorld.SiteInfo far = null;
			int k = (int) Math.floorMod(player.level().getGameTime() / 24000 + player.getId(), 7L);
			for (dev.voidmc.hearth.api.HearthWorld.SiteInfo i : sites) {
				if (i.center().distManhattan(settlement.center()) >= 1500 && k-- <= 0) {
					far = i;
					break;
				}
			}
			if (far == null) {
				return Component.translatable("hearth.wild.rumour.none");
			}
			int dx = far.center().getX() - settlement.center().getX();
			int dz = far.center().getZ() - settlement.center().getZ();
			Direction dir = Direction.getApproximateNearest(dx, 0, dz);
			int leagues = Math.max(1, (int) Math.round(Math.hypot(dx, dz) / 1000.0));
			return Component.translatable("hearth.wild.rumour.town", far.name(), Component.translatable("hearth.wild.dir." + dir.getSerializedName()), leagues);
		}
	}
}
