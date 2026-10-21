package dev.voidmc.mettle.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

/**
 * A Mythic card (docs/CARDS.md §9): one named card with one effect nothing else in Mettle has. Data, synced, loaded from
 * {@code data/<ns>/mettle/mythic/<id>.json}.
 *
 * @param source  how it is earned: one world first, a deed anyone may do once, or drawn (one per skill, once per world)
 * @param trigger the deed or first it answers (handled in code), e.g. {@code first_dragon}, {@code unclad_warden}
 * @param skill   drawn Mythics: the skill whose drafts can deal it
 * @param effect  what it does, one plain sentence
 * @param deed    how it is earned, one plain sentence ("First to fell the Ender Dragon")
 * @param epithet the last words of a tier IV byname worn with it slotted ("of the Mountain", docs/BUILD_FEEL.md §2.1)
 */
public record Mythic(Component name, Component lore, Component effect, Component deed, Source source, String trigger,
	Optional<ResourceKey<Skill>> skill, Identifier icon, Component epithet) {
	public enum Source implements StringRepresentable {
		WORLD_FIRST, DEED, DRAWN;

		public static final StringRepresentable.EnumCodec<Source> CODEC = StringRepresentable.fromEnum(Source::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final Codec<Mythic> CODEC = RecordCodecBuilder.create(i -> i.group(
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Mythic::name),
		ComponentSerialization.CODEC.fieldOf("lore").forGetter(Mythic::lore),
		ComponentSerialization.CODEC.fieldOf("effect").forGetter(Mythic::effect),
		ComponentSerialization.CODEC.fieldOf("deed").forGetter(Mythic::deed),
		Source.CODEC.fieldOf("source").forGetter(Mythic::source),
		Codec.STRING.fieldOf("trigger").forGetter(Mythic::trigger),
		ResourceKey.codec(MettleRegistries.SKILL).optionalFieldOf("skill").forGetter(Mythic::skill),
		Identifier.CODEC.fieldOf("icon").forGetter(Mythic::icon),
		ComponentSerialization.CODEC.optionalFieldOf("epithet", Component.empty()).forGetter(Mythic::epithet)
	).apply(i, Mythic::new));
}
