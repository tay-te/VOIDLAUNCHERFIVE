package dev.voidmc.mettle.gametest;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.MarketEvents;
import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.mettle.character.skill.StealthSkill;
import dev.voidmc.hearth.api.QuestEvents;
import dev.voidmc.hearth.api.QuestType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.compat.hearth.HearthRules;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Mettle with VOID Hearth loaded (the tour with {@code -Phearth}): the player-skills slot answers with Mettle's numbers,
 * quests and trade pay XP, a market quotes better for a well-spoken character, and a smith teaches a lesson once a day,
 * capped by the settlement's tier. Drives Hearth's api the way Hearth itself calls it; the character, coin and the
 * stand-in tutors are put back afterwards. Only called when Hearth is loaded (this class names Hearth types).
 */
final class HearthJourney {
	private static final ResourceKey<Skill> SPEECH = Progress.skill("speech");
	private static final ResourceKey<Skill> THIEVERY = Progress.skill("thievery");
	private static final ResourceKey<Skill> SURVIVAL = Progress.skill("survival");
	private static final ResourceKey<Skill> MARKSMANSHIP = Progress.skill("marksmanship");
	private static final ResourceKey<Skill> SMITHING = Progress.skill("smithing");
	private static final double EPS = 0.01;

	private HearthJourney() {
	}

	static void run(TestSingleplayerContext world) {
		String problem = world.getServer().computeOnServer(HearthJourney::check);
		if (problem != null) {
			throw new AssertionError("Hearth journey: " + problem);
		}
	}

	private static @Nullable String check(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		CharacterState saved = CharacterStore.get(player);
		EconomyService economy = HearthServices.economy();
		long coin = economy == null ? 0 : economy.balance(player);
		List<Resident> tutors = new ArrayList<>();
		Settlement town = null;
		try {
			String p = skills(player);
			if (p != null) {
				return p;
			}
			if (economy == null) {
				return "Hearth's economy module is not registered";
			}
			town = town(server);
			if (town == null) {
				return "no settlement near spawn, and none could be founded";
			}
			Mettle.LOGGER.info("Hearth journey: {} ({}) at {}", town.name(), town.tier().getSerializedName(), town.center().toShortString());
			p = questsAndTrade(player, town);
			if (p != null) {
				return p;
			}
			p = prices(player, economy, town);
			if (p != null) {
				return p;
			}
			return trainers(player, economy, town, tutors);
		} finally {
			for (Resident t : tutors) {
				town.removeResident(t.id);
			}
			if (economy != null) {
				long now = economy.balance(player);
				if (now > coin) {
					economy.charge(player, now - coin);
				} else if (now < coin) {
					economy.pay(player, coin - now);
				}
			}
			CharacterStore.set(player, saved);
			EffectsApplier.apply(player);
		}
	}

	// ------------------------------------------------------------------ the player-skills slot

	private static @Nullable String skills(ServerPlayer player) {
		PlayerSkills skills = HearthServices.playerSkills();
		if (!"VOID Mettle".equals(skills.toString())) {
			return "Hearth's player-skills slot holds " + skills + ", not Mettle";
		}
		set(player, CharacterStore.get(player).withXp(SPEECH, Curves.skillXpFor(5)), 9);
		int presence = presence(player);
		if (skills.level(player, PlayerSkills.SPEECH) != 5 || skills.level(player, "speech") != 5) {
			return "Speech reads " + skills.level(player, PlayerSkills.SPEECH) + ", expected 5";
		}
		if (skills.level(player, "mettle:no_such_skill") != PlayerSkills.UNKNOWN_LEVEL) {
			return "an unknown skill should read 0";
		}
		if (skills.attribute(player, PlayerSkills.PRESENCE) != presence || skills.attribute(player, "luck") != PlayerSkills.UNKNOWN_ATTRIBUTE) {
			return "Presence reads " + skills.attribute(player, PlayerSkills.PRESENCE) + ", expected " + presence;
		}
		PlayerSkills.Check c = skills.check(player, PlayerSkills.SPEECH, 10);
		if (Math.abs(c.odds() - HearthRules.checkOdds(5, presence, 10)) > 1e-9 || c.roll() < 0 || c.roll() >= 1) {
			return "a fair Speech check had odds " + c.odds() + ", expected " + HearthRules.checkOdds(5, presence, 10);
		}
		// The tour's character is a Ranger: Field Dressing is theirs, Coin Sense the Merchant's.
		if (!skills.hasPerk(player, SignaturePerks.FIELD_DRESSING) || skills.hasPerk(player, SignaturePerks.COIN_SENSE)) {
			return "signature perks read wrong through hasPerk";
		}
		double before = CharacterStore.get(player).xp(THIEVERY);
		skills.award(player, PlayerSkills.THIEVERY, 20, "hearth:law/lockpick");
		double gained = CharacterStore.get(player).xp(THIEVERY) - before;
		if (Math.abs(gained - 20 * multiplier(player, THIEVERY)) > EPS) {
			return "a picked lock gave " + gained + " Thievery XP, expected 20 × multipliers";
		}
		return stealth(player, skills);
	}

