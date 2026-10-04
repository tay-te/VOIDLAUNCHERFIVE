package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Dressings for special days (work in progress). */
public final class Dressings {
	private Dressings() {
	}

	/** The days a building dresses for. */
	public enum Kind implements StringRepresentable {
		MARKET, FESTIVAL, WEDDING, MOURNING, WINTER;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byName(String s) {
			return valueOf(s.toUpperCase(Locale.ROOT));
		}
	}

	/** One block of a dressing, in world coordinates, in placing order. */
	public record Step(BlockPos pos, BlockState state, @Nullable CompoundTag tag) {
	}

	public static List<Step> steps(PlotSpec spec, Blueprint bp, Kind kind, long day) {
		return List.of();
	}
}
