package dev.voidmc.hearth.npc.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.service.Escorts;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.life.FamilyLife;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Companions (docs/LIVING_WORLD_2.md §8.8): the wage cycle and leaving unpaid, folding at logout and returning at login
 * (the pin and the presence rule), never crossing dimensions, and death removing the resident and starting the funeral.
 */
class CompanionTest {
	private static final PlaceConfig CFG = PlaceConfig.DEFAULTS;
	private static final UUID PLAYER = new UUID(7, 7);

	@org.junit.jupiter.api.BeforeAll
	static void boot() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	@AfterEach
	void clear() {
		Escorts.clear();
	}

	private static Resident resident(String name, JobType job, Trait... traits) {
		Resident r = new Resident(UUID.nameUUIDFromBytes(name.getBytes()), name, "Brodsky", false, AgeStage.ADULT, -9000, job, 1, 2, 7, new CompoundTag());
		NpcData.of(r).setTraits(traits.length == 0 ? EnumSet.noneOf(Trait.class) : EnumSet.of(traits[0], traits)); // (duplicates collapse)
		return r;
	}

	@Test
	void theWageIsTakenAtDawnAndTheUnpaidGoHome() {
		Resident r = resident("Jan", JobType.GUARD, Trait.BRAVE);
		long wage = CompanionRules.wage(r, CFG);
		assertEquals(Math.round(CFG.wage(Station.of(JobType.GUARD)) * 2), wage, "twice their station's wage");
		assertEquals(wage * 5, CompanionRules.fee(r, CFG), "the fee is five days' wage");
		CompanionState st = CompanionState.hire(r, PLAYER, 10, wage);
		assertEquals(CompanionRules.Payday.SETTLED, CompanionRules.payday(st, 10, true, b -> true, CFG), "the hiring day is paid by the fee");
		long[] taken = {0};
		assertEquals(CompanionRules.Payday.PAID, CompanionRules.payday(st, 11, true, b -> {
			taken[0] += b;
			return true;
		}, CFG));
		assertEquals(wage, taken[0]);
		// offline days are neither charged nor counted against them
		assertEquals(CompanionRules.Payday.SKIPPED, CompanionRules.payday(st, 12, false, b -> {
			throw new AssertionError("charged while offline");
		}, CFG));
		assertEquals(0, st.unpaid());
		assertEquals(CompanionRules.Payday.UNPAID, CompanionRules.payday(st, 13, true, b -> false, CFG));
		assertEquals(1, st.unpaid());
		assertEquals(CompanionRules.Payday.LEFT, CompanionRules.payday(st, 14, true, b -> false, CFG), "two days unpaid and they go home");
		// paid again before the second day, the count starts over
		CompanionState st2 = CompanionState.hire(resident("Ota", JobType.HUNTER, Trait.GREEDY), PLAYER, 0, 4);
		CompanionRules.payday(st2, 1, true, b -> false, CFG);
		assertEquals(CompanionRules.Payday.PAID, CompanionRules.payday(st2, 2, true, b -> true, CFG));
		assertEquals(0, st2.unpaid());
	}

	@Test
	void theStateSavesWithTheResident() {
		Resident r = resident("Vit", JobType.GUARD, Trait.BRAVE);
		CompanionState st = CompanionState.hire(r, PLAYER, 3, 8);
		st.setFollow(CompanionState.Follow.WAIT);
		st.setWaitAt(new BlockPos(10, 64, -5));
		st.setFight(false);
		st.addLoyalty(12);
		var json = Resident.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
		Resident back = Resident.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		CompanionState b = CompanionState.of(back);
		assertNotNull(b);
		assertEquals(PLAYER, b.player());
		assertEquals(CompanionState.Follow.WAIT, b.follow());
		assertEquals(new BlockPos(10, 64, -5), b.waitAt());
		assertFalse(b.fight());
		assertEquals(12, b.loyalty());
		CompanionState.release(back);
		assertNull(CompanionState.of(back));
	}

	@Test
	void innkeepersNameTheBraveAndTheGreedyGuardsAndHuntersFirst() {
		SimpleSettlement s = new SimpleSettlement(1, "Ostrov", Culture.TEMPERATE, BlockPos.ZERO, -100);
		Resident farmer = resident("Farmer", JobType.FARMER, Trait.BRAVE);
		Resident guard = resident("Guard", JobType.GUARD, Trait.GREEDY);
		Resident hunter = resident("Hunter", JobType.HUNTER, Trait.BRAVE);
		Resident shy = resident("Shy", JobType.GUARD, Trait.SHY);
		Resident reeve = resident("Reeve", JobType.REEVE, Trait.BRAVE);
		Resident child = resident("Child", JobType.NONE, Trait.BRAVE);
		child.age = AgeStage.CHILD;
		Resident hired = resident("Hired", JobType.GUARD, Trait.BRAVE);
		CompanionState.hire(hired, PLAYER, 0, 8);
		for (Resident r : List.of(farmer, guard, hunter, shy, reeve, child, hired)) {
			s.addResident(r);
		}
		List<Resident> named = CompanionRules.candidates(s, 3);
		assertEquals(List.of(guard, hunter, farmer), named, "guards and hunters first; no office holders, children, the timid or the hired");
		assertEquals(2, CompanionRules.candidates(s, 2).size());
		assertFalse(CompanionRules.willing(s, reeve));
		assertFalse(CompanionRules.willing(s, hired));
	}