	/**
	 * Stealth (docs/BUILD_FEEL.md §14): Hearth's witnesses read the effective level (Stealth 6, Shadow II +2, and The Quiet
	 * Way's +2 while this Hearth reads no bonus keys, its key otherwise), never more than one channel; a crime nobody saw
	 * teaches Stealth and counts toward Never Seen.
	 */
	private static @Nullable String stealth(ServerPlayer player, PlayerSkills skills) {
		boolean declared = java.util.Arrays.stream(PlayerSkills.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals("bonus"));
		if (declared != dev.voidmc.mettle.compat.HearthLink.bonus()) {
			return "the loaded Hearth " + (declared ? "declares" : "lacks") + " PlayerSkills.bonus, but Mettle thinks otherwise";
		}
		CharacterState state = CharacterStore.get(player).withXp(StealthSkill.STEALTH, Curves.skillXpFor(6));
		java.util.Map<net.minecraft.resources.ResourceKey<dev.voidmc.mettle.api.Perk>, Integer> perks = new java.util.HashMap<>(state.perks());
		perks.put(dev.voidmc.mettle.character.Perks.key(StealthSkill.SHADOW), 2);
		perks.put(dev.voidmc.mettle.character.Perks.key(StealthSkill.THE_QUIET_WAY), 1);
		CharacterStore.set(player, state.withPerks(perks));
		EffectsApplier.apply(player);
		int want = 6 + 2 + (declared ? 0 : dev.voidmc.mettle.character.skill.stealth.StealthRules.FALLBACK_QUIET_WAY);
		if (skills.level(player, PlayerSkills.STEALTH) != want) {
			return "Hearth reads Stealth " + skills.level(player, PlayerSkills.STEALTH) + ", expected the effective " + want + " (bonus keys "
				+ (declared ? "read" : "not read") + ")";
		}
		double sight = StealthSkill.bonus(player, dev.voidmc.mettle.compat.HearthKeys.STEALTH_SIGHT);
		if (Math.abs(sight - 0.10) > 1e-9) {
			return "The Quiet Way's hearth:stealth/sight reads " + sight + ", expected 0.10";
		}
		if (declared && Math.abs(skills.bonus(player, dev.voidmc.mettle.compat.HearthKeys.STEALTH_SIGHT) - sight) > 1e-9) {
			return "Hearth's bonus(stealth/sight) differs from Mettle's";
		}
		long counted = dev.voidmc.mettle.character.FeatCounters.get(player, StealthSkill.NEVER_SEEN_COUNTER);
		double before = CharacterStore.get(player).xp(StealthSkill.STEALTH);
		skills.award(player, PlayerSkills.STEALTH, 10, "hearth:law/unseen/theft");
		double gained = CharacterStore.get(player).xp(StealthSkill.STEALTH) - before;
		long now = dev.voidmc.mettle.character.FeatCounters.get(player, StealthSkill.NEVER_SEEN_COUNTER);
		dev.voidmc.mettle.character.FeatCounters.add(player, StealthSkill.NEVER_SEEN_COUNTER, counted - now);
		if (Math.abs(gained - 10 * multiplier(player, StealthSkill.STEALTH)) > EPS) {
			return "a crime nobody saw gave " + gained + " Stealth XP, expected 10 × multipliers";
		}
		return now - counted == dev.voidmc.mettle.character.skill.stealth.StealthRules.NEVER_SEEN_CRIME ? null
			: "a crime nobody saw counted " + (now - counted) + " toward Never Seen, expected 5";
	}

	// ------------------------------------------------------------------ quests and trade

