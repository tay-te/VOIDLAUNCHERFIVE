package dev.voidmc.hearth.law.pocket;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.crime.Witnesses;
import dev.voidmc.hearth.law.folk.FolkTraits;
import dev.voidmc.hearth.law.item.LawItems;
import dev.voidmc.hearth.law.item.StolenGoods;
import dev.voidmc.hearth.law.justice.PlayerLaw;
import dev.voidmc.hearth.law.lock.Locks;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.law.stealth.Detection;
import dev.voidmc.hearth.law.stealth.Visibility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Picking a pocket, on the server (docs/LIVING_WORLD.md §4). Crouch behind a folk (or by a sleeper) and use them with an
 * empty hand: their pockets open ({@link Pockets}). Lifting an item takes time; the mark's awareness fills all the while
 * ({@link PickpocketModel}), faster as you lift, slower if they sleep, in the dark, or the player is practised. Fill it
 * and they catch you (a witnessed crime, the victim the witness). Leave in time and nobody knows — until they miss it.
 * The server keeps the clock, so a modified client cannot lift faster.
 */
public final class PickpocketSessions {
	/** Ticks between updates sent to the screen (it animates between them). */
	static final int UPDATE_TICKS = 4;
	private static final Map<UUID, Session> ACTIVE = new HashMap<>();
	private static int nextId = 1;

	private PickpocketSessions() {
	}

	private static final class Session {
		final int id;
		final UUID resident;
		final long settlementId;
		final int entity;
		final PickpocketModel model;
		final List<Pockets.PocketItem> items;
		final boolean[] taken;
		final long period;
		final boolean asleep;
		final Vec3 start;
		final int keyPlot;
		long value;
		int age;

		Session(int id, UUID resident, long settlementId, int entity, PickpocketModel model, List<Pockets.PocketItem> items, boolean[] taken,
			long period, boolean asleep, Vec3 start, int keyPlot) {
			this.id = id;
			this.resident = resident;
			this.settlementId = settlementId;
			this.entity = entity;
			this.model = model;
			this.items = items;
			this.taken = taken;
			this.period = period;
			this.asleep = asleep;
			this.start = start;
			this.keyPlot = keyPlot;
		}
	}

	public static boolean active(UUID player) {
		return ACTIVE.containsKey(player);
	}

