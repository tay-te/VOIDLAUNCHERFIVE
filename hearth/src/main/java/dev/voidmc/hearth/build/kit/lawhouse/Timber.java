package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The timber of the law's carpentry (scaffolds, posts, beams, the bar of a court, stocks): the style's frame wood where
 * it is a whole wood family, else the first role that is (beam, furniture, door, shutter, fence), else dark oak, so a
 * gallows never comes out in bamboo mosaic or plaster by accident. Every paint answers in its form.
 */
public final class Timber {
	private final Mat mat;

	private Timber(Mat mat) {
		this.mat = mat;
	}

	/** The structural timber (posts and beams). */
	public static Timber frame(Draft d) {
		return first(d, Role.FRAME, Role.BEAM, Role.FURNITURE, Role.DOOR, Role.SHUTTER, Role.FENCE, Role.RAILING);
	}

	/** The joiner's timber (benches, boards, bars and trapdoors). */
	public static Timber joinery(Draft d) {
		return first(d, Role.FURNITURE, Role.DOOR, Role.SHUTTER, Role.FRAME, Role.BEAM, Role.FENCE);
	}

	private static Timber first(Draft d, Role... roles) {
		for (Role r : roles) {
			Mat m = d.mat(r);
			if (m.has(Form.LOG) && m.has(Form.STAIRS) && m.has(Form.SLAB) && m.has(Form.FENCE) && m.has(Form.TRAPDOOR)) {
				return new Timber(m);
			}
		}
		for (Role r : roles) {
			Mat m = d.mat(r);
			if (m.has(Form.STAIRS) && m.has(Form.SLAB) && m.has(Form.FENCE)) {
				return new Timber(m);
			}
		}
		return new Timber(d.mat(Role.FRAME));
	}

	public Mat mat() {
		return this.mat;
	}

	/** A post (a log standing). */
	public Paint post() {
		return this.mat.has(Form.LOG) ? this.mat.log(Direction.Axis.Y) : this.mat.full();
	}

	/** A beam lying along an axis. */
	public Paint beam(Direction.Axis axis) {
		return this.mat.has(Form.LOG) ? this.mat.log(axis) : this.mat.full();
	}

	/** A hewn beam (stripped log) lying along an axis. */
	public Paint hewn(Direction.Axis axis) {
		return this.mat.has(Form.STRIPPED_LOG) ? this.mat.strippedLog(axis) : beam(axis);
	}

	public Paint planks() {
		return this.mat.full();
	}

	public Paint slab(SlabType t) {
		return this.mat.has(Form.SLAB) ? this.mat.slab(t) : this.mat.full();
	}

	public Paint stairs(Direction facing, Half half) {
		return this.mat.stairsOrSlab(facing, half);
	}

	public Paint fence() {
		return this.mat.has(Form.FENCE) ? this.mat.fence() : Paint.of(Blocks.DARK_OAK_FENCE);
	}

	public Paint gate(Direction facing, boolean open) {
		return this.mat.has(Form.FENCE_GATE) ? this.mat.gate(facing, open) : Paint.of(Blocks.DARK_OAK_FENCE_GATE).map(s -> s
			.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing)
			.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, open));
	}

	public Paint trapdoor(Direction facing, Half half, boolean open) {
		if (this.mat.has(Form.TRAPDOOR)) {
			return this.mat.trapdoor(facing, half, open);
		}
		return Paint.of(Blocks.DARK_OAK_TRAPDOOR).map(s -> s
			.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing)
			.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF, half)
			.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, open));
	}
}