	private static @Nullable String questsAndTrade(ServerPlayer player, Settlement town) {
		double gained = gain(player, SURVIVAL, () -> QuestEvents.COMPLETED.invoker().onEnded(player, town, QuestType.EXPLORATION,
			Identifier.fromNamespaceAndPath("hearth", "exploration/frostwatch"), SettlementTier.CITY));
		if (Math.abs(gained - 150 * multiplier(player, SURVIVAL)) > EPS) {
			return "an exploration quest gave " + gained + " Survival XP, expected 150 × multipliers";
		}
		gained = gain(player, SURVIVAL, () -> QuestEvents.FAILED.invoker().onEnded(player, town, QuestType.EXPLORATION,
			Identifier.fromNamespaceAndPath("hearth", "exploration/frostwatch"), SettlementTier.CITY));
		if (gained != 0) {
			return "a failed quest gave " + gained + " XP";
		}
		// A bounty pays the weapon used on it. The full tour fought with every weapon before this, so a first bounty closes
		// that tally (its XP goes to whatever was used most); then some bow work, and the bounty that pays it.
		QuestEvents.COMPLETED.invoker().onEnded(player, town, QuestType.BOUNTY, Identifier.fromNamespaceAndPath("hearth", "bounty/spiders"),
			SettlementTier.VILLAGE);
		Progress.award(player, MARKSMANSHIP, 30);
		gained = gain(player, MARKSMANSHIP, () -> QuestEvents.COMPLETED.invoker().onEnded(player, town, QuestType.BOUNTY,
			Identifier.fromNamespaceAndPath("hearth", "bounty/spiders"), SettlementTier.VILLAGE));
		if (Math.abs(gained - 120 * multiplier(player, MARKSMANSHIP)) > EPS) {
			return "a bounty fought with the bow gave " + gained + " Marksmanship XP, expected 120 × multipliers";
		}
		gained = gain(player, SPEECH, () -> MarketEvents.TRADED.invoker().onTraded(player, town, new ItemStack(Items.BREAD), 10, 300, true));
		if (Math.abs(gained - HearthRules.tradeXp(0, 3) * multiplier(player, SPEECH)) > EPS) {
			return "three crowns of business gave " + gained + " Speech XP";
		}
		return null;
	}

	// ------------------------------------------------------------------ prices

	private static @Nullable String prices(ServerPlayer player, EconomyService economy, Settlement town) {
		// Hearth never lets a player's factors pull a price through the town's spread (MarketPricer.Quote.adjusted: a town
		// short of a good sells it no cheaper than it buys it): look for a good this town has room on, so the check sees
		// Mettle's factor rather than the town's stock.
		ItemStack stack = null;
		long plainBuy = 0;
		long plainSell = 0;
		set(player, CharacterStore.get(player).withXp(SPEECH, 0), 0);
		for (var item : List.of(Items.IRON_INGOT, Items.BREAD, Items.WHEAT, Items.COAL, Items.OAK_LOG, Items.LEATHER, Items.COBBLESTONE,
			Items.COPPER_INGOT, Items.GOLD_INGOT)) {
			ItemStack s = new ItemStack(item, 64);
			long b = economy.price(player, town, s, true);
			long sl = economy.price(player, town, s, false);
			if (b >= 20 && sl < b * 0.85) {
				stack = s;
				plainBuy = b;
				plainSell = sl;
				break;
			}
		}
		if (stack == null) {
			return "no good at " + town.name() + " has room in its spread to show a price modifier";
		}
		set(player, CharacterStore.get(player).withXp(SPEECH, Curves.skillXpFor(20)), CharacterAttribute.MAX);
		long goodBuy = economy.price(player, town, stack, true);
		long goodSell = economy.price(player, town, stack, false);
		Mettle.LOGGER.info("Hearth journey: 64 {} at {}: buy {} → {}, sell {} → {} (Presence {}, Speech 20)", stack.getItem(), town.name(),
			plainBuy, goodBuy, plainSell, goodSell, presence(player));
		if (goodBuy >= plainBuy || goodSell <= plainSell) {
			return "Presence and Speech did not move prices: buy " + plainBuy + " → " + goodBuy + ", sell " + plainSell + " → " + goodSell;
		}
		return null;
	}

	// ------------------------------------------------------------------ trainers

