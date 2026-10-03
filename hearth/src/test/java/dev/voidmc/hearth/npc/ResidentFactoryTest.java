package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.npc.person.HearthResidentFactory;
import dev.voidmc.hearth.npc.person.NameBank;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.person.Trait;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class ResidentFactoryTest {
	private final HearthResidentFactory factory = new HearthResidentFactory(() -> 100);

	@Test
	void everyCultureHasPlentyOfNames() {
		for (Culture culture : Culture.values()) {
			NameBank.Names names = NameBank.of(culture);
			int total = names.male().size() + names.female().size() + names.family().size();
			assertTrue(names.male().size() >= 60 && names.female().size() >= 60 && names.family().size() >= 60,
				culture + " has too few names: " + total);
			assertTrue(total >= 200, culture + ": " + total);
		}
	}

	@Test
	void namesAreDeterministicPerCulture() {
		for (Culture culture : Culture.values()) {
			SimpleSettlement s = new SimpleSettlement(1, "Test", culture, BlockPos.ZERO, 0);
			Resident a = this.factory.create(s, AgeStage.ADULT, RandomSource.create(1234), null, null);
			Resident b = this.factory.create(s, AgeStage.ADULT, RandomSource.create(1234), null, null);
			assertEquals(a.fullName(), b.fullName());
			assertEquals(a.appearance, b.appearance);
			assertEquals(a.id, b.id);
			assertEquals(NpcData.of(a).traits(), NpcData.of(b).traits());
			assertTrue(NameBank.of(culture).family().contains(a.familyName), culture + " family name from its own list");
			assertTrue((a.female ? NameBank.of(culture).female() : NameBank.of(culture).male()).contains(a.givenName));
		}
	}

	@Test
	void culturesSoundDifferent() {
		SimpleSettlement karst = new SimpleSettlement(1, "K", Culture.KARST, BlockPos.ZERO, 0);
		SimpleSettlement alpine = new SimpleSettlement(2, "A", Culture.ALPINE, BlockPos.ZERO, 0);
		Set<String> k = new HashSet<>();
		Set<String> a = new HashSet<>();
		RandomSource r = RandomSource.create(5);
		for (int i = 0; i < 50; i++) {
			k.add(this.factory.create(karst, AgeStage.ADULT, r, null, null).familyName);
			a.add(this.factory.create(alpine, AgeStage.ADULT, r, null, null).familyName);
		}
		k.retainAll(a);
		assertTrue(k.isEmpty(), "no shared family names: " + k);
	}

	@Test
	void traitsNeverContradict() {
		RandomSource r = RandomSource.create(9);
		for (int i = 0; i < 2000; i++) {
			Set<Trait> traits = HearthResidentFactory.rollTraits(r);
			assertTrue(traits.size() >= 2 && traits.size() <= 3);
			for (Trait t : traits) {
				assertFalse(t.opposite() != null && traits.contains(t.opposite()), "contradiction in " + traits);
			}
		}
	}

	@Test
	void childrenTakeAParentsNameAndAreLinked() {
		SimpleSettlement s = new SimpleSettlement(1, "Test", Culture.NORTHERN, BlockPos.ZERO, 0);
		RandomSource r = RandomSource.create(77);
		Resident mother = this.factory.create(s, AgeStage.ADULT, r, null, null);
		Resident father = this.factory.create(s, AgeStage.ADULT, r, null, null);
		s.addResident(mother);
		s.addResident(father);
		for (int i = 0; i < 10; i++) {
			Resident child = this.factory.create(s, AgeStage.CHILD, r, mother, father);
			s.addResident(child);
			assertTrue(child.familyName.equals(mother.familyName) || child.familyName.equals(father.familyName));
			assertEquals(2, NpcData.of(child).parents().size());
			assertTrue(NpcData.of(mother).children().contains(child.id));
			assertTrue(NpcData.of(father).children().contains(child.id));
			assertTrue(child.bornDay > mother.bornDay, "children are younger than their parents");
		}
		assertEquals(father.id, NpcData.of(mother).spouse().orElseThrow());
		assertEquals(mother.id, NpcData.of(father).spouse().orElseThrow());
	}

	@Test
	void childrenInheritColouring() {
		SimpleSettlement s = new SimpleSettlement(1, "Test", Culture.TEMPERATE, BlockPos.ZERO, 0);
		RandomSource r = RandomSource.create(3);
		Resident a = this.factory.create(s, AgeStage.ADULT, r, null, null);
		Resident b = this.factory.create(s, AgeStage.ADULT, r, null, null);
		int skinA = a.appearance & 0xF;
		int skinB = b.appearance & 0xF;
		int inherited = 0;
		for (int i = 0; i < 50; i++) {
			int skin = this.factory.create(s, AgeStage.CHILD, r, a, b).appearance & 0xF;
			if (skin == skinA || skin == skinB || skin == (skinA + skinB) / 2) {
				inherited++;
			}
		}
		assertEquals(50, inherited, "a child's skin tone comes from the parents");
	}
}
