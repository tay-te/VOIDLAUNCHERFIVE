package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.model.SettlementQuests.Happening;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The hue and cry: when fire breaks out or a mine falls in, the folk near it run to the place and call for help, so a
 * player in town sees where the trouble is before the board or the bell tells them. A {@link NpcService.FolkBehaviour}
 * (a reaction, priority 210) the quest module teaches the npc module; it runs only for embodied folk, reads the town's
 * few happenings, and ends when the happening is answered or after a minute.
 */
public final class HueAndCry implements NpcService.FolkBehaviour {
	private static final Identifier ID = Hearth.id("quest/hue_and_cry");
	/** Folk this far from the trouble come running. */
	private static final double REACH = 48;
	/** They stop this short of it. */
	private static final double STAND_OFF = 6;
	private static final int MAX_TICKS = 1200;
	private static final int SHOUT_EVERY = 100;
	private final Map<UUID, Run> running = new ConcurrentHashMap<>();

	private record Run(int happening, BlockPos at, boolean fire, long started) {
	}

	/** Teaches the npc module the behaviour: now if it is up, else once the server starts (modules may init in any order). */
	public static void register() {
		NpcService npc = HearthServices.npc();
		if (npc != null) {
			npc.registerBehaviour(new HueAndCry());
			return;
		}
		ServerLifecycleEvents.SERVER_STARTING.register(new ServerLifecycleEvents.ServerStarting() {
			private boolean done;

			@Override
			public void onServerStarting(net.minecraft.server.MinecraftServer server) {
				NpcService later = HearthServices.npc();
				if (!this.done && later != null) {
					later.registerBehaviour(new HueAndCry());
					this.done = true;
				}
			}
		});
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return 210;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		return folk.awake() && trouble(folk) != null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Happening h = trouble(folk);
		if (h == null) {
			return;
		}
		BlockPos at = h.pos().getY() == Objective.SURFACE_Y ? new BlockPos(h.pos().getX(), folk.entity().getBlockY(), h.pos().getZ()) : h.pos();
		this.running.put(folk.resident().id, new Run(h.id(), at, h.kind().equals(Happening.FIRE), folk.gameTime()));
		folk.moveTo(standOff(folk, at), 1.25);
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		Run run = this.running.get(folk.resident().id);
		if (run == null) {
			return false;
		}
		long age = folk.gameTime() - run.started();
		if (age > MAX_TICKS || !folk.awake()) {
			return false;
		}
		if (age % 20 == 0) {
			Happening h = QuestStore.board(folk.settlement()).happening(run.happening());
			if (h == null || h.resolved()) {
				return false;
			}
		}
		if (folk.arrived() || folk.entity().blockPosition().closerThan(run.at(), STAND_OFF + 1)) {
			folk.lookAt(run.at());
			if (age % SHOUT_EVERY == 0) {
				int variant = (int) Math.floorMod(folk.resident().id.getLeastSignificantBits() + age / SHOUT_EVERY, 3);
				folk.say(Component.translatable((run.fire() ? "hearth.quest.cry.fire." : "hearth.quest.cry.collapse.") + variant), null);
				folk.swing();
			}
		} else if (age % 40 == 0) {
			folk.moveTo(standOff(folk, run.at()), 1.25);
		}
		return true;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		this.running.remove(folk.resident().id);
		folk.stop();
	}

	/** The fire or collapse in the folk's town that is near enough to run to, or null. */
	private static @Nullable Happening trouble(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		SettlementQuests data = QuestStore.board(s);
		if (data.happenings.isEmpty()) {
			return null;
		}
		BlockPos here = folk.entity().blockPosition();
		for (Happening h : data.happenings) {
			if (h.resolved() || !(h.kind().equals(Happening.FIRE) || h.kind().equals(Happening.COLLAPSE))) {
				continue;
			}
			double dx = h.pos().getX() - here.getX();
			double dz = h.pos().getZ() - here.getZ();
			if (dx * dx + dz * dz <= REACH * REACH) {
				return h;
			}
		}
		return null;
	}

	/** A spot a few blocks short of the trouble, on the folk's side of it. */
	private static BlockPos standOff(NpcService.FolkHandle folk, BlockPos at) {
		BlockPos here = folk.entity().blockPosition();
		double dx = here.getX() - at.getX();
		double dz = here.getZ() - at.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d <= STAND_OFF) {
			return here;
		}
		return new BlockPos(at.getX() + (int) Math.round(dx / d * STAND_OFF), at.getY(), at.getZ() + (int) Math.round(dz / d * STAND_OFF));
	}
}