	@Test
	void theyFoldAtLogoutAndComeBackAtLoginButNeverCrossDimensions() {
		UUID id = UUID.randomUUID();
		// pinned while hired: the embodiment manager never folds them, wherever they walk
		Escorts.pin(id, PLAYER);
		assertTrue(Escorts.pinned(id));
		assertEquals(List.of(id), Escorts.pinnedTo(PLAYER));
		// at logout the pin goes and their folk is folded; they wait as a record
		Escorts.unpin(id);
		assertFalse(Escorts.pinned(id));
		// back online in the same world, not embodied: beside the player again
		assertEquals(CompanionRules.Presence.EMBODY_NEAR, CompanionRules.presence(true, false, false, false, 0, false, false, true, CFG));
		// already at their side: left alone; far and the server struggling: folded to wait
		assertEquals(CompanionRules.Presence.KEEP, CompanionRules.presence(true, true, false, false, 0, false, true, true, CFG));
		assertEquals(CompanionRules.Presence.SHED, CompanionRules.presence(true, true, false, false, 0, true, true, true, CFG));
		// no room under the shrunken cap: they wait
		assertEquals(CompanionRules.Presence.KEEP, CompanionRules.presence(true, false, false, false, 0, false, false, false, CFG));
		// told to wait: only where they were left, once the player is near it
		assertEquals(CompanionRules.Presence.KEEP, CompanionRules.presence(true, false, true, false, 0, false, false, true, CFG));
		assertEquals(CompanionRules.Presence.EMBODY_AT_WAIT, CompanionRules.presence(true, false, true, true, 0, false, false, true, CFG));
		// the player in another dimension: they never follow; they wait, and after a day go home
		assertEquals(CompanionRules.Presence.KEEP, CompanionRules.presence(false, false, false, false, 100, false, false, true, CFG));
		assertEquals(CompanionRules.Presence.KEEP, CompanionRules.presence(false, true, false, false, CFG.companions().waitTicks(), false, false, true, CFG));
		assertEquals(CompanionRules.Presence.GO_HOME, CompanionRules.presence(false, true, false, false, CFG.companions().waitTicks() + 1, false, false, true,
			CFG));
	}

	@Test
	void deathRemovesTheResidentAndTheirFamilyMournsThem() {
		SimpleSettlement s = new SimpleSettlement(2, "Ledec", Culture.TEMPERATE, BlockPos.ZERO, -100);
		Resident jan = resident("Jan", JobType.GUARD, Trait.BRAVE);
		Resident wife = resident("Marta", JobType.BAKER);
		Resident son = resident("Pavel", JobType.NONE);
		Resident stranger = resident("Stranger", JobType.FARMER);
		NpcData.of(jan).setSpouse(wife.id);
		NpcData.of(wife).setSpouse(jan.id);
		NpcData.of(son).setParents(List.of(jan.id, wife.id));
		for (Resident r : List.of(jan, wife, son, stranger)) {
			s.addResident(r);
		}
		CompanionState.hire(jan, PLAYER, 0, 8);
		s.setResidentAway(jan.id, Long.MAX_VALUE, "companion");
		// who holds it against the player who led him to it
		List<Resident> kin = CompanionRules.kin(s, jan);
		assertEquals(Set.of(wife.id, son.id), Set.copyOf(kin.stream().map(r -> r.id).toList()));
		// the death: the resident is removed for good, and the town gives the funeral as for any death
		assertTrue(s.removeResident(jan.id));
		assertNull(s.resident(jan.id));
		SocialLedger ledger = new SocialLedger();
		List<FamilyLife.Posting> posts = new ArrayList<>();
		FamilyLife.gone(s, ledger, jan, "slain", 50, SocialConfig.Family.DEFAULTS, posts::add);
		assertEquals("hearth.social.notice.slain", posts.get(0).key());
		assertEquals(SocialLedger.FamilyEvent.Kind.FUNERAL, ledger.family.get(0).kind());
		assertTrue(ledger.family.get(0).family().containsAll(List.of(wife.id, son.id)));
	}

	@Test
	void companionsTakeThePlayersCrimesToHeart() {
		assertTrue(CompanionRules.crimeLoyalty(true, false, 4) <= -30, "theft from their own town costs a good deal");
		assertTrue(CompanionRules.crimeLoyalty(true, true, 4) <= -30, "even the greedy mind their own town robbed");
		assertEquals(0, CompanionRules.crimeLoyalty(false, true, 4), "the greedy do not mind elsewhere");
		assertTrue(CompanionRules.crimeLoyalty(false, false, 4) < 0);
		CompanionState st = CompanionState.hire(resident("Ota", JobType.HUNTER, Trait.BRAVE), PLAYER, 0, 4);
		st.addLoyalty(CompanionRules.crimeLoyalty(true, false, 10));
		assertTrue(CompanionRules.leaves(st, CFG), "a murder in their own town and they are gone");
	}

	@Test
	void thePlayersRosterSavesAndLoads() {
		CompanionRoster roster = CompanionRoster.EMPTY.with(new CompanionRoster.Entry(4, net.minecraft.world.level.Level.OVERWORLD, PLAYER));
		var json = CompanionRoster.CODEC.encodeStart(JsonOps.INSTANCE, roster).getOrThrow();
		CompanionRoster back = CompanionRoster.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(1, back.size());
		assertTrue(back.has(PLAYER));
		assertEquals(0, back.without(PLAYER).size());
		var cfg = PlaceConfig.CODEC.encodeStart(JsonOps.INSTANCE, CFG).getOrThrow();
		assertEquals(CFG, PlaceConfig.CODEC.parse(JsonOps.INSTANCE, cfg).getOrThrow());
	}
}
