package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.repair.fire.FireLeash;
import dev.voidmc.hearth.social.life.Venues;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A house on fire in an embodied town (docs/LIVING_WORLD_2.md §3.1, §3.3): with live fires (on in single player, off on
 * servers by default) a real fire is lit on its roof and held on the {@link FireLeash} — the brigade and the player put
 * it out, what burns is real damage the builders mend; with them off, smoke and flames rise from the roof and the books'
 * fire leaves its shell when the scene ends. Players who stand to the buckets (or put out flames) count in the outcome.
 */
final class FireScene implements SceneDirector {
	static final FireScene INSTANCE = new FireScene();
	/** Seconds a player must spend by the fire, out of harm's way and helping, to count as fighting it (shown fires). */
	static final int HELP_SECONDS = 20;

	private FireScene() {
	}

	@Override
	public void begin(SceneRun run, Settlement s) {
		PlotView p = run.event.plot() < 0 ? null : s.plot(run.event.plot());
		if (p == null) {
			run.put("end_now", Boolean.TRUE);
			return;
		}
		BoundingBox box = box(p);
		run.put("box", box);
		ServerLevel level = run.level();
		if (TownEvents.liveFires(level.getServer())) {
			int lit = FireLeash.light(level, box, 3);
			if (lit > 0) {
				FireLeash.Leash leash = FireLeash.hold(level, box, level.getGameTime() + (run.event.end() - run.event.start()) + 200);
				run.put("leash", leash);
				run.event.args().putBoolean("live", true);
			}
		}
	}

	static BoundingBox box(PlotView p) {
		var o = p.spec().origin();
		return new BoundingBox(o.getX(), o.getY() - 1, o.getZ(), o.getX() + p.spec().sizeX() - 1, o.getY() + p.spec().maxHeight(),
			o.getZ() + p.spec().sizeZ() - 1);
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		BoundingBox box = run.get("box");
		if (box == null) {
			return;
		}
		ServerLevel level = run.level();
		BlockPos roof = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			new BlockPos(box.getCenter().getX(), 0, box.getCenter().getZ()));
		if (run.get("leash") == null) {
			// a fire shown: smoke rising, flames licking the roof
			EventDressings.particles(level, "smoke", roof, 6);
			EventDressings.particles(level, "flame", roof.below(), 8);
		} else {
			EventDressings.particles(level, "smoke", roof, 3);
		}
		// who helps: players by the fire, at the buckets (counted once each)
		Map<UUID, Integer> near = run.get("helpers");
		if (near == null) {
			near = new HashMap<>();
			run.put("helpers", near);
		}
		BlockPos mid = box.getCenter();
		for (ServerPlayer p : EventDirector.playersNear(level, mid, 14)) {
			int secs = near.merge(p.getUUID(), 1, Integer::sum);
			if (secs == HELP_SECONDS && fought(run, p.getUUID())) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("hearth.events.fire.helping"), true);
			}
		}
	}

	@Override
	public boolean done(SceneRun run, Settlement s) {
		FireLeash.Leash leash = run.get("leash");
		if (leash == null) {
			return false;
		}
		// a live fire put out (after its first minute) ends the scene
		return run.level().getGameTime() - run.startedAt > 1200 && run.level().getGameTime() % 100 == 0 && !FireLeash.burning(leash);
	}

	@Override
	public void end(SceneRun run, Settlement s) {
		FireLeash.Leash leash = run.get("leash");
		if (leash != null) {
			FireLeash.release(leash);
		}
	}

	/** The fire watch saw a fire out in this town: its helpers fought it. */
	static void fireOut(Settlement s, java.util.Set<UUID> helpers) {
		SceneRun run = Scenes.running(s.id(), EventKind.FIRE);
		if (run == null) {
			return;
		}
		for (UUID h : helpers) {
			fought(run, h);
		}
		if (run.get("leash") != null) {
			run.put("end_now", Boolean.TRUE);
		}
	}

	/** Counts a player as fighting the fire, once; whether this was the first time. */
	static boolean fought(SceneRun run, UUID player) {
		java.util.Set<UUID> seen = run.get("fought");
		if (seen == null) {
			seen = new java.util.HashSet<>();
			run.put("fought", seen);
		}
		if (seen.add(player)) {
			run.iv.add(player, "fought", 1);
			return true;
		}
		return false;
	}

	static BlockPos middle(PlotView p) {
		return Venues.middle(p.spec());
	}
}