	private static @Nullable String trainers(ServerPlayer player, EconomyService economy, Settlement town, List<Resident> tutors) {
		DialogueProvider trainers = DialogueProvider.providers().stream().filter(d -> d.getClass().getSimpleName().equals("Trainers")).findFirst().orElse(null);
		if (trainers == null) {
			return "Mettle's trainers are not among Hearth's dialogue providers";
		}
		String tier = town.tier().getSerializedName();
		int cap = HearthRules.trainerCap(tier);
		String id = "mettle:train/smithing";

		// A lesson: open, paid, taught.
		set(player, CharacterStore.get(player).withXp(SMITHING, Curves.skillXpFor(1)), 0);
		long fee = HearthRules.lessonFee(1);
		economy.pay(player, fee + 50);
		Resident smith = tutor(town, tutors, JobType.SMITH);
		DialogueProvider.Option open = option(trainers, player, town, smith, id);
		if (open == null || open.locked() || open.order() < DialogueProvider.TRAINING_FIRST || open.order() > DialogueProvider.TRAINING_LAST) {
			return "a " + tier + " smith offered " + open + " to a Smithing 1 player with coin";
		}
		long coinBefore = economy.balance(player);
		double gained = gain(player, SMITHING, () -> {
			if (!trainers.select(player, town, smith, id)) {
				throw new AssertionError("the lesson was refused");
			}
		});
		if (trainers.reply(player, town, smith, id) == null) {
			return "the smith said nothing after the lesson";
		}
		if (Math.abs(gained - HearthRules.lessonXp(1) * multiplier(player, SMITHING)) > EPS) {
			return "a lesson gave " + gained + " Smithing XP, expected " + HearthRules.lessonXp(1) + " × multipliers";
		}
		if (coinBefore - economy.balance(player) != fee) {
			return "the lesson cost " + (coinBefore - economy.balance(player)) + " bits, expected " + fee;
		}
		DialogueProvider.Option again = option(trainers, player, town, smith, id);
		if (again == null || !again.locked() || trainers.select(player, town, smith, id)) {
			return "the same smith taught twice in a day: " + again;
		}

		// The settlement's tier caps what its folk teach.
		set(player, CharacterStore.get(player).withXp(SMITHING, Curves.skillXpFor(cap)), 0);
		Resident second = tutor(town, tutors, JobType.SMITH);
		DialogueProvider.Option beyond = option(trainers, player, town, second, id);
		if (beyond == null || !beyond.locked() || trainers.select(player, town, second, id)) {
			return "a " + tier + " smith (cap " + cap + ") offered " + beyond + " to a Smithing " + cap + " player";
		}

		// Cards v2: the same smith rebuilds a Smithing deck: a draught, the fee to the trainer, a scar.
		String rebuild = rebuildAtTrainer(player, economy, town, trainers, second, cap);
		if (rebuild != null) {
			return rebuild;
		}

		// No coin, no lesson.
		set(player, CharacterStore.get(player).withXp(SMITHING, 0), 0);
		economy.charge(player, economy.balance(player));
		Resident third = tutor(town, tutors, JobType.SMITH);
		DialogueProvider.Option poor = option(trainers, player, town, third, id);
		if (poor == null || !poor.locked()) {
			return "a smith offered an open lesson to a player with no coin: " + poor;
		}

		// Only a city's master teaches the Teaching; folk who teach nothing offer nothing.
		boolean teaching = option(trainers, player, town, third, "mettle:teach/smithing") != null;
		if (teaching != (town.tier() == SettlementTier.CITY)) {
			return "a " + tier + " smith " + (teaching ? "offered" : "did not offer") + " Folded Steel";
		}
		List<DialogueProvider.Option> farmer = new ArrayList<>();
		trainers.collect(player, town, tutor(town, tutors, JobType.FARMER), farmer);
		if (!farmer.isEmpty()) {
			return "a farmer offered " + farmer;
		}
		return null;
	}

