package dev.voidmc.hearth.law;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.law.justice.Warrant;
import dev.voidmc.hearth.law.state.LawState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class LawConfigAndStateTest {
	private static final UUID P = UUID.fromString("0000000a-0000-0000-0000-000000000001");
	private static final UUID W = UUID.fromString("0000000b-0000-0000-0000-000000000002");

	@Test
	void defaultsRoundTripAndAnEmptyFileIsTheDefaults() {
		JsonElement json = LawConfig.CODEC.encodeStart(JsonOps.INSTANCE, LawConfig.DEFAULTS).getOrThrow();
		assertEquals(LawConfig.DEFAULTS, LawConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
		assertEquals(LawConfig.DEFAULTS, LawConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
		// the justice block is one flat object even though it is read in two halves
		assertTrue(json.getAsJsonObject().getAsJsonObject("justice").has("guards_attack"));
		assertTrue(json.getAsJsonObject().getAsJsonObject("justice").has("jail"));
	}

	@Test
	void aPartialFileKeepsTheRest() {
		LawConfig c = LawConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
			"{\"mode\":\"gentle\",\"pvp_in_towns\":true,\"justice\":{\"jail_max_minutes\":5,\"outlaws\":false},\"crimes\":{\"trespass\":false}}"))
			.getOrThrow();
		assertEquals(LawConfig.Mode.GENTLE, c.mode());
		assertTrue(c.pvpInTowns());
		assertEquals(5, c.justice().jailMaxMinutes());
		assertFalse(c.justice().outlaws());
		assertEquals(LawConfig.Justice.DEFAULTS.jailBitsPerMinute(), c.justice().jailBitsPerMinute());
		assertFalse(c.crimes().trespass());
		assertTrue(c.crimes().theft());
	}

	@Test
	void gentleModeDropsTheHarshParts() {
		LawConfig gentle = new LawConfig(LawConfig.Mode.GENTLE, true, false, false, true, LawConfig.Crimes.DEFAULTS, LawConfig.Witnesses.DEFAULTS,
			LawConfig.Fines.DEFAULTS, LawConfig.Justice.DEFAULTS, LawConfig.Stealth.DEFAULTS).effective();
		assertFalse(gentle.justice().jail());
		assertFalse(gentle.justice().pillory());
		assertFalse(gentle.justice().outlaws());
		assertFalse(gentle.justice().guardsAttack());
		assertEquals(7, gentle.justice().bountyExpiryDays());
		assertEquals(0.5, gentle.fines().multiplier(), 1e-9);
		assertFalse(gentle.crimes().trespass());
		assertTrue(gentle.crimes().theft());
		LawConfig off = new LawConfig(LawConfig.Mode.OFF, true, false, false, true, LawConfig.Crimes.DEFAULTS, LawConfig.Witnesses.DEFAULTS,
			LawConfig.Fines.DEFAULTS, LawConfig.Justice.DEFAULTS, LawConfig.Stealth.DEFAULTS).effective();
		assertFalse(off.enabled());
		assertFalse(off.crimes().any());
		assertEquals(LawConfig.DEFAULTS, LawConfig.DEFAULTS.effective());
	}

	@Test
	void hoursWrapPastMidnight() {
		assertTrue(Law.within(22 * 60, 21 * 60, 6 * 60));
		assertTrue(Law.within(3 * 60, 21 * 60, 6 * 60));
		assertFalse(Law.within(12 * 60, 21 * 60, 6 * 60));
		assertTrue(Law.within(8 * 60, 7 * 60, 19 * 60));
		assertFalse(Law.within(20 * 60, 7 * 60, 19 * 60));
	}

	@Test
	void settlementStateRoundTrips() {
		LawState s = new LawState();
		Warrant w = s.warrantFor(P, 4);
		w.charge(Offence.THEFT, 50, 4);
		w.charge(Offence.MURDER, 1500, 5);
		w.setOutlaw(true);
		w.setOrigin(77);
		s.addSuspicion(P, 35);
		s.markSeen(P, 5);
		Crime c = new Crime(Offence.PICKPOCKETING, P, 9, new BlockPos(1, 64, -3), W, 12, true, 1234);
		s.pending().add(new LawState.PendingReport(s.nextId(), W, c, 5000));
		s.unsolved().add(new LawState.Unsolved(new Crime(Offence.THEFT, P, 9, BlockPos.ZERO, null, 3, false, 99), 5));
		s.locks().put(BlockPos.ZERO.asLong(), new LawState.LockOverride(BlockPos.ZERO.asLong(), 0, true));

		JsonElement json = LawState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		LawState back = LawState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		Warrant wb = back.warrant(P);
		assertEquals(1550, wb.bounty());
		assertTrue(wb.murder());
		assertTrue(wb.outlaw());
		assertEquals(77, wb.origin());
		assertEquals(Offence.MURDER, wb.worst());
		assertEquals(35, back.suspicion(P));
		assertEquals(5, back.seenDay(P));
		assertEquals(c, back.pending().get(0).crime());
		assertEquals(W, back.pending().get(0).witness());
		assertEquals(1, back.unsolved().size());
		assertTrue(back.locks().get(BlockPos.ZERO.asLong()).broken());
		assertEquals(2, back.nextId());
		// and it can still be changed after loading (the codec's maps are not the live ones)
		back.addSuspicion(W, 10);
		back.warrantFor(W, 6).charge(Offence.TRESPASS, 15, 6);
	}

	@Test
	void settledWarrantsArePruned() {
		LawState s = new LawState();
		s.warrantFor(P, 1).charge(Offence.THEFT, 20, 1);
		s.warrantFor(W, 1);
		s.addSuspicion(W, 10);
		s.addSuspicion(W, -10);
		s.prune();
		assertEquals(1, s.warrants().size());
		assertTrue(s.suspicionMap().isEmpty());
		s.warrant(P).clear();
		s.prune();
		assertTrue(s.isEmpty() || s.warrants().isEmpty());
	}
}