	/**
	 * Whether the player is placed to pick this folk's pocket: crouched, empty-handed, behind them (out of their field of
	 * view) or by them as they sleep.
	 */
	public static boolean canTry(ServerPlayer player, FolkHandle folk) {
		if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || !Law.config().crimes().pickpocketing()) {
			return false;
		}
		if (player.distanceTo(folk.entity()) > 2.5) {
			return false;
		}
		return !folk.awake() || !folk.canSee(player);
	}

	public static void start(ServerPlayer player, FolkHandle folk) {
		Resident r = folk.resident();
		Settlement s = folk.settlement();
		ServerLevel level = player.level();
		LawConfig.Stealth cfg = Law.config().stealth();
		long period = Pockets.period(Law.day(level), cfg.pocketRefreshDays());
		int keyPlot = Locks.keyPlot(s, r);
		List<Pockets.PocketItem> items = Pockets.of(r, period, keyPlot != PlotView.NONE, HearthServices.economy() != null);
		boolean[] taken = new boolean[items.size()];
		for (int i : Pockets.taken(r, period)) {
			if (i >= 0 && i < taken.length) {
				taken[i] = true;
			}
		}
		boolean asleep = !folk.awake();
		double rate = rate(player, folk, asleep);
		Session session = new Session(nextId++, r.id, s.id(), folk.entity().getId(), new PickpocketModel(rate), items, taken, period, asleep,
			player.position(), keyPlot);
		ACTIVE.put(player.getUUID(), session);
		send(player, session, s, LawPayloads.Pocket.OPEN);
	}

	/** How fast the mark's awareness fills: their wakefulness and sharpness, the light, the thief's armour and practice. */
	static double rate(ServerPlayer player, FolkHandle folk, boolean asleep) {
		LawConfig.Stealth cfg = Law.config().stealth();
		double rate = asleep ? cfg.pickpocketAsleep() : cfg.pickpocketAwake();
		Detection.Stance stance = Visibility.stance(player);
		rate *= FolkTraits.of(folk.resident()).observer().alertness();
		rate *= 0.75 + 0.5 * stance.light() / 15.0;
		rate *= 1.0 + player.getArmorValue() / 40.0;
		rate *= 1.0 - PlayerLaw.of(player).pickpocketSkill() * 0.02;
		return rate;
	}

	public static void action(ServerPlayer player, LawPayloads.PocketAction action) {
		Session s = ACTIVE.get(player.getUUID());
		if (s == null || s.id != action.session()) {
			return;
		}
		switch (action.action()) {
			case LawPayloads.PocketAction.LIFT -> {
				if (action.slot() >= 0 && action.slot() < s.items.size() && !s.taken[action.slot()]) {
					s.model.lift(action.slot(), s.items.get(action.slot()).weight());
				}
			}
			case LawPayloads.PocketAction.STOP -> s.model.cancelLift();
			default -> end(player, s, false);
		}
	}

	/** Every tick: the clock, lifts completing, the mark noticing, the player or the mark moving off. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		NpcService npc = HearthServices.npc();
		for (Iterator<Map.Entry<UUID, Session>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Session> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Session s = e.getValue();
			FolkHandle folk = npc == null ? null : npc.folk(s.resident);
			Settlement settlement = folk == null ? null : folk.settlement();
			if (player == null || folk == null || settlement == null) {
				it.remove();
				continue;
			}
			s.age++;
			if (player.position().distanceToSqr(s.start) > 1.5 * 1.5 || player.distanceTo(folk.entity()) > 3.0 || !player.isAlive()) {
				it.remove();
				finish(player, s, settlement, folk, false);
				continue;
			}
			int done = s.model.tick(0.05);
			if (done >= 0) {
				give(player, s, settlement, done);
			}
			if (!s.model.caught() && s.age % 20 == 0) {
				List<Witnesses.Witness> seen = Witnesses.find(player.level(), player, 0.0);
				if (seen.stream().anyMatch(w -> !w.folk().resident().id.equals(s.resident))) {
					// someone else saw the hand go in
					caught(player, s, settlement, folk, true);
					it.remove();
					continue;
				}
				if (!seen.isEmpty()) {
					// the mark half-turned and glimpsed something
					s.model.alarm(0.35);
				}
			}
			if (s.model.caught()) {
				it.remove();
				caught(player, s, settlement, folk, false);
				continue;
			}
			if (done >= 0 || s.age % UPDATE_TICKS == 0) {
				send(player, s, settlement, LawPayloads.Pocket.OPEN);
			}
		}
	}

	private static void give(ServerPlayer player, Session s, Settlement settlement, int slot) {
		Pockets.PocketItem item = s.items.get(slot);
		Resident r = settlement.resident(s.resident);
		if (r == null || s.taken[slot]) {
			return;
		}
		s.taken[slot] = true;
		Pockets.markTaken(r, s.period, slot);
		settlement.markDirty();
		ServerLevel level = player.level();
		Owner owner = new Owner(settlement.id(), r.id, null, Owner.Kind.HOUSEHOLD);
		switch (item.kind()) {
			case COIN -> {
				EconomyService economy = HearthServices.economy();
				if (economy != null) {
					economy.pay(player, item.bits());
					s.value += item.bits();
				}
			}
			case KEY -> {
				ItemStack key = LawItems.key(Locks.keyData(settlement, s.keyPlot, r));
				StolenGoods.mark(key, owner, r.fullName(), settlement.name(), Law.day(level), player.getUUID());
				player.getInventory().placeItemBackInInventory(key, net.minecraft.util.Prediction.SERVER_ONLY);
				s.value += 5;
			}
			case ITEM -> {
				ItemStack stack = stack(item);
				if (!stack.isEmpty()) {
					StolenGoods.mark(stack, owner, r.fullName(), settlement.name(), Law.day(level), player.getUUID());
					s.value += Law.value(settlement, stack);
					player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
		}
		level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BUNDLE_REMOVE_ONE, net.minecraft.sounds.SoundSource.PLAYERS, 0.3F, 1.3F);
	}

	private static void caught(ServerPlayer player, Session s, Settlement settlement, FolkHandle folk, boolean bystander) {
		send(player, s, settlement, LawPayloads.Pocket.CAUGHT);
		ServerLevel level = player.level();
		PlayerLaw.set(player, PlayerLaw.of(player).addPickpocketing());
		Crime crime = new Crime(Offence.PICKPOCKETING, player.getUUID(), settlement.id(), player.blockPosition(), s.resident, Math.max(1, s.value), false,
			level.getGameTime());
		List<Witnesses.Witness> witnesses = new ArrayList<>(Witnesses.find(level, player, 0.0));
		if (!bystander) {
			// they felt the hand: awake now and looking right at you
			witnesses.removeIf(w -> w.folk().resident().id.equals(s.resident));
			witnesses.add(0, new Witnesses.Witness(folk, FolkTraits.of(folk.resident()), 1.0, 1.0));
		}
		if (!Law.exempt(player)) {
			CrimeDesk.commitSeen(level, settlement, crime, witnesses);
		}
		player.sendOverlayMessage(Component.translatable("hearth.law.pocket.caught", folk.resident().fullName()).withStyle(ChatFormatting.RED));
	}

	private static void end(ServerPlayer player, Session s, boolean caught) {
		ACTIVE.remove(player.getUUID());
		NpcService npc = HearthServices.npc();
		FolkHandle folk = npc == null ? null : npc.folk(s.resident);
		if (folk != null) {
			finish(player, s, folk.settlement(), folk, caught);
		}
	}

	/** Slipped away: if anything was taken it is a theft nobody saw, to be missed later. */
	private static void finish(ServerPlayer player, Session s, Settlement settlement, FolkHandle folk, boolean caught) {
		send(player, s, settlement, LawPayloads.Pocket.DONE);
		boolean tookAnything = false;
		for (boolean t : s.taken) {
			tookAnything |= t;
		}
		if (!caught && tookAnything && s.value > 0) {
			PlayerLaw.set(player, PlayerLaw.of(player).addPickpocketing());
			Crime crime = new Crime(Offence.PICKPOCKETING, player.getUUID(), settlement.id(), player.blockPosition(), s.resident, s.value, false,
				player.level().getGameTime());
			if (!Law.exempt(player)) {
				CrimeDesk.commitSeen(player.level(), settlement, crime, List.of());
			}
		}
	}

	private static void send(ServerPlayer player, Session s, Settlement settlement, int state) {
		Resident r = settlement.resident(s.resident);
		List<ItemStack> shown = new ArrayList<>(s.items.size());
		for (int i = 0; i < s.items.size(); i++) {
			shown.add(s.taken[i] ? ItemStack.EMPTY : display(s.items.get(i), settlement, r, s.keyPlot));
		}
		LawPayloads.Pocket payload = new LawPayloads.Pocket(s.id, r == null ? "" : r.fullName(), s.asleep, shown, (float) s.model.awareness(),
			(float) s.model.awarenessRate(), s.model.lifting(), (float) s.model.liftProgress(), (float) s.model.liftRate(), state);
		if (ServerPlayNetworking.canSend(player, LawPayloads.Pocket.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** How a pocket item looks in the screen: coin as the economy's coins, the key as a key. */
	private static ItemStack display(Pockets.PocketItem item, Settlement s, @Nullable Resident r, int keyPlot) {
		return switch (item.kind()) {
			case COIN -> coins(item.bits());
			case KEY -> r == null ? new ItemStack(LawItems.KEY) : LawItems.key(Locks.keyData(s, keyPlot, r));
			case ITEM -> stack(item);
		};
	}

	/** A purse shown as the largest coin it holds (the economy's coin items, looked up by id so the modules stay apart). */
	static ItemStack coins(long bits) {
		String id = bits >= 100 ? "gold_crown" : bits >= 10 ? "iron_mark" : "copper_bit";
		long per = bits >= 100 ? 100 : bits >= 10 ? 10 : 1;
		Item item = BuiltInRegistries.ITEM.getValue(Hearth.id(id));
		if (item == Items.AIR) {
			item = Items.GOLD_NUGGET;
		}
		ItemStack stack = new ItemStack(item, (int) Math.clamp(bits / per, 1, 64));
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable("hearth.law.pocket.purse", bits)
			.withStyle(st -> st.withItalic(false)));
		return stack;
	}

	private static ItemStack stack(Pockets.PocketItem item) {
		Identifier id = Identifier.tryParse(item.item());
		Item it = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, item.count());
	}

	public static void forget(UUID player) {
		ACTIVE.remove(player);
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
