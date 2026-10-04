package dev.voidmc.hearth.client.standing;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService.Deed;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.standing.StandingEngine;
import dev.voidmc.hearth.standing.StandingModule;
import dev.voidmc.hearth.standing.net.StandingNetwork;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A scripted look at the standing module in a singleplayer world with live settlements, for screenshots without anyone
 * at the keyboard. Off unless {@code -Dhearth.dev.standing.tour=true}; start the client straight into a world
 * ({@code --quickPlaySingleplayer <world>}). It makes the player a friend of the nearest town with some fame, walks them
 * in (arrival banner and standing line), opens the reputation page, talks to a folk (the standing options with their
 * odds and the screen's standing line), flatters them, and writes {@code run/screenshots/hearth_standing_*.png}, then
 * quits.
 */
final class StandingClientTour {
	private static int ticks;
	private static @Nullable Settlement town;
	private static int folkId = -1;
	private static int waiting;

	private StandingClientTour() {
	}

	static void install() {
		if (!Boolean.getBoolean("hearth.dev.standing.tour")) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(StandingClientTour::tick);
	}

	private static void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (mc.player == null || mc.level == null || server == null) {
			waiting++;
			if (waiting % 200 == 0) {
				Hearth.LOG.info("[standing tour] waiting for a world; screen: {}", mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName());
			}
			return;
		}
		ticks++;
		switch (ticks) {
			case 60 -> server.execute(() -> setUp(server, mc));
			case 140 -> {
				// The player may have spawned in the town: forget that greeting so walking in greets again.
				ClientStanding.reset();
				server.execute(() -> walkIn(server, mc));
			}
			case 200 -> shot(mc, "arrival");
			case 300 -> server.execute(() -> {
				ServerPlayer p = player(server, mc);
				if (p != null) {
					StandingNetwork.sendPage(p);
				}
			});
			case 330 -> shot(mc, "page");
			case 340 -> mc.gui.setScreen(null);
			case 460 -> server.execute(() -> talk(server, mc));
			case 500 -> shot(mc, "conversation");
			case 510 -> {
				if (folkId >= 0) {
					ClientPlayNetworking.send(new TalkPayloads.Choose(folkId, "standing:flatter"));
				}
			}
			case 540 -> shot(mc, "flattered");
			case 550 -> {
				if (folkId >= 0) {
					ClientPlayNetworking.send(new TalkPayloads.Choose(folkId, "standing:reputation"));
				}
			}
			case 580 -> shot(mc, "reputation_asked");
			case 600 -> mc.stop();
			default -> {
			}
		}
	}

	private static @Nullable ServerPlayer player(MinecraftServer server, Minecraft mc) {
		return mc.player == null ? null : server.getPlayerList().getPlayer(mc.player.getUUID());
	}

	private static void setUp(MinecraftServer server, Minecraft mc) {
		ServerPlayer p = player(server, mc);
		StandingEngine engine = StandingModule.engine();
		HearthWorld w = p == null ? null : HearthWorld.of(p.level());
		if (p == null || engine == null || w == null) {
			Hearth.LOG.warn("[standing tour] no player, engine or Hearth world");
			return;
		}
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 1500");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
		town = w.settlements().stream().max(Comparator.comparingInt(Settlement::population)).orElse(null);
		if (town == null) {
			Hearth.LOG.warn("[standing tour] no live settlement");
			return;
		}
		engine.set(town, p.getUUID(), 30);
		engine.deed(new Deed(p.getUUID(), town.id(), "defense:raid_repelled", 30, null, town.center(), true));
		// Stand outside the town so walking in greets us.
		BlockPos out = town.center().offset(town.bounds().getXSpan() / 2 + 60, 0, 0);
		teleport(p, out);
		Hearth.LOG.info("[standing tour] {}: standing {}, title {}, fame {}", town.name(), engine.standing(town, p.getUUID()),
			engine.title(town, p.getUUID()).getSerializedName(), engine.fame(p.getUUID(), town.center()));
	}

	private static void walkIn(MinecraftServer server, Minecraft mc) {
		ServerPlayer p = player(server, mc);
		if (p != null && town != null) {
			teleport(p, town.center().offset(3, 0, 3));
		}
	}

	private static void talk(MinecraftServer server, Minecraft mc) {
		ServerPlayer p = player(server, mc);
		if (p == null || town == null) {
			return;
		}
		ServerLevel level = p.level();
		AABB box = new AABB(p.blockPosition()).inflate(96);
		List<PathfinderMob> folk = level.getEntitiesOfClass(PathfinderMob.class, box,
			e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(Hearth.id("folk")) && !e.isBaby());
		folk.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		if (folk.isEmpty()) {
			Hearth.LOG.warn("[standing tour] no folk embodied near {}", p.blockPosition());
			return;
		}
		PathfinderMob f = folk.getFirst();
		p.connection.teleport(f.getX() + 1.5, f.getY(), f.getZ() + 1.5, 135F, 10F);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, f.getEyePosition());
		folkId = f.getId();
		f.interact(p, InteractionHand.MAIN_HAND, f.position());
		Hearth.LOG.info("[standing tour] talking to folk #{} at {}", folkId, f.blockPosition());
	}

	private static void teleport(ServerPlayer p, BlockPos at) {
		ServerLevel level = p.level();
		level.getChunk(at.getX() >> 4, at.getZ() >> 4); // load it, so the heightmap is real
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		p.connection.teleport(at.getX() + 0.5, y, at.getZ() + 0.5, 270F, 8F);
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, "hearth_standing_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
			message -> Hearth.LOG.info("[standing tour] {}", message.getString()));
	}
}
