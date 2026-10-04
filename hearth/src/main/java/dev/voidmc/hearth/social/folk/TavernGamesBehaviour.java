package dev.voidmc.hearth.social.folk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.Agenda;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.dice.DiceTables;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Evenings in the tavern: from {@link #FROM} to {@link #TO}, two folk sit down at each gaming table and play dice —
 * throwing in turn, groaning and crowing — so a player who walks in finds someone to challenge. Who plays changes from
 * night to night; nobody is pulled from a gathering, a guard post or the altar.
 */
public final class TavernGamesBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("social/tavern_games");
	public static final int PRIORITY = 60;
	static final int FROM = 18 * 60 + 30;
	static final int TO = 23 * 60;

	private static final Map<Long, Night> NIGHTS = new HashMap<>();
	private final Map<UUID, State> states = new HashMap<>();

	/** Tonight's players: resident → their table, for one settlement. */
	record Night(long day, Map<UUID, BlockPos> seats) {
	}

	static final class State {
		final BlockPos table;
		final BlockPos stand;
		long nextMove;
		long nextThrow;

		State(BlockPos table, BlockPos stand) {
			this.table = table;
			this.stand = stand;
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

	static @Nullable BlockPos tableOf(Settlement s, Resident r) {
		long now = TownLife.now(s);
		long day = SocialTime.day(now);
		Night night = NIGHTS.get(s.id());
		if (night == null || night.day != day) {
			night = new Night(day, assign(s, day));
			if (NIGHTS.size() > 256) {
				NIGHTS.clear();
			}
			NIGHTS.put(s.id(), night);
		}
		return night.seats.get(r.id);
	}

	/** Two players for each gaming table, drawn afresh each night. */
	static Map<UUID, BlockPos> assign(Settlement s, long day) {
		Map<UUID, BlockPos> seats = new HashMap<>();
		List<Poi> tables = s.pois(PoiType.GAMING_TABLE);
		if (tables.isEmpty()) {
			return seats;
		}
		List<Resident> pool = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && r.job != JobType.GUARD && r.job != JobType.PRIEST && r.job != JobType.LORD && r.job != JobType.INNKEEPER) {
				pool.add(r);
			}
		}
		pool.sort(Comparator.comparingLong(r -> TownCalendar.mix(r.id.getLeastSignificantBits() ^ day * 0x9E3779B97F4A7C15L)));
		int next = 0;
		for (Poi t : tables) {
			for (int k = 0; k < 2 && next < pool.size(); k++) {
				seats.put(pool.get(next++).id, t.pos());
			}
		}
		return seats;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		if (!s.embodied() || !folk.awake() || !SocialModule.config().dice().enabled()) {
			return false;
		}
		int minute = SocialTime.minute(TownLife.now(s));
		if (minute < FROM || minute >= TO || tableOf(s, folk.resident()) == null || DiceTables.opponentOf(folk.resident().id) != null) {
			return false;
		}
		return Agenda.forResident(TownLife.events(s), folk.resident(), TownLife.now(s), SocialModule.config()) == null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		BlockPos table = tableOf(folk.settlement(), folk.resident());
		if (table == null) {
			return;
		}
		ServerLevel level = (ServerLevel) folk.entity().level();
		BlockPos stand = Venues.standFor(level, new Venues.Venue(table, table, List.of(table), 1), folk.resident().id, 11);
		State st = new State(table, stand);
		this.states.put(folk.resident().id, st);
		folk.moveTo(stand, 0.6);
		st.nextMove = folk.gameTime() + 80;
		st.nextThrow = folk.gameTime() + 60 + Math.floorMod(folk.resident().id.hashCode(), 60);
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		State st = this.states.get(folk.resident().id);
		if (st == null) {
			return false;
		}
		long t = folk.gameTime();
		if ((t & 63) == 0 && !wants(folk)) {
			return false;
		}
		if (folk.entity().blockPosition().distSqr(st.stand) > 2 * 2) {
			if (t >= st.nextMove) {
				folk.moveTo(st.stand, 0.6);
				st.nextMove = t + 100;
			}
			return true;
		}
		folk.stop();
		folk.lookAt(st.table);
		if (t >= st.nextThrow) {
			ServerLevel level = (ServerLevel) folk.entity().level();
			folk.hold(new ItemStack(Items.BONE));
			folk.swing();
			level.playSound(null, st.table, SoundEvents.BONE_BLOCK_HIT, SoundSource.NEUTRAL, 0.5F, 1.4F + level.getRandom().nextFloat() * 0.4F);
			Player near = level.getNearestPlayer(folk.entity(), 10);
			double roll = TownCalendar.chance(folk.resident().id.hashCode(), t, 5);
			if (near != null && roll < 0.18) {
				int n = 1 + (int) Math.floorMod(TownCalendar.mix(t), 4L);
				folk.say(Component.translatable("hearth.social.line.table." + n), null);
			}
			st.nextThrow = t + 160 + Math.floorMod(TownCalendar.mix(folk.resident().id.hashCode() + t), 160L);
		}
		return true;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		if (this.states.remove(folk.resident().id) != null) {
			folk.hold(ItemStack.EMPTY);
		}
	}
}
