package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.client.economy.MarketScreen;
import dev.voidmc.hearth.client.npc.ConversationScreen;
import dev.voidmc.hearth.client.quest.JournalScreen;
import dev.voidmc.hearth.client.quest.NoticeBoardScreen;
import dev.voidmc.hearth.economy.market.Market;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.embody.TownState;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.quest.QuestRegistry;
import dev.voidmc.hearth.quest.block.NoticeBoardBlock;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * End to end in the client: a settlement founded and grown by the real sim and planner (stand-in buildings while the
 * build module is incomplete), walked into by a player. Photographs the arrival banner, the town embodied by day and
 * night, folk and their skins, a builder working a plot (progress counter before and after), the conversation with a
 * merchant and the market trading against the settlement (stockpile and treasury logged), a resident's quest offers, the
 * notice board, and a supply quest for a real stalled build handed in and the build resuming. Screenshots land in
 * {@code build/run/clientGameTest/screenshots} as {@code hearth_int_*}; every check is printed as {@code [hearth tour]}.
 *
 * <p>Entrypoint {@code dev.voidmc.hearth.gametest.IntegrationTour}; {@code -Dhearth.tour=integration} runs only this tour.
 */
public class IntegrationTour implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hearth.tour.days", 60);

	private record Town(long id, BlockPos center, BoundingBox bounds) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("integration")) {
			return;
		}
		context.runOnClient(mc -> mc.options.renderDistance().set(10));
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261003");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			// time runs: the sim's clock follows the day (with it stopped, settlements stand still)
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("gamerule spawn_monsters false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set 3000");
			settle(context, 3_000);
			Town town = world.getServer().computeOnServer(IntegrationTour::foundTown);
			if (town == null) {
				log("no site for a settlement near spawn");
				return;
			}
			log("town #" + town.id + " at " + town.center.toShortString() + " bounds " + town.bounds);
			// build it into the world before anyone arrives: its chunks generated, the applier through what was recorded
			world.getServer().runOnServer(server -> forceChunks(server, town, true));
			waitApplier(context, world);
			world.getServer().runOnServer(server -> forceChunks(server, town, false));

			arrive(context, world, town);
			embodied(context, world, town);
			builder(context, world, town, "builder");
			questOffer(context, world, town);
			BlockPos board = noticeBoard(context, world, town);
			supply(context, world, town, board);
			builder(context, world, town, "builder_after_supply");
			night(context, world, town);
			merchant(context, world, town);
			builder(context, world, town, "builder_next_day");
			if (!Boolean.getBoolean("hearth.tour.nolife")) {
				new LivingLegs(context, world, town.id).run();
			}
			log("done: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = town(server, t(town));
				return s.name() + " " + s.population() + " souls, plots " + s.plotCounts() + ", stores " + s.stockpile() + ", treasury " + s.treasury()
					+ "; clock at " + s.simTick() + " of " + SimServer.world(server.overworld()).simNow(s) + ", no growth until " + s.clockState().noGrowthUntil();
			}));
		}
	}

	// ---------------------------------------------------------------- the town

	/** A live camp on gentle dry ground near spawn, grown {@link #DAYS} days by the real sim. */
	private static @Nullable Town foundTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		if (w == null) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos best = null;
		int bestRelief = Integer.MAX_VALUE;
		for (int r = 96; r <= 720 && (best == null || bestRelief > 3); r += 48) {
			for (int k = 0; k < 12; k++) {
				double a = k * Math.PI / 6;
				BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
				if (w.whyNotFound(at, SimWorld.FOUND_SPACING, true) != null) {
					continue;
				}
				int g = w.terrain().groundY(at.getX(), at.getZ());
				if (g <= w.terrain().seaLevel()) {
					continue;
				}
				int lo = g;
				int hi = g;
				for (int dx = -24; dx <= 24; dx += 12) {
					for (int dz = -24; dz <= 24; dz += 12) {
						int h = w.terrain().groundY(at.getX() + dx, at.getZ() + dz);
						lo = Math.min(lo, h);
						hi = Math.max(hi, h);
					}
				}
				if (hi - lo < bestRelief) {
					bestRelief = hi - lo;
					best = at;
				}
			}
		}
		if (best == null) {
			return null;
		}
		SimSettlement s = w.found(best, null, (String) null);
		long nanos = w.simulate(s, DAYS);
		log(String.format(Locale.ROOT, "founded %s (%s) at %s (relief %d), simulated %d days in %.0f ms: %d souls, %d beds, %s, plots %s, stores %s, treasury %d",
			s.name(), s.culture().getSerializedName(), s.center().toShortString(), bestRelief, DAYS, nanos / 1e6, s.population(), s.beds(),
			s.tier().getSerializedName(), s.plotCounts(), s.stockpile(), s.treasury()));
		return new Town(s.id(), s.center(), s.bounds());
	}

	private static Town t(Town t) {
		return t;
	}

	private static SimSettlement town(MinecraftServer server, Town t) {
		return SimServer.world(server.overworld()).settlement(t.id);
	}

	private static void forceChunks(MinecraftServer server, Town t, boolean force) {
		ServerLevel level = server.overworld();
		BoundingBox b = town(server, t).bounds();
		int r = 4;
		for (int cx = (t.center.getX() >> 4) - r; cx <= (t.center.getX() >> 4) + r; cx++) {
			for (int cz = (t.center.getZ() >> 4) - r; cz <= (t.center.getZ() >> 4) + r; cz++) {
				if (cx >= (b.minX() >> 4) - 1 && cx <= (b.maxX() >> 4) + 1 && cz >= (b.minZ() >> 4) - 1 && cz <= (b.maxZ() >> 4) + 1) {
					level.setChunkForced(cx, cz, force);
				}
			}
		}
	}

	private static void waitApplier(ClientGameTestContext context, TestSingleplayerContext world) {
		for (int i = 0; i < 120; i++) {
			settle(context, 500);
			int pending = world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).applier().pending());
			if (pending == 0 && i > 6) {
				break;
			}
		}
		log("applier: " + world.getServer().computeOnServer(server -> {
			var a = SimServer.world(server.overworld()).applier();
			return a.blocksPlaced.sum() + " blocks written, " + a.conflicts.sum() + " refused, " + a.blocksCleared.sum() + " cleared for rebuilds";
		}));
	}

	// ---------------------------------------------------------------- the visit

	private void arrive(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		// wait outside, then walk in
		BlockPos away = t.center.offset(0, 0, -(t.center.getZ() - t.bounds.minZ()) - 60);
		teleport(world, away.getX(), surface(world, away) + 20, away.getZ(), 0, 20);
		settle(context, 4_000);
		BlockPos edge = t.center.offset(0, 0, -24);
		teleport(world, edge.getX() + 0.5, surface(world, edge) + 8, edge.getZ() + 0.5, 0, 18);
		settle(context, 2_500);
		context.takeScreenshot("hearth_int_arrival");
		log("arrival: banner screenshot taken; embodied now " + world.getServer().computeOnServer(server -> town(server, t).embodied()));
	}

	private void embodied(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		settle(context, 12_000);
		log("embodied (day): " + folkReport(world, t));
		BlockPos c = t.center;
		shoot(context, world, "town_day", c.getX() - 28, surface(world, c) + 26, c.getZ() - 28, -45, 32);
		shoot(context, world, "town_day_2", c.getX() + 30, surface(world, c) + 18, c.getZ() + 10, 110, 22);
		// a close look at someone out of doors, from a few steps away in the open
		double[] view = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			for (Folk folk : NpcModule.embodiment().entities()) {
				if (folk.brain() == null || folk.brain().settlement.id() != t.id || folk.isSleeping() || !level.canSeeSky(folk.blockPosition())) {
					continue;
				}
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos cam = folk.blockPosition().relative(d, 3);
					if (level.getBlockState(cam).isAir() && level.getBlockState(cam.above()).isAir() && level.canSeeSky(cam)) {
						float yaw = d.getOpposite().toYRot();
						return new double[]{cam.getX() + 0.5, folk.getY(), cam.getZ() + 0.5, yaw};
					}
				}
			}
			return null;
		});
		if (view != null) {
			shoot(context, world, "folk_close", view[0], view[1], view[2], (float) view[3], 8);
		}
	}

	private void builder(ClientGameTestContext context, TestSingleplayerContext world, Town t, String name) {
		world.getServer().runOnServer(server -> {
			NpcConfig c = NpcModule.config();
			// builders a little quicker than the default 30 ticks a step, so a minute shows real progress
			NpcModule.setConfig(new NpcConfig(c.embodiment(), new NpcConfig.Ai(c.ai().thinkNear(), c.ai().thinkMid(), c.ai().thinkFar(),
				c.ai().nearDistance(), c.ai().midDistance(), c.ai().repathCooldown(), 10, true), c.threats(), c.talk()));
		});
		settle(context, 8_000);
		// the building with the most still to do (walls and a roof to raise: scaffolding), its stage started if the goods
		// are in store
		int[] plot = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			SimPlot best = null;
			int bestLeft = 0;
			for (SimPlot p : s.plots()) {
				if (!p.isActive() || p.spec().archetype().getPath().startsWith("infra/") || p.spec().archetype().getPath().startsWith("farm/")) {
					continue;
				}
				var f = s.facts(p);
				int left = -p.step();
				for (int st = p.stage(); st < f.stageCount(); st++) {
					left += f.stepCount()[st];
				}
				// one the builders can get on with now comes first
				if (!p.waitingForGoods()) {
					left += 100_000;
				}
				if (left > bestLeft) {
					best = p;
					bestLeft = left;
				}
			}
			if (best == null) {
				return null;
			}
			boolean started = s.tryStartStage(best.id());
			log("builder: chose plot #" + best.id() + " (" + best.spec().archetype().getPath() + ", " + bestLeft % 100_000 + " steps to go), stage started "
				+ started);
			return new int[]{best.id(), best.stage(), best.step(), best.spec().origin().getX() + best.spec().sizeX() / 2, best.spec().floorY(),
				best.spec().origin().getZ() + best.spec().sizeZ() / 2};
		});
		if (plot == null) {
			log("builder: no plot under construction with goods in hand; plots " + world.getServer().computeOnServer(server -> town(server, t).plotCounts()));
			return;
		}
		log("builder: plot #" + plot[0] + " at " + plot[3] + "," + plot[4] + "," + plot[5] + " starts at " + plot[1] + ":" + plot[2] + "; "
			+ builderReport(world, t, plot[0]));
		for (int i = 0; i < 3; i++) {
			settle(context, 15_000);
			shoot(context, world, name + "_" + i, plot[3] - 9.5, plot[4] + 7, plot[5] - 9.5, -45, 25);
			log(name + " " + i + ": " + builderReport(world, t, plot[0]));
		}
	}

	private void night(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		world.getServer().runCommand("time add 15000");
		settle(context, 30_000);
		log("night: " + folkReport(world, t));
		BlockPos c = t.center;
		shoot(context, world, "town_night", c.getX() - 22, surface(world, c) + 14, c.getZ() - 22, -45, 28);
		// morning of the next day: the sim's day turns over
		world.getServer().runCommand("time add 9000");
		settle(context, 20_000);
		log("next morning: " + folkReport(world, t));
	}

	private void merchant(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		// a merchant: one the town has, or (a young town has no stall yet) an adult made one for the visit
		UUID merchant = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			Resident pick = null;
			for (Resident r : s.residents()) {
				if (r.job == JobType.MERCHANT) {
					pick = r;
				}
			}
			if (pick == null) {
				for (Resident r : s.residents()) {
					if (r.age == AgeStage.ADULT && r.job != JobType.BUILDER && NpcModule.embodiment().entityOf(r.id) != null) {
						pick = r;
						break;
					}
				}
				if (pick != null) {
					log("merchant: " + pick.fullName() + " (" + pick.job.getSerializedName() + ") keeps the stall for this visit");
					pick.job = JobType.MERCHANT;
				}
			}
			return pick == null ? null : pick.id;
		});
		Integer entity = merchant == null ? null : talkTo(context, world, merchant);
		if (entity == null) {
			log("merchant: nobody to talk to");
			return;
		}
		context.takeScreenshot("hearth_int_talk_merchant");
		log("merchant options: " + options(world, t, merchant));
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
			p.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
			p.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		});
		String before = economy(world, t);
		world.getServer().runOnServer(server -> NpcModule.conversations().choose(player(server), entity, "economy:trade"));
		context.waitFor(mc -> mc.gui.screen() instanceof MarketScreen, 100);
		settle(context, 1_500);
		context.takeScreenshot("hearth_int_market");
		// sell: the first row of what the town buys, a whole stack's worth
		context.runOnClient(mc -> {
			if (mc.player.containerMenu instanceof MarketMenu menu) {
				ClientPlayNetworking.send(new EconomyActionPayload(menu.containerId, EconomyActionPayload.TRADE, Market.BUY_ID_BASE, 1));
			}
		});
		settle(context, 1_500);
		context.runOnClient(mc -> {
			if (mc.player.containerMenu instanceof MarketMenu menu) {
				ClientPlayNetworking.send(new EconomyActionPayload(menu.containerId, EconomyActionPayload.TRADE, Market.BUY_ID_BASE, 1));
			}
		});
		settle(context, 1_500);
		context.takeScreenshot("hearth_int_market_after");
		log("market: before " + before + "; after " + economy(world, t));
		context.runOnClient(mc -> mc.player.closeContainer());
		context.setScreen(() -> null);
		settle(context, 1_000);
	}

	private void questOffer(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		UUID giver = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			ServerPlayer p = player(server);
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() == null || f.brain().settlement.id() != t.id || f.isSleeping()) {
					continue;
				}
				for (DialogueProvider d : DialogueProvider.providers()) {
					if (!d.getClass().getName().contains("quest")) {
						continue;
					}
					List<DialogueProvider.Option> out = new ArrayList<>();
					d.collect(p, s, f.brain().resident, out);
					for (DialogueProvider.Option o : out) {
						if (o.order() >= 220 && o.order() < 250) {
							return f.brain().resident.id;
						}
					}
				}
			}
			return null;
		});
		if (giver == null) {
			log("quests: no embodied resident has an offer of their own; board: " + world.getServer().computeOnServer(server ->
				QuestStore.board(town(server, t)).offers.size() + " offers"));
			return;
		}
		Integer entity = talkTo(context, world, giver);
		if (entity != null) {
			context.takeScreenshot("hearth_int_talk_quest");
			log("quest giver options: " + options(world, t, giver));
			context.setScreen(() -> null);
			world.getServer().runOnServer(server -> NpcModule.conversations().end(player(server), entity));
		}
	}

	private @Nullable BlockPos noticeBoard(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		BlockPos board = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			ServerLevel level = server.overworld();
			List<Poi> boards = s.pois(PoiType.NOTICE_BOARD);
			for (Poi poi : boards) {
				if (level.getBlockState(poi.pos()).getBlock() instanceof NoticeBoardBlock) {
					log("board: the plaza's board at " + poi.pos().toShortString());
					return poi.pos();
				}
			}
			// no plaza yet (or it did not place one): a board by the well, as a player would set one up
			BlockPos at = s.center().relative(Direction.SOUTH, 4);
			at = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
			level.setBlock(at, QuestRegistry.NOTICE_BOARD.defaultBlockState().setValue(NoticeBoardBlock.FACING, Direction.NORTH), 3);
			log("board: none planned yet (" + boards.size() + " board POIs); placed one at " + at.toShortString());
			return at;
		});
		String resolves = world.getServer().computeOnServer(server -> {
			var s = QuestWorlds.at(server.overworld(), board);
			return s == null ? "nobody" : s.name() + (s.id() == t.id ? " (the town)" : " (another!)");
		});
		log("board at " + board.toShortString() + " resolves to " + resolves);
		Direction facing = world.getServer().computeOnServer(server -> {
			var state = server.overworld().getBlockState(board);
			return state.hasProperty(NoticeBoardBlock.FACING) ? state.getValue(NoticeBoardBlock.FACING) : Direction.NORTH;
		});
		BlockPos front = board.relative(facing, 3);
		shoot(context, world, "board_block", front.getX() + 0.5, board.getY() + 0.6, front.getZ() + 0.5, facing.getOpposite().toYRot(), 12);
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			NoticeBoardBlock.open(p, server.overworld(), town(server, t), board);
		});
		context.waitFor(mc -> mc.gui.screen() instanceof NoticeBoardScreen, 100);
		settle(context, 1_500);
		context.takeScreenshot("hearth_int_board");
		log("board offers: " + world.getServer().computeOnServer(server -> {
			StringBuilder b = new StringBuilder();
			for (QuestInstance q : QuestStore.board(town(server, t)).offers) {
				b.append(q.type.name().toLowerCase(Locale.ROOT)).append(" '").append(q.text.title()).append("'; ");
			}
			return b.toString();
		}));
		context.setScreen(() -> null);
		return board;
	}

	/**
	 * A real stall: a plot already waiting for goods the town has posted a supply quest for, or else a planned plot
	 * whose first stage's goods the stores are emptied of; take the quest, bring the goods, hand them in.
	 */
	private void supply(ClientGameTestContext context, TestSingleplayerContext world, Town t, @Nullable BlockPos board) {
		Integer plotId = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			for (QuestInstance q : QuestStore.board(s).offers) {
				if (q.type != QuestType.SUPPLY) {
					continue;
				}
				for (Objective o : q.objectives) {
					SimPlot p = s.plot(o.data.getIntOr(Objective.Keys.PLOT, -1));
					if (p != null && p.isActive()) {
						log("supply: plot #" + p.id() + " (" + p.spec().archetype().getPath() + ") stalled for " + q.objectives.getFirst().target
							+ (p.waitingForGoods() ? ", still waiting for " + p.stalledOn() : ", no longer waiting (traders brought some)"));
						return p.id();
					}
				}
			}
			for (SimPlot p : s.plots()) {
				if (p.state() == PlotState.PLANNED) {
					int[] need = s.facts(p).goods()[0];
					boolean any = false;
					for (int g = 0; g < need.length; g++) {
						if (need[g] > 0) {
							any = true;
							s.stockpile().take(Goods.VALUES[g], s.stockpile().get(Goods.VALUES[g]));
						}
					}
					if (any) {
						boolean started = s.tryStartStage(p.id());
						log("supply: emptied the stores of what plot #" + p.id() + " (" + p.spec().archetype().getPath() + ") needs: started " + started
							+ ", waiting for goods " + p.waitingForGoods() + " (" + p.stalledOn() + ")");
						return p.id();
					}
				}
			}
			return null;
		});
		if (plotId == null) {
			log("supply: no planned building to stall");
			return;
		}
		settle(context, 1_000);
		UUID quest = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			for (QuestInstance q : List.copyOf(QuestStore.board(s).offers)) {
				if (q.type == QuestType.SUPPLY && q.objectives.stream().anyMatch(o -> o.data.getIntOr(Objective.Keys.PLOT, -1) == plotId)) {
					log("supply quest: '" + q.text.title() + "' " + q.objectives.getFirst().count + " x " + q.objectives.getFirst().target + " (waypoint "
						+ q.turnInPos.toShortString() + ")");
					return QuestManager.accept(player(server), s, q.id) ? q.id : null;
				}
			}
			return null;
		});
		if (quest == null) {
			log("supply: no supply quest was posted for plot #" + plotId);
			return;
		}
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			for (QuestInstance q : QuestStore.get(p).active) {
				if (q.id.equals(quest)) {
					QuestManager.track(p, q.id);
					log("accepted; journal waypoint " + q.turnInPos.toShortString() + (board != null && q.turnInPos.equals(board) ? " (the board)" : ""));
					for (Objective o : q.objectives) {
						var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(o.target));
						int count = o.count;
						while (count > 0) {
							int n = Math.min(count, item.getDefaultMaxStackSize());
							p.getInventory().add(new ItemStack(item, n));
							count -= n;
						}
					}
				}
			}
		});
		if (board != null) {
			teleport(world, board.getX() + 0.5, board.getY(), board.getZ() - 2.5, 0, 10);
		}
		settle(context, 2_000);
		context.takeScreenshot("hearth_int_supply_hud");
		context.setScreen(JournalScreen::new);
		settle(context, 1_500);
		context.takeScreenshot("hearth_int_journal");
		context.setScreen(() -> null);
		String before = world.getServer().computeOnServer(server -> plotLine(town(server, t), plotId));
		int handed = world.getServer().computeOnServer(server -> QuestManager.handIn(player(server), town(server, t), null));
		settle(context, 2_000);
		context.takeScreenshot("hearth_int_supply_done");
		log("handed in " + handed + "; plot before " + before + "; after " + world.getServer().computeOnServer(server -> plotLine(town(server, t), plotId)));
		settle(context, 20_000);
		log("plot 20 s later: " + world.getServer().computeOnServer(server -> plotLine(town(server, t), plotId)));
	}

	// ---------------------------------------------------------------- helpers

	private static String plotLine(SimSettlement s, int id) {
		SimPlot p = s.plot(id);
		return p == null ? "gone" : p.state().getSerializedName() + " " + Cursor.toString(p.cursor()) + (p.waitingForGoods() ? " waiting for " + p.stalledOn() : "")
			+ ", stores " + s.stockpile();
	}

	private static String economy(TestSingleplayerContext world, Town t) {
		return world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			return "stone " + s.stockpile().get(Goods.STONE) + ", wood " + s.stockpile().get(Goods.WOOD) + ", treasury " + s.treasury();
		});
	}

	private static String options(TestSingleplayerContext world, Town t, UUID resident) {
		return world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
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

	/** Stands the player in front of a resident's entity and opens the conversation; the folk's entity id. */
	private static @Nullable Integer talkTo(ClientGameTestContext context, TestSingleplayerContext world, UUID resident) {
		for (int i = 0; i < 20; i++) {
			Integer id = world.getServer().computeOnServer(server -> {
				Folk f = NpcModule.embodiment().entityOf(resident);
				if (f == null || f.brain() == null) {
					return null;
				}
				ServerPlayer p = player(server);
				p.teleportTo(server.overworld(), f.getX(), f.getY(), f.getZ() - 2.5, java.util.Set.of(), 0.0F, 10.0F, true);
				return f.getId();
			});
			if (id != null) {
				settle(context, 1_500);
				world.getServer().runOnServer(server -> {
					var e = server.overworld().getEntity(id);
					if (e instanceof Folk f) {
						NpcModule.conversations().open(player(server), f);
					}
				});
				context.waitFor(mc -> mc.gui.screen() instanceof ConversationScreen, 100);
				settle(context, 3_000);
				return id;
			}
			settle(context, 1_000);
		}
		return null;
	}

	private static String folkReport(TestSingleplayerContext world, Town t) {
		return world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			int n = 0;
			int asleep = 0;
			StringBuilder b = new StringBuilder();
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() == null || f.brain().settlement.id() != t.id) {
					continue;
				}
				n++;
				asleep += f.isSleeping() ? 1 : 0;
				if (n <= 12) {
					b.append(System.lineSeparator()).append("    ").append(f.folkName()).append(" (").append(f.brain().resident.job.getSerializedName())
						.append("): ").append(f.brain().describe()).append(f.isSleeping() ? " [asleep]" : "").append(" at ").append(f.blockPosition().toShortString());
				}
			}
			return String.format(Locale.ROOT, "%s embodied %s, %d of %d residents as folk, %d asleep%s", s.name(), s.embodied(), n, s.population(), asleep, b);
		});
	}

	private static String builderReport(TestSingleplayerContext world, Town t, int plotId) {
		return world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			SimPlot p = s.plot(plotId);
			if (p == null) {
				return "plot gone (finished and replaced?)";
			}
			int scaffold = 0;
			ServerLevel level = server.overworld();
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			var r = p.reach();
			for (int x = r.minX() - 2; x <= r.maxX() + 2; x++) {
				for (int z = r.minZ() - 2; z <= r.maxZ() + 2; z++) {
					for (int y = p.spec().floorY(); y <= p.spec().floorY() + p.spec().maxHeight() + 2; y++) {
						if (level.getBlockState(m.set(x, y, z)).is(Blocks.SCAFFOLDING)) {
							scaffold++;
						}
					}
				}
			}
			StringBuilder who = new StringBuilder();
			int others = 0;
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() != null && f.brain().settlement.id() == t.id && f.brain().resident.job == JobType.BUILDER) {
					String doing = f.brain().describe();
					if (doing.contains("(plot " + plotId + ")")) {
						who.append(f.folkName()).append(": ").append(doing).append("; ");
					} else {
						others++;
						if (others == 1) {
							who.append("[others e.g. ").append(doing).append("] ");
						}
					}
				}
			}
			who.append(others).append(" builders elsewhere");
			long since = server.overworld().getGameTime() - p.lastEmbodiedAdvance();
			return p.state().getSerializedName() + " at " + Cursor.toString(p.cursor()) + (p.waitingForGoods() ? " (waiting for goods)" : "")
				+ (p.lastEmbodiedAdvance() == Long.MIN_VALUE ? ", never placed by a builder" : ", a builder placed a step " + since + " ticks ago")
				+ ", " + scaffold + " scaffolding blocks round it; builders: " + who;
		});
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static int surface(TestSingleplayerContext world, BlockPos at) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		});
	}

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, float yaw,
		float pitch) {
		teleport(world, x, y, z, yaw, pitch);
		settle(context, 4_000);
		context.takeScreenshot("hearth_int_" + name);
	}

	private static void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void log(String line) {
		System.out.println("[hearth tour] " + line);
	}
}
