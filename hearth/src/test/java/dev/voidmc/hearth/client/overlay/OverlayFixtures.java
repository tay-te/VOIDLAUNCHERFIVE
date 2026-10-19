package dev.voidmc.hearth.client.overlay;

import dev.voidmc.hearth.api.QuestType;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestReward;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.TurnIn;
import dev.voidmc.hearth.social.dice.DiceGame;
import dev.voidmc.hearth.social.dice.DiceView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Payloads as the server would send them, for the overlay tests. */
final class OverlayFixtures {
	static {
		dev.voidmc.hearth.testing.ItemBoot.boot();
	}

	private OverlayFixtures() {
	}

	static void boot() {
		// the static block above
	}

	static final int AGNES = 41;

	/** Agnes at the inn: small talk, a persuasion check, a bribe, a locked room, a Mettle lesson, and goodbye. */
	static TalkPayloads.Show talk() {
		return new TalkPayloads.Show(AGNES, true, "Agnes Garland", "hearth.npc.job.innkeeper", "Bradfield", Component.literal("Back again?"), List.of(
			new TalkPayloads.Option("npc:news", Component.literal("What's new in Bradfield?")),
			new TalkPayloads.Option("standing:ask/persuade", Component.literal("An old friend drinks for less, surely?"), false,
				Component.literal("Persuade · 62 %"), null),
			new TalkPayloads.Option("standing:ask/bribe", Component.literal("A few marks might loosen your tongue."), false, null, null),
			new TalkPayloads.Option("social:room", Component.literal("I'd like the upstairs room."), true, Component.literal("Needs Friend"),
				Component.literal("Needs Friend of Bradfield · you are Known")),
			new TalkPayloads.Option("mettle:train/speech", Component.literal("Teach me to talk folk round."), false, Component.literal("+40 XP"), null),
			new TalkPayloads.Option("npc:bye", Component.literal("Goodbye."))));
	}

	static TalkPayloads.Detail detail() {
		return new TalkPayloads.Detail(AGNES, "hearth.npc.job.innkeeper", "commerce/tavern", List.of("generous", "gossip", "sociable"), 20, 18,
			"quest:rescue", 25, 24, "known", "friend", 40, 19 * 60, 6, List.of(
			new TalkPayloads.OptionInfo("npc:news", "talk", -1, -1, "", -1),
			new TalkPayloads.OptionInfo("standing:ask/persuade", "persuade", 62, -1, "mettle:speech", 6),
			new TalkPayloads.OptionInfo("standing:ask/bribe", "bribe", 85, 30, "mettle:speech", 6),
			new TalkPayloads.OptionInfo("social:room", "service", -1, 120, "", -1),
			new TalkPayloads.OptionInfo("mettle:train/speech", "train", -1, -1, "mettle:speech", 6),
			new TalkPayloads.OptionInfo("npc:bye", "leave", -1, -1, "", -1)));
	}

	static MarketView.Offer offer(int id, ItemStack stack, long price, int available, int flags) {
		return new MarketView.Offer(id, stack, price, available, 0, flags, "food");
	}

	static MarketView market(MarketView.HaggleView haggle) {
		return new MarketView("Bradfield", "temperate", 24, 454, 1250, Component.literal("Have a look."), Component.empty(),
			List.of(offer(0, new ItemStack(Items.BREAD, 4), 6, 18, 0), offer(1, new ItemStack(Items.IRON_INGOT, 8), 24, 12, 0),
				offer(2, new ItemStack(Items.ARROW, 16), 9, 0, MarketView.CANNOT_AFFORD)),
			List.of(offer(10_000, new ItemStack(Items.OAK_LOG, 16), 8, 2, MarketView.NEEDED)), true, haggle,
			new MarketView.Merchant("Martin Coyle", "hearth.npc.job.merchant", 77, List.of("cheerful", "greedy"), 15, 5));
	}

	static MarketView.HaggleView haggle(long bandLow, long bandHigh, int speech) {
		return new MarketView.HaggleView(1, new ItemStack(Items.IRON_INGOT, 8), true, 24, 24, 19, 60, Component.literal("Twenty-four."), 5, bandLow,
			bandHigh, speech);
	}

	static DiceView dice(DiceGame.Phase phase, int keptMask, boolean canRoll, boolean canBank, int loaded) {
		return new DiceView(phase.ordinal(), "Edda", "bold", 20, 2000, 450, 300, 150, 3, new int[] {1, 5, 3, 3, 3, 6}, keptMask, 0b000010, 0b011110,
			400, 0b111111, 7, List.of(0, 10, 20), Component.literal("Your throw."), Component.empty(), -1, false, loaded, 454, canRoll, canBank);
	}

	static LawPayloads.Pocket pocket(int state) {
		return new LawPayloads.Pocket(9, "Ulrich", false, List.of(new ItemStack(Items.GOLD_NUGGET, 3), ItemStack.EMPTY, new ItemStack(Items.APPLE)),
			0.4F, 0.01F, -1, 0, 0, state);
	}

	static LawPayloads.Confront confront() {
		return new LawPayloads.Confront(55, true, "Guard Hendrik", Component.literal("Stop right there."), 140, List.of(
			new LawPayloads.Choice("pay", Component.literal("Pay the fine"), true),
			new LawPayloads.Choice("bribe", Component.literal("Bribe"), false),
			new LawPayloads.Choice("run", Component.literal("Run"), true)));
	}

	static QuestInstance quest(String title, QuestStatus status, int progress) {
		Objective o = new Objective(ObjectiveKind.DELIVER, "Bring 24 × Spruce Log", "minecraft:spruce_log", 24, progress, null, 0, 1L, null,
			new CompoundTag());
		return new QuestInstance(UUID.randomUUID(), Identifier.parse("hearth:supply"), QuestType.SUPPLY, status,
			new QuestInstance.Text(title, "The mill needs timber.", "Bring logs.", "How goes it?", "Thank you."),
			new QuestInstance.Giver(Optional.empty(), "Agnes Garland", "innkeeper"), 1L, "Bradfield", new BlockPos(100, 64, 0), List.of(o),
			new QuestReward(240, List.of(), 3, "2 crowns, 4 marks"), 0, new QuestInstance.Timing(1, 4, 2, 1000, 1000 + 2 * 24000),
			new QuestInstance.Rules(false, TurnIn.GIVER, -1000, false), List.of());
	}
}
