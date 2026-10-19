package dev.voidmc.hearth.law.folk;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.crime.Presence;
import dev.voidmc.hearth.law.justice.Confrontations;
import dev.voidmc.hearth.law.justice.Jail;
import dev.voidmc.hearth.law.justice.Warrant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Sends a guard after a player the moment a crime against them is filed, so the law does not hang on who happens to
 * be standing near. The nearest awake guard on duty goes if they are near enough; otherwise a guard on duty who is
 * only a record is made to appear a short walk from the player (coming from the watch); failing that, the nearest awake
 * guard on duty however far, then the nearest awake one off duty. The chosen guard walks up and confronts the player
 * ({@link GuardBehaviour}). If nobody can come within {@link #GIVE_UP_SECONDS}, the watch posts the bounty instead and
 * the player is told: guards who see them will know their face.
 */
public final class GuardDispatch {
	/** How long a call stands before the watch gives up on sending anyone and posts a notice. */
	static final int GIVE_UP_SECONDS = 45;
	/** Seconds between tries to find a guard to send while none is on the way. */
	static final int RETRY_SECONDS = 3;
	/** An awake guard on duty farther than this is passed over for one made to appear nearer, when one can be. */
	public static final double NEAR_ENOUGH = 64.0;
	/** Blocks from the player a guard sent for appears. */
	static final int ARRIVE_DISTANCE = 22;
	/** Times a call is renewed when a guard sent gives up on the way. */
	static final int MAX_CALLS = 3;

	private static final Map<UUID, Call> CALLS = new HashMap<>();
	/** Guard resident → the player they were sent after. */
	private static final Map<UUID, UUID> SENT = new HashMap<>();

	private GuardDispatch() {
	}

	private static final class Call {
		final long settlement;
		final int calls;
		int seconds;
		int nextTry;
		@Nullable UUID guard;

		Call(long settlement, int calls) {
			this.settlement = settlement;
			this.calls = calls;
		}
	}

	/**
	 * A guard who might be sent.
	 *
	 * @param distance blocks to the player ({@link Double#POSITIVE_INFINITY} for a record)
	 */
	public record Option(UUID id, boolean embodied, boolean awake, boolean onDuty, double distance) {
	}

	/**
	 * Who goes: the nearest awake guard on duty within {@code nearEnough}; else a guard on duty who is only a record (to
	 * be made to appear near the player); else the nearest awake guard on duty however far; else the nearest awake guard
	 * off duty within {@code nearEnough}; else nobody. Sleeping guards are left to sleep.
	 */
	public static @Nullable Option choose(List<Option> options, double nearEnough) {
		List<Option> sorted = new ArrayList<>(options);
		sorted.sort(Comparator.comparingDouble(Option::distance));
		for (Option o : sorted) {
			if (o.embodied && o.awake && o.onDuty && o.distance <= nearEnough) {
				return o;
			}
		}
		for (Option o : sorted) {
			if (!o.embodied && o.onDuty) {
				return o;
			}
		}
		for (Option o : sorted) {
			if (o.embodied && o.awake && o.onDuty) {
				return o;
			}
		}
		for (Option o : sorted) {
			if (o.embodied && o.awake && o.distance <= nearEnough) {
				return o;
			}
		}
		return null;
	}

	/** A crime against {@code player} was filed in {@code settlement}: if they are in the town, a guard is sent for them. */
	public static void call(ServerLevel level, Settlement settlement, UUID player) {
		call(level, settlement, player, 1);
	}

	private static void call(ServerLevel level, Settlement settlement, UUID player, int calls) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
		Long in = Presence.settlementId(player);
		boolean here = in != null ? in == settlement.id() : p != null && settlement.bounds().isInside(p.blockPosition());
		if (p == null || Law.exempt(p) || !here) {
			return;
		}
		Call existing = CALLS.get(player);
		if (existing != null && existing.settlement == settlement.id()) {
			return;
		}
		Call c = new Call(settlement.id(), calls);
		CALLS.put(player, c);
		send(level, settlement, p, c);
	}

	/** The player a guard was sent after, or null. */
	public static @Nullable UUID sentAfter(UUID guard) {
		return SENT.get(guard);
	}

	/** The guard sent took up the job: the call is answered. */
	static void answered(UUID guard, UUID player) {
		SENT.remove(guard);
		Call c = CALLS.get(player);
		if (c != null && guard.equals(c.guard)) {
			CALLS.remove(player);
		}
	}

	/** The guard sent gave up on the way (no path, too long): another is sent, a few times at most. */
	static void gaveUp(ServerLevel level, Settlement settlement, UUID guard, UUID player, int calls) {
		SENT.remove(guard);
		if (calls < MAX_CALLS) {
			CALLS.remove(player);
			call(level, settlement, player, calls + 1);
		}
	}

	/** How many times the call that sent this guard has been made (1 for the first). */
	static int calls(UUID player) {
		Call c = CALLS.get(player);
		return c == null ? 1 : c.calls;
	}

	/** Once a second: calls still waiting look for a guard again, or give up and post the bounty. */
	public static void tick(MinecraftServer server) {
		if (CALLS.isEmpty()) {
			return;
		}
		HearthWorld world = HearthWorld.of(server.overworld());
		Iterator<Map.Entry<UUID, Call>> it = CALLS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Call> e = it.next();
			Call c = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Settlement s = world == null ? null : world.settlement(c.settlement);
			if (p == null || s == null || !wanted(s, p) || !(p.level() instanceof ServerLevel level) || level.dimension() != s.dimension()) {
				drop(c, it);
				continue;
			}
			Confrontations.Talk talk = Confrontations.of(p.getUUID());
			if (talk != null && talk.state == Confrontations.State.OPEN) {
				// someone got to them
				drop(c, it);
				continue;
			}
			c.seconds++;
			if (c.guard != null) {
				FolkHandle g = HearthServices.npc() == null ? null : HearthServices.npc().folk(c.guard);
				if (g == null || !g.awake()) {
					SENT.remove(c.guard);
					c.guard = null;
				}
			}
			if (c.guard == null && c.seconds >= GIVE_UP_SECONDS) {
				notice(s, p);
				drop(c, it);
				continue;
			}
			if (c.guard == null && c.seconds >= c.nextTry) {
				send(level, s, p, c);
			}
		}
	}

	private static void drop(Call c, Iterator<Map.Entry<UUID, Call>> it) {
		if (c.guard != null) {
			SENT.remove(c.guard);
		}
		it.remove();
	}

	private static boolean wanted(Settlement s, ServerPlayer p) {
		if (Law.exempt(p) || Jail.serving(p) || !p.isAlive()) {
			return false;
		}
		Warrant w = Law.state(s).warrant(p.getUUID());
		return w != null && (w.active() || w.outlaw());
	}

	/** Picks a guard and sends them; when none can come now, tries again in a few seconds. */
	private static void send(ServerLevel level, Settlement s, ServerPlayer player, Call c) {
		c.nextTry = c.seconds + RETRY_SECONDS;
		NpcService npc = HearthServices.npc();
		if (npc == null || !wanted(s, player)) {
			return;
		}
		List<Option> options = new ArrayList<>();
		Map<UUID, Resident> byId = new HashMap<>();
		for (Resident r : s.residents()) {
			if (r.job != JobType.GUARD || r.age != AgeStage.ADULT || SENT.containsKey(r.id) || GuardBehaviour.busy(r.id)) {
				continue;
			}
			FolkHandle h = npc.folk(r.id);
			boolean onDuty;
			try {
				onDuty = npc.onDuty(s, r);
			} catch (RuntimeException ex) {
				onDuty = true;
			}
			double d = h == null || h.entity().level() != level ? Double.POSITIVE_INFINITY : h.entity().distanceTo(player);
			options.add(new Option(r.id, h != null, h != null && h.awake(), onDuty, d));
			byId.put(r.id, r);
		}
		Option pick = choose(options, NEAR_ENOUGH);
		if (pick == null) {
			return;
		}
		if (!pick.embodied) {
			FolkHandle h = appear(level, s, byId.get(pick.id), player, npc);
			if (h == null) {
				// no room for them here now: the nearest awake guard on duty, however far, rather than nobody
				List<Option> about = options.stream().filter(o -> o.embodied).toList();
				pick = choose(about, NEAR_ENOUGH * 4);
				if (pick == null) {
					return;
				}
			}
		}
		c.guard = pick.id;
		SENT.put(pick.id, player.getUUID());
	}

	/**
	 * Makes a guard on duty appear a short walk from the player: out in the open (a street, not a room or a roof), out
	 * of the player's sight if it can be, on the side toward the middle of town first.
	 */
	private static @Nullable FolkHandle appear(ServerLevel level, Settlement s, Resident r, ServerPlayer player, NpcService npc) {
		BlockPos spot = spotNear(level, player, s.center(), new int[]{ARRIVE_DISTANCE, ARRIVE_DISTANCE - 6, ARRIVE_DISTANCE + 6}, true);
		if (spot != null) {
			FolkHandle h = npc.embodyNear(level, s, r, spot);
			if (h != null) {
				return h;
			}
		}
		BlockPos at = player.blockPosition();
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			FolkHandle h = npc.embodyNear(level, s, r, at.offset((int) Math.round(Math.cos(a) * ARRIVE_DISTANCE), 0, (int) Math.round(Math.sin(a) * ARRIVE_DISTANCE)));
			if (h != null) {
				return h;
			}
		}
		return null;
	}

	/**
	 * A place to stand {@code radii} blocks (in that order of preference) from the player, at about their height: a
	 * floor with two blocks of air over it. Spots the player cannot see are preferred, and with {@code open} only spots
	 * under the open sky (a street or yard, from which folk can walk anywhere) count. Searched first on the side toward
	 * {@code toward}.
	 */
	static @Nullable BlockPos spotNear(ServerLevel level, ServerPlayer player, BlockPos toward, int[] radii, boolean open) {
		BlockPos at = player.blockPosition();
		double dx = toward.getX() - at.getX();
		double dz = toward.getZ() - at.getZ();
		double base = dx * dx + dz * dz < 1 ? 0 : Math.atan2(dz, dx);
		BlockPos seen = null;
		net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
		for (int r : radii) {
			for (int i = 0; i < 16; i++) {
				// 0, +1, -1, +2, -2 ... steps of a sixteenth of a turn away from the preferred side
				int step = (i + 1) / 2 * (i % 2 == 0 ? 1 : -1);
				double a = base + step * Math.PI / 8;
				int x = at.getX() + (int) Math.round(Math.cos(a) * r);
				int z = at.getZ() + (int) Math.round(Math.sin(a) * r);
				for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
					BlockPos p = new BlockPos(x, at.getY() + dy, z);
					if (!level.isLoaded(p) || !standable(level, p) || open && !level.canSeeSky(p.above())) {
						continue;
					}
					net.minecraft.world.phys.Vec3 head = net.minecraft.world.phys.Vec3.atBottomCenterOf(p).add(0, 1.6, 0);
					boolean hidden = level.clip(new net.minecraft.world.level.ClipContext(eye, head, net.minecraft.world.level.ClipContext.Block.COLLIDER,
						net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType() != net.minecraft.world.phys.HitResult.Type.MISS;
					if (hidden) {
						return p;
					}
					if (seen == null) {
						seen = p;
					}
					break;
				}
			}
		}
		return seen;
	}

	private static boolean standable(ServerLevel level, BlockPos p) {
		BlockPos below = p.below();
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty()
			&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
			&& !level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getFluidState(below).isEmpty();
	}

	/**
	 * A guard sent for a player has made no headway for a while (no path to them: a shut room, a wall the pathfinder
	 * will not take): they come round the corner instead, to a spot near the player, out of sight if one can be found.
	 * Each try comes closer. Returns whether they were moved.
	 */
	static boolean closeIn(FolkHandle folk, ServerPlayer player, int attempt) {
		if (!(folk.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		int[] radii = attempt <= 1 ? new int[]{9, 7, 11} : attempt == 2 ? new int[]{5, 4, 6} : new int[]{3, 2, 4};
		BlockPos spot = spotNear(level, player, folk.entity().blockPosition(), radii, false);
		if (spot == null) {
			return false;
		}
		folk.stop();
		folk.entity().teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		return true;
	}

	/** Nobody could come: the bounty goes up on the watch's notices, and the player hears of it. */
	private static void notice(Settlement s, ServerPlayer p) {
		Warrant w = Law.state(s).warrant(p.getUUID());
		long bounty = w == null ? 0 : w.bounty();
		p.sendSystemMessage(Component.translatable("hearth.law.notice_posted", s.name(), bounty).withStyle(ChatFormatting.RED));
	}

	public static void forget(UUID player) {
		Call c = CALLS.remove(player);
		if (c != null && c.guard != null) {
			SENT.remove(c.guard);
		}
		SENT.values().removeIf(player::equals);
	}

	public static void clear() {
		CALLS.clear();
		SENT.clear();
	}
}
