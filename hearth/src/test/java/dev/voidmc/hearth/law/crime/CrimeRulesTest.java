package dev.voidmc.hearth.law.crime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.item.StolenGoods;
import dev.voidmc.hearth.law.justice.Warrant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CrimeRulesTest {
	private static final UUID THIEF = UUID.fromString("00000000-0000-0000-0000-00000000beef");
	private static final UUID VICTIM = UUID.fromString("00000000-0000-0000-0000-00000000cafe");

	private static Crime crime(Offence o, long time) {
		return new Crime(o, THIEF, 1, BlockPos.ZERO, VICTIM, 5, true, time);
	}

	@Test
	void aLootedChestIsOneCrime() {
		assertTrue(CrimeDesk.same(crime(Offence.THEFT, 100), crime(Offence.THEFT, 150)));
		assertFalse(CrimeDesk.same(crime(Offence.THEFT, 100), crime(Offence.THEFT, 100 + CrimeDesk.MERGE_TICKS + 1)));
		assertFalse(CrimeDesk.same(crime(Offence.THEFT, 100), crime(Offence.VANDALISM, 110)));
		assertFalse(CrimeDesk.same(crime(Offence.MURDER, 100), crime(Offence.MURDER, 101)), "every death counts");
	}

	@Test
	void bodiesAreAlwaysFoundAndTrespassNever() {
		assertEquals(1.0, LawDays.discoveryChance(Offence.MURDER));
		assertEquals(0.0, LawDays.discoveryChance(Offence.TRESPASS));
		assertTrue(LawDays.discoveryChance(Offence.VANDALISM) > LawDays.discoveryChance(Offence.LOCKPICKING));
		assertTrue(LawDays.suspicion(new Crime(Offence.MURDER, THIEF, 1, BlockPos.ZERO, null, 0, false, 0))
			> LawDays.suspicion(new Crime(Offence.PICKPOCKETING, THIEF, 1, BlockPos.ZERO, null, 2, false, 0)));
	}

	@Test
	void stolenMarksFade() {
		assertFalse(StolenGoods.faded(10, 16, 7));
		assertTrue(StolenGoods.faded(10, 17, 7));
		assertFalse(StolenGoods.faded(10, 1000, 0), "0 means never");
	}

	@Test
	void wordOfAWarrantReachesTheNeighboursOnly() {
		SimpleSettlement home = new SimpleSettlement(1, "Ashbrook", Culture.TEMPERATE, new BlockPos(0, 64, 0), -100);
		SimpleSettlement near = new SimpleSettlement(2, "Coldbrook", Culture.TEMPERATE, new BlockPos(900, 64, 0), -100);
		SimpleSettlement far = new SimpleSettlement(3, "Farhold", Culture.TEMPERATE, new BlockPos(5000, 64, 0), -100);
		Warrant w = Law.state(home).warrantFor(THIEF, 2);
		w.charge(Offence.THEFT, 100, 2);
		LawDays.spread(new Worlds(List.of(home, near, far)), home, w, 4);
		Warrant there = Law.state(near).warrant(THIEF);
		assertEquals(Math.round(100 * Law.config().justice().spreadFraction()), there.bounty());
		assertEquals(1, there.origin());
		assertTrue(there.spread(), "word that came from elsewhere is not passed on again");
		assertNull(Law.state(far).warrant(THIEF));
	}

	/** Only what spreading reads: the settlements. */
	private record Worlds(List<Settlement> list) implements HearthWorld {
		@Override
		public ServerLevel level() {
			throw new UnsupportedOperationException();
		}

		@Override
		public TerrainProbe terrain() {
			throw new UnsupportedOperationException();
		}

		@Override
		public long day() {
			return 4;
		}

		@Override
		public Collection<? extends Settlement> settlements() {
			return this.list;
		}

		@Override
		public @Nullable Settlement settlement(long id) {
			return this.list.stream().filter(s -> s.id() == id).findFirst().orElse(null);
		}

		@Override
		public @Nullable Settlement settlementAt(BlockPos pos) {
			return null;
		}

		@Override
		public @Nullable Settlement nearest(BlockPos pos, int maxDistance) {
			return null;
		}

		@Override
		public <T> T data(HearthDataKey<T> key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void markDirty() {
		}

		@Override
		public @Nullable Settlement found(BlockPos at, @Nullable Culture culture, @Nullable Settlement parent, String reason) {
			return null;
		}

		@Override
		public @Nullable String whyNotFound(BlockPos at) {
			return "test";
		}

		@Override
		public List<SiteInfo> knownSites(BlockPos near, int radius) {
			return List.of();
		}
	}
}
