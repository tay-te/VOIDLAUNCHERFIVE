package dev.voidmc.hearth.social.folk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.Notices;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import dev.voidmc.hearth.social.office.Office;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The town crier: when a notice goes up in an embodied settlement (by day), the reeve — or the priest, or failing both
 * the eldest grown resident — walks to the notice board with a paper in hand and reads it out to the square.
 */
public final class CrierBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("social/crier");
	public static final int PRIORITY = 95;
	static final int READ_TICKS = 60;

	private final Map<UUID, State> states = new HashMap<>();

	static final class State {
		final SocialLedger.Notice notice;
		final BlockPos board;
		long arrivedAt = -1;
		long nextMove;

		State(SocialLedger.Notice notice, BlockPos board) {
			this.notice = notice;
			this.board = board;
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

	/** Who cries the town's news. */
	static @Nullable UUID crier(Settlement s) {
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		UUID reeve = ledger.offices.get(Office.REEVE.id());
		if (reeve != null) {
			return reeve;
		}
		UUID priest = ledger.offices.get(Office.PRIEST.id());
		if (priest != null) {
			return priest;
		}
		Resident eldest = null;
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && (eldest == null || r.bornDay < eldest.bornDay)) {
				eldest = r;
			}
		}
		return eldest == null ? null : eldest.id;
	}

	private static SocialLedger.@Nullable Notice uncried(Settlement s, long day) {
		for (SocialLedger.Notice n : s.data(SocialModule.LEDGER).notices) {
			if (!n.cried() && day - n.day() <= 1) {
				return n;
			}
		}
		return null;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		if (!s.embodied() || !folk.awake() || !SocialModule.config().life().enabled()) {
			return false;
		}
		int minute = SocialTime.minute(TownLife.now(s));
		if (minute < 7 * 60 || minute > 20 * 60) {
			return false;
		}
		// the cheap test first: most of the time there is nothing to cry
		return uncried(s, SocialTime.day(TownLife.now(s))) != null && folk.resident().id.equals(crier(s));
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		SocialLedger.Notice n = uncried(s, SocialTime.day(TownLife.now(s)));
		if (n == null) {
			return;
		}
		List<Poi> boards = s.pois(PoiType.NOTICE_BOARD);
		BlockPos board = boards.isEmpty() ? Venues.square(s) : Venues.nearest(Venues.positions(boards), s.center());
		BlockPos stand = Venues.standable((net.minecraft.server.level.ServerLevel) folk.entity().level(), board);
		State st = new State(n, stand == null ? board : stand);
		this.states.put(folk.resident().id, st);
		folk.hold(new ItemStack(Items.PAPER));
		folk.moveTo(st.board, 0.7);
		st.nextMove = folk.gameTime() + 80;
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		State st = this.states.get(folk.resident().id);
		if (st == null) {
			return false;
		}
		long t = folk.gameTime();
		if (st.arrivedAt < 0) {
			if (folk.entity().blockPosition().distSqr(st.board) <= 3 * 3 || t > st.nextMove + 1200) {
				st.arrivedAt = t;
				folk.stop();
				folk.lookAt(st.board);
				folk.swing();
				folk.say(Component.translatable("hearth.social.crier", Notices.text(st.notice, SocialTime.day(TownLife.now(folk.settlement())))), null);
				markCried(folk.settlement(), st.notice);
			} else if (t >= st.nextMove) {
				folk.moveTo(st.board, 0.7);
				st.nextMove = t + 80;
			}
			return true;
		}
		return t - st.arrivedAt < READ_TICKS;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		if (this.states.remove(folk.resident().id) != null) {
			folk.hold(ItemStack.EMPTY);
		}
	}

	static void markCried(Settlement s, SocialLedger.Notice n) {
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		int i = ledger.notices.indexOf(n);
		if (i >= 0) {
			ledger.notices.set(i, n.asCried());
			s.markDirty();
		}
	}
}
