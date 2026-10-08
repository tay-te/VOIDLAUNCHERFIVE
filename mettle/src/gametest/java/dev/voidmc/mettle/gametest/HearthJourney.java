package dev.voidmc.mettle.gametest;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.MarketEvents;
import dev.voidmc.hearth.api.PlayerSkills;
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
		return null;
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
		// A bounty pays the weapon used on it: some bow work first.
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
		ItemStack stack = new ItemStack(Items.IRON_INGOT, 64);
		set(player, CharacterStore.get(player).withXp(SPEECH, 0), 0);
		long plainBuy = economy.price(player, town, stack, true);
		long plainSell = economy.price(player, town, stack, false);
		set(player, CharacterStore.get(player).withXp(SPEECH, Curves.skillXpFor(20)), CharacterAttribute.MAX);
		long goodBuy = economy.price(player, town, stack, true);
		long goodSell = economy.price(player, town, stack, false);
		Mettle.LOGGER.info("Hearth journey: 64 iron ingots at {}: buy {} → {}, sell {} → {} (Presence {}, Speech 20)", town.name(),
			plainBuy, goodBuy, plainSell, goodSell, presence(player));
		if (plainBuy < 20) {
			return "the market prices 64 iron ingots at " + plainBuy + " bits; too little to see a modifier";
		}
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
