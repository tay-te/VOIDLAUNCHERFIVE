package dev.voidmc.mettle.core.net;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.BackgroundRules;
import dev.voidmc.mettle.character.moment.MomentKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Mettle's packets. Client→server payloads are requests: the server validates every one against the character. */
public final class MettlePayloads {
	private MettlePayloads() {
	}

	/** Server → client: show the background picker (first join, or after an op reset, which the picker mentions). */
	public record OpenPicker(boolean byOperator) implements CustomPacketPayload {
		public static final Type<OpenPicker> TYPE = new Type<>(Mettle.id("core/open_picker"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPicker> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, OpenPicker::byOperator, OpenPicker::new);

		@Override
		public Type<OpenPicker> type() {
			return TYPE;
		}
	}

	/**
	 * Client → server: "begin as this background". {@code spent} and {@code favoured} are only used by backgrounds that let
	 * the player place points or pick skills (the Wanderer); the server ignores them otherwise.
	 */
	public record ChooseBackground(ResourceKey<Background> background, Map<CharacterAttribute, Integer> spent, List<ResourceKey<Skill>> favoured)
		implements CustomPacketPayload {
		public static final Type<ChooseBackground> TYPE = new Type<>(Mettle.id("character/choose_background"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ChooseBackground> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(MettleRegistries.BACKGROUND), ChooseBackground::background,
			ByteBufCodecs.map(HashMap::new, CharacterAttribute.STREAM_CODEC, ByteBufCodecs.VAR_INT, 5), ChooseBackground::spent,
			ResourceKey.streamCodec(MettleRegistries.SKILL).apply(ByteBufCodecs.list(4)), ChooseBackground::favoured,
			ChooseBackground::new);

		@Override
		public Type<ChooseBackground> type() {
			return TYPE;
		}
	}

	/** Server → client: the background was accepted (the picker closes and the first hint shows). */
	public record BackgroundChosen(ResourceKey<Background> background) implements CustomPacketPayload {
		public static final Type<BackgroundChosen> TYPE = new Type<>(Mettle.id("character/background_chosen"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BackgroundChosen> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(MettleRegistries.BACKGROUND), BackgroundChosen::background,
			BackgroundChosen::new);

		@Override
		public Type<BackgroundChosen> type() {
			return TYPE;
		}
	}

	/** Server → client: the background was refused; the picker says why and lets the player choose again. */
	public record BackgroundRejected(BackgroundRules.Reason reason) implements CustomPacketPayload {
		public static final Type<BackgroundRejected> TYPE = new Type<>(Mettle.id("character/background_rejected"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BackgroundRejected> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> BackgroundRules.Reason.values()[i], Enum::ordinal), BackgroundRejected::reason,
			BackgroundRejected::new);

		@Override
		public Type<BackgroundRejected> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: a signature perk (later, any perk) just did something, for a HUD chip ("Rally · +1 ♥"). The
	 * overlay merges repeats and fades them.
	 */
	public record Feedback(Identifier perk, Component text) implements CustomPacketPayload {
		public static final Type<Feedback> TYPE = new Type<>(Mettle.id("character/feedback"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Feedback> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, Feedback::perk,
			ComponentSerialization.TRUSTED_STREAM_CODEC, Feedback::text,
			Feedback::new);

		@Override
		public Type<Feedback> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: a progress moment (UI.md §6b): a skill level (and the tier it opened), a character level, a perk
	 * learned or earned. Sent just before the matching {@link Feedback} chip, which the client then leaves off the action
	 * bar because the toast says it. {@code tier} is 0 unless a tier opened; {@code rank} is 0 for level moments.
	 */
	public record Moment(MomentKind kind, Optional<ResourceKey<Skill>> skill, int level, int tier, Optional<Identifier> perk, int rank)
		implements CustomPacketPayload {
		public static final Type<Moment> TYPE = new Type<>(Mettle.id("character/moment"));
		private static final StreamCodec<io.netty.buffer.ByteBuf, MomentKind> KIND = ByteBufCodecs.idMapper(i -> MomentKind.values()[i], Enum::ordinal);
		public static final StreamCodec<RegistryFriendlyByteBuf, Moment> STREAM_CODEC = StreamCodec.composite(
			KIND, Moment::kind,
			ByteBufCodecs.optional(ResourceKey.streamCodec(MettleRegistries.SKILL)), Moment::skill,
			ByteBufCodecs.VAR_INT, Moment::level,
			ByteBufCodecs.VAR_INT, Moment::tier,
			ByteBufCodecs.optional(Identifier.STREAM_CODEC), Moment::perk,
			ByteBufCodecs.VAR_INT, Moment::rank,
			Moment::new);

		@Override
		public Type<Moment> type() {
			return TYPE;
		}

		/** The id of the {@link Feedback} chip this moment stands for ({@code mettle:level/mining}, or the perk). */
		public Identifier feedbackId() {
			return switch (this.kind) {
				case LEVEL, TIER -> Mettle.id("level/" + this.skill.map(k -> k.identifier().getPath()).orElse("unknown"));
				case CHARACTER -> Mettle.id("level/character");
				default -> this.perk.orElse(Mettle.id("unknown"));
			};
		}
	}

	/**
	 * Client → server: the old "buy the next rank of a perk". Perks come from drafts now (docs/DRAFT.md); the server answers
	 * with a chip that says so. Kept registered so an older host that still sends it is told, not disconnected.
	 */
	public record LearnPerk(ResourceKey<Perk> perk) implements CustomPacketPayload {
		public static final Type<LearnPerk> TYPE = new Type<>(Mettle.id("character/learn_perk"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LearnPerk> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(MettleRegistries.PERK), LearnPerk::perk, LearnPerk::new);

		@Override
		public Type<LearnPerk> type() {
			return TYPE;
		}
	}

	/** Client → server: keep the {@code index}-th card (0–2) of a skill's draft hand. Answered with a chip either way. */
	public record PickCard(ResourceKey<Skill> skill, int index) implements CustomPacketPayload {
		public static final Type<PickCard> TYPE = new Type<>(Mettle.id("character/pick_card"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PickCard> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(MettleRegistries.SKILL), PickCard::skill,
			ByteBufCodecs.VAR_INT, PickCard::index,
			PickCard::new);

		@Override
		public Type<PickCard> type() {
			return TYPE;
		}
	}

	/** Client → server: spend a reroll on a fresh hand for a skill's draft. */
	public record Reroll(ResourceKey<Skill> skill) implements CustomPacketPayload {
		public static final Type<Reroll> TYPE = new Type<>(Mettle.id("character/reroll"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reroll> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(MettleRegistries.SKILL), Reroll::skill, Reroll::new);

		@Override
		public Type<Reroll> type() {
			return TYPE;
		}
	}

	/** Client → server: place one attribute point from character levels. */
	public record SpendAttribute(CharacterAttribute attribute) implements CustomPacketPayload {
		public static final Type<SpendAttribute> TYPE = new Type<>(Mettle.id("character/spend_attribute"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SpendAttribute> STREAM_CODEC = StreamCodec.composite(
			CharacterAttribute.STREAM_CODEC, SpendAttribute::attribute, SpendAttribute::new);

		@Override
		public Type<SpendAttribute> type() {
			return TYPE;
		}
	}
}
