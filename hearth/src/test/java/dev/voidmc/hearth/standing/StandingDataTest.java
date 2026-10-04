package dev.voidmc.hearth.standing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.model.Companies;
import dev.voidmc.hearth.standing.model.Company;
import dev.voidmc.hearth.standing.model.RegionData;
import dev.voidmc.hearth.standing.model.TownLedger;
import dev.voidmc.hearth.standing.rules.DeedRule;
import dev.voidmc.hearth.standing.rules.StandingData;
import dev.voidmc.hearth.standing.rules.StandingRules;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

class StandingDataTest {
	@Test
	void shippedDataLoadsCleanAndCoversTheLaw() {
		StandingData.Builder b = StandingTestKit.shipped();
		assertTrue(b.errors().isEmpty(), "errors: " + b.errors());
		StandingRules rules = b.build();
		for (LawService.Offence o : LawService.Offence.values()) {
			DeedRule r = rules.rule(Deeds.law(o));
			assertTrue(rules.deeds().containsKey(Deeds.law(o)), "no rule for " + Deeds.law(o));
			assertTrue(r.settlement() > 0, Deeds.law(o));
			assertNotNull(rules.line(Deeds.law(o), -30, "Alex", "Ashbrook", new Random(1)), "no words for " + o);
		}
		assertTrue(rules.rule("law:murder").violent());
		assertTrue(rules.rule(Deeds.BATHED).washes());
		for (Title t : Title.values()) {
			assertNotNull(rules.keyed("title_" + t.getSerializedName(), "Alex", "Ashbrook", new Random(2)), "no verdict for " + t);
		}
		for (Culture c : Culture.values()) {
			assertTrue(rules.dress(c.getSerializedName()).size() >= 3, "no dress for " + c);
		}
	}

	@Test
	void rulesFallBackByNamespace() {
		StandingRules rules = StandingTestKit.shipped().build();
		assertEquals(rules.deeds().get("economy:default"), rules.rule("economy:something_new"));
		assertEquals(rules.deeds().get("default"), rules.rule("mymod:thing"));
		assertEquals(DeedRule.DEFAULT, new StandingRules(java.util.Map.of(), java.util.Map.of(), java.util.Map.of()).rule("x:y"));
		String line = rules.line("quest:completed", 10, "Alex", "Ashbrook", new Random(3));
		assertTrue(line.contains("Alex") || line.contains("Ashbrook"), line);
		assertTrue(!line.contains("{"), line);
	}

	@Test
	void packsLayerAndReportBrokenEntries() {
		StandingTestKit.boot();
		StandingData.Builder b = StandingTestKit.shipped()
			.deeds(Hearth.id("extra"), JsonParser.parseString("{\"deeds\": {\"law:theft\": {\"settlement\": 2.0}, \"bad\": {\"settlement\": \"x\"}}}").getAsJsonObject())
			.gossip(Hearth.id("extra"), JsonParser.parseString("{\"lines\": {\"law:theft\": [\"Extra line about {player}.\"]}}").getAsJsonObject())
			.attire(Hearth.id("extra"), JsonParser.parseString("{\"cultures\": {\"temperate\": [\"blue\", \"nonsense\"]}}").getAsJsonObject());
		StandingRules r = b.build();
		assertEquals(2.0, r.rule("law:theft").settlement());
		assertEquals(1.0, r.rule("law:theft").witness() * 2, 1e-9, "unset fields take the defaults");
		assertTrue(r.lines().get("law:theft").size() > 1, "lines add up");
		assertTrue(r.dress("temperate").contains(DyeColor.BLUE));
		assertEquals(2, b.errors().size(), "the bad deed and the bad colour: " + b.errors());
	}

	@Test
	void savedStateRoundTrips() {
		UUID p = UUID.randomUUID();
		TownLedger l = new TownLedger();
		l.entry(p).value = -12.5;
		l.entry(p).title = Title.DISTRUSTED;
		l.addNews(new TownLedger.News(p, "Alex", "law:theft", -30, "Ashbrook", 4, true), 8);
		TownLedger back = roundTrip(TownLedger.CODEC, l);
		assertEquals(-12.5, back.get(p).value);
		assertEquals(Title.DISTRUSTED, back.get(p).title);
		assertEquals("law:theft", back.news().getFirst().kind());

		RegionData r = new RegionData();
		r.schedule(new RegionData.Travelling(p, "Alex", "law:theft", -10, 1, "Ashbrook", 2, 9, 4));
		r.schedule(new RegionData.Travelling(p, "Alex", "law:theft", -5, 1, "Ashbrook", 3, 7, 4));
		r.addFame(p, 10, 10, 30, 4, 500, 60, 8);
		r.addViolence(p, 20, 4, 30);
		Companies.form(r, p, "Wolves", 4, StandingConfig.Companies.DEFAULTS);
		RegionData rb = roundTrip(RegionData.CODEC, r);
		assertEquals(List.of(7L, 9L), rb.road().stream().map(RegionData.Travelling::arrival).toList(), "still ordered by arrival");
		assertEquals(30, rb.fameAt(p, 10, 10, 4, 2000, 60), 1e-6);
		assertEquals(20, rb.violence(p, 4, 30), 1e-9);
		Company c = rb.company("wolves");
		assertNotNull(c);
		assertEquals(List.of(p), c.members());
	}

	@Test
	void companyRules() {
		RegionData r = new RegionData();
		StandingConfig.Companies cfg = StandingConfig.Companies.DEFAULTS;
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		assertEquals(Companies.Result.BAD_NAME, Companies.form(r, a, "x", 0, cfg));
		assertEquals(Companies.Result.FORMED, Companies.form(r, a, "Black Hawks", 0, cfg));
		assertEquals(Companies.Result.NAME_TAKEN, Companies.form(r, b, "black hawks", 0, cfg));
		assertEquals(Companies.Result.NOT_INVITED, Companies.join(r, b, "Black Hawks", cfg));
		assertEquals(Companies.Result.INVITED, Companies.invite(r, a, b, cfg));
		assertEquals(Companies.Result.JOINED, Companies.join(r, b, "Black Hawks", cfg));
		assertEquals(Companies.Result.ALREADY_MEMBER, Companies.form(r, b, "Other", 0, cfg));
		assertEquals(Companies.Result.LEFT, Companies.leave(r, a));
		assertEquals(b, r.company("Black Hawks").leader(), "the oldest member leads when the founder leaves");
		assertEquals(Companies.Result.DISBANDED, Companies.leave(r, b));
		assertEquals(0, r.companies().size());
		assertEquals(Companies.Result.DISABLED, Companies.form(r, a, "Nope", 0, new StandingConfig.Companies(false, 0.25, 8, true)));
	}

	private static <T> T roundTrip(Codec<T> codec, T value) {
		Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
		return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
	}
}
