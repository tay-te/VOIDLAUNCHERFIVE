package dev.voidmc.hearth.economy.household;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Servants at work in a player's house (docs/LIVING_WORLD_2.md §8.3; priority 130, the band of work that interrupts the
 * routine): the routine already keeps them at the house (it is their home and their workplace), and this adds the
 * house's rounds — sweeping and tidying room to room, the larder chest stocked from what was bought, the doors shut at
 * dusk — and, when the owner comes home after a while away, the greeting at the door and the report of what happened
 * (the town's notices since, what folk call them, what the house cost). It wants a folk only for one of those tasks, so the
 * routine has them the rest of the day; a want costs a few map lookups.
 */
public final class ServantWork implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("economy/servant");
	public static final int PRIORITY = 130;
	/** Away this long (game ticks) and the owner is greeted when they come back. */
	static final long AWAY_TICKS = 20 * 60 * 5;
	/** A round of the house every this many ticks, for a while. */
	static final long ROUND_EVERY = 20 * 90;

	/** When each owner was last seen at each house (in memory: after a restart they are greeted once). */
	private static final Map<String, Long> SEEN = new HashMap<>();
	private static final Map<UUID, Task> TASKS = new HashMap<>();

	private enum Kind {
		GREET, LARDER, DOORS, ROUND
	}

	private static final class Task {
		final Kind kind;
		final BlockPos at;
		final @Nullable UUID player;
		int ticks;

		Task(Kind kind, BlockPos at, @Nullable UUID player) {
			this.kind = kind;
			this.at = at;
			this.player = player;
		}
	}

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
		ServantState st = ServantState.of(folk.resident());
		if (st == null || !folk.awake() || !(folk.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		Settlement s = folk.settlement();
		PlotView plot = s.plot(st.plot());
		if (plot == null || plot.owner() == null) {
			return false;
		}
		Task t = choose(level, folk, s, plot, st);
		if (t == null) {
			return false;
		}
		TASKS.put(folk.resident().id, t);
		return true;
	}

	private static @Nullable Task choose(ServerLevel level, NpcService.FolkHandle folk, Settlement s, PlotView plot, ServantState st) {
		long now = level.getGameTime();
		UUID owner = plot.owner();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		BlockPos door = door(s, plot);
		if (player != null && player.level() == level && near(player.blockPosition(), plot, 10)) {
			String key = s.id() + "/" + plot.id() + "/" + owner;
			Long last = SEEN.put(key, now);
			if (last == null || now - last > AWAY_TICKS) {
				return new Task(Kind.GREET, player.blockPosition(), owner);
			}
		}
		HouseBook.House h = s.data(Household.HOUSES).house(plot.id());
		if (h != null && h.pending > 0 && Larder.has(s, plot)) {
			List<Poi> store = Servants.pois(s, plot, PoiType.STORAGE);
			return new Task(Kind.LARDER, store.getFirst().pos(), null);
		}
		int minute = NpcTime.minuteOfDay(level);
		if (minute >= 20 * 60 && minute < 21 * 60 && door != null && open(level, door)) {
			return new Task(Kind.DOORS, door, null);
		}
		if (minute >= 7 * 60 && minute < 19 * 60 && (now + folk.entity().getId() * 37L) % ROUND_EVERY < 20) {
			List<Poi> spots = Servants.pois(s, plot, folk.entity().getRandom().nextBoolean() ? PoiType.HEARTH : PoiType.TABLE);
			BlockPos at = spots.isEmpty() ? centre(plot) : spots.get(folk.entity().getRandom().nextInt(spots.size())).pos();
			return new Task(Kind.ROUND, at, null);
		}
		return null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Task t = TASKS.get(folk.resident().id);
		if (t != null) {
			folk.moveTo(t.at, 0.6);
		}
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		Task t = TASKS.get(folk.resident().id);
		if (t == null || !(folk.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		if (++t.ticks > 20 * 40) {
			return false;
		}
		Settlement s = folk.settlement();
		ServantState st = ServantState.of(folk.resident());
		PlotView plot = st == null ? null : s.plot(st.plot());
		if (plot == null) {
			return false;
		}
		switch (t.kind) {
			case GREET -> {
				ServerPlayer player = t.player == null ? null : level.getServer().getPlayerList().getPlayer(t.player);
				if (player == null || player.level() != level) {
					return false;
				}
				if (folk.entity().distanceToSqr(player) > 3.5 * 3.5) {
					if (t.ticks % 10 == 1) {
						folk.moveTo(player.blockPosition(), 0.7);
					}
					return t.ticks < 20 * 20;
				}
				folk.stop();
				folk.lookAt(player);
				Household.greet(level, s, plot, folk, player);
				return false;
			}
			case LARDER -> {
				if (!folk.arrived() && t.ticks % 20 == 1) {
					folk.moveTo(t.at, 0.6);
				}
				if (folk.entity().blockPosition().closerThan(t.at, 3.0)) {
					HouseBook.House h = s.data(Household.HOUSES).house(plot.id());
					if (h != null && Larder.stock(level, s, plot, h) > 0) {
						folk.pose("tend", 40);
						s.markDirty();
					}
					return false;
				}
				return true;
			}
			case DOORS -> {
				if (!folk.arrived() && t.ticks % 20 == 1) {
					folk.moveTo(t.at, 0.6);
				}
				if (folk.entity().blockPosition().closerThan(t.at, 2.5)) {
					shut(level, t.at);
					return false;
				}
				return true;
			}
			case ROUND -> {
				if (t.ticks % 20 == 1 && !folk.arrived()) {
					folk.moveTo(t.at, 0.55);
				}
				if (folk.arrived()) {
					folk.pose(t.ticks % 2 == 0 ? "tend" : "craft", 60);
					return t.ticks < 20 * 12;
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		TASKS.remove(folk.resident().id);
		folk.stop();
	}

	static void clear() {
		SEEN.clear();
		TASKS.clear();
	}

	// ---------------------------------------------------------------- the house

	private static boolean near(BlockPos pos, PlotView plot, int margin) {
		var spec = plot.spec();
		return pos.getX() >= spec.origin().getX() - margin && pos.getX() < spec.origin().getX() + spec.sizeX() + margin
			&& pos.getZ() >= spec.origin().getZ() - margin && pos.getZ() < spec.origin().getZ() + spec.sizeZ() + margin
			&& Math.abs(pos.getY() - spec.origin().getY()) < 16;
	}

	static BlockPos centre(PlotView plot) {
		var spec = plot.spec();
		return new BlockPos(spec.origin().getX() + spec.sizeX() / 2, spec.origin().getY(), spec.origin().getZ() + spec.sizeZ() / 2);
	}

	static @Nullable BlockPos door(Settlement s, PlotView plot) {
		List<Poi> doors = Servants.pois(s, plot, PoiType.DOOR);
		return doors.isEmpty() ? null : doors.getFirst().pos();
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState st = level.getBlockState(pos);
		return st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.OPEN);
	}

	private static void shut(ServerLevel level, BlockPos pos) {
		BlockState st = level.getBlockState(pos);
		if (st.getBlock() instanceof DoorBlock door && st.getValue(DoorBlock.OPEN)) {
			door.setOpen(null, level, st, pos, false);
		}
	}

	static @Nullable HearthWorld world(ServerLevel level) {
		return HearthServices.world(level);
	}
}
