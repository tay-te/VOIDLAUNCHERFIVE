package dev.voidmc.hearth.law.own;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class OwnershipRulesTest {
	private static final Identifier STYLE = Identifier.fromNamespaceAndPath("hearth", "temperate");

	private static PlotSpec spec(String archetype, int x, int z, int sx, int sz) {
		return new PlotSpec(Identifier.fromNamespaceAndPath("hearth", archetype), STYLE, 1, new BlockPos(x, 64, z), sx, sz, Direction.NORTH, 12, 1,
			new CompoundTag());
	}

	private static Resident resident(String given, AgeStage age, long born, JobType job, int home, int work) {
		return new Resident(UUID.nameUUIDFromBytes(given.getBytes()), given, "Miller", false, age, born, job, home, work, 0, new CompoundTag());
	}

	private static SimpleSettlement.SimplePlot built(SimpleSettlement s, PlotSpec spec) {
		SimpleSettlement.SimplePlot p = s.addPlot(spec);
		p.state = PlotState.BUILT;
		return p;
	}

	@Test
	void homesBelongToTheHeadOfTheHousehold() {
		SimpleSettlement s = new SimpleSettlement(7, "Ashbrook", Culture.TEMPERATE, BlockPos.ZERO, -100);
		var home = built(s, spec("house/cottage", 0, 0, 9, 9));
		Resident child = resident("Pip", AgeStage.CHILD, -10, JobType.NONE, home.id(), -1);
		Resident young = resident("Edda", AgeStage.ADULT, -8000, JobType.BAKER, home.id(), -1);
		Resident old = resident("Oswin", AgeStage.ELDER, -20000, JobType.NONE, home.id(), -1);
		s.addResident(child);
		s.addResident(young);
		s.addResident(old);
		Owner owner = OwnerRules.owner(s, home);
		assertEquals(Owner.Kind.HOUSEHOLD, owner.kind());
		assertEquals(old.id, owner.resident());
		assertEquals(7, owner.settlementId());
	}

	@Test
	void workshopsBelongToTheirWorkerAndHallsToTheTown() {
		SimpleSettlement s = new SimpleSettlement(7, "Ashbrook", Culture.TEMPERATE, BlockPos.ZERO, -100);
		var smithy = built(s, spec("work/smithy", 20, 0, 9, 9));
		var hall = built(s, spec("civic/town_hall", 40, 0, 15, 15));
		var emptyShop = built(s, spec("work/bakery", 60, 0, 9, 9));
		Resident smith = resident("Brand", AgeStage.ADULT, -9000, JobType.SMITH, -1, smithy.id());
		s.addResident(smith);
		assertEquals(new Owner(7, smith.id, null, Owner.Kind.WORKPLACE), OwnerRules.owner(s, smithy));
		assertEquals(new Owner(7, null, null, Owner.Kind.SETTLEMENT), OwnerRules.owner(s, hall));
		assertEquals(Owner.Kind.SETTLEMENT, OwnerRules.owner(s, emptyShop).kind());
	}

	@Test
	void streetsAreEveryonesAndRuinsNobodys() {
		SimpleSettlement s = new SimpleSettlement(7, "Ashbrook", Culture.TEMPERATE, BlockPos.ZERO, -100);
		var road = built(s, spec("infra/road", 0, 0, 40, 3));
		var plaza = built(s, spec("landscape/plaza", 0, 10, 11, 11));
		var ruin = built(s, spec("house/cottage", 30, 30, 9, 9));
		ruin.state = PlotState.ABANDONED;
		var planned = s.addPlot(spec("house/cottage", 50, 50, 9, 9));
		assertNull(OwnerRules.owner(s, road));
		assertNull(OwnerRules.owner(s, plaza));
		assertNull(OwnerRules.owner(s, ruin));
		assertNull(OwnerRules.owner(s, planned));
	}

	@Test
	void boughtPropertyIsThePlayers() {
		SimpleSettlement s = new SimpleSettlement(7, "Ashbrook", Culture.TEMPERATE, BlockPos.ZERO, -100);
		var house = built(s, spec("house/cottage", 0, 0, 9, 9));
		UUID alex = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
		s.setPlotOwner(house.id(), alex);
		Owner o = OwnerRules.owner(s, house);
		assertEquals(Owner.Kind.PLAYER, o.kind());
		assertEquals(alex, o.player());
	}

	@Test
	void categoriesFromIds() {
		assertEquals(ArchetypeCategory.HOUSING, OwnerRules.categoryOf("house/hovel"));
		assertEquals(ArchetypeCategory.LAW, OwnerRules.categoryOf("law/jail"));
		assertEquals(ArchetypeCategory.NOBLE, OwnerRules.categoryOf("noble/keep"));
		assertEquals(ArchetypeCategory.UNDERGROUND, OwnerRules.categoryOf("deep/dwelling"));
		assertEquals(ArchetypeCategory.EXTRACTION, OwnerRules.categoryOf("extract/mine"));
		assertEquals(ArchetypeCategory.INFRASTRUCTURE, OwnerRules.categoryOf("infra/bridge"));
		assertTrue(OwnerRules.isPublic(ArchetypeCategory.LANDSCAPE));
	}

	@Test
	void theMostSpecificPlotWins() {
		SimpleSettlement s = new SimpleSettlement(7, "Ashbrook", Culture.TEMPERATE, BlockPos.ZERO, -100);
		var square = built(s, spec("landscape/plaza", 0, 0, 30, 30));
		var stall = built(s, spec("commerce/market_stall", 10, 10, 4, 4));
		var house = built(s, spec("house/cottage", 40, 0, 9, 9));
		PlotIndex index = PlotIndex.of(s, 100);
		assertSame(stall, index.plotAt(s, new BlockPos(11, 64, 12)));
		assertSame(square, index.plotAt(s, new BlockPos(2, 65, 2)));
		assertSame(house, index.plotAt(s, new BlockPos(44, 70, 4)));
		assertNull(index.plotAt(s, new BlockPos(44, 64 + 13, 4)), "above the plot's height");
		assertSame(house, index.plotAt(s, new BlockPos(44, 60, 4)), "a cellar under the floor");
		assertNull(index.plotAt(s, new BlockPos(100, 64, 100)));
	}

	@Test
	void theIndexFollowsNewPlots() {
		SimpleSettlement s = new SimpleSettlement(8, "Coldbrook", Culture.TEMPERATE, BlockPos.ZERO, -100);
		PlotIndex first = PlotIndex.of(s, 0);
		assertNull(first.plotAt(s, new BlockPos(3, 64, 3)));
		var house = built(s, spec("house/cottage", 0, 0, 9, 9));
		assertSame(house, PlotIndex.of(s, 1).plotAt(s, new BlockPos(3, 64, 3)));
		assertTrue(PlotIndex.indoors(house.spec(), new BlockPos(3, 64, 3)));
		assertTrue(!PlotIndex.indoors(house.spec(), new BlockPos(0, 64, 3)));
	}
}
