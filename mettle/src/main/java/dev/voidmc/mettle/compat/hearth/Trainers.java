package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Folk who teach (INTEGRATION.md §1.1): "Train Blades · 40 bits" in conversation, in Hearth's training band (300–399).
 * Who trains what follows the resident's job ({@link HearthRules#TRAINERS}); how far follows the settlement's tier
 * ({@link HearthRules#trainerCap}). A lesson is once a sim day per trainer, paid to the trainer's till or purse, and
 * worth {@link HearthRules#LESSON_SHARE} of the way to the next level. A city's master of the right trade also teaches
 * the skill's Teaching for {@link HearthRules#TEACHING_FEE} bits. Everything is checked again when picked: the screen
 * only shows what the server last said.
 */
final class Trainers implements DialogueProvider {
	static final String LESSON = "mettle:train/";
	static final String TEACHING = "mettle:teach/";
	private static final String LANG = "mettle.compat.hearth.";

	/** What the trainer says after the player's last pick, read once by {@link #reply}. */
	private final Map<UUID, Component> replies = new HashMap<>();

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		EconomyService economy = HearthServices.economy();
		List<String> skills = HearthRules.trains(resident.job.getSerializedName());
		if (economy == null || skills.isEmpty() || resident.age == AgeStage.CHILD) {
			return;
		}
		String tier = settlement.tier().getSerializedName();
		long day = day(player);
		long balance = economy.balance(player);
		CharacterState state = CharacterStore.get(player);
		for (int i = 0; i < skills.size(); i++) {
			String path = skills.get(i);
			ResourceKey<Skill> key = MettlePlayerSkills.skillKey(player, path);
			if (key == null) {
				continue;
			}
			Skill skill = skill(player, key);
			int order = Math.min(TRAINING_FIRST + 10 * i, TRAINING_LAST - 1);
			Option lesson = lesson(player, economy, state, resident, tier, day, balance, key, skill, path, order);
			if (lesson != null) {
				out.add(lesson);
			}
			if (HearthRules.teaches(resident.job.getSerializedName(), tier, path)) {
				Option teaching = teaching(player, economy, state, balance, key, skill, path, order + 1);
				if (teaching != null) {
					out.add(teaching);
				}
			}
		}
	}

	private @Nullable Option lesson(ServerPlayer player, EconomyService economy, CharacterState state, Resident resident, String tier,
		long day, long balance, ResourceKey<Skill> key, Skill skill, String path, int order) {
		int level = state.level(key);
		if (level >= dev.voidmc.mettle.core.Curves.SKILL_CAP) {
			return null;
		}
		long fee = HearthRules.lessonFee(level);
		Component price = economy.format(fee);
		Component label = Component.translatable(LANG + "train", skill.name(), price);
		String id = LESSON + path;
		if (!HearthRules.canTeach(tier, level)) {
			return new Option(id, label, order, true, Component.translatable(LANG + "hint.beyond"),
				Component.translatable(LANG + "tip.beyond", skill.name(), resident.givenName, HearthRules.trainerCap(tier)));
		}
		if (TrainerSessions.taughtToday(player, resident.id, day)) {
			return new Option(id, label, order, true, Component.translatable(LANG + "hint.tomorrow"),
				Component.translatable(LANG + "tip.tomorrow", resident.givenName));
		}
		if (balance < fee) {
			return new Option(id, label, order, true, Component.translatable(LANG + "hint.needs", price),
				Component.translatable(LANG + "tip.needs", price, economy.format(balance)));
		}
		int xp = (int) Math.round(lessonXp(player, state, key, level));
		return new Option(id, label, order, false, Component.translatable(LANG + "hint.xp", xp),
			Component.translatable(LANG + "tip.lesson", skill.name(), level, level + 1));
	}

	private @Nullable Option teaching(ServerPlayer player, EconomyService economy, CharacterState state, long balance,
		ResourceKey<Skill> key, Skill skill, String path, int order) {
		Holder.Reference<Perk> perk = teachingPerk(player, key);
		if (perk == null || state.rank(perk.key()) > 0) {
			return null;
		}
		Component price = economy.format(HearthRules.TEACHING_FEE);
		Component label = Component.translatable(LANG + "teach", perk.value().name(), price);
		String id = TEACHING + path;
		Component about = Component.translatable(LANG + "tip.teaching", perk.value().description());
		if (state.level(key) < HearthRules.TEACHING_MIN_LEVEL) {
			return new Option(id, label, order, true, Component.translatable(LANG + "hint.needs_level", skill.name(), HearthRules.TEACHING_MIN_LEVEL), about);
		}
		if (balance < HearthRules.TEACHING_FEE) {
			return new Option(id, label, order, true, Component.translatable(LANG + "hint.needs", price), about);
		}
		return new Option(id, label, order, false, null, about);
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		boolean lesson = optionId.startsWith(LESSON);
		if (!lesson && !optionId.startsWith(TEACHING)) {
			return false;
		}
		String path = optionId.substring(lesson ? LESSON.length() : TEACHING.length());
		String job = resident.job.getSerializedName();
		String tier = settlement.tier().getSerializedName();
		EconomyService economy = HearthServices.economy();
		ResourceKey<Skill> key = MettlePlayerSkills.skillKey(player, path);
		if (economy == null || key == null || resident.age == AgeStage.CHILD || !HearthRules.trains(job).contains(path)) {
			return false;
		}
		return lesson ? teachLesson(player, settlement, resident, economy, key, tier) : teachTeaching(player, settlement, resident, economy, key, job, tier, path);
	}

	private boolean teachLesson(ServerPlayer player, Settlement settlement, Resident resident, EconomyService economy, ResourceKey<Skill> key, String tier) {
		CharacterState state = CharacterStore.get(player);
		int level = state.level(key);
		long day = day(player);
		// Re-checked here: the options may be a moment old (a level gained, the coin spent, midnight passed).
		if (!HearthRules.canTeach(tier, level) || TrainerSessions.taughtToday(player, resident.id, day)) {
			return false;
		}
		long fee = HearthRules.lessonFee(level);
		if (!economy.charge(player, settlement, resident, fee)) {
			this.replies.put(player.getUUID(), Component.translatable(LANG + "reply.poor"));
			return true;
		}
		TrainerSessions.record(player, resident.id, day);
		Progress.award(player, key, HearthRules.lessonXp(level));
		// A master shows you another way to do it: a lesson also brings a draft reroll (docs/DRAFT.md §5).
		dev.voidmc.mettle.character.draft.Drafts.addRerolls(player, dev.voidmc.mettle.character.draft.DraftRules.REROLLS_PER_LESSON);
		Mettle.LOGGER.info("{} trained {} with {} of {} for {} bits", player.getName().getString(), key.identifier().getPath(),
			resident.fullName(), settlement.name(), fee);
		this.replies.put(player.getUUID(), Component.translatable(LANG + "reply.lesson." + player.getRandom().nextInt(3)));
		return true;
	}

	private boolean teachTeaching(ServerPlayer player, Settlement settlement, Resident resident, EconomyService economy, ResourceKey<Skill> key,
		String job, String tier, String path) {
		Holder.Reference<Perk> perk = teachingPerk(player, key);
		CharacterState state = CharacterStore.get(player);
		if (perk == null || !HearthRules.teaches(job, tier, path) || state.rank(perk.key()) > 0 || state.level(key) < HearthRules.TEACHING_MIN_LEVEL) {
			return false;
		}
		if (!economy.charge(player, settlement, resident, HearthRules.TEACHING_FEE)) {
			this.replies.put(player.getUUID(), Component.translatable(LANG + "reply.poor"));
			return true;
		}
		Perks.grant(player, perk.key());
		Mettle.LOGGER.info("{} was taught {} by {} of {}", player.getName().getString(), perk.key().identifier(), resident.fullName(), settlement.name());
		this.replies.put(player.getUUID(), Component.translatable(LANG + "reply.teaching", perk.value().name()));
		return true;
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		return this.replies.remove(player.getUUID());
	}

	void forget(UUID player) {
		this.replies.remove(player);
	}

	/** What a lesson will add, after the player's favoured and Wits multipliers (as {@link Progress#award} applies them). */
	private static double lessonXp(ServerPlayer player, CharacterState state, ResourceKey<Skill> key, int level) {
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		return HearthRules.lessonXp(level) * Progress.multiplier(state, background, key);
	}

	/** The skill's Teaching (its one perk of kind TEACHING), or null if it has none yet. */
	static Holder.@Nullable Reference<Perk> teachingPerk(ServerPlayer player, ResourceKey<Skill> skill) {
		return Perks.registry(player).listElements()
			.filter(r -> r.value().kind() == Perk.Kind.TEACHING && r.value().skill().equals(skill))
			.findFirst().orElse(null);
	}

	private static Skill skill(ServerPlayer player, ResourceKey<Skill> key) {
		return player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getValueOrThrow(key);
	}

	/** Hearth's sim day (the overworld's day count), or the overworld clock's day where Hearth keeps no world. */
	static long day(ServerPlayer player) {
		HearthWorld world = HearthServices.world(player.level());
		return world != null ? world.day() : player.level().getServer().overworld().getOverworldClockTime() / 24000L;
	}
}
