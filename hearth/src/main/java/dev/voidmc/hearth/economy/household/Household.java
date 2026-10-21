package dev.voidmc.hearth.economy.household;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.npc.companion.Companions;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.crowd.Role;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.settlement.ResidentClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The player's place: a house with servants (docs/LIVING_WORLD_2.md §8). In a house the player owns, up to three poor
 * adults keep house ({@link Servants}): they live and work there through the sim's daily assignment (a
 * {@link ResidentClaims} source), are paid from the owner's bank account each day and leave when unpaid three days, keep
 * the larder chest stocked with the town's food at the town's price ({@link Larder}), go round the house, shut the doors
 * at dusk, and greet the owner home with the news ({@link ServantWork}). Settings in {@code config/hearth/place.json}
 * ({@link Companions#config()}).
 *
 * <p>Lane B4 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 */
public final class Household {
	/** A town's player households (wages and larder go on while the owner is away). */
	public static final HearthDataKey<HouseBook> HOUSES = HearthDataKey.register(Hearth.id("economy/houses"), HearthDataKey.Scope.SETTLEMENT,
		HouseBook.CODEC, HouseBook::new);
	static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("household");
	/** Owners greeted home since the server started (the tour checks it). */
	public static final java.util.concurrent.atomic.AtomicInteger GREETINGS = new java.util.concurrent.atomic.AtomicInteger();

	private Household() {
	}

	/** Called once from {@code EconomyModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		HouseholdIndex.init();
		ResidentClaims.register(Servants.CLAIMS);
		NpcService npc = HearthServices.npc();
		if (npc != null) {
			npc.registerBehaviour(new ServantWork());
		}
		DialogueProvider.register(new HouseholdDialogue());
		SettlementEvents.DAY_PASSED.register((world, s, day) -> {
			HouseBook book = s.data(HOUSES);
			if (!book.isEmpty()) {
				TIMER.time(() -> daily(world, s, book, day));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServantWork.clear());
	}

	static PlaceConfig cfg() {
		return Companions.config();
	}

	/** The day's books of a town's households, then the larder put away where its chest is loaded. */
	static void daily(HearthWorld world, Settlement s, HouseBook book, long day) {
		EconomyService eco = HearthServices.economy();
		if (eco == null) {
			return;
		}
		ItemStack one = new ItemStack(net.minecraft.world.item.Items.BREAD);
		long unit = Math.max(1, eco.price(s, one, true));
		List<Servants.Left> left = Servants.daily(s, book, day, eco::chargeDeposit, units -> unit * units, (r, bits) -> eco.payResident(s, r, bits),
			cfg());
		s.markDirty();
		MinecraftServer server = world.level().getServer();
		for (Servants.Left l : left) {
			ServerPlayer p = server.getPlayerList().getPlayer(l.player());
			if (p != null) {
				p.sendSystemMessage(Component.translatable("hearth.household.left_" + l.why(), l.name(), s.name()).withStyle(ChatFormatting.GOLD));
			}
		}
		ServerLevel level = world.level().getServer().getLevel(s.dimension());
		if (level == null) {
			return;
		}
		for (HouseBook.House h : book.houses.values()) {
			PlotView plot = s.plot(h.plot);
			if (plot != null && h.pending > 0) {
				Larder.stock(level, s, plot, h);
			}
		}
	}

	/** The owner came home: the servant greets them at the door and tells them what happened while they were away. */
	static void greet(ServerLevel level, Settlement s, PlotView plot, NpcService.FolkHandle folk, ServerPlayer player) {
		HouseBook book = s.data(HOUSES);
		HouseBook.House h = book.house(plot.id());
		if (h == null) {
			return;
		}
		GREETINGS.incrementAndGet();
		Barks.say(folk, "servant_greet", Role.of(folk.resident(), false), player, null);
		HearthWorld w = HearthWorld.of(level);
		long today = w == null ? h.lastVisitDay : w.day();
		for (Component line : report(s, plot, h, player, today)) {
			player.sendSystemMessage(line);
		}
		h.lastVisitDay = today;
		h.spent = 0;
		s.markDirty();
	}

	/** What the servants tell the owner come home: the days away, the town's news since, what folk say, the house's costs. */
	static List<Component> report(Settlement s, PlotView plot, HouseBook.House h, ServerPlayer player, long today) {
		List<Component> out = new ArrayList<>();
		long away = Math.max(0, today - h.lastVisitDay);
		EconomyService eco = HearthServices.economy();
		out.add(Component.translatable(away <= 0 ? "hearth.household.report.today" : "hearth.household.report.away", away)
			.withStyle(ChatFormatting.GOLD));
		int notices = 0;
		var ledger = s.data(dev.voidmc.hearth.social.SocialModule.LEDGER);
		for (var n : dev.voidmc.hearth.social.life.Notices.current(ledger, today)) {
			if (n.day() < h.lastVisitDay || notices >= 3) {
				continue;
			}
			out.add(Component.literal("  • ").append(dev.voidmc.hearth.social.life.Notices.text(n, today)).withStyle(ChatFormatting.GRAY));
			notices++;
		}
		if (notices == 0) {
			out.add(Component.translatable("hearth.household.report.quiet").withStyle(ChatFormatting.GRAY));
		}
		StandingService standing = HearthServices.standing();
		if (standing != null) {
			StandingService.Title t = standing.title(s.id(), player.getUUID());
			out.add(Component.translatable("hearth.household.report.folk", Component.translatable(t.translationKey())).withStyle(ChatFormatting.GRAY));
		}
		if (eco != null && h.spent > 0) {
			out.add(Component.translatable("hearth.household.report.spent", eco.format(h.spent)).withStyle(ChatFormatting.GRAY));
		}
		out.add(Component.translatable(h.larderShort ? "hearth.household.report.larder_short" : "hearth.household.report.larder", h.stocked + h.pending)
			.withStyle(h.larderShort ? ChatFormatting.RED : ChatFormatting.GRAY));
		return out;
	}

	/** The player's servants in a town (by house), for the Household summary. */
	public static List<Resident> servants(Settlement s, UUID player) {
		List<Resident> out = new ArrayList<>();
		for (HouseBook.House h : s.data(HOUSES).houses.values()) {
			if (!h.owner.equals(player)) {
				continue;
			}
			for (UUID id : h.servants) {
				Resident r = s.resident(id);
				if (r != null) {
					out.add(r);
				}
			}
		}
		return out;
	}

	/** Food units the stockpile could spare for a larder (for the dialogue's word on it). */
	static int spare(Settlement s) {
		return Math.max(0, s.stockpile().get(Goods.FOOD) - 3 * s.population());
	}
}
