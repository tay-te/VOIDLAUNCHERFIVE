package dev.voidmc.hearth.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.social.life.Bells;
import dev.voidmc.hearth.social.life.Notices;
import dev.voidmc.hearth.social.calendar.SocialTime;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class SocialDataTest {
	@Test
	void ledgerRoundTrips() {
		SocialLedger l = new SocialLedger();
		UUID a = SocialTestKit.id(1);
		UUID b = SocialTestKit.id(2);
		Notices.post(l, "wedding", 3, 6, "hearth.social.notice.banns", List.of("Ada", "Bo"), 12);
		l.family.add(new SocialLedger.FamilyEvent(SocialLedger.FamilyEvent.Kind.WEDDING, 6, List.of(a, b), List.of("Ada", "Bo"), List.of(), ""));
		l.offices.put("reeve", a);
		l.lastLord = b;
		l.dressings.add(new SocialLedger.Dressing(123456789L, "minecraft:oak_fence", "market:6", 144000));
		l.rooms.add(new SocialLedger.Rental(42L, b, 7));
		l.addDiceNet(a, 6, 25);
		l.waresDay = 6;
		l.waresSold.put("minecraft:apple", 2);
		Tag nbt = SocialLedger.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow();
		SocialLedger back = SocialLedger.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
		assertEquals(l.notices, back.notices);
		assertEquals(l.family, back.family);
		assertEquals(l.offices, back.offices);
		assertEquals(b, back.lastLord);
		assertEquals(l.dressings, back.dressings);
		assertEquals(l.rooms, back.rooms);
		assertEquals(25, back.diceNet(a, 6));
		assertEquals(2, back.waresSold.get("minecraft:apple"));
		// an empty ledger reads from nothing
		SocialLedger empty = SocialLedger.CODEC.parse(NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).getOrThrow();
		assertTrue(empty.notices.isEmpty());
	}

	@Test
	void configRoundTripsAndReadsClockTimes() {
		JsonElement json = SocialConfig.CODEC.encodeStart(JsonOps.INSTANCE, SocialConfig.DEFAULTS).getOrThrow();
		SocialConfig back = SocialConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(SocialConfig.DEFAULTS, back);
		assertEquals("\"07:45\"", SocialConfig.CLOCK.encodeStart(JsonOps.INSTANCE, 7 * 60 + 45).getOrThrow().toString());
		SocialConfig partial = SocialConfig.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
			"{\"market\": {\"open\": \"08:30\"}, \"dice\": {\"target\": 3000}}")).getOrThrow();
		assertEquals(8 * 60 + 30, partial.market().open());
		assertEquals(3000, partial.dice().target());
		assertEquals(SocialConfig.Inn.DEFAULTS, partial.inn());
		assertTrue(SocialConfig.CLOCK.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("25:00")).isError());
	}

	@Test
	void noticesTrimAndTranslateTheirArguments() {
		SocialLedger l = new SocialLedger();
		for (int i = 0; i < 20; i++) {
			Notices.post(l, "birth", i, i, "hearth.social.notice.birth_one", List.of("C" + i, "P"), 12);
		}
		assertEquals(12, l.notices.size());
		assertEquals("C19", l.notices.get(11).args().get(0));
		assertTrue(Notices.has(l, "hearth.social.notice.birth_one", List.of("C19", "P"), 19));
		assertFalse(Notices.has(l, "hearth.social.notice.birth_one", List.of("C0", "P"), 0), "the oldest past notices go first");
		Notices.post(l, "festival", 20, 23, "hearth.social.notice.festival", List.of("@hearth.social.festival.harvest"), 12);
		Component c = Notices.text(l.notices.get(l.notices.size() - 1));
		Object arg = ((TranslatableContents) c.getContents()).getArgs()[0];
		assertTrue(arg instanceof Component inner && inner.getContents() instanceof TranslatableContents t
			&& t.getKey().equals("hearth.social.festival.harvest"));
		assertEquals("hearth.social.notice.festival", Notices.current(l, 22).get(0).key(), "newest first");
	}

	@Test
	void bellsRingAtTheirHoursOnly() {
		SocialConfig.Bells cfg = SocialConfig.Bells.DEFAULTS;
		assertEquals(0, Bells.due(cfg, 5 * 60 + 59, 6 * 60), "dawn");
		assertEquals(1, Bells.due(cfg, 11 * 60 + 58, 12 * 60 + 1), "noon");
		assertEquals(2, Bells.due(cfg, 16 * 60 + 59, 17 * 60), "dusk");
		assertEquals(-1, Bells.due(cfg, 12 * 60 + 1, 12 * 60 + 2));
		assertEquals(-1, Bells.due(cfg, 22 * 60, 6 * 60 + 30), "sleeping through the night rings nothing");
		assertEquals(-1, Bells.due(cfg, -1, 6 * 60));
	}

	@Test
	void townTicksFollowTheSimDay() {
		assertEquals(SocialTime.at(3, 6 * 60), 3 * 24000L);
		assertEquals(3, SocialTime.day(SocialTime.at(3, 2 * 60)), "two in the morning is still the day that began at sunrise");
	}
}
