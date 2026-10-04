package dev.voidmc.hearth.law.lock;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.crime.TheftWatch;
import dev.voidmc.hearth.law.crime.Witnesses;
import dev.voidmc.hearth.law.item.LawItems;
import dev.voidmc.hearth.law.justice.PlayerLaw;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.law.own.Ownership;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Picking a lock, on the server (docs/LIVING_WORLD.md §4). The client plays the minigame ({@link LockpickModel}); the
 * server keeps the attempt: it takes a pick for each one that snaps, watches for witnesses while the player kneels at
 * the lock (a folk who sees it makes it a crime and ends the attempt), checks that a reported success took a plausible
 * time, and then opens the lock (picked locks stay open until their owners lock up again) and what is behind it.
 */
public final class LockpickSessions {
	/** Seconds between witness checks while picking. */
	static final int CHECK_TICKS = 20;
	/** How far the player may step from the lock before the attempt ends. */
	static final double REACH = 4.5;
	private static final Map<UUID, Session> ACTIVE = new HashMap<>();
	private static int nextId = 1;

	private LockpickSessions() {
	}

	private record Session(int id, Locks.Lock lock, BlockHitResult hit, long started, long seed) {
	}

	public static boolean active(UUID player) {
		return ACTIVE.containsKey(player);
	}

	/** Kneels at a lock with a pick in hand. */
	public static void start(ServerPlayer player, Locks.Lock lock, BlockHitResult hit) {
		int picks = picks(player.getInventory());
		if (picks <= 0) {
			return;
		}
		if (!Witnesses.find(player.level(), player, 0.1).isEmpty() && Law.config().crimes().lockpicking()) {
			player.sendOverlayMessage(Component.translatable("hearth.law.lockpick.watched").withStyle(ChatFormatting.RED));
			return;
		}
		long seed = player.getRandom().nextLong();
		Session s = new Session(nextId++, lock, hit, player.level().getGameTime(), seed);
		ACTIVE.put(player.getUUID(), s);
		int skill = PlayerLaw.of(player).lockpickSkill();
		Component what = Component.translatable(lock.door() ? "hearth.law.lockpick.door" : "hearth.law.lockpick.chest",
			Ownership.name(lock.owner(), player.level().getServer()));
		if (ServerPlayNetworking.canSend(player, LawPayloads.LockpickStart.TYPE)) {
			ServerPlayNetworking.send(player, new LawPayloads.LockpickStart(s.id, lock.tier(), seed, skill, picks, what));
		}
	}

	/** What the client reports from the lock. */
	public static void event(ServerPlayer player, LawPayloads.LockpickEvent event) {
		Session s = ACTIVE.get(player.getUUID());
		if (s == null || s.id != event.session()) {
			return;
		}
		ServerLevel level = player.level();
		switch (event.kind()) {
			case LawPayloads.LockpickEvent.BROKE -> {
				takePick(player.getInventory());
				level.playSound(null, s.lock.pos(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
				int left = picks(player.getInventory());
				if (left <= 0) {
					end(player, s, LawPayloads.LockpickEnd.NO_PICKS);
				} else {
					send(player, s, LawPayloads.LockpickEnd.PICKS, left);
				}
			}
			case LawPayloads.LockpickEvent.OPENED -> opened(player, s, event.elapsedTicks());
			default -> ACTIVE.remove(player.getUUID());
		}
	}

	private static void opened(ServerPlayer player, Session s, int clientTicks) {
		ServerLevel level = player.level();
		long elapsed = level.getGameTime() - s.started;
		LockpickModel.Spec spec = LockpickModel.spec(s.lock.tier(), s.seed, PlayerLaw.of(player).lockpickSkill());
		double minimum = Law.config().stealth().lockpickMinSeconds() * spec.pins() * 20 * 0.8;
		ACTIVE.remove(player.getUUID());
		if (elapsed < minimum) {
			Hearth.LOG.warn("law: {} reported a lock picked in {} ticks (at least {} expected); ignored", player.getName().getString(), elapsed,
				(int) minimum);
			send(player, s, LawPayloads.LockpickEnd.GONE, picks(player.getInventory()));
			return;
		}
		Settlement settlement = s.lock.settlement();
		Locks.markPicked(settlement, s.lock, level.getGameTime());
		PlayerLaw.set(player, PlayerLaw.of(player).addLockpicking());
		level.playSound(null, s.lock.pos(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4F, 1.5F);
		player.sendOverlayMessage(Component.translatable("hearth.law.lockpick.opened").withStyle(ChatFormatting.GREEN));
		// a picked lock is a crime if anyone sees it, and a mystery for its owners if nobody does
		CrimeDesk.commit(level, player, settlement, Offence.LOCKPICKING, s.lock.pos(), s.lock.owner().resident(), 0, 0.1, null);
		BlockState state = level.getBlockState(s.lock.pos());
		if (state.getBlock() instanceof DoorBlock door) {
			door.setOpen(player, level, state, s.lock.pos(), true);
		} else {
			TheftWatch.expect(player, s.lock.pos(), s.lock.owner(), settlement);
			level.getBlockState(s.hit.getBlockPos()).useWithoutItem(level, player, s.hit);
		}
	}

	/** Witnesses and walking away, once a second per attempt. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Session>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Session> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Session s = e.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			if ((player.level().getGameTime() - s.started) % CHECK_TICKS != 0) {
				continue;
			}
			BlockPos pos = s.lock.pos();
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH || !player.isAlive()) {
				it.remove();
				send(player, s, LawPayloads.LockpickEnd.GONE, picks(player.getInventory()));
				continue;
			}
			if (Law.config().crimes().lockpicking() && !Witnesses.find(player.level(), player, 0.15).isEmpty()) {
				it.remove();
				send(player, s, LawPayloads.LockpickEnd.SPOTTED, picks(player.getInventory()));
				CrimeDesk.commit(player.level(), player, s.lock.settlement(), Offence.LOCKPICKING, pos, s.lock.owner().resident(), 0, 0.15, null);
			}
		}
	}

	private static void end(ServerPlayer player, Session s, int reason) {
		ACTIVE.remove(player.getUUID());
		send(player, s, reason, picks(player.getInventory()));
	}

	private static void send(ServerPlayer player, Session s, int reason, int picks) {
		if (ServerPlayNetworking.canSend(player, LawPayloads.LockpickEnd.TYPE)) {
			ServerPlayNetworking.send(player, new LawPayloads.LockpickEnd(s.id, reason, picks));
		}
	}

	public static int picks(Inventory inventory) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(LawItems.LOCKPICK)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void takePick(Inventory inventory) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(LawItems.LOCKPICK)) {
				stack.shrink(1);
				inventory.setChanged();
				return;
			}
		}
	}

	public static void forget(UUID player) {
		ACTIVE.remove(player);
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
