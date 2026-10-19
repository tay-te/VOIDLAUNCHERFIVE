package dev.voidmc.hearth.npc.talk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.haggle.Haggle;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.standing.StandingConfig;
import dev.voidmc.hearth.standing.logic.Titles;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** What the server adds for the Hub's screens (docs/OVERLAY.md): the conversation detail, option kinds, the haggle band. */
class HubPayloadsTest {
	static {
		dev.voidmc.hearth.testing.ItemBoot.boot();
	}

	private static RegistryFriendlyByteBuf buf() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
	}

	@Test
	void theDetailRoundTripsAndCapsItsLists() {
		List<TalkPayloads.OptionInfo> infos = new ArrayList<>();
		for (int i = 0; i < 70; i++) {
			infos.add(new TalkPayloads.OptionInfo("test:o" + i, "persuade", 60, i, "mettle:speech", 4));
		}
		List<String> traits = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			traits.add("t" + i);
		}
		TalkPayloads.Detail d = new TalkPayloads.Detail(42, "hearth.npc.job.baker", "commerce/bakery", traits, -5, 30, "standing:amends", 10, 24, "known",
			"friend", 40, 600, 7, infos);
		RegistryFriendlyByteBuf b = buf();
		TalkPayloads.Detail.CODEC.encode(b, d);
		TalkPayloads.Detail back = TalkPayloads.Detail.CODEC.decode(b);
		assertEquals(0, b.readableBytes());
		assertEquals(TalkPayloads.MAX_OPTIONS, back.options().size());
		assertEquals(16, back.traits().size());
		assertEquals(30, back.opinion());
		assertEquals("friend", back.nextTitle());
		assertEquals(7, back.speech());
		assertEquals(3, back.info("test:o3").cost());
		assertNull(back.info("nope"));
	}

	@Test
	void optionsNobodyDescribedAreClassifiedByIdAndBand() {
		assertEquals("leave", OptionKinds.infer(NpcDialogue.BYE, 10_000).kind());
		DialogueProvider.Info lesson = OptionKinds.infer("mettle:train/blades", 300);
		assertEquals("train", lesson.kind());
		assertEquals("mettle:blades", lesson.skill());
		assertEquals("train", OptionKinds.infer("othermod:lesson", 350).kind());
		assertEquals("trade", OptionKinds.infer("economy:trade", 100).kind());
		assertEquals("bank", OptionKinds.infer("economy:property", 120).kind());
		assertEquals("fence", OptionKinds.infer("law:fence", 120).kind());
		assertEquals("quest", OptionKinds.infer("quest:offer/abc", 210).kind());
		assertEquals("dice", OptionKinds.infer("social:dice", 160).kind());
		assertEquals("service", OptionKinds.infer("social:meal", 132).kind());
		assertEquals("talk", OptionKinds.infer("npc:news", 10).kind());
		assertEquals(-1, OptionKinds.infer("npc:news", 10).odds());
		// a speech check a provider described without a skill reads Speech
		assertEquals(PlayerSkills.SPEECH, OptionKinds.complete(new DialogueProvider.Info("bribe", 50, 20, null)).skill());
		assertNull(OptionKinds.complete(DialogueProvider.Info.of("trade")).skill());
	}

	@Test
	void describeOnlyCollectsInsideAConversation() {
		DialogueProvider.describe("x:y", DialogueProvider.Info.of("talk"));  // nobody listening: nothing happens
		Map<String, DialogueProvider.Info> sink = new HashMap<>();
		DialogueProvider.Registry.collectInfo(sink);
		try {
			DialogueProvider.describe("x:y", new DialogueProvider.Info("bribe", 70, 30, null));
		} finally {
			DialogueProvider.Registry.collectInfo(null);
		}
		DialogueProvider.describe("x:z", DialogueProvider.Info.of("talk"));
		assertEquals(1, sink.size());
		assertEquals(70, sink.get("x:y").odds());
	}

	@Test
	void theMarketViewCarriesTheMerchantAndTheHaggleBand() {
		MarketView.HaggleView h = new MarketView.HaggleView(1, new ItemStack(Items.IRON_INGOT, 8), true, 24, 22, 18, 70, Component.literal("No."), 9, 15,
			21, 8);
		MarketView v = new MarketView("Bradfield", "temperate", 10, 454, 1250, Component.literal("note"), Component.empty(), List.of(), List.of(), true, h,
			new MarketView.Merchant("Martin Coyle", "hearth.npc.job.merchant", 77, List.of("greedy"), 5, -3));
		RegistryFriendlyByteBuf b = buf();
		MarketView.STREAM_CODEC.encode(b, v);
		MarketView back = MarketView.STREAM_CODEC.decode(b);
		assertEquals(0, b.readableBytes());
		assertNotNull(back.merchant());
		assertEquals(77, back.merchant().entity());
		assertEquals(List.of("greedy"), back.merchant().traits());
		assertEquals(9, back.haggle().offersLeft());
		assertTrue(back.haggle().bandShown());
		assertEquals(21, back.haggle().bandHigh());
		// the old constructors still make a view: no merchant, the band hidden
		MarketView.HaggleView old = new MarketView.HaggleView(1, new ItemStack(Items.BREAD), true, 6, 6, 6, 100, Component.empty());
		assertFalse(old.bandShown());
		assertNull(new MarketView("A", "b", 0, 0, 0, Component.empty(), Component.empty(), List.of(), List.of(), false, old).merchant());
	}

	@Test
	void theLimitBandIsHiddenUntilSpeechThenNarrowsToTheLimit() {
		assertNull(Haggle.band(100, 80, Haggle.BAND_SPEECH - 1));
		long[] wide = Haggle.band(100, 80, Haggle.BAND_SPEECH);
		long[] narrow = Haggle.band(100, 80, 10);
		long[] exact = Haggle.band(100, 80, Haggle.EXACT_SPEECH);
		assertNotNull(wide);
		assertTrue(wide[0] <= 80 && 80 <= wide[1], "the limit lies inside the band");
		assertEquals(30, wide[1] - wide[0]);
		assertTrue(narrow[0] <= 80 && 80 <= narrow[1]);
		assertTrue(narrow[1] - narrow[0] < wide[1] - wide[0]);
		assertEquals(80, exact[0]);
		assertEquals(80, exact[1]);
		// the same deal, the same band: looking again tells nothing new
		assertEquals(wide[0], Haggle.band(100, 80, Haggle.BAND_SPEECH)[0]);
		Haggle h = Haggle.start(true, 100, 0.2, 100, 0, EconomyConfig.Haggle.DEFAULTS);
		assertTrue(h.offersLeft() > 0);
		h.offer(90);
		assertTrue(h.offersLeft() < 12);
	}

	@Test
	void theConfrontDetailRoundTrips() {
		var d = new dev.voidmc.hearth.law.net.LawPayloads.ConfrontDetail(55, "arrest", "hearth.npc.job.guard", List.of("brave"), -12, 24, "known",
			"Bradfield", "hearth.offence.theft", 2, "", List.of(new dev.voidmc.hearth.law.net.LawPayloads.ChoiceInfo("submit", "cell", -1, -1, 48)));
		RegistryFriendlyByteBuf b = buf();
		dev.voidmc.hearth.law.net.LawPayloads.ConfrontDetail.CODEC.encode(b, d);
		var back = dev.voidmc.hearth.law.net.LawPayloads.ConfrontDetail.CODEC.decode(b);
		assertEquals(0, b.readableBytes());
		assertEquals(d, back);
		assertEquals(48, back.info("submit").minutes());
	}

	@Test
	void theNextTitleUpTheLadder() {
		StandingConfig.Titles t = StandingConfig.Titles.DEFAULTS;
		assertEquals(Title.DISTRUSTED, Titles.next(-80, t).title());
		assertEquals(Title.STRANGER, Titles.next(-30, t).title());
		assertEquals(Title.KNOWN, Titles.next(0, t).title());
		assertEquals(10, Titles.next(0, t).at());
		assertEquals(Title.FRIEND, Titles.next(24, t).title());
		assertEquals(Title.HERO, Titles.next(60, t).title());
		assertNull(Titles.next(90, t));
	}
}
