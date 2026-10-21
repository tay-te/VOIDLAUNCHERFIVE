package dev.voidmc.mettle.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.BuildFeel;
import dev.voidmc.mettle.character.cards.CardBlocks;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.Rebuilds;
import dev.voidmc.mettle.client.moment.MomentClient;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.BuildState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Build feel (docs/BUILD_FEEL.md) played for real, through the overlay's calls where a player would use them: the guide
 * of a new character (every hint once, the guided first hand's Rare, Skip) and none for a migrated one; the Focus and its
 * once-a-day change; boons at Mining 7, 9 and 11 spent each way (Temper, Inscribe, Reroll) with their previews, marks and
 * refusals; milestones (a first card, a first set, a byname) as moments; Mythic shards, the forge-ready moment and a forge
 * at a Card Shrine, a held Mythic refused, the legend title on an emptied pool. The character is put back.
 */
final class BuildFeelJourney {
	private static final ResourceKey<Skill> MINING = Progress.skill("mining");
	private static final String MINING_ID = MINING.identifier().toString();

	private BuildFeelJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		// Calm first: hints and milestones wait 5 s after harm, and the journeys before may have hurt the player.
		context.waitTicks(110);
		CharacterState[] saved = new CharacterState[1];
		BuildState[] savedBuild = new BuildState[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			saved[0] = Cards.ensure(p);
			savedBuild[0] = BuildFeel.get(p);
			return null;
		}));
		guide(context, server);
		focus(context, server);
		boons(context, server);
		milestones(context, server);
		forge(context, server);
		// Put the character back: no cards, the record and build feel as they were.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			CharacterStore.set(p, saved[0]);
			CardStore.set(p, CardStore.get(p).withBuild(savedBuild[0]));
			EffectsApplier.apply(p);
			return null;
		}));
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- the guide (§4)

	private static void guide(ClientGameTestContext context, TestServerContext server) {
		// A migrated character (skill XP, cards) never gets the guide.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BuildFeel.set(p, BuildFeel.get(p).withoutOnce("guide/").withGuide(BuildState.Guide.OFF));
			CharacterStore.set(p, CharacterStore.get(p).withXp(MINING, Curves.skillXpFor(3)));
			BuildFeel.decideGuide(p);
			return BuildFeel.get(p).guide().on() ? "a character with XP got the guide" : null;
		}));
		// A new one: no XP, no cards, nothing waiting.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			CharacterState st = CharacterStore.get(p);
			Map<ResourceKey<Skill>, Double> zero = new HashMap<>();
			CharacterState fresh = st;
			for (var ref : s.registryAccess().lookupOrThrow(dev.voidmc.mettle.api.MettleRegistries.SKILL).listElements().toList()) {
				fresh = fresh.withXp(ref.key(), 0);
			}
			DraftState d = new DraftState(Map.of(), Map.of(), 2, st.drafts().seed(), st.drafts().dealt(), List.of());
			CharacterStore.set(p, fresh.withDraft(d));
			BuildFeel.set(p, BuildFeel.get(p).withoutOnce("guide/").withoutOnce("ms/").withGuide(BuildState.Guide.OFF).withFocus(Optional.empty(), -1));
			BuildFeel.decideGuide(p);
			EffectsApplier.apply(p);
			return BuildFeel.get(p).guide().on() ? null : "a new character did not get the guide";
		}));
		// 1: the first skill XP.
		check(server.computeOnServer(s -> {
			award(player(s), MINING, Curves.skillXpFor(1) + 1);
			return null;
		}));
		context.waitTicks(45);
		check(hint(context, 1, "Mining"));
		// 2: the first hand (Mining 2), after hint 1's six seconds; the guided first hand holds a Rare or better.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			award(p, MINING, Curves.skillXpFor(2) - CharacterStore.get(p).xp(MINING) + 1);
			List<DraftState.Card> hand = CharacterStore.get(p).drafts().hand(MINING);
			if (hand.isEmpty()) {
				return "Mining 2 dealt no hand";
			}
			if (hand.stream().noneMatch(c -> c.rarity() == DraftState.Rarity.RARE || c.rarity() == DraftState.Rarity.EPIC)) {
				return "the guided first hand holds no Rare: " + hand;
			}
			return BuildFeel.get(p).guide().firstHand() ? null : "the first hand was not marked as guided";
		}));
		context.waitTicks(150);
		check(hint(context, 2, null));
		// Keep & slot the card that needs least (Mining raised to it when the guided Rare needs more than 2): hint 2 steps
		// aside; the card is slotted, so hint 4 ("Slotted cards work") comes.
		int[] keep = new int[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			List<DraftState.Card> hand = CharacterStore.get(p).drafts().hand(MINING);
			for (int i = 0; i < hand.size(); i++) {
				if (!hand.get(i).wildcard() && hand.get(i).needs() < hand.get(keep[0]).needs()) {
					keep[0] = i;
				}
			}
			int need = hand.get(keep[0]).needs();
			if (CharacterStore.get(p).level(MINING) < need) {
				CharacterState st = CharacterStore.get(p);
				CharacterStore.set(p, st.withXp(MINING, Curves.skillXpFor(need)));
				EffectsApplier.apply(p);
			}
			return null;
		}));
		context.waitTicks(3);
		context.runOnClient(mc -> MettleActions.keepCard(MINING_ID, keep[0], true));
		context.waitTicks(45);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return CardStore.get(p).slotted().isEmpty() ? "Keep & slot slotted nothing (the hand: " + CharacterStore.get(p).drafts().hand(MINING) + ")" : null;
		}));
		check(hint(context, 4, null));
		// The Deck opened: it goes. A keyword badge hovered: hint 5.
		context.runOnClient(mc -> MettleActions.guideAdvance("deck"));
		context.waitTicks(5);
		context.runOnClient(mc -> MettleActions.guideAdvance("keyword"));
		context.waitTicks(45);
		check(hint(context, 5, null));
		// Skip the guide: every hint off; each was seen once.
		context.runOnClient(mc -> MettleActions.guideSkip());
		context.waitTicks(25);
		check(server.computeOnServer(s -> {
			BuildState.Guide g = BuildFeel.get(player(s)).guide();
			if (!g.skipped() || g.current() != 0) {
				return "Skip left the guide on: " + g;
			}
			return new HashSet<>(g.seen()).size() == g.seen().size() ? null : "a hint was shown twice: " + g.seen();
		}));
		check(context.computeOnClient(mc -> {
			JsonObject guide = build(mc).getAsJsonObject("guide");
			return !guide.get("on").getAsBoolean() && guide.get("hint").isJsonNull() ? null : "the overlay still shows the guide: " + guide;
		}));
	}

	private static String hint(ClientGameTestContext context, int id, String contains) {
		return context.computeOnClient(mc -> {
			JsonObject guide = build(mc).getAsJsonObject("guide");
			if (guide.get("hint").isJsonNull()) {
				return "no guide hint up (wanted " + id + "): " + guide;
			}
			JsonObject h = guide.getAsJsonObject("hint");
			if (h.get("id").getAsInt() != id) {
				return "guide hint " + h.get("id") + " up, wanted " + id + ": " + h;
			}
			if (h.get("text").getAsString().isBlank() || h.get("anchor").getAsString().isBlank()) {
				return "a hint without words or an anchor: " + h;
			}
			return contains == null || h.get("text").getAsString().contains(contains) ? null : "hint " + id + " says " + h.get("text");
		});
	}

	// ---------------------------------------------------------------- the Focus (§1.3)

	private static void focus(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(mc -> MettleActions.setFocus("mettle:deep"));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BuildState b = BuildFeel.get(p);
			if (!b.focus().map(Identifier.fromNamespaceAndPath("mettle", "deep")::equals).orElse(false)) {
				return "the Focus is " + b.focus();
			}
			if (BuildFeel.commitment(CardStore.get(p)).of(Identifier.fromNamespaceAndPath("mettle", "deep")) < 2) {
				return "the Focus doesn't count for momentum";
			}
			// Once a day: a second change today is refused…
			Optional<CardRules.Refusal> again = BuildFeel.focus(p, Optional.of(Identifier.fromNamespaceAndPath("mettle", "tide")));
			if (again.isEmpty() || again.get() != CardRules.Refusal.FOCUS_TODAY) {
				return "a second Focus change the same day gave " + again;
			}
			// …and allowed the next day.
			BuildFeel.set(p, b.withFocus(b.focus(), Cards.day(s) - 1));
			Optional<CardRules.Refusal> tomorrow = BuildFeel.focus(p, Optional.of(Identifier.fromNamespaceAndPath("mettle", "tide")));
			return tomorrow.isEmpty() ? null : "the Focus could not change the next day: " + tomorrow;
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject focus = build(mc).getAsJsonObject("focus");
			if (!"mettle:tide".equals(focus.get("keyword").getAsString()) || focus.get("canChange").getAsBoolean()) {
				return "the overlay's Focus: " + focus;
			}
			for (var e : build(mc).getAsJsonArray("momentum")) {
				JsonObject m = e.getAsJsonObject();
				if (m.get("keyword").getAsString().equals("mettle:tide")) {
					return m.get("perkLean").getAsDouble() > 1 ? null : "Tide doesn't lean with the Focus: " + m;
				}
			}
			return "no Tide in the overlay's momentum";
		}));
	}

	// ---------------------------------------------------------------- boons (§3)

	private static void boons(ClientGameTestContext context, TestServerContext server) {
		long[] ids = new long[2];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			CharacterState st = CharacterStore.get(p);
			CharacterStore.set(p, st.withXp(MINING, Curves.skillXpFor(6)).withDraft(st.drafts().withRerolls(2)));
			// A Rare Tunneler (Echo) with Deepborn 8 %, and a Quarryman carrying Deep: the seed for an Inscription.
			CardData t = Cards.giveExact(p, new DraftState.Card(key("mining/tunneler"), 1, DraftState.Rarity.RARE, false, List.of(id("echo")),
				List.of(new CardData.TraitRoll(id("mining/deepborn"), 8)), 6));
			CardData q = Cards.giveExact(p, new DraftState.Card(key("mining/quarryman"), 1, DraftState.Rarity.COMMON, false, List.of(id("brutal"), id("deep")),
				List.of(), 2));
			ids[0] = t.id();
			ids[1] = q.id();
			Cards.slot(p, t.id(), -1).ifPresent(r -> {
				throw new AssertionError("slotting the Tunneler: " + r);
			});
			Cards.slot(p, q.id(), -1).ifPresent(r -> {
				throw new AssertionError("slotting the Quarryman: " + r);
			});
			award(p, MINING, Curves.skillXpFor(7) - CharacterStore.get(p).xp(MINING) + 1);
			DraftState d = CharacterStore.get(p).drafts();
			return d.boons(MINING) == 1 ? null : "Mining 7 gave " + d.boons(MINING) + " boons";
		}));
		MomentClient.resetForTest();
		context.waitTicks(3);
		// The Temper confirm (B7): the preview says 8 → 10 %, then the Temper is made from the overlay.
		check(context.computeOnClient(mc -> {
			JsonObject options = JsonParser.parseString(MettleActions.boonOptions(String.valueOf(ids[0]))).getAsJsonObject();
			if (!options.getAsJsonArray("temper").get(0).getAsJsonObject().get("ok").getAsBoolean()) {
				return "the Tunneler can't be tempered: " + options;
			}
			JsonObject p = JsonParser.parseString(MettleActions.previewTemper(String.valueOf(ids[0]), "mettle:mining/deepborn")).getAsJsonObject();
			return p.get("ok").getAsBoolean() && p.get("to").getAsDouble() == 10 && p.has("traits") ? null : "the Temper preview: " + p;
		}));
		context.runOnClient(mc -> MettleActions.temperCard(String.valueOf(ids[0]), "mettle:mining/deepborn"));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CardData t = Cards.ledger(s).get(ids[0]).orElseThrow();
			if (t.traits().getFirst().value() != 10 || !t.tempered()) {
				return "the Temper didn't land in the ledger: " + t;
			}
			if (CharacterStore.get(p).drafts().boons(MINING) != 0) {
				return "the boon was not spent";
			}
			Optional<CardRules.Refusal> twice = BuildFeel.temper(p, ids[0], id("mining/deepborn"));
			return twice.isPresent() ? null : "a second Temper without a boon went through";
		}));
		// Mining 9: a boon spent on an Inscription of Deep (seeded by the Quarryman).
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			award(p, MINING, Curves.skillXpFor(9) - CharacterStore.get(p).xp(MINING) + 1);
			return CharacterStore.get(p).drafts().boons(MINING) == 1 ? null : "Mining 9 gave no boon";
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject p = JsonParser.parseString(MettleActions.previewInscribe(String.valueOf(ids[0]), "mettle:ember")).getAsJsonObject();
			if (p.get("ok").getAsBoolean()) {
				return "an Inscription with no seed in the deck was allowed: " + p;
			}
			JsonObject ok = JsonParser.parseString(MettleActions.previewInscribe(String.valueOf(ids[0]), "mettle:deep")).getAsJsonObject();
			return ok.get("ok").getAsBoolean() && ok.getAsJsonArray("sets").size() > 0 ? null : "the Inscribe preview: " + ok;
		}));
		context.runOnClient(mc -> MettleActions.inscribeCard(String.valueOf(ids[0]), "mettle:deep"));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			CardData t = Cards.ledger(s).get(ids[0]).orElseThrow();
			if (!t.keywords().equals(List.of(id("echo"), id("deep"))) || !t.inscribed()) {
				return "the Inscription didn't land: " + t;
			}
			return CardStore.get(player(s)).sets().getOrDefault(id("deep"), 0) == 2 ? null : "the inscribed Deep doesn't count: " + CardStore.get(player(s)).sets();
		}));
		// Mining 11: a boon spent on a reroll.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			award(p, MINING, Curves.skillXpFor(11) - CharacterStore.get(p).xp(MINING) + 1);
			return null;
		}));
		int[] before = new int[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterStore.set(p, CharacterStore.get(p).withDraft(CharacterStore.get(p).drafts().withRerolls(2)));
			before[0] = CharacterStore.get(player(s)).drafts().rerolls();
			return CharacterStore.get(player(s)).drafts().boons(MINING) == 1 ? null : "Mining 11 gave no boon";
		}));
		context.runOnClient(mc -> MettleActions.boonReroll(MINING_ID));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			DraftState d = CharacterStore.get(player(s)).drafts();
			return d.boons(MINING) == 0 && d.rerolls() == Math.min(5, before[0] + 1) ? null : "the reroll boon: " + d.boons(MINING) + " boons, " + d.rerolls() + " rerolls";
		}));
		// The deck shows its counts and the card its marks; the moments came.
		check(context.computeOnClient(mc -> {
			JsonObject cards = MettleState.build(mc).getAsJsonObject("cards");
			for (var e : cards.getAsJsonArray("decks")) {
				JsonObject d = e.getAsJsonObject();
				if (d.get("skill").getAsString().equals(MINING_ID) && (d.get("tempered").getAsInt() != 1 || d.get("inscribed").getAsInt() != 1)) {
					return "the Mining deck's counts: " + d;
				}
			}
			for (var e : cards.getAsJsonObject("binder").getAsJsonArray("cards")) {
				JsonObject c = e.getAsJsonObject();
				if (c.get("id").getAsString().equals(String.valueOf(ids[0]))) {
					JsonObject marks = c.getAsJsonObject("marks");
					if (marks.get("tempered").isJsonNull() || marks.get("inscribed").isJsonNull()) {
						return "the card's marks: " + marks;
					}
				}
			}
			List<String> got = MomentClient.received();
			return got.stream().filter(m -> m.startsWith("boon ")).count() >= 2 ? null : "the first Temper and Inscription moments: " + got;
		}));
	}

	// ---------------------------------------------------------------- milestones (§2.4)

	private static void milestones(ClientGameTestContext context, TestServerContext server) {
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			BuildFeel.set(p, BuildFeel.get(p).withoutOnce("ms/").withByname(BuildState.Bynames.NONE));
			CharacterStore.set(p, CharacterStore.get(p).withXp(MINING, Curves.skillXpFor(14)));
			EffectsApplier.apply(p);
			return null;
		}));
		MomentClient.resetForTest();
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (String perk : new String[] {"mining/lantern_eyes", "mining/ore_sense"}) {
				CardData c = Cards.giveExact(p, new DraftState.Card(key(perk), 1, DraftState.Rarity.COMMON, false, List.of(id("deep")), List.of(), 12));
				Cards.slot(p, c.id(), -1).ifPresent(r -> {
					throw new AssertionError("slotting " + perk + ": " + r);
				});
			}
			BuildState b = BuildFeel.get(p);
			if (b.byname().tier() != 1 || b.byname().worn().isEmpty()) {
				return "a first 2-set gave no byname: " + b.byname();
			}
			return b.done("ms/first_card") && b.done("ms/first_set") ? null : "the milestones were not recorded: " + b.once();
		}));
		context.waitTicks(10);
		check(context.computeOnClient(mc -> {
			List<String> got = MomentClient.received();
			for (String want : new String[] {"card ", "set mettle:deep", "byname "}) {
				if (got.stream().noneMatch(m -> m.startsWith(want))) {
					return "no " + want.trim() + " moment: " + got;
				}
			}
			JsonObject worn = build(mc).getAsJsonObject("byname").getAsJsonObject("worn");
			return worn != null && worn.get("text").getAsString().equals("the Deepdelver") ? null : "the overlay's byname: " + worn;
		}));
		// The moment shows on the HUD once it is calm (no screen open).
		context.waitTicks(20);
		context.takeScreenshot("mettle_build_feel_milestone");
		// A milestone fires once: slotting another Deep card brings no second first-set moment.
		MomentClient.resetForTest();
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CardData c = Cards.giveExact(p, new DraftState.Card(key("mining/quarryman"), 1, DraftState.Rarity.COMMON, false, List.of(id("brutal"), id("deep")),
				List.of(), 2));
			Cards.slot(p, c.id(), -1);
			return null;
		}));
		context.waitTicks(10);
		check(context.computeOnClient(mc -> MomentClient.received().stream().anyMatch(m -> m.startsWith("set mettle:deep"))
			? "the first set fired twice: " + MomentClient.received() : null));
	}

	// ---------------------------------------------------------------- shards and the forge (§5)

	private static void forge(ClientGameTestContext context, TestServerContext server) {
		BlockPos[] shrine = new BlockPos[1];
		BlockState[] was = new BlockState[1];
		MomentClient.resetForTest();
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.clear(p);
			BuildFeel.set(p, BuildFeel.get(p).withoutOnce("ms/forge_ready").withForge(0, 0, 0, ""));
			CharacterStore.set(p, CharacterStore.get(p).withXp(MINING, Curves.skillXpFor(18)));
			// Make sure the Stonesong waits (an earlier journey may have claimed it).
			Cards.ledger(s).unclaim("stonesong");
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle build shards 30 " + p.getGameProfile().name());
			shrine[0] = p.blockPosition().relative(p.getDirection(), 2);
			was[0] = s.overworld().getBlockState(shrine[0]);
			s.overworld().setBlockAndUpdate(shrine[0], CardBlocks.CARD_SHRINE.defaultBlockState());
			s.overworld().getBlockState(shrine[0]).useWithoutItem(s.overworld(), p, new BlockHitResult(Vec3.atCenterOf(shrine[0]), Direction.UP, shrine[0], false));
			if (Rebuilds.sitting(p).isEmpty()) {
				return "the Card Shrine opened no sitting";
			}
			return BuildFeel.get(p).shards() == 30 ? null : "the shards op: " + BuildFeel.get(p).shards();
		}));
		context.waitTicks(10);
		check(context.computeOnClient(mc -> {
			if (MomentClient.received().stream().noneMatch(m -> m.startsWith("forge_ready "))) {
				return "no forge-ready moment at 30 shards: " + MomentClient.received();
			}
			JsonObject forge = build(mc).getAsJsonObject("forge");
			for (var e : forge.getAsJsonArray("pool")) {
				JsonObject m = e.getAsJsonObject();
				if (m.get("id").getAsString().equals("mettle:stonesong")) {
					return m.get("canForgeNow").getAsBoolean() ? null : "the Forge tab can't forge the Stonesong: " + m;
				}
			}
			return "the Stonesong is not in the Forge tab";
		}));
		context.runOnClient(mc -> MettleActions.forgeMythic("mettle:stonesong"));
		context.waitTicks(10);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BuildState b = BuildFeel.get(p);
			if (b.shards() != 0 || b.forges() != 1) {
				return "the forge's shards and count: " + b.shards() + " / " + b.forges();
			}
			Optional<CardData> m = CardStore.get(p).cards().stream().filter(c -> c.mythicId().map(Identifier.fromNamespaceAndPath("mettle", "stonesong")::equals).orElse(false)).findFirst();
			if (m.isEmpty() || m.get().drawn().how() != CardData.How.FORGED) {
				return "no forged Stonesong in the binder: " + CardStore.get(p).cards();
			}
			if (!Cards.ledger(s).first("stonesong").map(h -> h.player().equals(p.getUUID())).orElse(false)) {
				return "the Hall of Firsts doesn't name the forger";
			}
			// One of a kind: forging the held Stonesong again is refused, and the shards stay.
			BuildFeel.set(p, b.withShards(45));
			Optional<CardRules.Refusal> again = BuildFeel.forge(p, "mettle:stonesong");
			if (again.isEmpty() || again.get() != CardRules.Refusal.MYTHIC_HELD || BuildFeel.get(p).shards() != 45) {
				return "forging a held Mythic gave " + again + " with " + BuildFeel.get(p).shards() + " shards left";
			}
			// No legend while drawn Mythics wait.
			Optional<CardRules.Refusal> legend = BuildFeel.forge(p, "legend");
			if (legend.isEmpty() || legend.get() != CardRules.Refusal.POOL_LEFT) {
				return "the legend title with Mythics left: " + legend;
			}
			// Every drawn Mythic held: the shards buy the legend title.
			List<String> claimed = new java.util.ArrayList<>();
			for (var d : dev.voidmc.mettle.character.cards.Mythics.pool(s)) {
				if (!d.claimed()) {
					Cards.ledger(s).claim(d.id().getPath(), new CardData.Holder(new java.util.UUID(9, 9), "Somebody", 1));
					claimed.add(d.id().getPath());
				}
			}
			Optional<CardRules.Refusal> title = BuildFeel.forge(p, "legend");
			claimed.forEach(c -> Cards.ledger(s).unclaim(c));
			if (title.isPresent()) {
				return "the legend title on an empty pool: " + title;
			}
			BuildState after = BuildFeel.get(p);
			return after.legend() == 1 && after.shards() == 0 && !after.legendOf().isEmpty() ? null : "the legend: " + after;
		}));
		context.waitTicks(5);
		check(context.computeOnClient(mc -> {
			JsonObject legend = build(mc).getAsJsonObject("byname").getAsJsonObject("legend");
			return legend != null && legend.get("stars").getAsInt() == 1 && legend.get("title").getAsString().startsWith("the Legend of ") ? null
				: "the overlay's legend: " + legend;
		}));
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Rebuilds.close(p);
			s.overworld().setBlockAndUpdate(shrine[0], was[0]);
			Cards.ledger(s).unclaim("stonesong");
			return null;
		}));
	}

	// ---------------------------------------------------------------- helpers

	private static JsonObject build(net.minecraft.client.Minecraft mc) {
		return MettleState.build(mc).getAsJsonObject("cards").getAsJsonObject("build");
	}

	private static void award(ServerPlayer p, ResourceKey<Skill> skill, double xp) {
		CharacterState state = CharacterStore.get(p);
		var background = CharacterStore.background(p.level().registryAccess(), state);
		Progress.award(p, skill, Math.max(1, xp) / Progress.multiplier(state, background, skill));
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("mettle", path);
	}

	private static ResourceKey<dev.voidmc.mettle.api.Perk> key(String path) {
		return ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.PERK, id(path));
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
