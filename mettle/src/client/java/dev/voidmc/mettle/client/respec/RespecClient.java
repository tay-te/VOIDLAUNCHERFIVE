package dev.voidmc.mettle.client.respec;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.character.respec.Draughts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Draught liquid takes the colour of the skill it was brewed for, read from the skill's data ({@code "colour"}). */
public final class RespecClient {
	private RespecClient() {
	}

	public static void init() {
		ItemTintSources.ID_MAPPER.put(Mettle.id("skill_colour"), SkillColour.MAP_CODEC);
	}

	/** Item model tint {@code mettle:skill_colour}: the colour of the stack's {@code mettle:forgets} skill, else {@code default}. */
	public record SkillColour(int defaultColor) implements ItemTintSource {
		public static final MapCodec<SkillColour> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(SkillColour::defaultColor)
		).apply(i, SkillColour::new));

		@Override
		public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
			Identifier skill = stack.get(Draughts.FORGETS);
			ClientLevel world = level != null ? level : Minecraft.getInstance().level;
			if (skill != null && world != null) {
				return world.registryAccess().lookupOrThrow(MettleRegistries.SKILL).getOptional(skill)
					.map(s -> ARGB.opaque(s.colour()))
					.orElse(ARGB.opaque(this.defaultColor));
			}
			return ARGB.opaque(this.defaultColor);
		}

		@Override
		public MapCodec<SkillColour> type() {
			return MAP_CODEC;
		}
	}
}
