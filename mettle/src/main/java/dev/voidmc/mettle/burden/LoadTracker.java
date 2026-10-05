package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.MettleAttributes;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Keeps each player's load current on the server (BURDEN.md §2). Recomputed when something changes (the inventory
 * trigger, equipment, perks, capacity, game mode) and every 40 ticks as a fallback; the per-tick cost is a few field
 * reads. The synced attachment only moves when a value moves by 0.1 kg or the band changes; band effects only when the
 * band changes.
 */
public final class LoadTracker {
	/** Fallback recompute interval, in ticks. */
	public static final int FALLBACK_TICKS = 40;
	/** The same band's chip is not repeated within this many ticks (standing on a threshold, picking up and dropping). */
	private static final int CHIP_GAP = 100;

	private static final Map<UUID, Tracked> TRACKED = new HashMap<>();

	private LoadTracker() {
	}

	private static final class Tracked {
		@Nullable LoadState exact;
		boolean dirty = true;
		@Nullable Object character;
		double capacity = Double.NaN;
		boolean exempt;
		@Nullable Entity vehicle;
		final Map<LoadBand, Long> chippedAt = new EnumMap<>(LoadBand.class);
	}

	private static Tracked tracked(ServerPlayer player) {
		return TRACKED.computeIfAbsent(player.getUUID(), u -> new Tracked());
	}

	public static void markDirty(ServerPlayer player) {
		tracked(player).dirty = true;
	}

	public static void markAllDirty() {
		TRACKED.values().forEach(t -> t.dirty = true);
	}

	/** The exact current state, recomputed first if anything changed since. */
	public static LoadState state(ServerPlayer player) {
		Tracked t = tracked(player);
		if (t.dirty || t.exact == null) {
			recompute(player, t);
		}
		return t.exact;
	}

	public static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Tracked t = tracked(player);
			Object character = player.getAttached(CharacterStore.CHARACTER);
			double capacity = player.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			if (t.dirty || t.exact == null || (now + player.getId()) % FALLBACK_TICKS == 0 || character != t.character || capacity != t.capacity
				|| Loads.exempt(player) != t.exempt) {
				recompute(player, t);
			}
			Entity vehicle = player.getControlledVehicle();
			if (vehicle != t.vehicle) {
				BandEffects.dismount(t.vehicle);
				t.vehicle = vehicle;
				BandEffects.mount(vehicle, t.exact.band(), Bands.rules(player));
			}
			if (player.isSprinting() && Sprint.blockedByLoad(player)) {
				player.setSprinting(false);
			}
		}
	}

	public static void leave(ServerPlayer player) {
		Tracked t = TRACKED.remove(player.getUUID());
		if (t != null) {
			BandEffects.dismount(t.vehicle);
		}
	}

	public static void clear() {
		TRACKED.clear();
	}

	private static void recompute(ServerPlayer player, Tracked t) {
		LoadState next = Loads.compute(player);
		LoadState prev = t.exact;
		t.exact = next;
		t.dirty = false;
		t.character = player.getAttached(CharacterStore.CHARACTER);
		t.capacity = player.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
		t.exempt = next.exempt();

		BandRules rules = Bands.rules(player);
		BandEffects.apply(player, next.band(), rules);
		BandEffects.mount(t.vehicle, next.band(), rules);
		if (next.differsEnough(player.getAttached(LoadState.ATTACHMENT))) {
			player.setAttached(LoadState.ATTACHMENT, next);
		}
		LoadBand from = prev == null ? LoadBand.LIGHT : prev.band();
		if (from != next.band()) {
			changed(player, t, from, next);
		}
	}

	/** A band change: a chip that says what happened and what is heaviest, and the strain sound on the way into Overburdened. */
	private static void changed(ServerPlayer player, Tracked t, LoadBand from, LoadState now) {
		LoadBand to = now.band();
		if (Bands.atLeast(to, LoadBand.OVERBURDENED)) {
			if (Sprint.blockedByLoad(player)) {
				player.setSprinting(false);
			}
			if (!Bands.atLeast(from, LoadBand.OVERBURDENED)) {
				player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BUNDLE_INSERT_FAIL), SoundSource.PLAYERS,
					player.getX(), player.getY(), player.getZ(), 0.7f, 0.6f, player.getRandom().nextLong()));
			}
		}
		long tick = player.level().getGameTime();
		Long last = t.chippedAt.get(to);
		if (last != null && tick - last < CHIP_GAP) {
			return;
		}
		t.chippedAt.put(to, tick);
		String band = to.name().toLowerCase(Locale.ROOT);
		Component text = to == LoadBand.LIGHT
			? Component.translatable("mettle.burden.feedback.light")
			: Component.translatable("mettle.burden.feedback." + band, heaviest(now));
		SignaturePerks.feedback(player, Mettle.id("burden/" + band), text);
	}

	/** "cobblestone ×3 stacks · 48.0 kg" (or "iron chestplate · 4.8 kg" for one). */
	public static Component heaviest(LoadState state) {
		if (!state.hasHeaviest()) {
			return Component.empty();
		}
		Item item = BuiltInRegistries.ITEM.getValue(state.heaviest());
		Component name = item.getDefaultInstance().getHoverName();
		String kg = Weights.total(state.heaviestKg());
		return state.heaviestStacks() > 1
			? Component.translatable("mettle.burden.heaviest.many", name, state.heaviestStacks(), kg)
			: Component.translatable("mettle.burden.heaviest.one", name, kg);
	}
}
