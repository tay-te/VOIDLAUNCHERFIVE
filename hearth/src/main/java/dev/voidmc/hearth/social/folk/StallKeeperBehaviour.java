package dev.voidmc.hearth.social.folk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.social.life.Dressings;
import dev.voidmc.hearth.social.life.TownLife;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * On market day the town's merchants and makers keep the stalls set up in the square: each walks behind a counter,
 * holds up their wares and calls them out to passers-by. A stall goes to the merchants first, then the trades with
 * something to sell (bakers, butchers, fishers, tailors, smiths...), in a fixed order so the same folk keep the same
 * stall all day.
 */
public final class StallKeeperBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("social/stall_keeper");
	public static final int PRIORITY = 90;
	/** The trades that keep a stall, best first. */
	static final List<JobType> TRADES = List.of(JobType.MERCHANT, JobType.TRADER, JobType.BAKER, JobType.BUTCHER, JobType.FISHER, JobType.FARMER,
		JobType.TAILOR, JobType.SMITH, JobType.CARPENTER, JobType.BREWER, JobType.CHANDLER, JobType.BEEKEEPER, JobType.HUNTER, JobType.HERDER,
		JobType.DYER, JobType.MASON);

	private static final Map<String, Assignment> ASSIGNMENTS = new HashMap<>();
	private final Map<UUID, State> states = new HashMap<>();

	record Assignment(int stalls, List<UUID> keepers) {
	}

	static final class State {
		final String event;
		final Dressings.Stall stall;
		long nextMove;
		long nextCall;

		State(String event, Dressings.Stall stall) {
			this.event = event;
			this.stall = stall;
		}
	}

	/** Which stall a resident keeps on this market day, or -1. */
	public static int stallOf(Settlement s, TownEvent e, Resident r) {
		List<Dressings.Stall> stalls = TownLife.stalls(s, e.key());
		if (stalls.isEmpty() || r.age != AgeStage.ADULT || !TRADES.contains(r.job)) {
			return -1;
		}
		String key = s.id() + "/" + e.key();
		Assignment a = ASSIGNMENTS.get(key);
		if (a == null || a.stalls != stalls.size()) {
			List<Resident> keepers = new ArrayList<>();
			for (Resident o : s.residents()) {
				if (o.age == AgeStage.ADULT && TRADES.contains(o.job)) {
					keepers.add(o);
				}
			}
			keepers.sort(Comparator.<Resident>comparingInt(o -> TRADES.indexOf(o.job)).thenComparing(o -> o.id));
			List<UUID> ids = new ArrayList<>();
			for (int i = 0; i < Math.min(stalls.size(), keepers.size()); i++) {
				ids.add(keepers.get(i).id);
			}
			a = new Assignment(stalls.size(), ids);
			if (ASSIGNMENTS.size() > 256) {
				ASSIGNMENTS.clear();
			}
			ASSIGNMENTS.put(key, a);
		}
		return a.keepers.indexOf(r.id);
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	private static @Nullable TownEvent market(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		if (!s.embodied() || !SocialModule.config().market().enabled()) {
			return null;
		}
		return TownLife.current(s, TownEvent.Kind.MARKET);
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		TownEvent e = market(folk);
		return e != null && folk.awake() && stallOf(folk.settlement(), e, folk.resident()) >= 0;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		TownEvent e = market(folk);
		if (e == null) {
			return;
		}
		int i = stallOf(folk.settlement(), e, folk.resident());
		List<Dressings.Stall> stalls = TownLife.stalls(folk.settlement(), e.key());
		if (i < 0 || i >= stalls.size()) {
			return;
		}
		State st = new State(e.key(), stalls.get(i));
		this.states.put(folk.resident().id, st);
		folk.hold(new ItemStack(ware(folk.resident().job)));
		folk.moveTo(st.stall.keeper(), 0.6);
		st.nextMove = folk.gameTime() + 80;
		st.nextCall = folk.gameTime() + 100;
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		State st = this.states.get(folk.resident().id);
		if (st == null) {
			return false;
		}
		long t = folk.gameTime();
		if ((t & 31) == 0) {
			TownEvent e = market(folk);
			if (e == null || !e.key().equals(st.event) || TownLife.stalls(folk.settlement(), e.key()).isEmpty()) {
				return false;
			}
		}
		if (folk.entity().blockPosition().distSqr(st.stall.keeper()) > 1.5 * 1.5) {
			if (t >= st.nextMove) {
				folk.moveTo(st.stall.keeper(), 0.6);
				st.nextMove = t + 80;
			}
			return true;
		}
		folk.stop();
		ServerLevel level = (ServerLevel) folk.entity().level();
		Player near = level.getNearestPlayer(folk.entity(), 10);
		if (near != null) {
			folk.lookAt(near);
		} else {
			folk.lookAt(st.stall.customer());
		}
		if (t >= st.nextCall && near != null) {
			Item ware = ware(folk.resident().job);
			int n = 1 + (int) Math.floorMod(TownCalendar.mix(folk.resident().id.hashCode() + t / 400), 5L);
			folk.say(Component.translatable("hearth.social.line.stall." + n, new ItemStack(ware).getHoverName()), null);
			folk.swing();
			st.nextCall = t + 400 + Math.floorMod(TownCalendar.mix(t), 400L);
		}
		return true;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		if (this.states.remove(folk.resident().id) != null) {
			folk.hold(ItemStack.EMPTY);
		}
	}

	/** What a trade holds up at its stall. */
	static Item ware(JobType job) {
		return switch (job) {
			case BAKER -> Items.BREAD;
			case BUTCHER -> Items.COOKED_PORKCHOP;
			case FISHER -> Items.COD;
			case FARMER -> Items.CARROT;
			case TAILOR, DYER -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
			case SMITH -> Items.IRON_INGOT;
			case CARPENTER -> Items.OAK_PLANKS;
			case BREWER -> Items.HONEY_BOTTLE;
			case CHANDLER -> Items.CANDLE;
			case BEEKEEPER -> Items.HONEYCOMB;
			case HUNTER -> Items.RABBIT_HIDE;
			case HERDER -> Items.MILK_BUCKET;
			case MASON -> Items.BRICK;
			default -> Items.EMERALD;
		};
	}
}
