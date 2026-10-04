package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.RepairService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.client.economy.MarketScreen;
import dev.voidmc.hearth.client.law.ConfrontScreen;
import dev.voidmc.hearth.client.law.LockpickScreen;
import dev.voidmc.hearth.client.law.PickpocketScreen;
import dev.voidmc.hearth.client.npc.ConversationScreen;
import dev.voidmc.hearth.client.standing.ReputationScreen;
import dev.voidmc.hearth.economy.market.Market;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.Shops;
import dev.voidmc.hearth.law.item.LawItems;
import dev.voidmc.hearth.law.justice.Confrontations;
import dev.voidmc.hearth.law.lock.LockpickSessions;
import dev.voidmc.hearth.law.lock.Locks;
import dev.voidmc.hearth.law.own.Ownership;
import dev.voidmc.hearth.law.pocket.PickpocketSessions;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import dev.voidmc.hearth.standing.net.StandingNetwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The living world's legs of the client tour (phase 3), on the town {@link IntegrationTour} grew: a forester felling
 * real trees, a miner or quarrier on an excavation's steps, a hauler, a caravan of the sim met on the road, a theft
 * unseen and a theft seen (witness, guard, the fine), the stolen goods refused by the town's merchant and bought by a
 * fence, the lockpicking and pickpocketing screens, standing and title moving, a broken window mended by a builder, a
 * supply contract and an official's quest, and the conversation's speech checks. Every step logs {@code [hearth tour]};
 * screenshots are {@code hearth_int_*}. Each leg stands alone: one that cannot be set up says why and the next goes on.
 */
final class LivingLegs {
	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final long townId;
	private final Set<UUID> used = new HashSet<>();
	private static final AtomicInteger TITLE_EVENTS = new AtomicInteger();
	private static boolean listening;

	LivingLegs(ClientGameTestContext context, TestSingleplayerContext world, long townId) {
		this.context = context;
		this.world = world;
		this.townId = townId;
	}

	void run() {
		this.world.getServer().runOnServer(server -> {
			if (!listening) {
				listening = true;
				SettlementEvents.STANDING_CHANGED.register((w, s, p, from, to) -> {
					TITLE_EVENTS.incrementAndGet();
					log("standing event: " + s.name() + " " + from.getSerializedName() + " -> " + to.getSerializedName());
				});
			}
			NpcConfig c = NpcModule.config();
			// folk think every tick near the camera, so a minute shows real work
			NpcModule.setConfig(c.withAi(new NpcConfig.Ai(1, 1, 1, 64, 128, 20, 10, true)));
		});
		leg("forester", this::forester);
		leg("excavation", this::excavation);
		leg("hauler", this::hauler);
		leg("caravan", this::caravan);
		leg("theft", this::theft);
		leg("lockpick", this::lockpick);
		leg("pickpocket", this::pickpocket);
		leg("standing", this::standing);
		leg("repair", this::repair);
		leg("quests", this::quests);
		leg("speech", this::speech);
	}

	private void leg(String name, Runnable leg) {
		log("---- " + name);
		// back in town first: a leg out on the road leaves the town's chunks unloaded and its folk folded
		BlockPos c = this.world.getServer().computeOnServer(server -> town(server).center());
		teleport(c.getX() + 0.5, surface(c) + 3, c.getZ() + 0.5, 0, 20);
		settle(6_000);
		try {
			leg.run();
		} catch (Throwable t) {
			log(name + " FAILED with " + t);
			t.printStackTrace();
		}
		this.context.setScreen(() -> null);
		this.world.getServer().runCommand("gamemode creative @a");
	}

	// ---------------------------------------------------------------- gatherers

