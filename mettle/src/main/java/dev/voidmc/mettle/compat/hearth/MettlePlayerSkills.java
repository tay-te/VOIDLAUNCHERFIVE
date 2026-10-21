package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.SpeechSkill;
import dev.voidmc.mettle.character.skill.StealthSkill;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Mettle's answer to Hearth's {@link PlayerSkills} slot: the numbers Hearth's speech, haggling, lockpicking, pickpocketing
 * and stealth read, and the door Hearth reports XP through. Keys: skills by id ({@code mettle:speech}; a bare
 * {@code speech} is taken as Mettle's), attributes by name ({@code presence}). Server thread.
 */
final class MettlePlayerSkills implements PlayerSkills {
	/**
	 * The level Hearth reads: Stealth's and Speech's are their effective levels (the perks, traits and Teachings that
	 * count higher wherever Hearth reads them, and the fallbacks while this Hearth reads no bonus keys; docs/BUILD_FEEL.md
	 * §14), at most 20; every other skill its plain level.
	 */
	@Override
	public int level(ServerPlayer player, String skill) {
		ResourceKey<Skill> key = skillKey(player, skill);
		if (key == null) {
			return UNKNOWN_LEVEL;
		}
		return effective(player, key);
	}

	static int effective(ServerPlayer player, ResourceKey<Skill> key) {
		if (key.equals(StealthSkill.STEALTH)) {
			return StealthSkill.effectiveLevel(player);
		}
		if (key.equals(SpeechSkill.SPEECH)) {
			return SpeechSkill.effectiveLevel(player);
		}
		return CharacterStore.get(player).level(key);
	}

	/**
	 * Request 12: Mettle's bonus at one of Hearth's decision points ({@code PlayerSkills.BONUS_*}): Speech's haggling,
	 * speech-check, retry, standing and greeting keys and Stealth's sight, give-up, unseen-beyond and pickpocket keys; 0 for
	 * any other. Each effect answers here or through {@link #level} / {@link #attribute}, never both (HearthChannels).
	 */
	@Override
	public double bonus(ServerPlayer player, String key) {
		if (key.startsWith("hearth:stealth/") || key.startsWith("hearth:pickpocket/")) {
			return StealthSkill.bonus(player, key);
		}
		return SpeechSkill.bonus(player, key);
	}

	/**
	 * Request 14: the title the player wears: a deed title worn instead of the byname, else the byname, else a deed title
	 * worn beside it, else the legend title (BUILD_FEEL.md §2.1, §5.3, §12); empty when none.
	 */
	@Override
	public Component title(ServerPlayer player) {
		var b = dev.voidmc.mettle.character.cards.BuildFeel.get(player);
		java.util.Optional<Component> deed = b.titles().worn().map(dev.voidmc.mettle.character.cards.Deeds::title);
		if (deed.isPresent() && b.titles().instead()) {
			return deed.get();
		}
		java.util.Optional<Component> byname = MettleServices.cards().byname(player);
		if (byname.isPresent()) {
			return byname.get();
		}
		if (deed.isPresent()) {
			return deed.get();
		}
		return b.legend() > 0 ? Component.translatable("mettle.byname.legend", b.legendOf()) : Component.empty();
	}

	/** Request 10: the Mythics the player holds, as {@code mettle:mythic/<id>}. */
	@Override
	public List<Identifier> titles(ServerPlayer player) {
		List<Identifier> held = player.getAttached(dev.voidmc.mettle.character.cards.Mythics.PUBLIC);
		return held == null ? List.of() : held.stream().map(id -> id.withPath("mythic/" + id.getPath())).toList();
	}

	@Override
	public int attribute(ServerPlayer player, String attribute) {
		CharacterAttribute a = attribute(attribute);
		if (a == null) {
			return UNKNOWN_ATTRIBUTE;
		}
		if (a == CharacterAttribute.PRESENCE) {
			// Commanding Voice, Voice of the Realm, Demagogue, The Courtier's Manners (and Charmer's fallback): at most 20.
			return SpeechSkill.effectivePresence(player);
		}
		CharacterState state = CharacterStore.get(player);
		return state.attribute(a, CharacterStore.background(player.level().registryAccess(), state));
	}

	/** A perk held (bought or earned), or the signature perk of the player's background ({@code mettle:coin_sense}). */
	@Override
	public boolean hasPerk(ServerPlayer player, Identifier perk) {
		return Perks.has(player, perk) || SignaturePerks.has(player, perk);
	}

	/** Odds by {@link HearthRules#checkOdds} from the skill's level and its governing attribute; rolled now. No XP: Hearth awards it. */
	@Override
	public Check check(ServerPlayer player, String skill, int difficulty) {
		return new Check(odds(player, skill, difficulty), player.getRandom().nextDouble());
	}

	static double odds(ServerPlayer player, String skill, int difficulty) {
		ResourceKey<Skill> key = skillKey(player, skill);
		if (key == null) {
			return HearthRules.checkOdds(UNKNOWN_LEVEL, UNKNOWN_ATTRIBUTE, difficulty);
		}
		CharacterState state = CharacterStore.get(player);
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		Skill s = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getValueOrThrow(key);
		int level = effective(player, key);
		int attribute = s.attribute() == CharacterAttribute.PRESENCE ? SpeechSkill.effectivePresence(player) : state.attribute(s.attribute(), background);
		return HearthRules.checkOdds(level, attribute, difficulty);
	}

	/**
	 * Hearth's XP for a passed check, a struck haggle, a picked lock... through {@link Progress#award}, so the favoured and
	 * Wits multipliers and every {@code MODIFY_XP} listener apply. Unreleased skills (Speech, Thievery) bank it
	 * for when their trees ship.
	 */
	@Override
	public void award(ServerPlayer player, String skill, float xp, String reason) {
		ResourceKey<Skill> key = skillKey(player, skill);
		if (key == null || !(xp > 0)) {
			return;
		}
		Mettle.LOGGER.debug("Hearth awards {} {} XP in {} ({})", player.getName().getString(), xp, key.identifier(), reason);
		// A crime nobody saw counts toward Stealth's Never Seen (20 of them).
		if (reason.startsWith("hearth:law/unseen/") && !player.isCreative()) {
			dev.voidmc.mettle.character.FeatCounters.add(player, StealthSkill.NEVER_SEEN_COUNTER, dev.voidmc.mettle.character.skill.stealth.StealthRules.NEVER_SEEN_CRIME);
		}
		double base = xp;
		if (key.equals(SpeechSkill.SPEECH)) {
			base = HearthChecks.awarded(player, reason, xp);
		}
		Progress.award(player, key, base);
	}

	/** A registered Mettle skill by id, or null. */
	static @Nullable ResourceKey<Skill> skillKey(ServerPlayer player, String skill) {
		Identifier id = skill.indexOf(':') < 0 ? Mettle.id(skill) : Identifier.tryParse(skill);
		if (id == null) {
			return null;
		}
		ResourceKey<Skill> key = ResourceKey.create(MettleRegistries.SKILL, id);
		Optional<Skill> s = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getOptional(key);
		return s.isPresent() ? key : null;
	}

	private static @Nullable CharacterAttribute attribute(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		int colon = n.indexOf(':');
		if (colon >= 0) {
			n = n.substring(colon + 1);
		}
		for (CharacterAttribute a : CharacterAttribute.values()) {
			if (a.getSerializedName().equals(n)) {
				return a;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return "VOID Mettle";
	}
}