	/** A trainer's rebuild (CARDS.md §7.3): offered with a deck, opens a sitting capped by the tier, charges the fee to them. */
	private static @Nullable String rebuildAtTrainer(ServerPlayer player, EconomyService economy, Settlement town, DialogueProvider trainers,
		Resident smith, int cap) {
		String id = "mettle:rebuild/smithing";
		dev.voidmc.mettle.character.cards.Cards.clear(player);
		var card = dev.voidmc.mettle.character.cards.Cards.giveExact(player, new dev.voidmc.mettle.core.DraftState.Card(
			dev.voidmc.mettle.character.Perks.key(net.minecraft.resources.Identifier.fromNamespaceAndPath("mettle", "smithing/tinker")), 1,
			dev.voidmc.mettle.core.DraftState.Rarity.COMMON, false, List.of(net.minecraft.resources.Identifier.fromNamespaceAndPath("mettle", "ember")),
			List.of(new dev.voidmc.mettle.core.CardData.TraitRoll(net.minecraft.resources.Identifier.fromNamespaceAndPath("mettle", "smithing/diligent"), 8)), 2));
		if (dev.voidmc.mettle.character.cards.Cards.slot(player, card.id(), -1).isPresent()) {
			return "could not slot a Tinker card at Smithing " + cap;
		}
		dev.voidmc.mettle.core.CardStore.set(player, dev.voidmc.mettle.core.CardStore.get(player).withFreeSwaps(0).withFreeRebuilds(List.of()));
		DialogueProvider.Option offer = option(trainers, player, town, smith, id);
		if (offer == null || offer.locked() || offer.order() < DialogueProvider.TRAINING_FIRST || offer.order() > DialogueProvider.TRAINING_LAST) {
			return "a smith offered no open rebuild to a player with a Smithing deck: " + offer;
		}
		if (!trainers.select(player, town, smith, id) || dev.voidmc.mettle.character.cards.Rebuilds.sitting(player)
			.map(s -> s.kind() != dev.voidmc.mettle.character.cards.Rebuilds.Kind.TRAINER || s.cap() != cap).orElse(true)) {
			return "the rebuild option opened no trainer sitting capped at " + cap;
		}
		player.getInventory().add(dev.voidmc.mettle.character.respec.Draughts.forgetting(net.minecraft.resources.Identifier.fromNamespaceAndPath("mettle", "smithing")));
		long fee = dev.voidmc.mettle.character.cards.CardRules.feeBits(card.needs());
		economy.pay(player, fee + 10);
		long before = economy.balance(player);
		var no = dev.voidmc.mettle.character.cards.Rebuilds.rebuild(player, List.of(card.id()));
		if (no.isPresent()) {
			return "the trainer's rebuild was refused: " + no.get();
		}
		var after = dev.voidmc.mettle.core.CardStore.get(player).card(card.id()).orElseThrow();
		boolean master = town.tier() == SettlementTier.CITY;
		if (after.slotted() || before - economy.balance(player) != fee || (!master && after.scars() != 1)) {
			return "after the trainer's rebuild: " + after + ", paid " + (before - economy.balance(player)) + " of " + fee;
		}
		dev.voidmc.mettle.character.cards.Rebuilds.close(player);
		dev.voidmc.mettle.character.cards.Cards.clear(player);
		return null;
	}

	private static Resident tutor(Settlement town, List<Resident> tutors, JobType job) {
		Resident r = new Resident(UUID.randomUUID(), "Tamsin", "Tourwright", true, AgeStage.ADULT, -12000, job, Resident.NO_PLOT, Resident.NO_PLOT, 7, new CompoundTag());
		town.addResident(r);
		tutors.add(r);
		return r;
	}

	private static DialogueProvider.@Nullable Option option(DialogueProvider provider, ServerPlayer player, Settlement town, Resident resident, String id) {
		List<DialogueProvider.Option> out = new ArrayList<>();
		provider.collect(player, town, resident, out);
		return out.stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
	}

	/** A live settlement near spawn, or one founded on the first open ground found in rings round it. */
	private static @Nullable Settlement town(MinecraftServer server) {
		HearthWorld w = HearthServices.world(server.overworld());
		if (w == null) {
			return null;
		}
		BlockPos spawn = server.overworld().getRespawnData().pos();
		Settlement near = w.nearest(spawn, 2000);
		if (near != null) {
			return near;
		}
		for (int r = 128; r <= 720; r += 48) {
			for (int k = 0; k < 12; k++) {
				double a = k * Math.PI / 6;
				BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
				if (w.whyNotFound(at) == null) {
					Settlement s = w.found(at, null, null, "VOID Mettle client tour");
					if (s != null) {
						return s;
					}
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ helpers

	/** Sets the character and Presence to {@code presence} placed points above the background's (0: none placed). */
	private static void set(ServerPlayer player, CharacterState state, int presence) {
		Map<CharacterAttribute, Integer> spent = new EnumMap<>(CharacterAttribute.class);
		spent.putAll(state.spent());
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		int base = state.withSpent(Map.of()).attribute(CharacterAttribute.PRESENCE, background);
		spent.put(CharacterAttribute.PRESENCE, Math.clamp(presence == CharacterAttribute.MAX ? CharacterAttribute.MAX - base : presence, 0, CharacterAttribute.MAX - base));
		CharacterStore.set(player, state.withSpent(spent));
	}

	private static int presence(ServerPlayer player) {
		CharacterState state = CharacterStore.get(player);
		return state.attribute(CharacterAttribute.PRESENCE, CharacterStore.background(player.level().registryAccess(), state));
	}

	private static double multiplier(ServerPlayer player, ResourceKey<Skill> skill) {
		CharacterState state = CharacterStore.get(player);
		return Progress.multiplier(state, CharacterStore.background(player.level().registryAccess(), state), skill);
	}

	private static double gain(ServerPlayer player, ResourceKey<Skill> skill, Runnable action) {
		double before = CharacterStore.get(player).xp(skill);
		action.run();
		return CharacterStore.get(player).xp(skill) - before;
	}
}