	private void forester() {
		this.world.getServer().runCommand("time set 2500");
		Object[] setup = this.world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server);
			ResourceSite best = null;
			double bestD = Double.MAX_VALUE;
			for (ResourceSite site : s.sites()) {
				double d = Math.sqrt(site.center().distSqr(s.center()));
				if (site.kind() == ResourceSite.Kind.WOODS && site.remaining() > 0 && d < bestD) {
					best = site;
					bestD = d;
				}
			}
			if (best == null) {
				return null;
			}
			Resident r = pick(s, res -> res.job == JobType.FORESTER, res -> res.job != JobType.BUILDER);
			if (r == null) {
				return null;
			}
			r.job = JobType.FORESTER;
			log("forester: " + r.fullName() + " works the " + best.material() + " wood at " + best.center().toShortString() + " (" + (int) bestD
				+ " blocks out); town logs " + s.stockpile().get(Goods.LOGS));
			return new Object[]{r.id, best.center(), s.stockpile().get(Goods.LOGS)};
		});
		if (setup == null) {
			log("forester: no wood in the territory or nobody to send");
			return;
		}
		UUID id = (UUID) setup[0];
		BlockPos wood = (BlockPos) setup[1];
		int y = surface(wood);
		teleport(wood.getX() - 12, y + 18, wood.getZ() - 12, -45, 35);
		settle(4_000);
		spawnNear(id, new BlockPos(wood.getX(), y, wood.getZ()));
		int[] before = countTrees(wood, y);
		for (int i = 0; i < 4; i++) {
			settle(20_000);
			int[] now = countTrees(wood, y);
			log(String.format(Locale.ROOT, "forester %d: logs %d -> %d, saplings %d -> %d; %s", i, before[0], now[0], before[1], now[1], doing(id)));
			Vec3 at = folkAt(id);
			if (at != null) {
				shoot("forester_" + i, at.x - 8, at.y + 7, at.z - 8, -45, 30);
			} else {
				this.context.takeScreenshot("hearth_int_forester_" + i);
			}
		}
		log("forester: town logs now " + this.world.getServer().computeOnServer(server -> town(server).stockpile().get(Goods.LOGS)) + " (was "
			+ setup[2] + "; the felled tree's logs are credited when carried to the yard)");
	}

	private void excavation() {
		this.world.getServer().runCommand("time set 2500");
		Object[] setup = this.world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server);
			SimPlot pit = null;
			for (SimPlot p : s.plots()) {
				if (p.spec().archetype().getPath().startsWith("extract/") && p.isActive()) {
					pit = p;
					break;
				}
			}
			if (pit == null) {
				StringBuilder b = new StringBuilder();
				for (SimPlot p : s.plots()) {
					if (p.spec().archetype().getPath().startsWith("extract/")) {
						b.append(p.spec().archetype().getPath()).append(' ').append(p.state().getSerializedName()).append("; ");
					}
				}
				log("excavation: none under way in " + s.name() + " (" + b + ")");
				return null;
			}
			String path = pit.spec().archetype().getPath();
			JobType trade = path.contains("mine") || path.contains("shaft") ? JobType.MINER
				: path.contains("quarry") ? JobType.QUARRIER : path.contains("plantation") ? JobType.FORESTER : JobType.CLAY_DIGGER;
			Resident r = pick(s, res -> res.job == trade, res -> res.job != JobType.BUILDER);
			if (r == null) {
				return null;
			}
			r.job = trade;
			r.work = pit.id();
			s.tryStartStage(pit.id());
			log("excavation: " + r.fullName() + " (" + trade.getSerializedName() + ") on plot #" + pit.id() + " " + path + " at "
				+ Cursor.toString(pit.cursor()) + (pit.waitingForGoods() ? " (waiting for goods)" : ""));
			return new Object[]{r.id, pit.id(), pit.spec().origin(), pit.spec().sizeX(), pit.spec().sizeZ()};
		});
		if (setup == null) {
			return;
		}
		UUID id = (UUID) setup[0];
		int plot = (int) setup[1];
		BlockPos o = (BlockPos) setup[2];
		BlockPos mid = o.offset((int) setup[3] / 2, 0, (int) setup[4] / 2);
		teleport(mid.getX() - 10, surface(mid) + 12, mid.getZ() - 10, -45, 40);
		settle(3_000);
		spawnNear(id, mid);
		for (int i = 0; i < 4; i++) {
			settle(20_000);
			String line = this.world.getServer().computeOnServer(server -> {
				SimPlot p = town(server).plot(plot);
				return p == null ? "gone" : p.state().getSerializedName() + " " + Cursor.toString(p.cursor()) + (p.lastEmbodiedAdvance() == Long.MIN_VALUE
					? ", no step placed by a worker yet" : ", a step placed " + (server.overworld().getGameTime() - p.lastEmbodiedAdvance()) + " ticks ago");
			});
			log("excavation " + i + ": " + line + "; " + doing(id));
			shoot("excavation_" + i, mid.getX() - 10, surface(mid) + 12, mid.getZ() - 10, -45, 40);
		}
	}

	private void hauler() {
		this.world.getServer().runCommand("time set 2500");
		UUID id = this.world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server);
			Resident r = pick(s, res -> res.job == JobType.HAULER, res -> res.job != JobType.BUILDER);
			if (r != null) {
				r.job = JobType.HAULER;
				log("hauler: " + r.fullName());
			}
			return r == null ? null : r.id;
		});
		if (id == null) {
			log("hauler: nobody to carry");
			return;
		}
		BlockPos c = this.world.getServer().computeOnServer(server -> town(server).center());
		spawnNear(id, c);
		for (int i = 0; i < 3; i++) {
			settle(12_000);
			log("hauler " + i + ": " + doing(id));
			Vec3 at = folkAt(id);
			if (at != null) {
				shoot("hauler_" + i, at.x - 5, at.y + 4, at.z - 5, -45, 25);
			}
		}
	}

	private void caravan() {
		this.world.getServer().runCommand("time set 2500");
		long[] ids = this.world.getServer().computeOnServer(server -> {
			SimWorld w = SimServer.world(server.overworld());
			SimSettlement s = town(server);
			SimSettlement other = null;
			for (SimSettlement o : w.settlements()) {
				if (o != s && Math.sqrt(o.center().distSqr(s.center())) < 1500 && (other == null
					|| o.center().distSqr(s.center()) < other.center().distSqr(s.center()))) {
					other = o;
				}
			}
			if (other == null) {
				for (int k = 0; k < 16 && other == null; k++) {
					double a = k * Math.PI / 8;
					BlockPos at = s.center().offset((int) (Math.cos(a) * 700), 0, (int) (Math.sin(a) * 700));
					if (w.whyNotFound(at) == null) {
						other = (SimSettlement) w.found(at, null, null, "a neighbour for the caravan leg");
					}
				}
			}
			if (other == null) {
				return null;
			}
			s.stockpile().add(Goods.WOOD, 200);
			s.stockpile().add(Goods.STONE, 200);
			long id = w.caravanTrade().dispatch(s, other);
			var view = w.caravan(id);
			log("caravan: #" + id + " from " + s.name() + " to " + other.name() + (view == null ? " (none sent)" : ", " + view.cargo().length
				+ " goods, arrives in " + (view.arriveTick() - view.departTick()) / 20 + " s; npc journeys " + NpcModule.caravans().journeys()));
			return view == null ? null : new long[]{id};
		});
		if (ids == null) {
			log("caravan: no neighbour or nothing to send");
			return;
		}
		for (int i = 0; i < 4; i++) {
			BlockPos at = this.world.getServer().computeOnServer(server -> {
				HearthWorld hw = HearthWorld.of(server.overworld());
				var v = hw.caravan(ids[0]);
				return v == null ? null : v.positionAt(hw.clock() + 400);
			});
			if (at == null) {
				log("caravan " + i + ": no longer on the road");
				break;
			}
			teleport(at.getX() - 10, surface(at) + 6, at.getZ() - 10, -45, 20);
			settle(10_000);
			String parties = this.world.getServer().computeOnServer(server -> NpcModule.caravans().parties() + " parties; "
				+ String.join("; ", NpcModule.caravans().describe(server.overworld().getGameTime())));
			log("caravan " + i + ": at " + at.toShortString() + ", " + parties);
			this.context.takeScreenshot("hearth_int_caravan_" + i);
		}
	}

	// ---------------------------------------------------------------- the law

	private void theft() {
		BlockPos chest = this.world.getServer().computeOnServer(this::placeHouseChest);
		if (chest == null) {
			log("theft: no house to put a chest in");
			return;
		}
		// unseen: at night, everyone abed
		this.world.getServer().runCommand("time set 18500");
		settle(8_000);
		stand(chest);
		this.world.getServer().runCommand("gamemode survival @a");
		settle(1_500);
		boolean opened = open(chest);
		settle(1_500);
		this.context.takeScreenshot("hearth_int_theft_chest");
		take(0);
		settle(2_000);
		this.world.getServer().runOnServer(server -> player(server).closeContainer());
		settle(2_000);
		log("theft unseen: opened " + opened + "; " + lawLine());

		// the merchant will not buy it; the fence will
		this.world.getServer().runCommand("time set 2500");
		settle(5_000);
		merchantRefuses();
		fenceBuys();

		// seen: in daylight, a neighbour beside the chest and a guard on the street
		this.world.getServer().runCommand("gamemode survival @a");
		UUID[] folk = this.world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server);
			Resident witness = pick(s, r -> false, r -> r.job != JobType.GUARD && r.job != JobType.BUILDER);
			Resident guard = pick(s, r -> r.job == JobType.GUARD, r -> r.job != JobType.BUILDER);
			if (witness == null || guard == null) {
				return null;
			}
			guard.job = JobType.GUARD;
			return new UUID[]{witness.id, guard.id};
		});
		if (folk == null) {
			log("theft seen: no folk to see it");
			return;
		}
		stand(chest);
		BlockPos here = this.world.getServer().computeOnServer(server -> player(server).blockPosition());
		spawnNear(folk[0], here.relative(Direction.NORTH, 3));
		spawnNear(folk[1], here.relative(Direction.SOUTH, 10));
		settle(3_000);
		this.world.getServer().runOnServer(server -> {
			Folk w = NpcModule.embodiment().entityOf(folk[0]);
			if (w != null && w.brain() != null) {
				w.brain().handle().stop();
				w.brain().handle().lookAt(player(server));
			}
		});
		open(chest);
		settle(800);
		take(1);
		settle(1_500);
		this.world.getServer().runOnServer(server -> player(server).closeContainer());
		log("theft seen: " + lawLine() + "; witness " + doing(folk[0]) + "; guard " + doing(folk[1]));
		this.context.takeScreenshot("hearth_int_theft_seen");
		boolean confronted = false;
		for (int i = 0; i < 60 && !confronted; i++) {
			settle(1_000);
			confronted = this.context.computeOnClient(mc -> mc.gui.screen() instanceof ConfrontScreen);
		}
		log("guard confrontation on screen: " + confronted + "; guard " + doing(folk[1]));
		if (!confronted) {
			return;
		}
		settle(1_500);
		this.context.takeScreenshot("hearth_int_confront");
		String paid = this.world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			SimSettlement s = town(server);
			LawService law = HearthServices.law();
			EconomyService eco = HearthServices.economy();
			long bounty = law.bounty(p.getUUID(), s.id());
			eco.pay(p, bounty + 50);
			long treasury = s.treasury();
			Folk g = NpcModule.embodiment().entityOf(folk[1]);
			if (g == null) {
				return "the guard is gone";
			}
			Confrontations.choose(p, g.getId(), "pay");
			return "bounty " + bounty + " paid: treasury " + treasury + " -> " + s.treasury() + ", wanted now " + law.wanted(p.getUUID(), s.id());
		});
		log("fine: " + paid);
		settle(2_000);
		this.context.takeScreenshot("hearth_int_fine_paid");
	}

	/** A chest in the yard of a lived-in house, with what a household keeps: the theft's target. */
	private @Nullable BlockPos placeHouseChest(MinecraftServer server) {
		SimSettlement s = town(server);
		ServerLevel level = server.overworld();
		for (SimPlot p : s.plots()) {
			if (p.state() != PlotState.BUILT || !p.spec().archetype().getPath().startsWith("house/")) {
				continue;
			}
			boolean lived = s.residents().stream().anyMatch(r -> r.home == p.id());
			if (!lived) {
				continue;
			}
			var spec = p.spec();
			for (int dx = 1; dx < spec.sizeX() - 1; dx++) {
				for (int dz = 1; dz < spec.sizeZ() - 1; dz++) {
					int x = spec.origin().getX() + dx;
					int z = spec.origin().getZ() + dz;
					BlockPos at = null;
					for (int y = spec.floorY(); y <= spec.floorY() + 2 && at == null; y++) {
						BlockPos q = new BlockPos(x, y, z);
						if (level.isLoaded(q) && level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
							&& level.getBlockState(q.below()).isSolid() && level.getBlockState(q.below()).getBlock() != Blocks.FARMLAND) {
							at = q;
						}
					}
					if (at == null) {
						continue;
					}
					level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
					if (level.getBlockEntity(at) instanceof ChestBlockEntity c) {
						c.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
						c.setItem(1, new ItemStack(Items.OAK_LOG, 16));
						c.setItem(2, new ItemStack(Items.BREAD, 6));
						c.setItem(3, new ItemStack(Items.IRON_INGOT, 4));
						c.setItem(4, new ItemStack(Items.WHEAT, 20));
					}
					LawService.Owner owner = Ownership.owner(level, at);
					log("theft: a chest at " + at.toShortString() + " in plot #" + p.id() + " (" + spec.archetype().getPath() + "), owner "
						+ (owner == null ? "nobody" : owner.kind() + " " + (owner.resident() == null ? "" : s.resident(owner.resident()) == null ? "?"
							: s.resident(owner.resident()).fullName())));
					if (owner != null) {
						return at;
					}
				}
			}
		}
		return null;
	}

	private void merchantRefuses() {
		UUID merchant = this.world.getServer().computeOnServer(server -> {
			Resident r = pick(town(server), res -> res.job == JobType.MERCHANT, res -> res.job != JobType.BUILDER && res.job != JobType.GUARD);
			if (r != null) {
				r.job = JobType.MERCHANT;
			}
			return r == null ? null : r.id;
		});
		if (merchant == null || talkTo(merchant) == null) {
			log("merchant: nobody to sell to");
			return;
		}
		String view = this.world.getServer().computeOnServer(server -> marketLine(server, merchant));
		log("merchant's view of the stolen goods: " + view);
		Integer entity = entityOf(merchant);
		this.world.getServer().runOnServer(server -> NpcModule.conversations().choose(player(server), entity, "economy:trade"));
		this.context.waitFor(mc -> mc.gui.screen() instanceof MarketScreen, 100);
		settle(1_500);
		this.context.takeScreenshot("hearth_int_merchant_refuses");
		this.context.runOnClient(mc -> mc.player.closeContainer());
		this.context.setScreen(() -> null);
	}

	private void fenceBuys() {
		UUID fence = this.world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server);
			Resident r = pick(s, res -> res.job == JobType.FENCE, res -> res.job != JobType.BUILDER && res.job != JobType.GUARD);
			if (r != null) {
				r.job = JobType.FENCE;
				r.work = Resident.NO_PLOT;
				Shop shop = Shops.keptBy(s, r.id, server.overworld().getGameTime() / 24000L);
				log("fence: " + r.fullName() + (shop == null ? " (keeps no back room yet)" : " keeps a back room (fence " + shop.fence + ")"));
			}
			return r == null ? null : r.id;
		});
		if (fence == null || talkTo(fence) == null) {
			log("fence: nobody");
			return;
		}
		log("fence options: " + options(fence));
		Integer entity = entityOf(fence);
		long before = this.world.getServer().computeOnServer(server -> HearthServices.economy().balance(player(server)));
		this.world.getServer().runOnServer(server -> NpcModule.conversations().choose(player(server), entity, "economy:fence"));
		boolean open = false;
		for (int i = 0; i < 50 && !open; i++) {
			settle(100);
			open = this.context.computeOnClient(mc -> mc.gui.screen() instanceof MarketScreen);
		}
		settle(1_500);
		this.context.takeScreenshot("hearth_int_fence");
		String view = this.world.getServer().computeOnServer(server -> marketLine(server, fence));
		log("fence's view: " + view);
		// sell the first stolen row
		int row = this.world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			SimSettlement s = town(server);
			MarketView v = new Market(s, Shops.keptBy(s, fence, server.overworld().getGameTime() / 24000L)).view(p);
			for (MarketView.Offer o : v.buys()) {
				if ((o.flags() & MarketView.STOLEN) != 0 && o.owned() > 0) {
					return o.id();
				}
			}
			return -1;
		});
		if (row >= 0) {
			this.context.runOnClient(mc -> {
				if (mc.player.containerMenu instanceof MarketMenu menu) {
					ClientPlayNetworking.send(new EconomyActionPayload(menu.containerId, EconomyActionPayload.TRADE, row, 1));
				}
			});
			settle(1_500);
			this.context.takeScreenshot("hearth_int_fence_sold");
		}
		long after = this.world.getServer().computeOnServer(server -> HearthServices.economy().balance(player(server)));
		log("fence: stolen row " + row + ", the player's coin " + before + " -> " + after + "; " + lawLine());
		this.context.runOnClient(mc -> mc.player.closeContainer());
		this.context.setScreen(() -> null);
	}

	private String marketLine(MinecraftServer server, UUID keeper) {
		ServerPlayer p = player(server);
		SimSettlement s = town(server);
		Shop shop = Shops.keptBy(s, keeper, server.overworld().getGameTime() / 24000L);
		MarketView v = new Market(s, shop).view(p);
		StringBuilder b = new StringBuilder();
		for (MarketView.Offer o : v.buys()) {
			if (o.owned() > 0) {
				b.append(BuiltInRegistries.ITEM.getKey(o.stack().getItem()).getPath()).append(" x").append(o.owned()).append(" @").append(o.price())
					.append((o.flags() & MarketView.STOLEN) != 0 ? " STOLEN" : "").append((o.flags() & MarketView.REFUSED) != 0 ? " REFUSED" : "")
					.append((o.flags() & MarketView.FENCED) != 0 ? " FENCED" : "").append("; ");
			}
		}
		return (shop == null ? "town counter" : shop.fence ? "fence" : "shop " + shop.key) + ": " + b;
	}

	private void lockpick() {
		this.world.getServer().runCommand("time set 18500");
		BlockPos lock = this.world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			SimSettlement s = town(server);
			for (SimPlot p : s.plots()) {
				if (p.state() != PlotState.BUILT) {
					continue;
				}
				var spec = p.spec();
				for (int x = spec.origin().getX(); x < spec.origin().getX() + spec.sizeX(); x++) {
					for (int z = spec.origin().getZ(); z < spec.origin().getZ() + spec.sizeZ(); z++) {
						for (int y = spec.floorY(); y < spec.floorY() + 4; y++) {
							BlockPos pos = new BlockPos(x, y, z);
							if (!level.isLoaded(pos)) {
								continue;
							}
							BlockState st = level.getBlockState(pos);
							Locks.Lock l = Locks.lockable(st) ? Locks.at(level, pos, st) : null;
							if (l != null && l.locked()) {
								log("lockpick: " + (l.door() ? "door" : "chest") + " at " + pos.toShortString() + " tier " + l.tier() + " of plot #" + p.id()
									+ " " + spec.archetype().getPath());
								return pos;
							}
						}
					}
				}
			}
			return null;
		});
		if (lock == null) {
			log("lockpick: no locked door or chest in loaded chunks (night locks need the hour)");
			return;
		}
		stand(lock);
		this.world.getServer().runCommand("gamemode survival @a");
		settle(1_500);
		this.world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LawItems.LOCKPICK, 5));
			BlockState st = server.overworld().getBlockState(lock);
			Locks.Lock l = Locks.at(server.overworld(), lock, st);
			if (l != null) {
				LockpickSessions.start(p, l, new BlockHitResult(Vec3.atCenterOf(lock), Direction.UP, lock, false));
			}
		});
		boolean shown = false;
		for (int i = 0; i < 40 && !shown; i++) {
			settle(100);
			shown = this.context.computeOnClient(mc -> mc.gui.screen() instanceof LockpickScreen);
		}
		settle(1_200);
		this.context.takeScreenshot("hearth_int_lockpick");
		log("lockpick screen: " + shown + ", session " + this.world.getServer().computeOnServer(server -> LockpickSessions.active(player(server).getUUID())));
	}

	private void pickpocket() {
		this.world.getServer().runCommand("time set 2500");
		UUID mark = this.world.getServer().computeOnServer(server -> {
			Resident r = pick(town(server), res -> false, res -> res.job != JobType.GUARD && res.job != JobType.BUILDER);
			return r == null ? null : r.id;
		});
		if (mark == null) {
			log("pickpocket: nobody");
			return;
		}
		BlockPos c = this.world.getServer().computeOnServer(server -> town(server).center());
		spawnNear(mark, c);
		settle(2_000);
		this.world.getServer().runCommand("gamemode survival @a");
		String tried = this.world.getServer().computeOnServer(server -> {
			Folk f = NpcModule.embodiment().entityOf(mark);
			if (f == null || f.brain() == null) {
				return "the mark is not embodied";
			}
			ServerPlayer p = player(server);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			f.brain().handle().stop();
			// behind them, crouched: out of their field of view
			float yaw = f.getYHeadRot();
			double bx = f.getX() + Mth.sin(yaw * Mth.DEG_TO_RAD) * 1.6;
			double bz = f.getZ() - Mth.cos(yaw * Mth.DEG_TO_RAD) * 1.6;
			p.teleportTo(server.overworld(), bx, f.getY(), bz, Set.of(), yaw, 20.0F, true);
			p.setShiftKeyDown(true);
			boolean can = PickpocketSessions.canTry(p, f.brain().handle());
			PickpocketSessions.start(p, f.brain().handle());
			return "can try " + can + " (behind " + f.folkName() + ")";
		});
		boolean shown = false;
		for (int i = 0; i < 40 && !shown; i++) {
			settle(100);
			shown = this.context.computeOnClient(mc -> mc.gui.screen() instanceof PickpocketScreen);
		}
		settle(1_200);
		this.context.takeScreenshot("hearth_int_pickpocket");
		log("pickpocket: " + tried + ", screen " + shown);
		this.world.getServer().runOnServer(server -> player(server).setShiftKeyDown(false));
	}

	// ---------------------------------------------------------------- standing, repairs, quests, talk

	private void standing() {
		String line = this.world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			SimSettlement s = town(server);
			StandingService st = HearthServices.standing();
			StandingService.Title before = st.title(s.id(), p.getUUID());
			int b = st.standing(s.id(), p.getUUID());
			st.deed(new StandingService.Deed(p.getUUID(), s.id(), "defense:raid_repelled", 40, null, s.center(), true));
			st.deed(new StandingService.Deed(p.getUUID(), s.id(), "quest:completed", 25, null, s.center(), true));
			StandingNetwork.sendPage(p);
			return "standing " + b + " (" + before.getSerializedName() + ") -> " + st.standing(s.id(), p.getUUID()) + " ("
				+ st.title(s.id(), p.getUUID()).getSerializedName() + "), fame " + st.fame(p.getUUID(), s.center()) + ", renown "
				+ st.renown(p.getUUID(), s.center()) + "; title events " + TITLE_EVENTS.get();
		});
		log("standing: " + line);
		this.context.waitFor(mc -> mc.gui.screen() instanceof ReputationScreen, 100);
		settle(1_500);
		this.context.takeScreenshot("hearth_int_reputation");
	}

	private void repair() {
		this.world.getServer().runCommand("time set 2500");
		Object[] setup = this.world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			SimSettlement s = town(server);
			for (SimPlot p : s.plots()) {
				if (p.state() != PlotState.BUILT || p.spec().archetype().getPath().startsWith("infra/")) {
					continue;
				}
				var spec = p.spec();
				for (int x = spec.origin().getX(); x < spec.origin().getX() + spec.sizeX(); x++) {
					for (int z = spec.origin().getZ(); z < spec.origin().getZ() + spec.sizeZ(); z++) {
						for (int y = spec.floorY() + 1; y < spec.floorY() + 6; y++) {
							BlockPos pos = new BlockPos(x, y, z);
							if (!level.isLoaded(pos)) {
								continue;
							}
							BlockState st = level.getBlockState(pos);
							String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
							if (id.contains("glass") && level.canSeeSky(pos.above(4)) ) {
								Resident b = pick(s, r -> r.job == JobType.BUILDER, r -> false);
								if (b == null) {
									return null;
								}
								return new Object[]{pos, st, b.id, p.id()};
							}
						}
					}
				}
			}
			return null;
		});
		if (setup == null) {
			log("repair: no window in a built house in loaded chunks, or no builder");
			return;
		}
		BlockPos pos = (BlockPos) setup[0];
		BlockState was = (BlockState) setup[1];
		stand(pos);
		this.world.getServer().runCommand("gamemode survival @a");
		settle(1_000);
		String broke = this.world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			RepairService repairs = HearthServices.repairs();
			int before = repairs.damaged(town(server));
			boolean ok = p.gameMode.destroyBlock(pos);
			return "broke " + BuiltInRegistries.BLOCK.getKey(was.getBlock()) + " at " + pos.toShortString() + ": " + ok + "; damaged " + before;
		});
		this.world.getServer().runCommand("gamemode creative @a");
		settle(2_000);
		log("repair: " + broke + " -> " + this.world.getServer().computeOnServer(server -> HearthServices.repairs().damaged(town(server))) + "; "
			+ lawLine());
		this.context.takeScreenshot("hearth_int_window_broken");
		spawnNear((UUID) setup[2], pos);
		boolean mended = false;
		for (int i = 0; i < 18 && !mended; i++) {
			settle(5_000);
			mended = this.world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos).is(was.getBlock()));
			if (i % 4 == 0) {
				log("repair " + i + ": builder " + doing((UUID) setup[2]));
			}
		}
		log("repair: window mended " + mended + "; damaged now " + this.world.getServer().computeOnServer(server -> HearthServices.repairs().damaged(town(server)))
			+ "; builder " + doing((UUID) setup[2]));
		Vec3 at = Vec3.atCenterOf(pos);
		shoot("window_mended", at.x - 4, at.y + 1, at.z - 4, -45, 10);
		// the law's view of the broken window: pardoned so the rest of the tour is not a chase
		this.world.getServer().runOnServer(server -> HearthServices.law().pardon(player(server).getUUID(), town(server).id(), "tour"));
	}

	private void quests() {
		String line = this.world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			SimSettlement s = town(server);
			StringBuilder b = new StringBuilder();
			List<QuestInstance> offers = new ArrayList<>(QuestStore.board(s).offers);
			for (QuestInstance q : offers) {
				b.append(q.type.getSerializedName()).append(':').append(q.template).append("; ");
			}
			String took = "";
			for (QuestType want : List.of(QuestType.CONTRACT, QuestType.OFFICIAL)) {
				for (QuestInstance q : offers) {
					if (q.type == want && q.takenBy.isEmpty()) {
						String lock = QuestManager.lock(p, s, q, QuestStore.get(p));
						boolean ok = QuestManager.accept(p, s, q.id);
						took += want.getSerializedName() + " '" + q.text.title() + "' (from " + q.giver.name() + ") accepted " + ok
							+ (lock.isEmpty() ? "" : " [" + lock + "]") + "; ";
						break;
					}
				}
			}
			return "board: " + b + " | " + (took.isEmpty() ? "no contract or official's quest on the board" : took);
		});
		log("quests: " + line);
		this.context.setScreen(dev.voidmc.hearth.client.quest.JournalScreen::new);
		settle(1_500);
		this.context.takeScreenshot("hearth_int_journal_living");
		this.context.setScreen(() -> null);
	}

	private void speech() {
		this.world.getServer().runCommand("time set 2500");
		UUID who = this.world.getServer().computeOnServer(server -> {
			Resident r = pick(town(server), res -> false, res -> res.job != JobType.GUARD);
			return r == null ? null : r.id;
		});
		if (who == null) {
			log("speech: nobody");
			return;
		}
		BlockPos c = this.world.getServer().computeOnServer(server -> town(server).center());
		spawnNear(who, c);
		settle(2_000);
		Integer entity = talkTo(who);
		if (entity == null) {
			log("speech: could not talk");
			return;
		}
		log("speech options: " + options(who));
		this.context.takeScreenshot("hearth_int_speech_options");
		this.world.getServer().runOnServer(server -> NpcModule.conversations().choose(player(server), entity, "standing:flatter"));
		settle(2_000);
		this.context.takeScreenshot("hearth_int_speech_flatter");
		this.world.getServer().runOnServer(server -> NpcModule.conversations().end(player(server), entity));
	}

	// ---------------------------------------------------------------- helpers

	private SimSettlement town(MinecraftServer server) {
		return SimServer.world(server.overworld()).settlement(this.townId);
	}

	/** An adult of the town, preferring one {@code first} accepts, else one {@code then} accepts; not one already used. */
	private @Nullable Resident pick(SimSettlement s, Predicate<Resident> first, Predicate<Resident> then) {
		for (int pass = 0; pass < 2; pass++) {
			for (Predicate<Resident> want : List.of(first, then)) {
				for (Resident r : s.residents()) {
					if (r.age == AgeStage.ADULT && !this.used.contains(r.id) && want.test(r)) {
						this.used.add(r.id);
						return r;
					}
				}
			}
			// a small town has few adults: earlier legs' folk are asked again
			this.used.clear();
		}
		return null;
	}

	private void spawnNear(UUID resident, BlockPos near) {
		this.world.getServer().runOnServer(server -> {
			SimSettlement s = town(server);
			Resident r = s.resident(resident);
			if (r == null) {
				return;
			}
			Folk f = NpcModule.embodiment().entityOf(resident);
			ServerLevel level = server.overworld();
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
			if (f != null && !f.isFolded()) {
				f.teleportTo(near.getX() + 0.5, y, near.getZ() + 0.5);
			} else {
				NpcModule.embodiment().spawnNear(level, s, r, new BlockPos(near.getX(), y, near.getZ()));
			}
		});
	}

	private @Nullable Vec3 folkAt(UUID resident) {
		return this.world.getServer().computeOnServer(server -> {
			Folk f = NpcModule.embodiment().entityOf(resident);
			return f == null ? null : f.position();
		});
	}

	private @Nullable Integer entityOf(UUID resident) {
		return this.world.getServer().computeOnServer(server -> {
			Folk f = NpcModule.embodiment().entityOf(resident);
			return f == null ? null : f.getId();
		});
	}

	private String doing(UUID resident) {
		return this.world.getServer().computeOnServer(server -> {
			Folk f = NpcModule.embodiment().entityOf(resident);
			if (f == null || f.brain() == null) {
				return "not embodied";
			}
			var b = f.brain().behaviour();
			return f.folkName() + " (" + f.brain().resident.job.getSerializedName() + "): " + (b != null ? "[" + b.id() + "] " : "") + f.brain().describe()
				+ " at " + f.blockPosition().toShortString();
		});
	}

	private int[] countTrees(BlockPos c, int y) {
		return this.world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int logs = 0;
			int saplings = 0;
			for (BlockPos p : BlockPos.betweenClosed(c.getX() - 24, y - 4, c.getZ() - 24, c.getX() + 24, y + 20, c.getZ() + 24)) {
				if (!level.isLoaded(p)) {
					continue;
				}
				BlockState st = level.getBlockState(p);
				if (st.is(BlockTags.LOGS)) {
					logs++;
				} else if (st.is(BlockTags.SAPLINGS)) {
					saplings++;
				}
			}
			return new int[]{logs, saplings};
		});
	}

	private String lawLine() {
		return this.world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			SimSettlement s = town(server);
			LawService law = HearthServices.law();
			int stolen = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (law.isStolen(p.getInventory().getItem(i))) {
					stolen += p.getInventory().getItem(i).getCount();
				}
			}
			return "wanted " + law.wanted(p.getUUID(), s.id()) + ", bounty " + law.bounty(p.getUUID(), s.id()) + ", stolen items carried " + stolen
				+ ", posters " + law.wanted(s.id()).size();
		});
	}

	/** Opens a container the way a player's click does (through the use-block path the law watches). */
	private boolean open(BlockPos pos) {
		return this.world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			p.gameMode.useItemOn(p, server.overworld(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
			return p.containerMenu != p.inventoryMenu;
		});
	}

	/** Shift-clicks a container slot into the player's inventory. */
	private void take(int slot) {
		this.world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			if (p.containerMenu != p.inventoryMenu) {
				p.containerMenu.clicked(slot, 0, ContainerInput.QUICK_MOVE, p);
				p.containerMenu.broadcastChanges();
			}
		});
	}

	/** Stands the player two blocks in front of a block, looking at it. */
	private void stand(BlockPos target) {
		this.world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer p = player(server);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos q = target.relative(d, 2);
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos at = q.above(dy);
					if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getBlockState(at.below()).isSolid()) {
						Vec3 eye = Vec3.atBottomCenterOf(at).add(0, 1.62, 0);
						Vec3 c = Vec3.atCenterOf(target);
						float yaw = (float) Math.toDegrees(Math.atan2(-(c.x - eye.x), c.z - eye.z));
						float pitch = (float) -Math.toDegrees(Math.atan2(c.y - eye.y, Math.hypot(c.x - eye.x, c.z - eye.z)));
						p.getAbilities().flying = false;
						p.onUpdateAbilities();
						p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), yaw, pitch, true);
						return;
					}
				}
			}
			p.teleportTo(level, target.getX() + 0.5, target.getY() + 1, target.getZ() - 1.5, Set.of(), 0, 30, true);
		});
	}

	private @Nullable Integer talkTo(UUID resident) {
		for (int i = 0; i < 10; i++) {
			Integer id = this.world.getServer().computeOnServer(server -> {
				Folk f = NpcModule.embodiment().entityOf(resident);
				if (f == null || f.brain() == null) {
					return null;
				}
				ServerPlayer p = player(server);
				p.teleportTo(server.overworld(), f.getX(), f.getY(), f.getZ() - 2.5, Set.of(), 0.0F, 10.0F, true);
				return f.getId();
			});
			if (id == null) {
				BlockPos c = this.world.getServer().computeOnServer(server -> town(server).center());
				spawnNear(resident, c);
				settle(1_500);
				continue;
			}
			settle(1_200);
			this.world.getServer().runOnServer(server -> {
				var e = server.overworld().getEntity(id);
				if (e instanceof Folk f) {
					NpcModule.conversations().open(player(server), f);
				}
			});
			this.context.waitFor(mc -> mc.gui.screen() instanceof ConversationScreen, 100);
			settle(2_000);
			return id;
		}
		return null;
	}

	private String options(UUID resident) {
		return this.world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server);
			Resident r = s.resident(resident);
			StringBuilder b = new StringBuilder();
			for (DialogueProvider d : DialogueProvider.providers()) {
				List<DialogueProvider.Option> out = new ArrayList<>();
				d.collect(player(server), s, r, out);
				for (DialogueProvider.Option o : out) {
					b.append(o.id()).append(" \"").append(o.label().getString()).append("\"; ");
				}
			}
			return b.toString();
		});
	}

	private int surface(BlockPos at) {
		return this.world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		});
	}

	private void teleport(double x, double y, double z, float yaw, float pitch) {
		this.world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), x, y, z, Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
	}

	private void shoot(String name, double x, double y, double z, float yaw, float pitch) {
		teleport(x, y, z, yaw, pitch);
		settle(3_000);
		this.context.takeScreenshot("hearth_int_" + name);
	}

	private void settle(long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			this.context.waitTick();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void log(String line) {
		System.out.println("[hearth tour] " + line);
	}
}
