package dev.voidmc.hearth.social.folk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.Agenda;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Folk go to the town's gatherings: the square on market day, the festival round the stage or the bonfire, the chapel
 * for a wedding (the couple at the altar) and the inn's tables for the feast, the cemetery for a funeral (kin in
 * front, carrying candles), the hall for the lord's court (the lord at the throne). They spread round the venue, face
 * what matters, cheer, talk now and then, and at festivals and feasts one of them plays. Who comes and when is
 * {@link Agenda#attends}; this only walks them there and keeps them busy until it ends.
 */
public final class GatherBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("social/gather");
	public static final int PRIORITY = 80;

	private final Map<UUID, State> states = new HashMap<>();
	/** Event key → the musician's resident id. */
	private final Map<String, UUID> musicians = new HashMap<>();

	static final class State {
		final String event;
		final BlockPos target;
		final BlockPos focus;
		long nextMove;
		long nextAct;
		int beat;
		boolean musician;

		State(String event, BlockPos target, BlockPos focus) {
			this.event = event;
			this.target = target;
			this.focus = focus;
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
		if (!SocialModule.config().life().enabled() || !folk.settlement().embodied()) {
			return false;
		}
		TownEvent e = eventFor(folk);
		return e != null && (folk.awake() || e.kind() == TownEvent.Kind.FUNERAL || e.kind() == TownEvent.Kind.WEDDING);
	}

	private static @Nullable TownEvent eventFor(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		Resident r = folk.resident();
		long now = TownLife.now(s);
		TownEvent e = Agenda.forResident(TownLife.events(s), r, now, SocialModule.config());
		if (e != null && e.kind() == TownEvent.Kind.MARKET && StallKeeperBehaviour.stallOf(s, e, r) >= 0) {
			return null; // keeping a stall instead
		}
		return e;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		TownEvent e = eventFor(folk);
		if (e == null) {
			return;
		}
		Settlement s = folk.settlement();
		Resident r = folk.resident();
		ServerLevel level = (ServerLevel) folk.entity().level();
		Venues.Venue v = TownLife.venue(s, e);
		BlockPos target;
		boolean principal = e.principals().contains(r.id);
		if (principal && e.kind() == TownEvent.Kind.COURT && r.id.equals(e.principals().get(0))) {
			target = Venues.standable(level, v.focus()) != null ? Venues.standable(level, v.focus()) : v.focus();
		} else if (principal || e.kind() == TownEvent.Kind.FUNERAL && e.family().contains(r.id)) {
			// the couple, the court and the mourning family stand close to what matters
			target = Venues.standFor(level, new Venues.Venue(v.focus(), v.focus(), java.util.List.of(v.focus()), 2), r.id, 7);
		} else {
			target = Venues.standFor(level, v, r.id, e.kind().ordinal());
		}
		State st = new State(e.key(), target, v.focus());
		st.musician = (e.kind() == TownEvent.Kind.FESTIVAL || e.kind() == TownEvent.Kind.FEAST) && SocialModule.config().festivals().music()
			&& r.id.equals(musician(s, e));
		this.states.put(r.id, st);
		folk.hold(heldItem(e, r, principal));
		folk.moveTo(target, e.kind() == TownEvent.Kind.FUNERAL ? 0.45 : 0.6);
		st.nextMove = folk.gameTime() + 60;
		st.nextAct = folk.gameTime() + 40 + Math.floorMod(r.id.hashCode(), 80);
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		Resident r = folk.resident();
		State st = this.states.get(r.id);
		if (st == null) {
			return false;
		}
		long t = folk.gameTime();
		if ((t & 31) == 0) {
			TownEvent e = eventFor(folk);
			if (e == null || !e.key().equals(st.event)) {
				return false;
			}
		}
		boolean near = folk.entity().blockPosition().distSqr(st.target) <= 2.5 * 2.5;
		if (!near) {
			if (t >= st.nextMove) {
				if (!folk.moveTo(st.target, 0.6)) {
					// no way there: stay where they are, facing the gathering
					st.nextMove = t + 200;
				} else {
					st.nextMove = t + 80;
				}
			}
			return true;
		}
		folk.stop();
		folk.lookAt(st.focus);
		ServerLevel level = (ServerLevel) folk.entity().level();
		if (st.musician) {
			if (t % 5 == 0) {
				int tune = (int) Math.floorMod(TownCalendar.mix(st.event.hashCode()), 4L);
				Music.play(level, folk.entity().position(), folk.settlement().culture(), tune, st.beat++);
				if (st.beat % 8 == 0) {
					folk.swing();
				}
			}
			return true;
		}
		if (t >= st.nextAct) {
			act(folk, st, kindOf(st.event), level);
			st.nextAct = t + 120 + Math.floorMod(TownCalendar.mix(r.id.hashCode() + t), 240L);
		}
		return true;
	}

	private static void act(NpcService.FolkHandle folk, State st, TownEvent.Kind kind, ServerLevel level) {
		Player near = level.getNearestPlayer(folk.entity(), 12);
		double roll = TownCalendar.chance(folk.resident().id.hashCode(), folk.gameTime(), 3);
		switch (kind) {
			case FESTIVAL, FEAST -> {
				if (roll < 0.5) {
					folk.swing(); // a cheer, a raised cup
				} else if (near != null && roll < 0.65) {
					folk.lookAt(near);
					folk.say(line(kind, folk.resident(), folk.gameTime()), null);
				}
			}
			case WEDDING -> {
				if (roll < 0.25) {
					folk.swing();
				}
			}
			case MARKET -> {
				if (near != null && roll < 0.2) {
					folk.say(line(kind, folk.resident(), folk.gameTime()), null);
				}
			}
			case FUNERAL -> {
				if (near != null && roll < 0.08) {
					folk.say(line(kind, folk.resident(), folk.gameTime()), null);
				}
			}
			case COURT -> {
				if (roll < 0.15) {
					folk.swing();
				}
			}
		}
	}

	static Component line(TownEvent.Kind kind, Resident r, long t) {
		int n = switch (kind) {
			case FESTIVAL -> 8;
			case FEAST -> 5;
			case WEDDING -> 4;
			case MARKET -> 5;
			case FUNERAL -> 4;
			case COURT -> 3;
		};
		int i = 1 + (int) Math.floorMod(TownCalendar.mix(r.id.getLeastSignificantBits() + t / 600), (long) n);
		return Component.translatable("hearth.social.line." + kind.id() + "." + i);
	}

	private static TownEvent.Kind kindOf(String key) {
		String k = key.substring(0, key.indexOf(':'));
		for (TownEvent.Kind kind : TownEvent.Kind.values()) {
			if (kind.id().equals(k)) {
				return kind;
			}
		}
		return TownEvent.Kind.FESTIVAL;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		State st = this.states.remove(folk.resident().id);
		if (st != null) {
			folk.hold(ItemStack.EMPTY);
		}
		if (this.states.isEmpty()) {
			this.musicians.clear();
		}
	}

	/** One musician per festival or feast: an entertainer if the town has one at it, else a cheerful soul. */
	private @Nullable UUID musician(Settlement s, TownEvent e) {
		return this.musicians.computeIfAbsent(e.key(), k -> {
			long now = TownLife.now(s);
			Resident best = null;
			long bestScore = Long.MAX_VALUE;
			for (Resident r : s.residents()) {
				if (r.age != AgeStage.ADULT || !Agenda.attends(e, r, now, SocialModule.config()) || e.principals().contains(r.id)) {
					continue;
				}
				long score = TownCalendar.mix(r.id.getMostSignificantBits() ^ k.hashCode()) >>> 4;
				if (r.job == JobType.ENTERTAINER) {
					score = 0;
				} else if (FolkFacts.has(r, "cheerful") || FolkFacts.has(r, "sociable")) {
					score >>>= 2;
				}
				if (score < bestScore) {
					bestScore = score;
					best = r;
				}
			}
			return best == null ? null : best.id;
		});
	}

	static ItemStack heldItem(TownEvent e, Resident r, boolean principal) {
		return switch (e.kind()) {
			case FESTIVAL -> e.festival() == null ? ItemStack.EMPTY : item(e.festival().held);
			case WEDDING -> principal ? new ItemStack(Items.OXEYE_DAISY) : new ItemStack(Items.POPPY);
			case FEAST -> new ItemStack(r.age == AgeStage.CHILD ? Items.COOKIE : Items.BREAD);
			case FUNERAL -> e.family().contains(r.id) ? new ItemStack(Items.CANDLE) : ItemStack.EMPTY;
			case MARKET -> switch ((int) Math.floorMod(r.id.getLeastSignificantBits(), 4L)) {
				case 0 -> new ItemStack(Items.APPLE);
				case 1 -> new ItemStack(Items.BREAD);
				default -> ItemStack.EMPTY;
			};
			case COURT -> ItemStack.EMPTY;
		};
	}

	static ItemStack item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}
}
