package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.folk.Music;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * A part in a town event's scene (docs/LIVING_WORLD_2.md §0.3: scene roles, priority 230): the folk walks where the
 * timeline sends them, stands facing what matters, holds what the part carries, takes up the poses (kneel at the altar,
 * cower in the pillory, cheer at the joust) and plays if they are the musician; dancing pairs circle. The runner
 * ({@link Scenes}) gives the orders; this only carries them out, cheaply (a path asked for once a few seconds).
 */
public final class SceneRoleBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("events/scene_role");
	public static final int PRIORITY = 230;

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		Scenes.Order o = Scenes.order(folk.resident().id);
		return o != null && !o.run.ended && folk.settlement().embodied();
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Scenes.Order o = Scenes.order(folk.resident().id);
		if (o != null) {
			o.moved = false;
			o.nextMove = 0;
			o.holdDone = false;
			o.poseDone = o.pose.isEmpty();
		}
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		Scenes.Order o = Scenes.order(folk.resident().id);
		if (o == null || o.run.ended) {
			return false;
		}
		long t = folk.gameTime();
		if (!o.holdDone) {
			folk.hold(o.hold);
			o.holdDone = true;
		}
		BlockPos target = o.target;
		if (target == null) {
			if (o.focus != null && (t & 15) == 0) {
				folk.lookAt(o.focus);
			}
			return true;
		}
		BlockPos goal = target;
		if (o.dance) {
			// a slow turn round the spot: four points, a quarter every three seconds, partners opposite
			int q = (int) Math.floorMod(t / 60 + (folk.resident().id.getLeastSignificantBits() & 1) * 2, 4L);
			goal = target.offset(q == 0 ? 1 : q == 2 ? -1 : 0, 0, q == 1 ? 1 : q == 3 ? -1 : 0);
		}
		double d2 = folk.entity().blockPosition().distSqr(goal);
		if (d2 > 2.2 * 2.2) {
			if (t >= o.nextMove || !o.moved) {
				boolean ok = folk.moveTo(goal, o.dance ? 0.5 : o.speed);
				o.moved = true;
				o.nextMove = t + (ok ? 60 : 200);
				if (!ok && d2 > 24 * 24) {
					// no way there from far off: the part is played where they are
					o.target = null;
				}
			}
			return true;
		}
		if (!o.dance) {
			folk.stop();
		}
		if (o.focus != null && (t & 7) == 0) {
			folk.lookAt(o.focus);
		}
		if (!o.poseDone) {
			folk.pose(o.pose.isEmpty() ? "none" : o.pose, o.poseTicks);
			o.poseDone = true;
		}
		if (o.music && (t % 5) == 0 && folk.entity().level() instanceof ServerLevel level) {
			int tune = (int) Math.floorMod(TownCalendar.mix(o.run.event.seed()), 4L);
			int beat = (int) ((t - o.run.startedAt) / 5);
			Music.play(level, folk.entity().position(), folk.settlement().culture(), tune, beat);
			if (beat % 8 == 0) {
				folk.swing();
			}
		}
		return true;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		folk.hold(ItemStack.EMPTY);
		folk.pose("none", 0);
	}
}
