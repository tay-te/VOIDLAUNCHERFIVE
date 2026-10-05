package dev.voidmc.mettle.core.net;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.BackgroundRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	/** Client → server: buy the next rank of a perk. Answered with a feedback chip either way. */
	public record LearnPerk(ResourceKey<Perk> perk) implements CustomPacketPayload {
		public static final Type<LearnPerk> TYPE = new Type<>(Mettle.id("character/learn_perk"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LearnPerk> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(MettleRegistries.PERK), LearnPerk::perk, LearnPerk::new);

		@Override
		public Type<LearnPerk> type() {
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
