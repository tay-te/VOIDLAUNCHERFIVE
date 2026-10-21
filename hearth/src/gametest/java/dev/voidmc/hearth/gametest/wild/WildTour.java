package dev.voidmc.hearth.gametest.wild;

import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.gametest.Tours;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.wild.PostBook;
import dev.voidmc.hearth.sim.wild.PostSite;
import dev.voidmc.hearth.sim.wild.PostType;
import dev.voidmc.hearth.sim.wild.PostWorld;
import dev.voidmc.hearth.sim.wild.Wild;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Small places in the wild (docs/LIVING_WORLD_2.md §7.8; {@code -Dhearth.tour=wild}, or {@code HEARTH_LW_TOUR=wild}):
 * on the dev seed, the nearest post of every type round spawn (sited off the server thread over
 * {@code hearth.wild.rings} rings of regions), founded as its chunks load and photographed by day; the hospice in a
 * snowstorm with its bell; the toll keeper's stop (the conversation open on the gate); the posts' tick cost. Screenshots
 * are {@code hearth_wild_*}; every step logs {@code [hearth tour] wild:}.
 */
public class WildTour implements FabricClientGameTest {
	private static final int RINGS = Integer.getInteger("hearth.wild.rings", 3);

	static boolean wanted() {
		String env = System.getenv("HEARTH_LW_TOUR");
		if (env != null && (env.contains("wild") || env.contains("all"))) {
			return true;
		}
		String only = System.getProperty("hearth.tour");
		return only != null && !only.isBlank() && Tours.wanted("wild");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!wanted()) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(10);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(System.getProperty("hearth.wild.seed", "20261003"));
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			new Run(context, world).run();
		}
	}

	private static final class Run {
		final ClientGameTestContext context;
		final TestSingleplayerContext world;

		Run(ClientGameTestContext context, TestSingleplayerContext world) {
			this.context = context;
			this.world = world;
		}

		void run() {
			cmd("gamerule spawn_monsters false");
			cmd("gamerule advance_weather false");
			cmd("gamerule advance_time false");
			cmd("weather clear");
			cmd("time set 3000");
			settle(6_000);
			Map<PostType, PostSite> nearest = search();
			log("found " + nearest.size() + " of " + PostType.values().length + " types within " + RINGS + " rings: " + nearest.keySet());
			// -Dhearth.wild.only=toll_gate,hospice: those types alone
			String only = System.getProperty("hearth.wild.only", "");
			if (!only.isBlank()) {
				nearest.keySet().removeIf(t -> !java.util.Arrays.asList(only.split(",")).contains(t.getSerializedName()));
			}
			for (PostType t : PostType.values()) {
				PostSite s = nearest.get(t);
				if (s == null) {
					log(t.getSerializedName() + ": none sited within " + RINGS + " rings of spawn");
					continue;
				}
				try {
					visit(s);
				} catch (RuntimeException e) {
					log(t.getSerializedName() + " failed: " + e);
					e.printStackTrace();
				}
			}
			PostSite hospice = nearest.get(PostType.HOSPICE);
			if (hospice != null) {
				storm(hospice);
			}
			PostSite toll = nearest.get(PostType.TOLL_GATE);
			if (toll != null) {
				tollStop(toll);
			}
			stats();
		}

		void cmd(String c) {
			this.world.getServer().runCommand(c);
		}

		/** The nearest site of each type round spawn, worked out off the server thread. */
		Map<PostType, PostSite> search() {
			long t0 = System.currentTimeMillis();
			CompletableFuture<List<PostSite>> job = this.world.getServer().computeOnServer(server -> {
				SimWorld w = SimServer.world(server.overworld());
				PostWorld p = Wild.posts(w);
				BlockPos spawn = server.overworld().getRespawnData().pos();
				return CompletableFuture.supplyAsync(() -> {
					List<PostSite> out = new ArrayList<>();
					int rx0 = Atlas.region(spawn.getX());
					int rz0 = Atlas.region(spawn.getZ());
					for (int r = 0; r <= RINGS; r++) {
						for (int dx = -r; dx <= r; dx++) {
							for (int dz = -r; dz <= r; dz++) {
								if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
									out.addAll(p.region(rx0 + dx, rz0 + dz));
								}
							}
						}
					}
					out.sort(java.util.Comparator.comparingDouble(s -> s.center().distSqr(spawn)));
					return out;
				});
			});
			while (!job.isDone() && System.currentTimeMillis() - t0 < 600_000) {
				settle(1_000);
			}
			List<PostSite> all = job.getNow(List.of());
			log(String.format(Locale.ROOT, "%d posts sited in %d regions in %.1f s", all.size(), (2 * RINGS + 1) * (2 * RINGS + 1),
				(System.currentTimeMillis() - t0) / 1000.0));
			Map<PostType, PostSite> out = new EnumMap<>(PostType.class);
			for (PostSite s : all) {
				out.putIfAbsent(s.type(), s);
			}
			return out;
		}

		/** Flies to a post, waits for it to be founded and written, and photographs it from its front. */
		void visit(PostSite site) {
			String name = site.type().getSerializedName();
			log(name + ": " + site);
			// over it first, so its chunks load (and it is founded)
			teleport(site.x() + 0.5, site.y() + 40, site.z() + 0.5, 0, 90);
			SimSettlement s = waitFounded(site, 60_000);
			if (s == null) {
				log(name + ": not founded (" + this.world.getServer().computeOnServer(server -> {
					SimWorld w = SimServer.world(server.overworld());
					return w.data(PostBook.KEY).known(site.key()) ? "passed over" : "not yet";
				}) + ")");
				return;
			}
			waitWritten(s, 60_000);
			log(name + ": " + describe(s));
			Direction f = site.facing();
			// a nomad camp is shot on the ground it stands on
			int[] ground = this.world.getServer().computeOnServer(server -> {
				var d = dev.voidmc.hearth.sim.wild.PostData.of(s);
				int g = d == null ? 0 : d.ground;
				return new int[]{site.groundX(g), site.groundZ(g)};
			});
			int gx = site.type() == PostType.NOMAD_CAMP ? ground[0] : site.x();
			int gz = site.type() == PostType.NOMAD_CAMP ? ground[1] : site.z();
			int out = site.type() == PostType.NOMAD_CAMP ? 30 : Math.max(22, site.type().radius + 8);
			Direction side = f.getClockWise();
			double x = gx + f.getStepX() * out + side.getStepX() * 8 + 0.5;
			double z = gz + f.getStepZ() * out + side.getStepZ() * 8 + 0.5;
			if (site.type() == PostType.TOLL_GATE) {
				// down the road, looking along it at the gate
				x = site.x() + f.getStepX() * 22 + side.getStepX() * 6 + 0.5;
				z = site.z() + f.getStepZ() * 22 + side.getStepZ() * 6 + 0.5;
			}
			// above the land in front, and never below the post's own floor (a pass falls away from its saddle)
			double y = site.type() == PostType.TOLL_GATE ? surface(BlockPos.containing(x, 0, z)) + 5
				: Math.max(surface(BlockPos.containing(x, 0, z)) + 10, site.y() + 16);
			look(name, x, y, z, gx + 0.5, site.y() + 3, gz + 0.5);
		}

		@Nullable SimSettlement waitFounded(PostSite site, long ms) {
			long until = System.currentTimeMillis() + ms;
			while (System.currentTimeMillis() < until) {
				SimSettlement s = this.world.getServer().computeOnServer(server -> {
					SimWorld w = SimServer.world(server.overworld());
					PostBook book = w.data(PostBook.KEY);
					long id = book.settlement(site.key());
					if (id == 0 && !book.known(site.key())) {
						Wild.posts(w).found(site);
					}
					return id == 0 ? null : w.settlement(id);
				});
				if (s != null) {
					return s;
				}
				if (this.world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).data(PostBook.KEY).known(site.key()))) {
					return null;
				}
				settle(1_000);
			}
			return null;
		}

		/** Waits until every plot's middle chunk holds what the books say. */
		void waitWritten(SimSettlement s, long ms) {
			long until = System.currentTimeMillis() + ms;
			int left = -1;
			while (System.currentTimeMillis() < until) {
				left = this.world.getServer().computeOnServer(server -> {
					SimWorld w = SimServer.world(server.overworld());
					int n = 0;
					for (SimPlot p : s.plots()) {
						if (p.state() == PlotState.DEMOLISHING) {
							continue;
						}
						var r = p.reach();
						int cx = (r.minX() + r.maxX()) >> 5;
						int cz = (r.minZ() + r.maxZ()) >> 5;
						if (!w.written(s, p.id(), cx, cz)) {
							n++;
						}
					}
					return n;
				});
				if (left == 0) {
					break;
				}
				settle(1_000);
			}
			settle(3_000);
			log(s.name() + ": " + (left == 0 ? "written" : left + " plots not yet written"));
		}

		String describe(SimSettlement s) {
			return this.world.getServer().computeOnServer(server -> {
				StringBuilder b = new StringBuilder(s.name()).append(" (").append(s.culture().getSerializedName()).append(", ").append(s.population())
					.append(" souls in ").append(s.beds()).append(" beds): ");
				Map<String, Integer> jobs = new java.util.TreeMap<>();
				for (var r : s.residents()) {
					jobs.merge(r.job.getSerializedName(), 1, Integer::sum);
				}
				b.append(jobs).append("; plots");
				for (SimPlot p : s.plots()) {
					b.append(' ').append(p.spec().archetype().getPath()).append('=').append(p.state().getSerializedName());
				}
				return b.toString();
			});
		}

		/** The hospice in a snowstorm, its bell ringing. */
		void storm(PostSite site) {
			cmd("weather thunder");
			cmd("time set 9000");
			Direction f = site.facing();
			double x = site.x() + f.getStepX() * (site.type().radius + 8) + 0.5;
			double z = site.z() + f.getStepZ() * (site.type().radius + 8) + 0.5;
			double y = Math.max(surface(BlockPos.containing(x, 0, z)) + 4, site.y() + 12);
			teleport(x, y, z, 0, 0);
			settle(12_000);
			look("hospice_storm", x, y, z, site.x() + 0.5, site.y() + 6, site.z() + 0.5);
			String bell = this.world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				SimWorld w = SimServer.world(level);
				SimSettlement s = w.settlement(w.data(PostBook.KEY).settlement(site.key()));
				return s == null ? "no hospice" : "raining " + level.isRaining() + ", embodied " + s.embodied() + ", bell at "
					+ dev.voidmc.hearth.sim.wild.WildFolk.bellOf(s);
			});
			log("hospice storm: " + bell);
			cmd("weather clear");
			cmd("time set 3000");
		}

		/** On foot on the road toward the gate, unpaid: the keeper steps out and opens the conversation. */
		void tollStop(PostSite site) {
			Direction f = site.facing();
			int far = 20;
			double x = site.x() + f.getStepX() * far + 0.5;
			double z = site.z() + f.getStepZ() * far + 0.5;
			cmd("gamemode survival @a");
			for (int d = far; d >= 7; d -= 2) {
				double px = site.x() + f.getStepX() * d + 0.5;
				double pz = site.z() + f.getStepZ() * d + 0.5;
				double py = surface(BlockPos.containing(px, 0, pz)) + 0.1;
				float yaw = (float) (Mth.atan2(site.z() + 0.5 - pz, site.x() + 0.5 - px) * Mth.RAD_TO_DEG) - 90.0F;
				teleport(px, py, pz, yaw, 8);
				settle(1_500);
				if (talking()) {
					break;
				}
			}
			// the keeper walks out from wherever he is to the passage
			for (int k = 0; k < 20 && !talking(); k++) {
				settle(1_000);
			}
			settle(1_500);
			boolean open = talking();
			this.context.takeScreenshot("hearth_wild_toll_stop");
			log("toll stop: conversation " + (open ? "open" : "not open") + " near " + BlockPos.containing(x, 0, z).toShortString() + "; "
				+ this.world.getServer().computeOnServer(server -> {
					SimWorld w = SimServer.world(server.overworld());
					SimSettlement s = w.settlement(w.data(PostBook.KEY).settlement(site.key()));
					if (s == null) {
						return "no post";
					}
					StringBuilder b = new StringBuilder("stops begun " + dev.voidmc.hearth.sim.wild.WildFolk.tollStops + ", embodied " + s.embodied());
					ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
					for (var r : s.residents()) {
						var fh = dev.voidmc.hearth.api.HearthServices.npc().folk(r.id);
						b.append("; ").append(r.job.getSerializedName()).append(fh == null ? " not embodied" : " at " + fh.entity().blockPosition().toShortString()
							+ (fh.awake() ? " awake" : " asleep") + (r.job == dev.voidmc.hearth.api.JobType.TOLLKEEPER ? " wants " + dev.voidmc.hearth.sim.wild.WildFolk.wantsToll(fh) : "") + String.format(Locale.ROOT, " %.1f from the player", Math.sqrt(fh.entity().distanceToSqr(p))));
					}
					b.append("; player at ").append(p.blockPosition().toShortString()).append(p.isCreative() ? " creative" : " survival");
					return b.toString();
				}));
			this.context.setScreen(() -> null);
			cmd("gamemode creative @a");
		}

		boolean talking() {
			return this.world.getServer().computeOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				return dev.voidmc.hearth.npc.NpcModule.conversations().talkingTo(p) != null;
			});
		}

		void stats() {
			String s = this.world.getServer().computeOnServer(server -> {
				StringBuilder b = new StringBuilder();
				SimWorld w = SimServer.world(server.overworld());
				b.append(Wild.posts(w).describe()).append('\n');
				for (String line : LivingWorldStats.lines()) {
					b.append(line).append('\n');
				}
				b.append(String.format(Locale.ROOT, "server tick %.2f ms avg", server.getAverageTickTimeNanos() / 1e6));
				return b.toString();
			});
			for (String line : s.split("\n")) {
				log("stats: " + line);
			}
		}

		int surface(BlockPos at) {
			return this.world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				level.getChunk(at.getX() >> 4, at.getZ() >> 4);
				return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			});
		}

		void teleport(double x, double y, double z, float yaw, float pitch) {
			this.world.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				p.getAbilities().flying = p.isCreative();
				p.onUpdateAbilities();
				p.teleportTo(server.overworld(), x, y, z, Set.of(), Mth.wrapDegrees(yaw), pitch, true);
			});
		}

		void look(String name, double x, double y, double z, double tx, double ty, double tz) {
			double dx = tx - x;
			double dz = tz - z;
			double dy = ty - y;
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float) (-Mth.atan2(dy, Math.hypot(dx, dz)) * Mth.RAD_TO_DEG);
			teleport(x, y, z, yaw, pitch);
			settle(4_000);
			this.context.takeScreenshot("hearth_wild_" + name);
			log("shot " + name);
		}

		void settle(long ms) {
			long until = System.currentTimeMillis() + ms;
			while (System.currentTimeMillis() < until) {
				this.context.waitTick();
			}
		}

		static void log(String line) {
			System.out.println("[hearth tour] wild: " + line);
		}
	}
}
