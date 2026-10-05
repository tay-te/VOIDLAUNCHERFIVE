package dev.voidmc.mettle.gear;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.GearSlot;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;

/**
 * Belt quick use (BURDEN.md §7): the pocket's item is swapped into the main hand and, if it can be used (food, a potion),
 * the use starts at once. The item you were holding waits in that pocket and comes back when the use finishes, when the
 * item runs out, or when you switch to another hotbar slot. The server does all of it; the client only names a pocket.
 */
public final class BeltUse {
	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	/** Client → server: use belt pocket {@code pocket} (0-based). */
	public record Request(int pocket) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Mettle.id("gear/belt_use"));
		public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Request::pocket, Request::new);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** A swap in progress: where the held item waits and what it was. */
	private record Active(int pocket, int hotbarSlot, ItemStack held, boolean using) {
	}

	private BeltUse() {
	}

	public static void use(ServerPlayer player, int pocket) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}
		Active previous = ACTIVE.remove(player.getUUID());
		if (previous != null) {
			restore(player, previous);
		}
		GearInventory gear = GearInventory.of(player);
		if (pocket < 0 || pocket >= gear.pocketCount(GearSlot.BELT)) {
			return;
		}
		ItemStack item = gear.pocket(GearSlot.BELT, pocket);
		if (item.isEmpty()) {
			return;
		}
		if (player.isUsingItem()) {
			player.stopUsingItem();
		}
		Inventory inventory = player.getInventory();
		int hotbar = inventory.getSelectedSlot();
		ItemStack held = inventory.getItem(hotbar);
		gear.setPocket(GearSlot.BELT, pocket, held);
		inventory.setItem(hotbar, item);
		// The client must have the item in hand before it hears the use started.
		player.containerMenu.broadcastChanges();
		player.gameMode.useItem(player, player.level(), item, InteractionHand.MAIN_HAND);
		ACTIVE.put(player.getUUID(), new Active(pocket, hotbar, held.copy(), player.isUsingItem()));
	}

	/** Brings the held item back once the use is over (every server tick, only for players mid-swap). */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Active> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Active a = entry.getValue();
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			boolean switched = player.getInventory().getSelectedSlot() != a.hotbarSlot();
			boolean done = a.using() ? !player.isUsingItem() : player.getInventory().getItem(a.hotbarSlot()).isEmpty();
			if (switched || done) {
				it.remove();
				restore(player, a);
			}
		}
	}

	/** Puts things back now (leaving the server: never leave a sword in the belt). */
	public static void end(ServerPlayer player) {
		Active a = ACTIVE.remove(player.getUUID());
		if (a != null && player.isAlive()) {
			restore(player, a);
		}
	}

	public static boolean active(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	private static void restore(ServerPlayer player, Active a) {
		GearInventory gear = GearInventory.of(player);
		if (a.pocket() >= gear.pocketCount(GearSlot.BELT)) {
			return;
		}
		ItemStack waiting = gear.pocket(GearSlot.BELT, a.pocket());
		if (!ItemStack.matches(waiting, a.held())) {
			// The player moved things around meanwhile; nothing to give back.
			return;
		}
		Inventory inventory = player.getInventory();
		ItemStack inHand = inventory.getItem(a.hotbarSlot());
		inventory.setItem(a.hotbarSlot(), waiting);
		if (inHand.isEmpty() || gear.fits(GearSlot.BELT, inHand)) {
			gear.setPocket(GearSlot.BELT, a.pocket(), inHand);
		} else {
			gear.setPocket(GearSlot.BELT, a.pocket(), ItemStack.EMPTY);
			inventory.placeItemBackInInventory(inHand, Prediction.SERVER_ONLY);
		}
	}
}
