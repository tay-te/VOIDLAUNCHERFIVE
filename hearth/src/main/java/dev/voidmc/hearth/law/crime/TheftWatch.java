package dev.voidmc.hearth.law.crime;

import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.item.LawComponents;
import dev.voidmc.hearth.law.item.OwnedMark;
import dev.voidmc.hearth.law.item.StolenGoods;
import dev.voidmc.hearth.law.item.StolenMark;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.law.own.Ownership;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Theft from containers (docs/LIVING_WORLD.md §4). When a player opens someone else's chest, its items are marked
 * {@link OwnedMark owned} (the screen shows the red hand on each); anything with that mark that turns up in the player's
 * own inventory has been taken, becomes {@link StolenMark stolen}, and is a theft, witnessed or not. When the chest
 * closes the marks come off what is left in it, and stolen goods put back where they came from are forgiven. Watching
 * where the items go rather than how they were moved catches every way of moving them.
 */
public final class TheftWatch {
	/** Ticks after a click on a container within which a newly opened menu is taken to be that container's. */
	private static final int EXPECT_TICKS = 5;
	private static final Map<UUID, Expect> EXPECT = new HashMap<>();
	private static final Map<UUID, Session> OPEN = new HashMap<>();

	private TheftWatch() {
	}

	private record Expect(BlockPos pos, Owner owner, long settlementId, long tick) {
	}

	private record Session(AbstractContainerMenu menu, BlockPos pos, Owner owner, String ownerName, long settlementId, String settlementName) {
	}

	/** The player clicked an owned container; the menu that opens next is watched. */
	public static void expect(ServerPlayer player, BlockPos pos, Owner owner, Settlement settlement) {
		EXPECT.put(player.getUUID(), new Expect(pos.immutable(), owner, settlement.id(), player.level().getGameTime()));
	}

	/** Whether any container is being watched (so the server tick can skip all of this when nobody is at one). */
	public static boolean idle() {
		return EXPECT.isEmpty() && OPEN.isEmpty();
	}

	public static void tick(MinecraftServer server) {
		if (idle()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Expect>> it = EXPECT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Expect> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Expect x = e.getValue();
			if (player == null || player.level().getGameTime() - x.tick > EXPECT_TICKS) {
				it.remove();
				continue;
			}
			if (player.containerMenu != player.inventoryMenu && !OPEN.containsKey(e.getKey())) {
				it.remove();
				begin(player, x);
			}
		}
		for (Iterator<Map.Entry<UUID, Session>> it = OPEN.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Session> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Session s = e.getValue();
			if (player == null || player.containerMenu != s.menu) {
				it.remove();
				end(s, player);
				continue;
			}
			takenNow(player, s.settlementId);
		}
	}

	private static void begin(ServerPlayer player, Expect x) {
		AbstractContainerMenu menu = player.containerMenu;
		Settlement s = Ownership.settlement(x.settlementId, player.level().getServer());
		if (s == null) {
			return;
		}
		Component name = Ownership.name(x.owner, player.level().getServer());
		Session session = new Session(menu, x.pos, x.owner, name.getString(), s.id(), s.name());
		OwnedMark mark = new OwnedMark(x.owner, session.ownerName);
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory) && slot.hasItem()) {
				slot.getItem().set(LawComponents.OWNED, mark);
			}
		}
		menu.broadcastChanges();
		OPEN.put(player.getUUID(), session);
		if (ServerPlayNetworking.canSend(player, LawPayloads.ContainerOwner.TYPE)) {
			ServerPlayNetworking.send(player, new LawPayloads.ContainerOwner(menu.containerId, name));
		}
	}

	private static void end(Session s, @Nullable ServerPlayer player) {
		for (Session other : OPEN.values()) {
			if (other.pos.equals(s.pos)) {
				return; // someone else still has it open: the marks stay until they close it too
			}
		}
		for (Slot slot : s.menu.slots) {
			if (slot.container instanceof Inventory || !slot.hasItem()) {
				continue;
			}
			ItemStack stack = slot.getItem();
			stack.remove(LawComponents.OWNED);
			StolenMark stolen = stack.get(LawComponents.STOLEN);
			if (stolen != null && stolen.owner().equals(s.owner)) {
				stack.remove(LawComponents.STOLEN); // put back where it came from
			}
			slot.setChanged();
		}
		if (player != null) {
			takenNow(player, s.settlementId);
		}
	}

	/**
	 * Turns owned marks in the player's inventory into stolen marks and reports the theft (one crime for everything
	 * taken at once, merged with the same theft moments earlier). Also catches items thrown out of the screen and
	 * picked up later, since the periodic sweep calls it for players in towns.
	 */
	public static void takenNow(ServerPlayer player, long fallbackSettlement) {
		Inventory inventory = player.getInventory();
		ServerLevel level = player.level();
		long value = 0;
		Owner victim = null;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			OwnedMark owned = stack.isEmpty() ? null : stack.get(LawComponents.OWNED);
			if (owned == null) {
				continue;
			}
			stack.remove(LawComponents.OWNED);
			if (Ownership.isOwner(owned.owner(), player.getUUID()) || Law.exempt(player)) {
				continue;
			}
			Settlement s = Ownership.settlement(owned.owner().settlementId(), level.getServer());
			StolenGoods.mark(stack, owned.owner(), owned.ownerName(), s == null ? "" : s.name(), Law.day(level), player.getUUID());
			value += Law.value(s, stack);
			victim = owned.owner();
		}
		if (victim == null) {
			return;
		}
		Settlement s = Ownership.settlement(victim.settlementId(), level.getServer());
		if (s == null) {
			s = Ownership.settlement(fallbackSettlement, level.getServer());
		}
		if (s == null || victim.kind() == Owner.Kind.PLAYER && !Law.config().protectPlayerProperty()) {
			return;
		}
		CrimeDesk.commit(level, player, s, Offence.THEFT, player.blockPosition(), victim.resident(), value, 0.05, null);
	}

	public static void forget(UUID player) {
		EXPECT.remove(player);
		Session s = OPEN.remove(player);
		if (s != null) {
			end(s, null);
		}
	}

	public static void clear() {
		EXPECT.clear();
		for (Session s : OPEN.values()) {
			for (Slot slot : s.menu.slots) {
				if (slot.hasItem()) {
					slot.getItem().remove(LawComponents.OWNED);
				}
			}
		}
		OPEN.clear();
	}
}
