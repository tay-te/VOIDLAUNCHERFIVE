package dev.voidmc.hearth.social.dice;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import dev.voidmc.hearth.social.service.ServiceSites;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.Nullable;

/**
 * Who plays dice, for how much, and where: folk at a gaming table, in a tavern or an inn (or anywhere, by config),
 * grown, not priests, willing to play this player today. A folk's largest stake follows their station and temper and
 * what the town's purse can cover; a folk who has lost too much today stops.
 */
public final class DiceTables {
	public static final ExtendedMenuType<DiceMenu, DiceView> MENU = Registry.register(BuiltInRegistries.MENU, Hearth.id("social/dice"),
		new ExtendedMenuType<>(DiceMenu::new, DiceView.STREAM_CODEC));

	/** Resident → the player they are playing (for the folk behaviour that keeps them at the table). */
	private static final Map<UUID, UUID> PLAYING = new HashMap<>();
	/** Residents who just moved at the table (they gesture). */
	private static final Map<UUID, Boolean> MOVED = new HashMap<>();

	private DiceTables() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(DicePayloads.Action.TYPE, DicePayloads.Action.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DicePayloads.View.TYPE, DicePayloads.View.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DicePayloads.Action.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			if (player.containerMenu instanceof DiceMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
				menu.handle(player, payload);
			}
		}));
	}

	/** The folk's way of playing. */
	public static DicePersonality personality(Resident r) {
		return DicePersonality.of(FolkFacts.traits(r), r.id.getLeastSignificantBits());
	}

	/** Whether this folk would sit down to dice here (a gaming table near, a tavern or inn, or anywhere by config). */
	public static boolean atTable(ServerPlayer player, Settlement s, Resident r) {
		SocialConfig.Dice cfg = SocialModule.config().dice();
		if (cfg.anywhere()) {
			return true;
		}
		BlockPos at = player.blockPosition();
		for (var p : s.pois(PoiType.GAMING_TABLE)) {
			if (p.pos().distSqr(at) <= 8 * 8) {
				return true;
			}
		}
		if (ServiceSites.innkeeper(s, r) || r.job == JobType.ENTERTAINER) {
			return true;
		}
		PlotView here = Venues.plotAt(s, at);
		return here != null && (here.spec().archetype().getPath().equals("commerce/tavern") || here.spec().archetype().getPath().equals("commerce/inn"));
	}

	/** Why this folk will not play the player now (a translation key), or null if they will. */
	public static @Nullable String refusal(ServerPlayer player, @Nullable Settlement s, @Nullable Resident r) {
		if (s == null || r == null) {
			return "hearth.social.dice.refuse.gone";
		}
		if (r.age != AgeStage.ADULT && r.age != AgeStage.ELDER || r.job == JobType.PRIEST) {
			return "hearth.social.dice.refuse.never";
		}
		StandingService.Title title = Hooks.title(player, s);
		if (title == StandingService.Title.OUTLAW || title == StandingService.Title.DISTRUSTED || Hooks.opinion(r.id, player) < -20) {
			return "hearth.social.dice.refuse.distrust";
		}
		long day = SocialTime.day(TownLife.now(s));
		int net = s.data(SocialModule.LEDGER).diceNet(r.id, day);
		if (net >= 500_000) {
			return "hearth.social.dice.refuse.cheat";
		}
		long most = maxStake(s, r);
		if (most > 0 && -net >= SocialModule.config().dice().dailyLossLimit() * most) {
			return "hearth.social.dice.refuse.broke";
		}
		if (PLAYING.containsKey(r.id) && !PLAYING.get(r.id).equals(player.getUUID())) {
			return "hearth.social.dice.refuse.busy";
		}
		return null;
	}

	/** The most this folk will stake: by station and temper, never more than a quarter of the town's purse. */
	public static long maxStake(Settlement s, Resident r) {
		int base = switch (r.station) {
			case POOR -> 5;
			case PEASANT -> 10;
			case ARTISAN -> 25;
			case MERCHANT -> 60;
			case CLERGY -> 0;
			case SCHOLAR -> 15;
			case OFFICIAL -> 30;
			case GENTRY -> 100;
			case LORD -> 250;
		};
		long most = Math.round(base * personality(r).stakeFactor);
		if (base > 0) {
			// anyone who plays at all will put up the smallest stake
			int smallest = Integer.MAX_VALUE;
			for (int st : SocialModule.config().dice().stakes()) {
				if (st > 0) {
					smallest = Math.min(smallest, st);
				}
			}
			if (smallest != Integer.MAX_VALUE) {
				most = Math.max(most, smallest);
			}
		}
		return Math.max(0, Math.min(most, s.treasury() / 4));
	}

	/** The stakes offered: the config's, up to the folk's limit and the player's purse; just 0 without an economy. */
	public static List<Integer> stakes(ServerPlayer player, @Nullable Settlement s, @Nullable Resident r) {
		List<Integer> out = new ArrayList<>();
		if (Hooks.economy() == null || s == null || r == null) {
			out.add(0);
			return out;
		}
		long most = maxStake(s, r);
		long purse = Hooks.balance(player);
		for (int st : SocialModule.config().dice().stakes()) {
			if (st > 0 && st <= most && st <= purse) {
				out.add(st);
			}
		}
		if (out.isEmpty()) {
			out.add(0); // a friendly game for nothing
		}
		return out;
	}

	/** Opens the table with this folk; returns false (with the refusal said) if they will not play. */
	public static boolean open(ServerPlayer player, Settlement s, Resident r) {
		String refusal = refusal(player, s, r);
		if (refusal != null) {
			Hooks.reply(player, r, s, Component.translatable(refusal));
			return false;
		}
		List<Integer> stakes = stakes(player, s, r);
		DiceMenu[] made = new DiceMenu[1];
		player.openMenu(new ExtendedMenuProvider<DiceView>() {
			@Override
			public DiceView getScreenOpeningData(ServerPlayer p) {
				return made[0] != null ? made[0].view() : new DiceMenu(-1, p, s, r, stakes).view();
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("hearth.social.dice.title", r.fullName());
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				made[0] = new DiceMenu(id, player, s, r, stakes);
				return made[0];
			}
		});
		if (made[0] != null) {
			PLAYING.put(r.id, player.getUUID());
		}
		return made[0] != null;
	}

	/** A line from the folk for a moment of the game, in their temper. */
	static Component say(DicePersonality p, String moment, RandomGenerator random) {
		int variants = switch (moment) {
			case "folk_bank", "folk_bust", "player_bust" -> 2;
			default -> 1;
		};
		int n = 1 + random.nextInt(variants);
		return Component.translatable("hearth.social.dice.say." + p.id() + "." + moment + "." + n);
	}

	/** The player the resident is playing, or null. */
	public static @Nullable UUID opponentOf(UUID resident) {
		return PLAYING.get(resident);
	}

	static void folkMoved(UUID resident) {
		MOVED.put(resident, Boolean.TRUE);
	}

	/** Whether the folk moved at the table since last asked. */
	public static boolean takeMoved(UUID resident) {
		return MOVED.remove(resident) != null;
	}

	static void ended(UUID resident) {
		PLAYING.remove(resident);
		MOVED.remove(resident);
	}

	public static void clear() {
		PLAYING.clear();
		MOVED.clear();
	}
}
