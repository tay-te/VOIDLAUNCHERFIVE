package dev.voidmc.mettle.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.character.cards.CardConfig;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.TalentRules;
import dev.voidmc.mettle.character.cards.Talents;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.gear.GearInventory;
import dev.voidmc.mettle.gear.GearItems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Talents (docs/BUILD_FEEL.md §13): an old character gets its talent drafts at once, the hand of three in the overlay, a
 * keep through the overlay's action, a reroll from the shared pool, the row locked after the low levels, a real night's
 * sleep opening the waking window and a free rearrange in it, Fleet's movement as a card modifier under the ceiling,
 * Packrat's belt pocket and its contents handed back when it leaves. The character is put back after.
 */
final class TalentJourney {
	private static final Identifier FLEET = Identifier.fromNamespaceAndPath("mettle", "fleet");
	private static final Identifier PACKRAT = Identifier.fromNamespaceAndPath("mettle", "packrat");

	private TalentJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		long[] savedTime = new long[1];
		int[] savedSwaps = new int[1];
		ItemStack[] savedBelt = new ItemStack[1];
		server.runCommand("gamemode survival @a");

		// An old character (level ≥ 8) with no talents: its drafts for 3 and 7 queue at once, a hand of three is dealt.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.ensure(p);
			savedTime[0] = s.overworld().getDefaultClockTime();
			savedSwaps[0] = CardStore.get(p).freeSwaps();
			savedBelt[0] = GearInventory.of(p).worn(GearSlot.BELT).copy();
			while (CharacterStore.get(p).characterLevel() < 8) {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "mettle xp mettle:survival 2000 @a");
			}
			release(s, p);
			CharacterState st = CharacterStore.get(p);
			DraftState d = st.drafts();
			CharacterStore.set(p, st.withDraft(d.withTalent(new DraftState.TalentDraft(0, List.of(), 2, d.talent().dealt()))));
			Cards.setFreeSwaps(p, 0);
			Talents.refresh(p);
			int level = CharacterStore.get(p).characterLevel();
			DraftState.TalentDraft t = CharacterStore.get(p).drafts().talent();
			int want = TalentRules.draftsBetween(2, level);
			if (t.queued() != want || want < 2) {
				return "talent drafts queued " + t.queued() + ", wanted " + want + " (character level " + level + ")";
			}
			if (t.hand().size() != 3 || !TalentRules.distinct(t.hand())) {
				return "the talent hand is not three different talents: " + t.hand();
			}
			return t.upTo() == level ? null : "talent drafts paid up to " + t.upTo() + ", not " + level;
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject talents = MettleState.build(mc).getAsJsonObject("talents");
			JsonArray hand = talents.getAsJsonObject("draft").getAsJsonArray("hand");
			if (hand.size() != 3 || !talents.get("unlocked").getAsBoolean() || talents.get("slots").getAsInt() != TalentRules.slots(talents.get("characterLevel").getAsInt())) {
				return "the overlay's talents: " + talents;
			}
			JsonObject first = hand.get(0).getAsJsonObject();
			if (first.get("name").getAsString().isBlank() || first.get("description").getAsString().isBlank() || !"#A39E96".equals(first.get("edge").getAsString())
				|| first.getAsJsonArray("keywords").size() != 0 || !"slot".equals(first.get("goes").getAsString())) {
				return "a talent offer as the Hub gets it: " + first;
			}
			return talents.getAsJsonArray("earned").size() == 5 ? null : "the five earned talents: " + talents.getAsJsonArray("earned");
		}));
		context.takeScreenshot("mettle_talents_draft");

		// Keep the first card through the overlay: a talent card in the ledger, in the row.
		context.runOnClient(mc -> MettleActions.keepTalent(0));
		context.waitTicks(10);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CardsState c = CardStore.get(p);
			if (c.talent().slotted().size() != 1 || !c.cards().stream().noneMatch(CardData::talent)) {
				return "after the keep: row " + c.talent().slotted() + ", skill cards hold a talent " + c.cards().stream().anyMatch(CardData::talent);
			}
			CardData kept = c.talent().slotted().getFirst();
			if (!kept.talent() || Cards.ledger(s).get(kept.id()).isEmpty() || !Cards.printId(s.registryAccess(), kept).startsWith("TL")) {
				return "the kept talent is not a TL card in the ledger: " + kept;
			}
			Identifier trait = TalentRules.trait(kept.talentId().orElseThrow());
			if (c.trait(trait) <= 0) {
				return "the kept talent's value is not active: " + c.traits();
			}
			// A reroll from the shared pool deals the next hand again.
			Drafts.setRerolls(p, 1);
			List<DraftState.Card> before = CharacterStore.get(p).drafts().talent().hand();
			Optional<CardRules.Refusal> no = Talents.reroll(p);
			DraftState after = CharacterStore.get(p).drafts();
			if (no.isPresent() || after.rerolls() != 0 || after.talent().hand().equals(before) || after.talent().hand().size() != 3) {
				return "the talent reroll: " + no + ", rerolls " + after.rerolls() + ", hand " + after.talent().hand();
			}
			if (Talents.reroll(p).orElse(null) != CardRules.Refusal.NO_REROLLS) {
				return "a talent reroll with none left was not refused";
			}
			// The second keep fills the second slot.
			if (Talents.keep(p, 1).isPresent() || CardStore.get(p).talent().slotted().size() != 2) {
				return "the second keep did not fill slot 2: " + CardStore.get(p).talent().slotted();
			}
			// Past the low levels, with no free swap and no night's sleep, the row is locked.
			Optional<CardRules.Refusal> locked = Talents.unslot(p, kept.id());
			return locked.orElse(null) == CardRules.Refusal.TALENT_LOCKED ? null : "taking a talent out without sleep or a swap gave " + locked;
		}));

		// A real night's sleep in a bed: woken by the morning, the row may be rearranged freely for 60 s.
		BlockPos[] bed = new BlockPos[1];
		ServerPlayer.RespawnConfig[] savedRespawn = new ServerPlayer.RespawnConfig[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			var level = s.overworld();
			BlockPos foot = p.blockPosition().relative(Direction.EAST, 2);
			BlockPos head = foot.relative(Direction.EAST);
			level.setBlockAndUpdate(foot.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(head.below(), Blocks.STONE.defaultBlockState());
			BlockState footState = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT);
			p.teleportTo(foot.getX() - 0.5, foot.getY(), foot.getZ() + 0.5);
			level.setBlockAndUpdate(foot, footState);
			level.setBlockAndUpdate(head, footState.setValue(BedBlock.PART, BedPart.HEAD));
			bed[0] = foot;
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "time set 18000");
			// The tour stops the clock; a night's sleep needs it to run (the wake-up moves it to the morning).
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "gamerule advance_time true");
			// No monster may keep the player awake.
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "kill @e[type=!player,type=!item,distance=..40]");
			savedRespawn[0] = p.getRespawnConfig();
			return null;
		}));
		// The night has to reach the world's sky before a bed lets anyone sleep.
		context.waitTicks(10);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			var level = s.overworld();
			BlockPos head = bed[0].relative(Direction.EAST);
			BlockState headState = level.getBlockState(head);
			var bedBlock = (net.minecraft.world.level.block.AbstractBedBlock) headState.getBlock();
			var tried = p.startSleepInBed(bedBlock, headState, bedBlock.getBedRule(level, head), head);
			if (tried.left().isPresent()) {
				return "the player could not lie down in the bed: " + tried.left().get() + " (clock " + level.getDefaultClockTime() + ")";
			}
			return p.isSleeping() ? null : "the player could not lie down in the bed (clock " + level.getDefaultClockTime() + ")";
		}));
		context.waitTicks(140);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			long gameTime = s.overworld().getGameTime();
			CardsState c = CardStore.get(p);
			if (p.isSleeping() || !c.talent().awake(gameTime)) {
				return "after a night's sleep: sleeping " + p.isSleeping() + ", window until " + c.talent().wakeUntil() + " (now " + gameTime + ", clock "
					+ s.overworld().getDefaultClockTime() + ")";
			}
			CardData out = c.talent().slotted().getFirst();
			if (Talents.unslot(p, out.id()).isPresent() || CardStore.get(p).freeSwaps() != 0) {
				return "taking a talent out on waking was refused or spent a swap";
			}
			if (Talents.slot(p, out.id(), 0).isPresent()) {
				return "putting it back was refused";
			}
			return null;
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject re = MettleState.build(mc).getAsJsonObject("talents").getAsJsonObject("rearrange");
			return "wake".equals(re.get("way").getAsString()) && re.get("secondsLeft").getAsInt() > 0 ? null : "the overlay's waking window: " + re;
		}));
		context.takeScreenshot("mettle_talents_waking");

		// Fleet is a card modifier (counted by the movement ceiling), Packrat a fifth belt pocket that hands its item back.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Cards.setFreeSwaps(p, 3);
			CardsState c = CardStore.get(p);
			for (CardData slotted : c.talent().slotted()) {
				if (Talents.unslot(p, slotted.id()).isPresent()) {
					return "clearing the row in the waking window was refused";
				}
			}
			CardData fleet = Talents.give(p, FLEET, DraftState.Rarity.EPIC, 4, true);
			var speed = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Identifier.fromNamespaceAndPath("mettle", "trait/talent/fleet"));
			if (!fleet.slotted() || speed == null || Math.abs(speed.amount() - 0.04) > 1e-9) {
				return "Fleet in the row: slotted " + fleet.slotted() + ", modifier " + speed;
			}
			GearInventory gear = GearInventory.of(p);
			gear.setWorn(GearSlot.BELT, new ItemStack(GearItems.BELT));
			int plain = gear.pocketCount(GearSlot.BELT);
			CardData packrat = Talents.give(p, PACKRAT, null, 0, true);
			if (!packrat.slotted() || gear.pocketCount(GearSlot.BELT) != plain + 1) {
				return "Packrat: belt pockets " + plain + " -> " + gear.pocketCount(GearSlot.BELT);
			}
			gear.setPocket(GearSlot.BELT, plain, new ItemStack(Items.BREAD, 3));
			int bread = p.getInventory().countItem(Items.BREAD);
			if (Talents.unslot(p, packrat.id()).isPresent()) {
				return "Packrat could not leave the row in the window";
			}
			if (gear.pocketCount(GearSlot.BELT) != plain || p.getInventory().countItem(Items.BREAD) != bread + 3) {
				return "Packrat left: pockets " + gear.pocketCount(GearSlot.BELT) + ", bread " + p.getInventory().countItem(Items.BREAD) + " (was " + bread + ")";
			}
			return null;
		}));

		// Put back: the talents released, the drafts paid up to now, the bed gone, the clock, the swaps and the belt as they were.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			release(s, p);
			CharacterState st = CharacterStore.get(p);
			DraftState d = st.drafts();
			CharacterStore.set(p, st.withDraft(d.withTalent(new DraftState.TalentDraft(0, List.of(), st.characterLevel(), d.talent().dealt()))));
			CardStore.set(p, CardStore.get(p).withWakeUntil(0));
			Cards.setFreeSwaps(p, savedSwaps[0]);
			GearInventory.of(p).setWorn(GearSlot.BELT, savedBelt[0]);
			p.getInventory().clearOrCountMatchingItems(st2 -> st2.is(Items.BREAD), false, 3, p.inventoryMenu.getCraftSlots());
			p.setRespawnPosition(savedRespawn[0], false);
			if (bed[0] != null) {
				s.overworld().setBlockAndUpdate(bed[0], Blocks.AIR.defaultBlockState());
				s.overworld().setBlockAndUpdate(bed[0].relative(Direction.EAST), Blocks.AIR.defaultBlockState());
			}
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "gamerule advance_time false");
			s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "time set " + Math.floorMod(savedTime[0], 24000L));
			return CardStore.get(p).talent().cards().isEmpty() ? null : "talent cards left after the journey";
		}));
		context.waitTicks(3);
	}

	/** Every talent card the player owns, released (ids are never reused). */
	private static void release(MinecraftServer s, ServerPlayer p) {
		for (CardData c : List.copyOf(CardStore.get(p).talent().cards())) {
			Cards.ledger(s).update(c.id(), x -> x.withPlace(CardData.Place.RELEASED));
		}
		Cards.sync(p);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}

	static {
		// Referenced so a renamed config field fails the build, not the tour.
		CardConfig.get();
	}
}
