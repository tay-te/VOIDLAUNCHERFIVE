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
	 * The level Hearth reads: Stealth's is its effective level (Shadow, and the fallbacks while this Hearth reads no bonus
	 * keys; docs/BUILD_FEEL.md §14), at most 20; every other skill its plain level.
	 */
	@Override
	public int level(ServerPlayer player, String skill) {
		ResourceKey<Skill> key = skillKey(player, skill);
		if (key == null) {
			return UNKNOWN_LEVEL;
		}
		return key.equals(StealthSkill.STEALTH) ? StealthSkill.effectiveLevel(player) : CharacterStore.get(player).level(key);
	}

	/** Request 12: Mettle's bonus at one of Hearth's decision points (Stealth's keys today; 0 for any other key). */
	@Override
	public double bonus(ServerPlayer player, String key) {
		return StealthSkill.bonus(player, key);
	}

	/** Request 14: the title the player wears (the byname today), empty before their first set. */
	@Override
	public Component title(ServerPlayer player) {
		return MettleServices.cards().byname(player).orElse(Component.empty());
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
		int level = key.equals(StealthSkill.STEALTH) ? StealthSkill.effectiveLevel(player) : state.level(key);
		return HearthRules.checkOdds(level, state.attribute(s.attribute(), background), difficulty);
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
		Progress.award(player, key, xp);
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
