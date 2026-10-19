package dev.voidmc.hearth.gametest.play;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.client.economy.BankScreen;
import dev.voidmc.hearth.client.economy.MarketScreen;
import dev.voidmc.hearth.client.law.ConfrontScreen;
import dev.voidmc.hearth.client.npc.ConversationScreen;
import dev.voidmc.hearth.client.quest.JournalScreen;
import dev.voidmc.hearth.client.quest.NoticeBoardScreen;
import dev.voidmc.hearth.client.quest.QuestClient;
import dev.voidmc.hearth.client.sim.ArrivalBanner;
import dev.voidmc.hearth.economy.bank.DepositStore;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.economy.shop.Shops;
import dev.voidmc.hearth.gametest.Tours;
import dev.voidmc.hearth.law.justice.Confrontations;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.law.own.Ownership;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.quest.block.NoticeBoardBlock;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import dev.voidmc.hearth.social.service.PlayerLife;
import dev.voidmc.hearth.social.service.ServiceSites;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The new player's playthrough: one survival visit to a temperate town on the Expanse Earth world, played through the
 * game's own inputs wherever there is one (right-clicking folk and blocks, conversation options, market, bank, board,
 * confrontation and journal packets as the screens send them). Commands only set the stage (found and grow the town, the
 * hour, goods a traveller would carry). Eight steps, each recorded PASS or FAIL with what happened, where, and a JPEG:
 * arrive, talk, money, a quest, crime, night at the inn, save and reload, leave and come back.
 *
 * <p>{@code -Dhearth.tour=play}; {@code -Dhearth.play.out=DIR} (results.md and JPEGs), {@code -Dhearth.play.days=N} (the
 * town's growth before the visit, default 100), {@code -Dhearth.play.away=N} (days away in step 8, default 12). Lines are
 * printed as {@code [hearth play]}.
 */
public class PlaythroughTour implements FabricClientGameTest {
	private static final Path OUT = Path.of(System.getProperty("hearth.play.out", "build/play"));
	private static final int DAYS = Integer.getInteger("hearth.play.days", 100);
	private static final int AWAY = Integer.getInteger("hearth.play.away", 12);
	private static final String SEED = System.getProperty("hearth.play.seed", "20261003");
	private static final AtomicInteger CRIMES_SEEN = new AtomicInteger();
	private static final AtomicInteger CRIMES_UNSEEN = new AtomicInteger();
	private static final AtomicInteger STANDING_EVENTS = new AtomicInteger();
	private static boolean listening;

	private ClientGameTestContext context;
	private TestSingleplayerContext world;
	private long townId;
	private String townName = "?";
	private final List<Step> steps = new ArrayList<>();
	private @Nullable Step cur;
	private final Set<UUID> used = new HashSet<>();

	// what the steps hand on
	private @Nullable UUID questId;
	private @Nullable BlockPos innBed;

	/** One step of the playthrough: its checks, notes and screenshots. */
	private static final class Step {
		final int n;
		final String title;
		final List<String> lines = new ArrayList<>();
		final List<String> fails = new ArrayList<>();
		final List<String> shots = new ArrayList<>();

		Step(int n, String title) {
			this.n = n;
			this.title = title;
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("play")) {
			return;
		}
		this.context = context;
		try {
			Files.createDirectories(OUT);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		context.getInput().resizeWindow(1280, 720);
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(8);
			mc.options.fov().set(75);
		});
		hud(false);
		this.world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(SEED);
				s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				s.setAllowCommands(true);
			})
			.create();
		TestWorldSave save = this.world.getWorldSave();
		boolean open = true;
		try {
			if (!setup()) {
				write();
				return;
			}
			run(1, "Arrive", this::arrive);
			run(2, "Talk to a resident", this::talk);
			run(3, "Money: sell, buy, haggle, bank", this::money);
			run(4, "Quest: take, do, turn in", this::quest);
			run(5, "Crime: seen and unseen", this::crime);
			run(6, "Night: folk abed, a rented inn bed", this::night);
			Map<String, String> before = snapshot();
			quit();
			open = false;
			this.world = save.open();
			open = true;
			run(7, "Save, quit to title, reload", () -> reload(before));
			run(8, "Leave for a while, come back", this::away);
		} finally {
			if (open) {
				quit();
			}
			write();
			log("done: " + this.steps.stream().map(s -> s.n + " " + (s.fails.isEmpty() ? "PASS" : "FAIL")).toList());
		}
	}

	// ---------------------------------------------------------------- setup

	private boolean setup() {
		for (String c : List.of("gamerule advance_weather false", "gamerule spawn_monsters false", "weather clear", "time set 1000", "gamemode spectator @a",
			"tp @a 30000 300 30000")) {
			cmd(c);
		}
		settle(3_000);
		Long id = srv(PlaythroughTour::foundTown);
		if (id == null) {
			Step s = new Step(0, "Setup");
			s.fails.add("no temperate site for a town on the Earth world near spawn");
			this.steps.add(s);
			return false;
		}
		this.townId = id;
		this.townName = srv(server -> town(server).name());
		srv(server -> {
			forceChunks(server, true);
			return null;
		});
		waitApplier();
		srv(server -> {
			forceChunks(server, false);
			if (!listening) {
				listening = true;
				SettlementEvents.CRIME_COMMITTED.register((w, s, c) -> {
					(c.witnessed() ? CRIMES_SEEN : CRIMES_UNSEEN).incrementAndGet();
					log("crime event: " + s.name() + " " + c.offence().getSerializedName() + " worth " + c.value() + ", witnessed " + c.witnessed() + " at "
						+ c.pos().toShortString());
				});
				SettlementEvents.STANDING_CHANGED.register((w, s, p, from, to) -> {
					STANDING_EVENTS.incrementAndGet();
					log("standing event: " + s.name() + " " + from.getSerializedName() + " -> " + to.getSerializedName());
				});
			}
			// folk near the player think every tick, so a minute of watching shows real behaviour
			NpcConfig c = NpcModule.config();
			NpcModule.setConfig(c.withAi(new NpcConfig.Ai(1, 1, 1, 64, 128, 20, 10, true)));
			return null;
		});
		log("town: " + srv(server -> {
			SimSettlement s = town(server);
			return s.name() + " #" + s.id() + " at " + s.center().toShortString() + ", " + s.population() + " souls, " + s.tier().getSerializedName() + ", plots "
				+ s.plotCounts() + ", treasury " + s.treasury();
		}));
		return true;
	}

	/** A temperate town on gentle dry ground near spawn, grown {@link #DAYS} days with its stores kept up. */
	private static @Nullable Long foundTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		if (w == null) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos best = null;
		int bestRelief = Integer.MAX_VALUE;
		Map<String, Integer> why = new java.util.TreeMap<>();
		for (int r = 128; r <= 4000 && (best == null || bestRelief > 3 || r < 512); r += 64) {
			for (int k = 0; k < 16; k++) {
				double a = k * Math.PI / 8 + r * 0.01;
				BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
				int g = w.terrain().groundY(at.getX(), at.getZ());
				if (g <= w.terrain().seaLevel() + 2) {
					why.merge("water", 1, Integer::sum);
					continue;
				}
				Culture vote = CultureVote.at(at.getX(), at.getZ(), w.probe()::biomeHolder, HearthServices::culture);
				why.merge(vote == null ? "culture none" : "culture " + vote.getSerializedName(), 1, Integer::sum);
				if (vote == Culture.TEMPERATE) {
					String no = w.whyNotFound(at, SimWorld.FOUND_SPACING, true);
					if (no != null) {
						why.merge("temperate refused: " + (no.length() > 40 ? no.substring(0, 40) : no), 1, Integer::sum);
						continue;
					}
				}
				int lo = g;
				int hi = g;
				for (int dx = -40; dx <= 40; dx += 10) {
					for (int dz = -40; dz <= 40; dz += 10) {
						int h = w.terrain().groundY(at.getX() + dx, at.getZ() + dz);
						lo = Math.min(lo, h);
						hi = Math.max(hi, h);
					}
				}
				if (hi - lo >= bestRelief) {
					continue;
				}
				Culture c = CultureVote.at(at.getX(), at.getZ(), w.probe()::biomeHolder, HearthServices::culture);
				if (c != Culture.TEMPERATE || w.whyNotFound(at, SimWorld.FOUND_SPACING, true) != null) {
					continue;
				}
				bestRelief = hi - lo;
				best = at;
			}
		}
		log("site search from spawn " + spawn.toShortString() + ": " + why);
		if (best == null) {
			return null;
		}
		SimSettlement s = w.found(best, Culture.TEMPERATE, null);
		for (int d = 0; d < DAYS; d += 4) {
			// the stores kept up while it grows, then a month on its own so the markets settle
			for (Goods g : d < DAYS - 30 ? Goods.VALUES : new Goods[0]) {
				int want = s.stockpile().capacity(g) * 3 / 4 - s.stockpile().get(g);
				if (want > 0) {
					s.stockpile().add(g, want);
				}
			}
			if (s.treasury() < 3000) {
				s.deposit(3000 - s.treasury());
			}
			w.simulate(s, 4);
		}
		log(String.format(Locale.ROOT, "founded %s at %s (relief %d), grew %d days: %d souls, %s, plots %s", s.name(), s.center().toShortString(), bestRelief,
			DAYS, s.population(), s.tier().getSerializedName(), s.plotCounts()));
		return s.id();
	}

	// ---------------------------------------------------------------- 1. arrive

	private void arrive() {
		BlockPos c = srv(server -> town(server).center());
		BoundingBox b = srv(server -> town(server).bounds());
		cmd("gamemode survival @a");
		BlockPos out = new BlockPos(c.getX(), 0, b.maxZ() + 70);
		stand(out, 180);
		settle(6_000);
		ok(this.context.computeOnClient(mc -> ArrivalBanner.current() == null), "outside the town no settlement is shown (at " + where() + ")");
		// walk in from the south: the edge of the town, then on to the middle
		BlockPos edge = new BlockPos(c.getX(), 0, b.maxZ() - 4);
		stand(edge, 180);
		boolean banner = false;
		for (int i = 0; i < 200 && !banner; i++) {
			this.context.waitTick();
			banner = this.context.computeOnClient(mc -> ArrivalBanner.current() != null && staticField(ArrivalBanner.class, "shown") != null);
		}
		settle(1_200);
		String arrived = this.context.computeOnClient(mc -> ArrivalBanner.current() == null ? "none" : ArrivalBanner.current().name());
		ok(banner, "the arrival banner shows on walking in (at " + where() + "): '" + arrived + "'");
		ok(this.townName.equals(arrived), "the banner names the town (" + this.townName + ")");
		Object standing = this.context.computeOnClient(mc -> callStatic("dev.voidmc.hearth.client.standing.ClientStanding", "shown"));
		Object here = this.context.computeOnClient(mc -> callStatic("dev.voidmc.hearth.client.standing.ClientStanding", "here"));
		ok(standing != null || here != null, "the standing line shows under the banner: shown " + standing + ", here " + here);
		shot("1_arrival", true);
		settle(4_000);

		// folk about the town: who is out, who moves, who is at work
		List<String> folk = new ArrayList<>();
		Map<Integer, Vec3> at0 = srv(server -> {
			Map<Integer, Vec3> m = new HashMap<>();
			for (Folk f : townFolk()) {
				m.put(f.getId(), f.position());
			}
			return m;
		});
		settle(12_000);
		int[] n = srv(server -> {
			SimSettlement s = town(server);
			int moved = 0;
			int working = 0;
			int total = 0;
			for (Folk f : townFolk()) {
				total++;
				Vec3 was = at0.get(f.getId());
				if (was != null && was.distanceTo(f.position()) > 1.5) {
					moved++;
				}
				Resident r = f.brain().resident;
				SimPlot work = r.work == Resident.NO_PLOT ? null : s.plot(r.work);
				if (work != null && work.reach().inflatedBy(3).isInside(f.blockPosition())) {
					working++;
				}
				if (folk.size() < 10) {
					folk.add(f.folkName() + " (" + r.job.getSerializedName() + ") " + f.brain().describe() + " at " + f.blockPosition().toShortString());
				}
			}
			return new int[]{total, moved, working, s.population()};
		});
		note("folk: " + n[0] + " embodied of " + n[3] + " residents, " + n[1] + " moved over 12 s, " + n[2] + " at their work plot; e.g. " + String.join("; ", folk));
		ok(n[0] >= 4, "the town has folk out (" + n[0] + " embodied)");
		ok(n[1] >= 2, "folk walk about (" + n[1] + " moved more than 1.5 blocks in 12 s)");
		ok(n[2] >= 1, "folk are at work (" + n[2] + " inside their work plot)");
		camera(c.getX() - 26, surface(c) + 22, c.getZ() + 26, c.getX(), surface(c), c.getZ());
		shot("1_town_day", false);
		cmd("gamemode survival @a");
	}

	// ---------------------------------------------------------------- 2. talk

	private void talk() {
		UUID who = srv(server -> {
			Folk f = nearestFolk(server, r -> r.job != JobType.GUARD && r.age == AgeStage.ADULT);
			return f == null ? null : f.brain().resident.id;
		});
		if (who == null) {
			ok(false, "nobody awake in the town to talk to");
			return;
		}
		this.used.add(who);
		Integer entity = talkTo(who);
		if (entity == null) {
			return;
		}
		String name = srv(server -> town(server).resident(who).fullName());
		String greeting = this.context.computeOnClient(mc -> speech(mc));
		List<TalkPayloads.Option> options = this.context.computeOnClient(PlaythroughTour::options);
		note(name + " says: \"" + greeting + "\"; options: " + describe(options));
		ok(!greeting.isBlank(), "a greeting");
		ok(options.size() >= 3, "dialogue options (" + options.size() + ")");
		shot("2_talk", false);
		TalkPayloads.Option check = options.stream().filter(o -> !o.locked() && (o.label().getString().contains("%")
			|| o.hint() != null && o.hint().getString().contains("%"))).findFirst().orElse(null);
		ok(check != null, "a speech check with odds among the options");
		if (check != null) {
			int before = standing();
			int chat0 = chatSize();
			choose(entity, check.id());
			settle(2_500);
			String reply = this.context.computeOnClient(mc -> speech(mc));
			// the outcome is written on the standing strip along the bottom of the conversation (and in chat)
			String result = this.context.computeOnClient(mc -> {
				Object talk = callStatic("dev.voidmc.hearth.client.standing.ClientStanding", "talk");
				return talk == null ? "" : ((dev.voidmc.hearth.standing.net.StandingPayloads.Talk) talk).result().getString();
			});
			note("chose \"" + check.label().getString() + (check.hint() == null ? "" : " " + check.hint().getString()) + "\": speech \"" + reply
				+ "\"; strip \"" + result + "\"; chat " + chatSince(chat0) + "; standing " + before + " -> " + standing());
			ok(!result.isBlank(), "the speech check's outcome is shown in the conversation");
			ok(!reply.equals(greeting), "the resident answers in the conversation itself (not only in chat)");
			shot("2_speech_check", false);
		}
		end(entity);
	}

	// ---------------------------------------------------------------- 3. money

	private void money() {
		// what a traveller brings to sell
		srv(server -> {
			ServerPlayer p = player(server);
			for (ItemStack st : List.of(new ItemStack(Items.WHEAT, 48), new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.COBBLESTONE, 64),
				new ItemStack(Items.LEATHER, 12), new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.CARROT, 24))) {
				p.getInventory().add(st);
			}
			return null;
		});
		UUID merchant = srv(server -> {
			SimSettlement s = town(server);
			long day = HearthWorld.of(server.overworld()).day();
			Shops.sync(s, day);
			Resident pick = null;
			for (Resident r : s.residents()) {
				if (r.age == AgeStage.ADULT && (Shops.keptBy(s, r.id, day) != null && !Shops.keptBy(s, r.id, day).fence || r.job == JobType.MERCHANT
					|| r.job == JobType.TRADER) && (pick == null || NpcModule.embodiment().entityOf(r.id) != null)) {
					pick = r;
				}
			}
			if (pick == null) {
				return null;
			}
			log("merchant: " + pick.fullName() + " (" + pick.job.getSerializedName() + "), shop " + (Shops.keptBy(s, pick.id, day) == null ? "none (town counter)"
				: Shops.keptBy(s, pick.id, day).key));
			return pick.id;
		});
		if (merchant == null) {
			ok(false, "nobody in the town keeps a shop or trades");
			return;
		}
		Integer entity = talkTo(merchant);
		if (entity == null) {
			return;
		}
		List<TalkPayloads.Option> opts = this.context.computeOnClient(PlaythroughTour::options);
		ok(opts.stream().anyMatch(o -> o.id().equals("economy:trade")), "the merchant offers to trade: " + describe(opts));
		choose(entity, "economy:trade");
		if (!waitScreen(MarketScreen.class, 100)) {
			ok(false, "the market screen opens");
			return;
		}
		settle(1_200);
		shot("3_market", false);
		MarketView v = marketView();
		note("market of " + v.settlement() + " (till " + v.treasury() + " bits, player " + v.balance() + "): sells " + rows(v.sells()) + "; buys " + rows(v.buys()));
		long payable = v.buys().stream().filter(o -> o.owned() > 0 && o.available() > 0).count();
		long carriedKinds = v.buys().stream().filter(o -> o.owned() > 0).count();
		ok(payable * 2 >= carriedKinds && carriedKinds > 0, "the merchant can pay for the traveller's goods (" + payable + " of " + carriedKinds
			+ " kinds carried can be sold; till " + v.treasury() + " bits)");
		// sell: a row the market buys and we carry
		MarketView.Offer sell = v.buys().stream().filter(o -> o.owned() >= o.stack().getCount() && o.available() > 0 && !o.has(MarketView.REFUSED)
			&& !o.has(MarketView.STOLEN)).findFirst().orElse(null);
		long coins0 = coins();
		if (sell == null) {
			ok(false, "the market buys something the traveller carries (" + rows(v.buys()) + ")");
		} else {
			int stock0 = stock(sell.goods());
			int held0 = held(sell.stack().getItem());
			long till0 = v.treasury();
			action(EconomyActionPayload.TRADE, sell.id(), 0);
			long coins1 = coins();
			MarketView after = marketView();
			int held1 = held(sell.stack().getItem());
			note(String.format(Locale.ROOT, "sold %d %s for %d bits (listed %d): coins %d -> %d, carried %d -> %d, town %s stock %d -> %d, till/treasury %d -> %d; %s",
				sell.stack().getCount(), name(sell.stack()), coins1 - coins0, sell.price(), coins0, coins1, held0, held1, sell.goods(), stock0, stock(sell.goods()), till0,
				after.treasury(), after.status().getString()));
			ok(coins1 - coins0 == sell.price(), "selling pays the listed price (" + sell.price() + ", got " + (coins1 - coins0) + ")");
			ok(held0 - held1 == sell.stack().getCount(), "the goods leave the pack");
			ok(stock(sell.goods()) > stock0 || after.treasury() < till0, "the town's stock or till moves");
			// sell a second lot to have money to spend
			action(EconomyActionPayload.TRADE, sell.id(), 1);
			v = marketView();
		}
		if (coins() < 80) {
			// a traveller's purse, so buying and haggling can be tried at all
			long have = coins();
			give(null, 150);
			note("only " + have + " bits after selling: given 150 to go on");
			action(EconomyActionPayload.HAGGLE_CANCEL, 0, 0);
			v = marketView();
		}
		// buy: something cheap that is in stock
		long coins2 = coins();
		MarketView.Offer buy = v.sells().stream().filter(o -> o.available() > 0 && o.price() <= coins2)
			.min((a, b2) -> Long.compare(a.price(), b2.price())).orElse(null);
		if (buy == null) {
			ok(false, "something in stock the traveller can afford (coins " + coins2 + "; " + rows(v.sells()) + ")");
		} else {
			int held0 = held(buy.stack().getItem());
			int avail0 = buy.available();
			int stock0 = stock(buy.goods());
			action(EconomyActionPayload.TRADE, buy.id(), 0);
			long coins3 = coins();
			MarketView after = marketView();
			MarketView.Offer row = after.sells().stream().filter(o -> o.id() == buy.id()).findFirst().orElse(null);
			note(String.format(Locale.ROOT, "bought %d %s for %d bits (listed %d): coins %d -> %d, carried %d -> %d, row available %d -> %s, %s stock %d -> %d; %s",
				buy.stack().getCount(), name(buy.stack()), coins2 - coins3, buy.price(), coins2, coins3, held0, held(buy.stack().getItem()), avail0,
				row == null ? "gone" : row.available(), buy.goods(), stock0, stock(buy.goods()), after.status().getString()));
			ok(coins2 - coins3 == buy.price(), "buying costs the listed price (" + buy.price() + ", paid " + (coins2 - coins3) + ")");
			ok(held(buy.stack().getItem()) - held0 == buy.stack().getCount(), "the bought goods are in the pack");
			ok(row == null || row.available() < avail0 || stock(buy.goods()) < stock0, "the stock goes down");
			v = after;
		}
		// haggle over the dearest thing we can afford
		long coins4 = coins();
		MarketView.Offer dear = v.sells().stream().filter(o -> o.available() > 0 && o.price() <= coins4 && o.price() >= 6 && !o.has(MarketView.SOUR))
			.max((a, b2) -> Long.compare(a.price(), b2.price())).orElse(null);
		if (dear == null) {
			ok(false, "something worth haggling over (6 bits or more) in reach (coins " + coins4 + ")");
		} else {
			action(EconomyActionPayload.HAGGLE_START, dear.id(), 0);
			MarketView.HaggleView h = marketView().haggle();
			ok(h != null, "haggling opens over " + name(dear.stack()));
			if (h != null) {
				long list = h.list();
				long offer = Math.max(1, Math.round(h.counter() * 0.85));
				note("haggle: list " + list + ", merchant asks " + h.counter() + ", patience " + h.patience() + ": \"" + h.line().getString() + "\"; offering " + offer);
				int held0 = held(dear.stack().getItem());
				action(EconomyActionPayload.HAGGLE_OFFER, dear.id(), offer);
				MarketView mid = marketView();
				shot("3_haggle", false);
				if (mid.haggle() != null) {
					note("merchant: \"" + mid.haggle().line().getString() + "\" (now asks " + mid.haggle().counter() + ", patience " + mid.haggle().patience() + ")");
					action(EconomyActionPayload.HAGGLE_ACCEPT, dear.id(), 0);
				}
				long paid = coins4 - coins();
				note("haggle struck: paid " + paid + " for a list price of " + list + "; " + marketView().status().getString());
				ok(paid > 0 && held(dear.stack().getItem()) > held0, "the haggled deal goes through");
				ok(paid > 0 && paid < list, "haggling gets it under the list price (" + paid + " < " + list + ")");
			}
		}
		closeMenu();
		end(entity);

		// the bank
		UUID banker = srv(server -> {
			SimSettlement s = town(server);
			for (Resident r : s.residents()) {
				String work = ServiceSites.workPath(s, r);
				if (r.age == AgeStage.ADULT && (r.job == JobType.MINTER || work.equals("commerce/bank"))) {
					return r.id;
				}
			}
			return null;
		});
		if (banker == null) {
			ok(false, "the town has a banker or minter (plots " + srv(server -> archetypes(town(server))) + ")");
			banker = srv(server -> {
				Resident r = pick(town(server), res -> res.job != JobType.GUARD && res.job != JobType.MERCHANT);
				if (r != null) {
					r.job = JobType.MINTER;
					log("bank: no banker; " + r.fullName() + " made a minter to try the counter");
				}
				return r == null ? null : r.id;
			});
			if (banker == null) {
				return;
			}
		}
		Integer bentity = talkTo(banker);
		if (bentity == null) {
			return;
		}
		List<TalkPayloads.Option> bopts = this.context.computeOnClient(PlaythroughTour::options);
		ok(bopts.stream().anyMatch(o -> o.id().equals("economy:bank")), "the banker offers the bank: " + describe(bopts));
		choose(bentity, "economy:bank");
		if (!waitScreen(BankScreen.class, 100)) {
			ok(false, "the bank screen opens");
			end(bentity);
			return;
		}
		settle(1_000);
		long purse0 = coins();
		long dep0 = deposit();
		long amount = Math.max(1, purse0 / 2);
		action(EconomyActionPayload.DEPOSIT, 0, amount);
		long purse1 = coins();
		long dep1 = deposit();
		shot("3_bank", false);
		note("deposit " + amount + ": purse " + purse0 + " -> " + purse1 + ", on deposit " + dep0 + " -> " + dep1);
		ok(purse0 - purse1 == amount && dep1 - dep0 == amount, "depositing moves the coin to the bank");
		action(EconomyActionPayload.WITHDRAW, 0, amount);
		long purse2 = coins();
		long dep2 = deposit();
		note("withdraw " + amount + ": purse " + purse1 + " -> " + purse2 + ", on deposit " + dep1 + " -> " + dep2);
		ok(purse2 - purse1 == amount && dep1 - dep2 == amount, "withdrawing gives it back");
		closeMenu();
		end(bentity);
	}

	// ---------------------------------------------------------------- 4. quest

	private void quest() {
		BlockPos board = srv(server -> {
			ServerLevel level = server.overworld();
			// the point of interest is the spot in front of the board, facing it
			for (Poi poi : town(server).pois(PoiType.NOTICE_BOARD)) {
				for (BlockPos q : List.of(poi.pos().relative(poi.facing()), poi.pos(), poi.pos().relative(poi.facing()).above(), poi.pos().relative(poi.facing()).below())) {
					if (level.isLoaded(q) && level.getBlockState(q).getBlock() instanceof NoticeBoardBlock) {
						return q;
					}
				}
			}
			log("quest: " + town(server).pois(PoiType.NOTICE_BOARD).size() + " board points, none with a board block beside it");
			return null;
		});
		ok(board != null, "the town has a notice board" + (board == null ? "" : " at " + board.toShortString()));
		QuestPayloads.Board.Entry pickEntry = null;
		if (board != null) {
			standAt(board);
			settle(1_500);
			click(board);
			if (waitScreen(NoticeBoardScreen.class, 100)) {
				settle(1_200);
				shot("4_board", false);
				QuestPayloads.Board b = this.context.computeOnClient(mc -> (QuestPayloads.Board) field(mc.gui.screen(), "board"));
				List<String> list = new ArrayList<>();
				for (QuestPayloads.Board.Entry e : b.entries()) {
					list.add(e.quest().type.getSerializedName() + " '" + e.quest().text.title() + "' [" + kinds(e.quest()) + "]" + (e.lock().isEmpty() ? "" : " locked: "
						+ e.lock()));
					if (pickEntry == null && e.lock().isEmpty() && doable(e.quest())) {
						pickEntry = e;
					}
				}
				note("board: " + list);
				if (pickEntry != null) {
					UUID id = pickEntry.quest().id;
					this.context.runOnClient(mc -> ClientPlayNetworking.send(new QuestPayloads.BoardAction(b.settlementId(), b.boardPos(),
						QuestPayloads.BoardAction.Action.ACCEPT, Optional.of(id))));
					settle(1_500);
				}
				this.context.setScreen(() -> null);
			} else {
				ok(false, "right-clicking the board opens it");
			}
		}
		QuestInstance q = pickEntry == null ? null : activeQuest(pickEntry.quest().id);
		if (q == null) {
			// ask around: a resident with a quest of their own
			q = askAround();
		}
		if (q == null) {
			ok(false, "a quest a new player can do (deliver, fetch, courier or talk) is on offer");
			return;
		}
		UUID id = q.id;
		this.questId = id;
		note("took '" + q.text.title() + "' (" + q.type.getSerializedName() + ", " + kinds(q) + ") from " + q.giver.name() + ", reward " + q.reward.coin() + " bits + "
			+ q.reward.reputation() + " standing; turn in " + q.rules.turnIn().getSerializedName() + " at " + q.turnInPos.toShortString());
		ok(true, "a quest is accepted");
		// the HUD tracks it
		settle(1_500);
		QuestClient.Tracked t = this.context.computeOnClient(mc -> QuestClient.tracked(mc.player));
		if (t == null || !t.id().equals(id)) {
			this.context.runOnClient(mc -> ClientPlayNetworking.send(new QuestPayloads.JournalAction(id, QuestPayloads.JournalAction.Action.TRACK)));
			settle(1_500);
			t = this.context.computeOnClient(mc -> QuestClient.tracked(mc.player));
		}
		ok(t != null && t.id().equals(id), "the quest HUD tracks it: " + (t == null ? "nothing" : t.title() + " " + t.objectives()));
		String hudBefore = hudLines();
		long coins0 = coins();
		int stand0 = standing();
		// do it
		for (int round = 0; round < 4; round++) {
			QuestInstance live = activeQuest(id);
			if (live == null || live.allDone()) {
				break;
			}
			for (Objective o : live.currentObjectives()) {
				if (o.done()) {
					continue;
				}
				switch (o.kind) {
					case DELIVER -> {
						Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(o.target));
						int need = o.count - o.progress - held(item);
						if (need > 0) {
							// fetched: gathered on the road (given here), then carried to the hand-in like a player would
							give(item, need);
							note("fetched " + need + " " + o.target + " (given: the gathering is not the quest's step)");
						}
					}
					case TALK, COURIER -> {
						if (o.entity == null) {
							continue;
						}
						Integer e = talkTo(o.entity);
						if (e == null) {
							continue;
						}
						List<TalkPayloads.Option> opts = this.context.computeOnClient(PlaythroughTour::options);
						String opt = opts.stream().map(TalkPayloads.Option::id).filter(x -> x.equals("quest:talk/" + id) || x.equals("quest:handin")).findFirst()
							.orElse(null);
						ok(opt != null, "the " + o.kind.getSerializedName() + " target offers the quest's line: " + describe(opts));
						if (opt != null) {
							choose(e, opt);
							settle(2_000);
						}
						end(e);
					}
					default -> note("objective " + o.kind.getSerializedName() + " not scripted");
				}
			}
			settle(1_000);
		}
		QuestClient.Tracked t2 = this.context.computeOnClient(mc -> QuestClient.tracked(mc.player));
		String hudAfter = hudLines();
		note("HUD before: " + hudBefore + "; after: " + hudAfter + (t2 != null && t2.handIn() ? " (hand in)" : ""));
		ok(t2 != null && (!hudAfter.equals(hudBefore) || t2.handIn()), "the HUD shows the progress");
		shot("4_quest_hud", true);
		this.context.setScreen(JournalScreen::new);
		settle(1_500);
		shot("4_journal", false);
		this.context.setScreen(() -> null);
		// turn it in: to the giver, else at the board
		QuestInstance live = activeQuest(id);
		boolean handed = false;
		if (live != null && live.giver.resident().isPresent()) {
			Integer e = talkTo(live.giver.resident().get());
			if (e != null) {
				List<TalkPayloads.Option> opts = this.context.computeOnClient(PlaythroughTour::options);
				if (opts.stream().anyMatch(o -> o.id().equals("quest:handin"))) {
					choose(e, "quest:handin");
					settle(2_500);
					shot("4_turned_in", false);
					handed = true;
				} else {
					note("the giver offers no hand-in: " + describe(opts));
				}
				end(e);
			}
		}
		if (!handed && activeQuest(id) != null && board != null) {
			standAt(board);
			settle(1_000);
			click(board);
			if (waitScreen(NoticeBoardScreen.class, 100)) {
				QuestPayloads.Board b = this.context.computeOnClient(mc -> (QuestPayloads.Board) field(mc.gui.screen(), "board"));
				note("board hand-ins: " + b.handIns().size());
				this.context.runOnClient(mc -> ClientPlayNetworking.send(new QuestPayloads.BoardAction(b.settlementId(), b.boardPos(),
					QuestPayloads.BoardAction.Action.HAND_IN, Optional.empty())));
				settle(2_000);
				shot("4_turned_in", false);
				this.context.setScreen(() -> null);
			}
		}
		boolean done = activeQuest(id) == null;
		PlayerQuests.QuestRecord rec = srv(server -> {
			PlayerQuests pq = QuestStore.get(player(server));
			return pq.history.isEmpty() ? null : pq.history.getLast();
		});
		long coins1 = coins();
		int stand1 = standing();
		note("after: coins " + coins0 + " -> " + coins1 + ", standing " + stand0 + " -> " + stand1 + ", history " + (rec == null ? "empty" : rec.title() + " "
			+ rec.outcome()));
		ok(done && rec != null && rec.title().equals(q.text.title()), "the quest is turned in and in the journal's history");
		ok(coins1 - coins0 >= q.reward.coin() && (q.reward.coin() == 0 || coins1 > coins0), "the coin reward is paid (" + q.reward.coin() + ")");
		ok(stand1 > stand0, "standing rises");
	}

	/** A resident's own quest, asked for in conversation. */
	private @Nullable QuestInstance askAround() {
		List<UUID> givers = srv(server -> {
			SimSettlement s = town(server);
			List<UUID> out = new ArrayList<>();
			for (QuestInstance offer : QuestStore.board(s).offers) {
				if (offer.giver.resident().isPresent() && doable(offer)) {
					out.add(offer.giver.resident().get());
				}
			}
			return out;
		});
		for (UUID g : givers) {
			Integer e = talkTo(g);
			if (e == null) {
				continue;
			}
			List<TalkPayloads.Option> opts = this.context.computeOnClient(PlaythroughTour::options);
			TalkPayloads.Option offer = opts.stream().filter(o -> o.id().startsWith("quest:offer/")).findFirst().orElse(null);
			if (offer == null) {
				end(e);
				continue;
			}
			choose(e, offer.id());
			settle(1_500);
			UUID id = UUID.fromString(offer.id().substring("quest:offer/".length()));
			opts = this.context.computeOnClient(PlaythroughTour::options);
			if (opts.stream().anyMatch(o -> o.id().equals("quest:accept/" + id))) {
				choose(e, "quest:accept/" + id);
				settle(1_500);
			}
			end(e);
			QuestInstance q = activeQuest(id);
			if (q != null) {
				note("asked " + q.giver.name() + " for work in conversation");
				return q;
			}
		}
		return null;
	}

	/** The tracked quest's lines as the quest HUD draws them (with what the player carries toward a delivery). */
	private String hudLines() {
		return this.context.computeOnClient(mc -> {
			var pq = QuestStore.get(mc.player);
			QuestInstance q = pq.tracked == null ? null : pq.get(pq.tracked);
			if (q == null) {
				return "(nothing tracked)";
			}
			List<String> out = new ArrayList<>();
			try {
				Method m = Class.forName("dev.voidmc.hearth.client.quest.QuestUi").getDeclaredMethod("objectiveLine", Objective.class,
					net.minecraft.client.player.LocalPlayer.class);
				m.setAccessible(true);
				for (Objective o : q.currentObjectives()) {
					out.add((String) m.invoke(null, o, mc.player));
				}
			} catch (ReflectiveOperationException e) {
				out.add("? " + e);
			}
			return q.status + " " + out;
		});
	}

	private static boolean doable(QuestInstance q) {
		if (q.objectives.isEmpty()) {
			return false;
		}
		for (Objective o : q.objectives) {
			if (o.kind != ObjectiveKind.DELIVER && o.kind != ObjectiveKind.TALK && o.kind != ObjectiveKind.COURIER) {
				return false;
			}
			if (o.kind != ObjectiveKind.DELIVER && (o.entity == null || o.settlement != q.settlementId)) {
				return false;
			}
		}
		return true;
	}

	private static String kinds(QuestInstance q) {
		StringBuilder b = new StringBuilder();
		for (Objective o : q.objectives) {
			b.append(b.isEmpty() ? "" : ", ").append(o.kind.getSerializedName()).append(' ').append(o.count).append(' ').append(o.target);
		}
		return b.toString();
	}

	private @Nullable QuestInstance activeQuest(UUID id) {
		return srv(server -> {
			for (QuestInstance q : QuestStore.get(player(server)).active) {
				if (q.id.equals(id)) {
					return q;
				}
			}
			return null;
		});
	}

	// ---------------------------------------------------------------- 5. crime

	private void crime() {
		timeTo(4_000);
		settle(4_000);
		cmd("gamemode survival @a");
		int stand0 = standing();
		long coins0 = coins();
		BlockPos chest = srv(server -> ownedContainer(server, true, null, false));
		if (chest == null) {
			ok(false, "an unlocked owned container (stall, shop or house) in the loaded town to steal from");
			return;
		}
		note("the mark: " + srv(server -> describeContainer(server, chest)));
		standAt(chest);
		BlockPos here = srv(server -> player(server).blockPosition());
		// a witness: the nearest folk out and about, a few steps away, looking our way
		UUID witness = srv(server -> {
			Folk f = nearestFolk(server, r -> r.job != JobType.GUARD && r.age == AgeStage.ADULT);
			return f == null ? null : f.brain().resident.id;
		});
		if (witness != null) {
			BlockPos spot = srv(server -> freeBeside(server.overworld(), here, 3));
			srv(server -> {
				Folk w = NpcModule.embodiment().entityOf(witness);
				if (w != null) {
					w.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				}
				return null;
			});
			settle(1_500);
			srv(server -> {
				Folk w = NpcModule.embodiment().entityOf(witness);
				if (w != null && w.brain() != null) {
					w.brain().handle().stop();
					w.brain().handle().lookAt(player(server));
				}
				return null;
			});
		}
		note("witness " + doing(witness));
		int seen0 = CRIMES_SEEN.get();
		boolean opened = openContainer(chest);
		ok(opened, "right-clicking the container opens it (" + chest.toShortString() + ")");
		takeFirst();
		settle(800);
		this.context.runOnClient(mc -> mc.player.closeContainer());
		settle(1_500);
		shot("5_theft_seen", false);
		String pending = pendingReports();
		note("just after the theft at " + where() + ": " + lawLine() + "; pending reports " + pending);
		ok(!pending.isEmpty() || srv(server -> HearthServices.law().wanted(player(server).getUUID(), this.townId)), "the witness saw the theft (a report is on its way)");
		boolean wanted = false;
		int waited = 0;
		for (; waited < 110 && !wanted; waited++) {
			settle(1_000);
			wanted = srv(server -> HearthServices.law().wanted(player(server).getUUID(), this.townId));
		}
		note("reported after " + waited + " s: " + lawLine() + "; witness " + doing(witness));
		ok(CRIMES_SEEN.get() > seen0 && wanted, "the player is reported (wanted)");
		// a guard comes
		note("the watch: " + srv(server -> {
			int all = 0;
			int out = 0;
			int up = 0;
			for (Resident r : town(server).residents()) {
				if (r.job == JobType.GUARD) {
					all++;
					Folk f = NpcModule.embodiment().entityOf(r.id);
					if (f != null && !f.isFolded()) {
						out++;
						up += f.isSleeping() ? 0 : 1;
					}
				}
			}
			return all + " guards, " + out + " embodied, " + up + " awake at " + server.overworld().getOverworldClockTime() % 24_000L;
		}));
		UUID guard = srv(server -> {
			Folk f = nearestFolk(server, r -> r.job == JobType.GUARD);
			if (f != null) {
				return f.brain().resident.id;
			}
			for (Resident r : town(server).residents()) {
				if (r.job == JobType.GUARD && r.age == AgeStage.ADULT) {
					return r.id;
				}
			}
			return null;
		});
		if (guard == null) {
			ok(false, "the town has a guard");
			return;
		}
		UUID g = guard;
		if (srv(server -> NpcModule.embodiment().entityOf(g) == null)) {
			spawnNear(g, srv(server -> town(server).center()));
			note("the guard was not about; called over to the town centre");
		}
		boolean confronted = false;
		for (int i = 0; i < 75 && !confronted && wanted; i++) {
			settle(1_000);
			confronted = this.context.computeOnClient(mc -> mc.gui.screen() instanceof ConfrontScreen);
			if (i % 15 == 14) {
				note("waiting for the guard (" + (i + 1) + " s): " + doing(g) + ", " + srv(server -> {
					Folk f = NpcModule.embodiment().entityOf(g);
					return f == null ? "?" : String.format(Locale.ROOT, "%.0f blocks away", f.distanceTo(player(server)));
				}));
			}
		}
		ok(confronted, "a guard comes and confronts the player");
		if (!confronted && wanted) {
			srv(server -> {
				Folk gf = NpcModule.embodiment().entityOf(g);
				if (gf != null && gf.brain() != null) {
					Confrontations.open(player(server), gf.brain().handle(), Confrontations.Kind.ARREST);
				}
				return null;
			});
			confronted = waitScreen(ConfrontScreen.class, 60);
			note("confrontation opened by the tour to go on: " + confronted);
		}
		if (confronted) {
			settle(1_500);
			confronted = this.context.computeOnClient(mc -> mc.gui.screen() instanceof ConfrontScreen);
			ok(confronted, "the confrontation stays open to answer");
		}
		if (confronted) {
			shot("5_guard", false);
			List<LawPayloads.Choice> choices = this.context.computeOnClient(mc -> {
				@SuppressWarnings("unchecked")
				List<LawPayloads.Choice> c = (List<LawPayloads.Choice>) field(mc.gui.screen(), "choices");
				return List.copyOf(c);
			});
			int confrontEntity = this.context.computeOnClient(mc -> ((ConfrontScreen) mc.gui.screen()).entity());
			StringBuilder cs = new StringBuilder();
			for (LawPayloads.Choice c : choices) {
				cs.append(c.id()).append(" \"").append(c.label().getString()).append("\"").append(c.enabled() ? "" : " (off)").append("; ");
			}
			long bounty = srv(server -> HearthServices.law().bounty(player(server).getUUID(), this.townId));
			note("guard says: \"" + this.context.computeOnClient(mc -> ((Component) field(mc.gui.screen(), "speech")).getString()) + "\"; bounty " + bounty
				+ "; choices " + cs);
			long purse = coins();
			if (purse < bounty) {
				give(null, bounty - purse);
				note("short of the fine by " + (bounty - purse) + " bits: given");
			}
			long before = coins();
			boolean canPay = choices.stream().anyMatch(c -> c.id().equals("pay") && c.enabled());
			ok(canPay, "paying the fine is a choice");
			this.context.runOnClient(mc -> ClientPlayNetworking.send(new LawPayloads.ConfrontChoice(confrontEntity, canPay ? "pay" : "submit")));
			settle(3_000);
			boolean still = srv(server -> HearthServices.law().wanted(player(server).getUUID(), this.townId));
			note("after " + (canPay ? "paying" : "submitting") + ": coins " + before + " -> " + coins() + ", " + lawLine() + ", jailed "
				+ srv(server -> dev.voidmc.hearth.law.justice.Jail.serving(player(server))));
			ok(!still, "the fine clears the charge");
			ok(!canPay || before - coins() >= bounty, "the fine is taken (" + bounty + ")");
			shot("5_after_fine", false);
			this.context.setScreen(() -> null);
		}
		int stand1 = standing();
		note("standing " + stand0 + " -> " + stand1 + "; coins " + coins0 + " -> " + coins());
		ok(stand1 < stand0, "standing drops after the theft");

		// unseen: deep in the night, the household abed, nobody about; a locked one is picked
		timeTo(18_500);
		settle(12_000);
		BlockPos dark = srv(server -> ownedContainer(server, false, chest, true));
		if (dark == null) {
			ok(false, "a second owned container for the unseen theft");
			return;
		}
		standAt(dark);
		String awake = srv(server -> {
			int n = 0;
			for (Folk f : townFolk()) {
				if (!f.isSleeping() && f.distanceTo(player(server)) < 20) {
					n++;
				}
			}
			return n + " folk awake within 20 blocks";
		});
		boolean locked = srv(server -> {
			var l = dev.voidmc.hearth.law.lock.Locks.at(server.overworld(), dark, server.overworld().getBlockState(dark));
			return l != null && l.locked();
		});
		note("unseen theft at " + dark.toShortString() + " (" + srv(server -> describeContainer(server, dark)) + ", " + (locked ? "locked" : "unlocked") + "), "
			+ awake);
		srv(server -> {
			player(server).setShiftKeyDown(true);
			return null;
		});
		long bounty0 = srv(server -> HearthServices.law().bounty(player(server).getUUID(), this.townId));
		int seen1 = CRIMES_SEEN.get();
		boolean opened2;
		if (locked) {
			opened2 = pickLock(dark);
		} else {
			opened2 = openContainer(dark);
		}
		takeFirst();
		settle(800);
		this.context.runOnClient(mc -> mc.player.closeContainer());
		srv(server -> {
			player(server).setShiftKeyDown(false);
			return null;
		});
		String pending2 = pendingReports();
		boolean confronted2 = false;
		for (int i = 0; i < 30; i++) {
			settle(1_000);
			confronted2 |= this.context.computeOnClient(mc -> mc.gui.screen() instanceof ConfrontScreen);
		}
		boolean wanted2 = srv(server -> HearthServices.law().wanted(player(server).getUUID(), this.townId));
		long bounty1 = srv(server -> HearthServices.law().bounty(player(server).getUUID(), this.townId));
		note("unseen: opened " + opened2 + "; " + lawLine() + "; pending reports " + (pending2.isEmpty() ? "none" : pending2) + "; unseen crimes logged "
			+ CRIMES_UNSEEN.get());
		ok(opened2, "the second container opens" + (locked ? " (lock picked)" : ""));
		ok(opened2 && pending2.isEmpty() && CRIMES_SEEN.get() == seen1 && !wanted2 && bounty1 == bounty0 && !confronted2, "an unseen theft goes unreported");
		shot("5_theft_unseen", false);
	}

	/** Picks a lock as the lockpicking screen does: a pick in hand, a right-click, the pins set, the client's word sent. */
	private boolean pickLock(BlockPos pos) {
		srv(server -> {
			player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dev.voidmc.hearth.law.item.LawItems.LOCKPICK, 5));
			return null;
		});
		settle(500);
		click(pos);
		boolean shown = waitScreen(dev.voidmc.hearth.client.law.LockpickScreen.class, 60);
		ok(shown, "right-clicking the locked container with a pick opens the lockpicking screen");
		if (!shown) {
			return false;
		}
		settle(1_500);
		shot("5_lockpick", false);
		int session = this.context.computeOnClient(mc -> (Integer) field(mc.gui.screen(), "session"));
		settle(5_000);
		this.context.runOnClient(mc -> ClientPlayNetworking.send(new LawPayloads.LockpickEvent(session, LawPayloads.LockpickEvent.OPENED, 130)));
		for (int i = 0; i < 60; i++) {
			this.context.waitTick();
			if (this.context.computeOnClient(mc -> mc.player.containerMenu != mc.player.inventoryMenu)) {
				settle(600);
				return true;
			}
		}
		return false;
	}

	/** The reports witnesses are carrying against the player in this town. */
	private String pendingReports() {
		return srv(server -> {
			UUID me = player(server).getUUID();
			StringBuilder b = new StringBuilder();
			for (var p : dev.voidmc.hearth.law.Law.state(town(server)).pending()) {
				if (p.crime().offender().equals(me)) {
					Resident w = town(server).resident(p.witness());
					b.append(p.crime().offence().getSerializedName()).append(" seen by ").append(w == null ? "?" : w.fullName()).append("; ");
				}
			}
			return b.toString();
		});
	}

	/**
	 * An owned container in a built plot, preferring a stall or shop ({@code preferTrade}) and filled if empty; not {@code
	 * not}; locked ones only if {@code lockedOk}.
	 */
	private @Nullable BlockPos ownedContainer(MinecraftServer server, boolean preferTrade, @Nullable BlockPos not, boolean lockedOk) {
		ServerLevel level = server.overworld();
		SimSettlement s = town(server);
		BlockPos best = null;
		int bestScore = Integer.MIN_VALUE;
		int owned = 0;
		int lockedN = 0;
		List<Folk> awake = townFolk().stream().filter(f -> !f.isSleeping()).toList();
		for (SimPlot p : s.plots()) {
			if (p.state() != PlotState.BUILT) {
				continue;
			}
			String path = p.spec().archetype().getPath();
			int score = path.startsWith("commerce/market") ? (preferTrade ? 4 : 1) : path.startsWith("commerce/") || path.equals("house/shophouse")
				? (preferTrade ? 3 : 1) : path.startsWith("house/") ? 2 : 0;
			var b = p.reach();
			for (BlockPos q : BlockPos.betweenClosed(b.minX(), p.spec().floorY() - 1, b.minZ(), b.maxX(), p.spec().floorY() + 8, b.maxZ())) {
				if (!level.isLoaded(q) || q.equals(not)) {
					continue;
				}
				BlockEntity be = level.getBlockEntity(q);
				// one with a screen to open (not a decorated pot, which takes what is in your hand)
				if (!(be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity) || Ownership.owner(level, q) == null) {
					continue;
				}
				owned++;
				BlockState st = level.getBlockState(q);
				var lock = dev.voidmc.hearth.law.lock.Locks.at(level, q, st);
				boolean isLocked = lock != null && lock.locked();
				if (isLocked) {
					lockedN++;
					if (!lockedOk) {
						continue;
					}
				}
				int d = not == null ? 0 : -(int) Math.sqrt(q.distSqr(not)) / 64;
				int sc = score * 10 + d + (isLocked ? 0 : 1);
				if (lockedOk) {
					// unseen: out of doors (no trespass) and as far from anyone awake as can be
					double near = 60;
					for (Folk f : awake) {
						near = Math.min(near, Math.sqrt(f.blockPosition().distSqr(q)));
					}
					sc = (level.canSeeSky(q.above()) ? 1000 : 0) + (int) near * 10 + (isLocked ? 0 : 1);
				}
				if (sc > bestScore || sc == bestScore && best != null && q.distSqr(s.center()) < best.distSqr(s.center())) {
					bestScore = sc;
					best = q.immutable();
				}
			}
		}
		log("crime: " + owned + " owned containers in the loaded town, " + lockedN + " locked now");
		if (best != null && level.getBlockEntity(best) instanceof Container c && c.isEmpty()) {
			c.setItem(0, new ItemStack(Items.BREAD, 6));
			c.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
			c.setChanged();
		}
		return best;
	}

	private String describeContainer(MinecraftServer server, BlockPos pos) {
		ServerLevel level = server.overworld();
		LawService.Owner o = Ownership.owner(level, pos);
		SimSettlement s = town(server);
		SimPlot plot = null;
		for (SimPlot p : s.plots()) {
			if (p.reach().isInside(pos)) {
				plot = p;
			}
		}
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath() + " in " + (plot == null ? "no plot" : "#" + plot.id() + " "
			+ plot.spec().archetype().getPath()) + ", owner " + (o == null ? "none" : o.kind() + (o.resident() == null || s.resident(o.resident()) == null ? ""
				: " " + s.resident(o.resident()).fullName()));
	}

	private boolean openContainer(BlockPos pos) {
		emptyHand();
		click(pos);
		for (int i = 0; i < 40; i++) {
			this.context.waitTick();
			if (this.context.computeOnClient(mc -> mc.player.containerMenu != mc.player.inventoryMenu)) {
				settle(600);
				return true;
			}
		}
		return false;
	}

	/** Takes up an empty hotbar slot, as a player does before opening something (a held item can be used on it instead). */
	private void emptyHand() {
		// the last hotbar slot cleared into the pack if it must be
		srv(server -> {
			var inv = player(server).getInventory();
			if (!inv.getItem(8).isEmpty()) {
				for (int i = 9; i < 36; i++) {
					if (inv.getItem(i).isEmpty()) {
						inv.setItem(i, inv.getItem(8));
						inv.setItem(8, ItemStack.EMPTY);
						break;
					}
				}
			}
			return null;
		});
		settle(300);
		this.context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(8));
		settle(300);
	}

	/** Shift-clicks the first filled slot of the open container into the pack, as the client does. */
	private void takeFirst() {
		this.context.runOnClient(mc -> {
			var menu = mc.player.containerMenu;
			if (menu == mc.player.inventoryMenu) {
				return;
			}
			int size = menu.slots.size() - 36;
			for (int i = 0; i < size; i++) {
				if (!menu.slots.get(i).getItem().isEmpty()) {
					mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
					return;
				}
			}
		});
	}

	private String lawLine() {
		return srv(server -> {
			ServerPlayer p = player(server);
			LawService law = HearthServices.law();
			int stolen = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (law.isStolen(p.getInventory().getItem(i))) {
					stolen += p.getInventory().getItem(i).getCount();
				}
			}
			return "wanted " + law.wanted(p.getUUID(), this.townId) + ", bounty " + law.bounty(p.getUUID(), this.townId) + ", stolen items carried " + stolen;
		});
	}

	// ---------------------------------------------------------------- 6. night

	private void night() {
		if (srv(server -> HearthServices.law().wanted(player(server).getUUID(), this.townId))) {
			srv(server -> {
				HearthServices.law().pardon(player(server).getUUID(), this.townId, "playthrough");
				return null;
			});
			note("still wanted from step 5: pardoned by the tour so the night can be tried");
		}
		timeTo(11_200);
		settle(8_000);
		cmd("gamemode survival @a");
		List<UUID> keepers = srv(server -> {
			SimSettlement s = town(server);
			List<UUID> out = new ArrayList<>();
			for (Resident r : s.residents()) {
				if (r.age == AgeStage.ADULT && ServiceSites.innkeeper(s, r) && out.size() < 4) {
					out.add(r.id);
				}
			}
			return out;
		});
		ok(!keepers.isEmpty(), "the town has an innkeeper (plots " + srv(server -> archetypes(town(server))) + ")");
		for (UUID keeper : keepers) {
			if (this.innBed != null) {
				break;
			}
			String inn = srv(server -> innLine(server, keeper));
			note("inn: " + inn);
			if (inn.contains(": 0 bed points")) {
				// a tavern: ale and meals, no lodging (and since this run's fix it no longer offers a bed)
				continue;
			}
			Integer e = talkTo(keeper);
			if (e == null) {
				continue;
			}
			List<TalkPayloads.Option> opts = this.context.computeOnClient(PlaythroughTour::options);
			boolean offers = opts.stream().anyMatch(o -> o.id().equals("social:bed"));
			ok(offers, "the innkeeper offers a bed: " + describe(opts));
			long c0 = coins();
			if (offers) {
				int chat0 = chatSize();
				choose(e, "social:bed");
				settle(2_000);
				String said = this.context.computeOnClient(mc -> speech(mc)) + "\" / chat " + chatSince(chat0) + " \"";
				this.innBed = srv(server -> PlayerLife.of(player(server)).bed().map(PlayerLife.Bed::pos).orElse(null));
				note("asked for a bed: \"" + said + "\" bed " + (this.innBed == null ? "none" : this.innBed.toShortString()) + ", coins " + c0 + " -> " + coins());
				if (this.innBed != null) {
					shot("6_innkeeper", false);
				}
			}
			end(e);
		}
		ok(this.innBed != null, "a bed is rented and paid for");
		// the town goes to bed
		timeTo(18_000);
		BlockPos c = srv(server -> town(server).center());
		stand(c, 0);
		settle(30_000);
		int[] n = srv(server -> {
			SimSettlement s = town(server);
			int homed = 0;
			int inBed = 0;
			int asleep = 0;
			int out = 0;
			for (Folk f : townFolk()) {
				Resident r = f.brain().resident;
				if (r.home == Resident.NO_PLOT || s.plot(r.home) == null) {
					continue;
				}
				homed++;
				if (f.isSleeping()) {
					asleep++;
					BlockPos bed = f.getSleepingPos().orElse(null);
					if (bed != null && s.plot(r.home).reach().inflatedBy(1).isInside(bed)) {
						inBed++;
					}
				} else if (!s.plot(r.home).reach().inflatedBy(1).isInside(f.blockPosition())) {
					out++;
				}
			}
			return new int[]{homed, asleep, inBed, out};
		});
		note("midnight: " + n[0] + " embodied folk with homes, " + n[1] + " asleep, " + n[2] + " in a bed of their own home, " + n[3] + " still out");
		ok(n[0] > 0 && n[2] * 2 >= n[0], "folk go home to bed (" + n[2] + "/" + n[0] + " in their own beds)");
		double[] cam = srv(server -> {
			ServerLevel level = server.overworld();
			for (Folk f : townFolk()) {
				if (!f.isSleeping()) {
					continue;
				}
				BlockPos bed = f.getSleepingPos().orElse(f.blockPosition());
				for (int r = 2; r <= 3; r++) {
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockPos q = bed.relative(d, r);
						if (level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
							&& !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty() && !level.canSeeSky(q)) {
							return new double[]{q.getX() + 0.5, q.getY(), q.getZ() + 0.5, f.getX(), f.getY() + 0.3, f.getZ()};
						}
					}
				}
			}
			return null;
		});
		if (cam != null) {
			camera(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5]);
			shot("6_folk_abed", false);
			cmd("gamemode survival @a");
		}
		// to our own bed
		if (this.innBed == null) {
			ok(false, "an inn bed to sleep in");
			return;
		}
		BlockPos bed = this.innBed;
		standAt(bed);
		settle(1_500);
		long day0 = srv(server -> server.overworld().getOverworldClockTime() / 24_000L);
		click(bed);
		boolean sleeping = false;
		for (int i = 0; i < 60 && !sleeping; i++) {
			this.context.waitTick();
			sleeping = this.context.computeOnClient(mc -> mc.player.isSleeping());
		}
		ok(sleeping, "the player lies down in the rented bed (" + bed.toShortString() + ", " + srv(server -> server.overworld().getBlockState(bed).getBlock()
			instanceof BedBlock ? "a bed" : "not a bed!") + ")");
		if (sleeping) {
			settle(1_500);
			shot("6_inn_bed", false);
		}
		boolean morning = false;
		for (int i = 0; i < 40 && sleeping && !morning; i++) {
			settle(1_000);
			morning = srv(server -> server.overworld().getOverworldClockTime() / 24_000L > day0 && server.overworld().getOverworldClockTime() % 24_000L < 2_000);
		}
		long t = srv(server -> server.overworld().getOverworldClockTime() % 24_000L);
		note("woke at " + t + " on day " + srv(server -> server.overworld().getOverworldClockTime() / 24_000L) + " (slept from day " + day0 + ")");
		ok(morning, "the night passes in the inn bed");
	}

	// ---------------------------------------------------------------- 7. save and reload

	private Map<String, String> snapshot() {
		Map<String, String> m = new HashMap<>();
		m.put("coins", Long.toString(coins()));
		m.put("deposit", Long.toString(deposit()));
		m.put("standing", Integer.toString(standing()));
		srv(server -> {
			ServerPlayer p = player(server);
			PlayerQuests pq = QuestStore.get(p);
			List<String> active = new ArrayList<>();
			for (QuestInstance q : pq.active) {
				active.add(q.id + " " + q.status);
			}
			m.put("quests", active + " history " + pq.history.size());
			SimSettlement s = town(server);
			List<String> people = new ArrayList<>();
			for (Resident r : s.residents()) {
				people.add(r.id.toString().substring(0, 8));
			}
			java.util.Collections.sort(people);
			m.put("town", s.name() + " " + s.tier().getSerializedName() + " " + s.population() + " souls, " + s.plots().size() + " plots " + s.plotCounts());
			m.put("folk", Integer.toHexString(people.hashCode()) + " (" + people.size() + ")");
			m.put("bed", PlayerLife.of(p).bed().map(b -> b.pos().toShortString()).orElse("none"));
			m.put("wanted", Boolean.toString(HearthServices.law().wanted(p.getUUID(), this.townId)));
			return null;
		});
		log("before quitting: " + m);
		return m;
	}

	private void reload(Map<String, String> before) {
		settle(3_000);
		boolean back = srv(server -> SimServer.world(server.overworld()).settlement(this.townId) != null);
		ok(back, "the town is in the world again");
		if (!back) {
			return;
		}
		Map<String, String> after = snapshot();
		for (String k : List.of("coins", "deposit", "standing", "quests", "town", "folk", "bed", "wanted")) {
			ok(before.get(k).equals(after.get(k)), k + " intact: " + before.get(k) + (before.get(k).equals(after.get(k)) ? "" : " -> " + after.get(k)));
		}
		cmd("gamemode survival @a");
		BlockPos c = srv(server -> town(server).center());
		stand(c, 0);
		settle(15_000);
		int folk = srv(server -> townFolk().size());
		note("after reload at " + where() + ": " + folk + " folk embodied");
		ok(folk >= 3, "the town's folk are about again (" + folk + ")");
		camera(c.getX() + 24, surface(c) + 18, c.getZ() + 24, c.getX(), surface(c), c.getZ());
		shot("7_reloaded", false);
	}

	// ---------------------------------------------------------------- 8. away and back

	private void away() {
		Map<Integer, String> plots0 = srv(server -> plotStates(town(server)));
		String town0 = srv(server -> {
			SimSettlement s = town(server);
			return s.population() + " souls, " + s.tier().getSerializedName() + ", plots " + s.plotCounts() + ", treasury " + s.treasury();
		});
		BlockPos c = srv(server -> town(server).center());
		cmd("gamemode creative @a");
		srv(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), c.getX() + 5000.5, 220, c.getZ() + 0.5, Set.of(), 0, 0, true);
			return null;
		});
		settle(15_000);
		for (int d = 0; d < AWAY; d++) {
			cmd("time add 24000");
			settle(1_500);
		}
		// the sim catches the town up while we are away
		String caught = "";
		for (int i = 0; i < 120; i++) {
			settle(1_000);
			long behind = srv(server -> {
				SimWorld w = SimServer.world(server.overworld());
				SimSettlement s = w.settlement(this.townId);
				return w.simNow(s) - s.simTick();
			});
			caught = behind / 1000 + " h behind";
			if (behind < 2_000) {
				break;
			}
		}
		note("away " + AWAY + " days at " + where() + "; sim " + caught);
		cmd("gamemode survival @a");
		stand(c, 0);
		srv(server -> {
			forceChunks(server, true);
			return null;
		});
		waitApplier();
		srv(server -> {
			forceChunks(server, false);
			return null;
		});
		settle(5_000);
		Map<Integer, String> plots1 = srv(server -> plotStates(town(server)));
		String town1 = srv(server -> {
			SimSettlement s = town(server);
			return s.population() + " souls, " + s.tier().getSerializedName() + ", plots " + s.plotCounts() + ", treasury " + s.treasury();
		});
		List<String> changes = new ArrayList<>();
		BlockPos show = null;
		for (var e : plots1.entrySet()) {
			String was = plots0.get(e.getKey());
			if (!e.getValue().equals(was)) {
				changes.add("#" + e.getKey() + " " + (was == null ? "new" : was) + " -> " + e.getValue());
				if (show == null && e.getValue().contains("built") && (was == null || !was.contains("built"))) {
					int id = e.getKey();
					show = srv(server -> town(server).plot(id).spec().origin());
				}
			}
		}
		note("before: " + town0 + "; after: " + town1);
		note("plots changed (" + changes.size() + "): " + changes.subList(0, Math.min(12, changes.size())));
		ok(!changes.isEmpty(), "the town has grown or built something while away");
		if (show != null) {
			BlockPos s = show;
			camera(s.getX() - 14, surface(s) + 12, s.getZ() - 14, s.getX() + 4, surface(s), s.getZ() + 4);
			shot("8_new_building", false);
		}
		camera(c.getX() - 30, surface(c) + 26, c.getZ() - 30, c.getX(), surface(c), c.getZ());
		shot("8_town_after", false);
	}

	private String innLine(MinecraftServer server, UUID keeper) {
		SimSettlement s = town(server);
		Resident r = s.resident(keeper);
		var inn = r == null ? null : ServiceSites.inn(s, r);
		if (inn == null) {
			return (r == null ? "?" : r.fullName()) + " keeps no inn";
		}
		ServerLevel level = server.overworld();
		List<Poi> beds = ServiceSites.pois(s, inn, PoiType.BED);
		int family = 0;
		for (Resident x : s.residents()) {
			if (x.home == inn.id()) {
				family++;
			}
		}
		Map<String, Integer> blocks = new java.util.TreeMap<>();
		for (Poi b : beds) {
			blocks.merge(level.isLoaded(b.pos()) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(b.pos()).getBlock()).getPath() : "unloaded", 1, Integer::sum);
		}
		return r.fullName() + " keeps #" + inn.id() + " " + inn.spec().archetype().getPath() + " at " + inn.spec().origin().toShortString() + ": " + beds.size()
			+ " bed points " + blocks + ", " + family + " residents live there";
	}

	private static Map<Integer, String> plotStates(SimSettlement s) {
		Map<Integer, String> m = new HashMap<>();
		for (SimPlot p : s.plots()) {
			m.put(p.id(), p.spec().archetype().getPath() + " " + p.state().getSerializedName() + (p.state() == PlotState.BUILT ? "" : " " + Cursor.toString(p.cursor())));
		}
		return m;
	}

	// ---------------------------------------------------------------- steps and the report

	private void run(int n, String title, Runnable body) {
		this.cur = new Step(n, title);
		this.steps.add(this.cur);
		log("==== step " + n + ": " + title);
		try {
			body.run();
		} catch (Throwable t) {
			ok(false, "the step threw " + t);
			t.printStackTrace();
		}
		try {
			this.context.setScreen(() -> null);
		} catch (Throwable t) {
			log("could not clear the screen: " + t);
		}
		log("step " + n + " " + (this.cur.fails.isEmpty() ? "PASS" : "FAIL " + this.cur.fails));
		write();
	}

	private void ok(boolean pass, String what) {
		log((pass ? "ok   " : "FAIL ") + what);
		if (this.cur != null) {
			this.cur.lines.add((pass ? "- ok: " : "- **FAIL**: ") + what);
			if (!pass) {
				this.cur.fails.add(what);
			}
		}
	}

	private void note(String what) {
		log("     " + what);
		if (this.cur != null) {
			this.cur.lines.add("- " + what);
		}
	}

	private void write() {
		StringBuilder b = new StringBuilder("# Playthrough results (raw, written by PlaythroughTour)\n\n");
		b.append("Town: ").append(this.townName).append(" #").append(this.townId).append(", seed ").append(SEED).append(", grown ").append(DAYS).append(" days\n\n");
		for (Step s : this.steps) {
			b.append("## ").append(s.n).append(". ").append(s.title).append(": ").append(s.fails.isEmpty() ? "PASS" : "FAIL").append("\n\n");
			for (String l : s.lines) {
				b.append(l).append('\n');
			}
			for (String f : s.fails) {
				if (s.lines.stream().noneMatch(l -> l.contains(f))) {
					b.append("- **FAIL**: ").append(f).append('\n');
				}
			}
			if (!s.shots.isEmpty()) {
				b.append("- shots: ").append(String.join(", ", s.shots)).append('\n');
			}
			b.append('\n');
		}
		try {
			Files.writeString(OUT.resolve("results.md"), b.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			log("could not write results: " + e);
		}
	}

	// ---------------------------------------------------------------- talking and screens

	/** Walks up to a resident and right-clicks them (the client's own interact packet); their entity id, or null. */
	private @Nullable Integer talkTo(UUID resident) {
		for (int i = 0; i < 6; i++) {
			Integer id = srv(server -> {
				Folk f = NpcModule.embodiment().entityOf(resident);
				if (f == null || f.brain() == null || f.isFolded()) {
					return null;
				}
				ServerLevel level = server.overworld();
				BlockPos at = freeBeside(level, f.blockPosition(), 2);
				ServerPlayer p = player(server);
				Vec3 eye = Vec3.atBottomCenterOf(at).add(0, 1.62, 0);
				Vec3 to = f.getEyePosition();
				float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - eye.x), to.z - eye.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(to.y - eye.y, Math.hypot(to.x - eye.x, to.z - eye.z)));
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
				p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), yaw, pitch, true);
				return f.getId();
			});
			if (id == null) {
				if (i == 0) {
					note("(" + srv(server -> town(server).resident(resident) == null ? "?" : town(server).resident(resident).fullName()) + " is not about; called over)");
				}
				spawnNear(resident, srv(server -> town(server).center()));
				settle(1_500);
				continue;
			}
			settle(1_000);
			boolean sent = this.context.computeOnClient(mc -> {
				Entity e = mc.level.getEntity(id);
				if (e == null) {
					return false;
				}
				mc.gameMode.interact(mc.player, e, new EntityHitResult(e), InteractionHand.MAIN_HAND);
				return true;
			});
			if (sent && waitScreen(ConversationScreen.class, 60)) {
				settle(1_500);
				return id;
			}
			log("talk: right-click on " + id + " (sent " + sent + ") opened no conversation; again");
		}
		ok(false, "right-clicking " + srv(server -> town(server).resident(resident) == null ? resident.toString() : town(server).resident(resident).fullName())
			+ " opens a conversation (" + doing(resident) + ", hour " + srv(server -> server.overworld().getOverworldClockTime() % 24_000L) + ")");
		return null;
	}

	private int chatSize() {
		return this.context.computeOnClient(mc -> ((List<?>) field(mc.gui.hud.getChat(), "allMessages")).size());
	}

	/** Chat lines since the chat held {@code before} (oldest first). */
	private List<String> chatSince(int before) {
		return this.context.computeOnClient(mc -> {
			List<?> all = (List<?>) field(mc.gui.hud.getChat(), "allMessages");
			List<String> out = new ArrayList<>();
			for (int i = Math.max(0, all.size() - before) - 1; i >= 0; i--) {
				out.add(((net.minecraft.client.multiplayer.chat.GuiMessage) all.get(i)).content().getString());
			}
			return out;
		});
	}

	private void choose(int entity, String option) {
		this.context.runOnClient(mc -> ClientPlayNetworking.send(new TalkPayloads.Choose(entity, option)));
		settle(1_200);
	}

	private void end(int entity) {
		this.context.runOnClient(mc -> ClientPlayNetworking.send(new TalkPayloads.End(entity)));
		settle(300);
		this.context.setScreen(() -> null);
		settle(300);
	}

	@SuppressWarnings("unchecked")
	private static List<TalkPayloads.Option> options(Minecraft mc) {
		if (!(mc.gui.screen() instanceof ConversationScreen s)) {
			return List.of();
		}
		return List.copyOf((List<TalkPayloads.Option>) field(s, "options"));
	}

	private static String speech(Minecraft mc) {
		if (!(mc.gui.screen() instanceof ConversationScreen s)) {
			return "";
		}
		return ((Component) field(s, "speech")).getString();
	}

	private static String describe(List<TalkPayloads.Option> options) {
		StringBuilder b = new StringBuilder();
		for (TalkPayloads.Option o : options) {
			b.append(o.id()).append(" \"").append(o.label().getString()).append(o.hint() == null ? "" : " " + o.hint().getString()).append('"')
				.append(o.locked() ? " (locked)" : "").append("; ");
		}
		return b.toString();
	}

	private boolean waitScreen(Class<?> screen, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (this.context.computeOnClient(mc -> screen.isInstance(mc.gui.screen()))) {
				return true;
			}
			this.context.waitTick();
		}
		log("(no " + screen.getSimpleName() + " came up)");
		return false;
	}

	/** An economy button, as the market or bank screen sends it; waits for the server's answer. */
	private void action(int action, int index, long amount) {
		this.context.runOnClient(mc -> ClientPlayNetworking.send(new EconomyActionPayload(mc.player.containerMenu.containerId, action, index, amount)));
		settle(1_200);
	}

	private MarketView marketView() {
		return srv(server -> player(server).containerMenu instanceof MarketMenu m ? m.view() : null);
	}

	private void closeMenu() {
		this.context.runOnClient(mc -> mc.player.closeContainer());
		settle(500);
	}

	private static String rows(List<MarketView.Offer> rows) {
		StringBuilder b = new StringBuilder();
		for (MarketView.Offer o : rows) {
			b.append(o.stack().getCount()).append(' ').append(name(o.stack())).append(" @").append(o.price()).append(" (").append(o.available())
				.append(o.owned() > 0 ? ", have " + o.owned() : "").append(")").append(o.flags() == 0 ? "" : " f" + o.flags()).append("; ");
			if (b.length() > 700) {
				b.append("...");
				break;
			}
		}
		return b.toString();
	}

	private static String name(ItemStack st) {
		return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
	}

	// ---------------------------------------------------------------- the player

	private long coins() {
		return srv(server -> HearthServices.economy().balance(player(server)));
	}

	private long deposit() {
		return srv(server -> DepositStore.balance(server, player(server).getUUID()));
	}

	private int standing() {
		return srv(server -> HearthServices.standing().standing(this.townId, player(server).getUUID()));
	}

	private int stock(String goods) {
		return srv(server -> {
			Goods g = Goods.CODEC.byName(goods);
			return g == null ? -1 : town(server).stockpile().get(g);
		});
	}

	private int held(Item item) {
		return srv(server -> {
			ServerPlayer p = player(server);
			int n = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(item)) {
					n += p.getInventory().getItem(i).getCount();
				}
			}
			return n;
		});
	}

	/** Items into the pack, or (item null) coin. */
	private void give(@Nullable Item item, long count) {
		srv(server -> {
			ServerPlayer p = player(server);
			if (item == null) {
				HearthServices.economy().pay(p, count);
				return null;
			}
			long left = count;
			while (left > 0) {
				int n = (int) Math.min(left, item.getDefaultMaxStackSize());
				p.getInventory().add(new ItemStack(item, n));
				left -= n;
			}
			return null;
		});
	}

	private String where() {
		return srv(server -> player(server).blockPosition().toShortString());
	}

	private String doing(@Nullable UUID resident) {
		if (resident == null) {
			return "nobody";
		}
		return srv(server -> {
			Folk f = NpcModule.embodiment().entityOf(resident);
			if (f == null || f.brain() == null) {
				return "not embodied";
			}
			return f.folkName() + " (" + f.brain().resident.job.getSerializedName() + "): " + f.brain().describe() + " at " + f.blockPosition().toShortString();
		});
	}

	/** Right-clicks a block, as the client does. */
	private void click(BlockPos pos) {
		this.context.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos,
			false)));
		settle(300);
	}

	/** On foot on the ground at a column, facing {@code yaw}. */
	private void stand(BlockPos column, float yaw) {
		srv(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(column.getX() >> 4, column.getZ() >> 4);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			ServerPlayer p = player(server);
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			p.teleportTo(level, column.getX() + 0.5, y, column.getZ() + 0.5, Set.of(), yaw, 10, true);
			return null;
		});
		settle(1_000);
	}

	/** On foot two blocks from a block, looking at it. */
	private void standAt(BlockPos target) {
		srv(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer p = player(server);
			BlockPos at = freeBeside(level, target, 2);
			Vec3 eye = Vec3.atBottomCenterOf(at).add(0, 1.62, 0);
			Vec3 c = Vec3.atCenterOf(target);
			float yaw = (float) Math.toDegrees(Math.atan2(-(c.x - eye.x), c.z - eye.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(c.y - eye.y, Math.hypot(c.x - eye.x, c.z - eye.z)));
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), yaw, pitch, true);
			return null;
		});
		settle(800);
	}

	/** A spot a player can stand {@code r} (or fewer) blocks from a block, on the same level if it can. */
	private static BlockPos freeBeside(ServerLevel level, BlockPos target, int r) {
		for (int d = r; d >= 1; d--) {
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				for (int dy : new int[]{0, -1, 1}) {
					BlockPos at = target.relative(dir, d).above(dy);
					if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
						&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
						return at;
					}
				}
			}
		}
		return target.above();
	}

	/** A camera: creative and flying at {@code x,y,z}, looking at a point (a gamemode switch only, for the photograph). */
	private void camera(double x, double y, double z, double lx, double ly, double lz) {
		cmd("gamemode creative @a");
		double dx = lx - x;
		double dy = ly - (y + 1.62);
		double dz = lz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		srv(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), x, y, z, Set.of(), Mth.wrapDegrees(yaw), Mth.clamp(pitch, -70, 70), true);
			return null;
		});
		settle(3_500);
	}

	private void hud(boolean visible) {
		this.context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == visible) {
				mc.gui.hud.toggle();
			}
		});
	}

	/** A JPEG of what the player sees now; the HUD shown only when it is the point. */
	private void shot(String name, boolean withHud) {
		hud(withHud);
		this.context.waitTicks(2);
		Path png = this.context.takeScreenshot("hearth_play_" + name);
		hud(false);
		Path jpg = OUT.resolve(name + ".jpg");
		try {
			BufferedImage img = ImageIO.read(png.toFile());
			BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
			rgb.getGraphics().drawImage(img, 0, 0, null);
			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.85f);
			Files.deleteIfExists(jpg);
			try (ImageOutputStream os = ImageIO.createImageOutputStream(jpg.toFile())) {
				writer.setOutput(os);
				writer.write(null, new IIOImage(rgb, null, null), param);
			}
			writer.dispose();
			Files.deleteIfExists(png);
			if (this.cur != null) {
				this.cur.shots.add(jpg.toAbsolutePath().toString());
			}
		} catch (IOException e) {
			log("screenshot " + name + " failed: " + e);
		}
	}

	// ---------------------------------------------------------------- the town and its folk

	private SimSettlement town(MinecraftServer server) {
		return SimServer.world(server.overworld()).settlement(this.townId);
	}

	private List<Folk> townFolk() {
		List<Folk> out = new ArrayList<>();
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == this.townId && !f.isFolded()) {
				out.add(f);
			}
		}
		return out;
	}

	private @Nullable Folk nearestFolk(MinecraftServer server, Predicate<Resident> want) {
		ServerPlayer p = player(server);
		Folk best = null;
		for (Folk f : townFolk()) {
			if (f.isSleeping() || !want.test(f.brain().resident) || this.used.contains(f.brain().resident.id)) {
				continue;
			}
			if (best == null || f.distanceTo(p) < best.distanceTo(p)) {
				best = f;
			}
		}
		return best;
	}

	/** An adult not used yet that {@code want} accepts (an embodied one first). */
	private @Nullable Resident pick(SimSettlement s, Predicate<Resident> want) {
		Resident fallback = null;
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.ADULT || this.used.contains(r.id) || !want.test(r)) {
				continue;
			}
			if (NpcModule.embodiment().entityOf(r.id) != null) {
				this.used.add(r.id);
				return r;
			}
			if (fallback == null) {
				fallback = r;
			}
		}
		if (fallback != null) {
			this.used.add(fallback.id);
		}
		return fallback;
	}

	private void spawnNear(UUID resident, BlockPos near) {
		srv(server -> {
			SimSettlement s = town(server);
			Resident r = s.resident(resident);
			if (r == null) {
				return null;
			}
			Folk f = NpcModule.embodiment().entityOf(resident);
			ServerLevel level = server.overworld();
			boolean room = level.getBlockState(near).isAir() && level.getBlockState(near.above()).isAir() && level.getBlockState(near.below()).isSolid();
			int y = room ? near.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
			if (f != null && !f.isFolded()) {
				f.teleportTo(near.getX() + 0.5, y, near.getZ() + 0.5);
			} else {
				NpcModule.embodiment().spawnNear(level, s, r, new BlockPos(near.getX(), y, near.getZ()));
			}
			return null;
		});
	}

	private static String archetypes(SimSettlement s) {
		Map<String, Integer> n = new java.util.TreeMap<>();
		for (SimPlot p : s.plots()) {
			if (p.state() == PlotState.BUILT) {
				String path = p.spec().archetype().getPath();
				if (!path.startsWith("infra/") && !path.startsWith("farm/") && !path.startsWith("house/")) {
					n.merge(path, 1, Integer::sum);
				}
			}
		}
		return n.toString();
	}

	private void forceChunks(MinecraftServer server, boolean force) {
		ServerLevel level = server.overworld();
		BoundingBox b = town(server).bounds();
		for (int cx = (b.minX() >> 4) - 1; cx <= (b.maxX() >> 4) + 1; cx++) {
			for (int cz = (b.minZ() >> 4) - 1; cz <= (b.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, force);
			}
		}
	}

	private void waitApplier() {
		for (int i = 0; i < 240; i++) {
			settle(500);
			int pending = srv(server -> SimServer.world(server.overworld()).applier().pending());
			if (pending == 0 && i > 6) {
				break;
			}
		}
		log("applier: " + srv(server -> SimServer.world(server.overworld()).applier().blocksPlaced.sum()) + " blocks written");
	}

	private int surface(BlockPos at) {
		return srv(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		});
	}

	/** Forward (never back: the sim follows the clock) to the next {@code t} of the day. */
	private void timeTo(long t) {
		long now = srv(server -> server.overworld().getOverworldClockTime() % 24_000L);
		long add = ((t - now) % 24_000L + 24_000L) % 24_000L;
		if (add > 0) {
			cmd("time add " + add);
		}
	}

	// ---------------------------------------------------------------- plumbing

	private <T> T srv(Function<MinecraftServer, T> f) {
		return this.world.getServer().computeOnServer(f::apply);
	}

	private void cmd(String c) {
		this.world.getServer().runCommand(c);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Leaves the world without the framework's close (it can deadlock on 26.3; see PersistTour.quit). */
	private void quit() {
		MinecraftServer server = srv(s -> s);
		this.world.getServer().runOnServer(s -> s.halt(false));
		this.context.waitFor(mc -> server.isStopped(), 2400);
		this.context.waitFor(mc -> mc.level == null, 2400);
		this.context.waitTicks(2);
		this.context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
		settle(1_000);
	}

	private void settle(long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			this.context.waitTick();
		}
	}

	private static @Nullable Object field(Object o, String name) {
		try {
			Field f = o.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.get(o);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static @Nullable Object staticField(Class<?> c, String name) {
		try {
			Field f = c.getDeclaredField(name);
			f.setAccessible(true);
			return f.get(null);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static @Nullable Object callStatic(String cls, String name) {
		try {
			Method m = Class.forName(cls).getDeclaredMethod(name);
			m.setAccessible(true);
			return m.invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void log(String line) {
		System.out.println("[hearth play] " + line);
	}
}
