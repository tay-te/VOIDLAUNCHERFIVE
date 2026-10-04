package dev.voidmc.hearth.build.kit.light;

import dev.voidmc.hearth.api.StageKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A light fitting as blocks: its parts (supports first), its emitters, what it costs a builder, and a taste bonus. */
public final class Fixture {
	/** The kinds of fitting, roughly in a builder's order of preference. */
	public enum Kind {
		/** A lantern hung from the ceiling, on a chain in taller rooms. */
		CEILING,
		/** A wheel of four lanterns on a chain, for tall halls. */
		WHEEL,
		/** A wheel of candles round a hung lantern (the north). */
		CANDLE_WHEEL,
		/** Candles already in the room, lit. */
		RELIGHT,
		/** Lit candles on a table, a barrel, a counter or a shelf. */
		CANDLES,
		/** A lantern stood on a piece of furniture. */
		LAMP,
		/** A lantern hung from a bracket on a wall. */
		SCONCE,
		/** A torch on a wall. */
		TORCH,
		/** A lantern hung under an eave, a porch roof or a gallery. */
		EAVE,
		/** A lantern (or a torch) stood on a fence or wall post. */
		POST_TOP,
		/** The culture's yard light: a lamp post, a brazier, a stone lantern, a tiki torch. */
		YARD,
		/** Light hidden under a rug, set into the floor. */
		GLOW,
		/** Vanilla's invisible light block: the last resort. */
		HIDDEN
	}

	/** One block of a fitting; a null stage means the stage of where it stands (inside or out). */
	public record Part(int x, int y, int z, BlockState state, @Nullable StageKind stage) {
	}

	final Kind kind;
	final List<Part> parts = new ArrayList<>();
	/** {x, y, z, emission} */
	final List<int[]> emitters = new ArrayList<>();
	final double cost;
	double bonus;
	/** For the last resort: what it was put there for ("room", "door", "dark", "pocket"). */
	String why = "";

	Fixture(Kind kind, double cost) {
		this.kind = kind;
		this.cost = cost;
	}

	Fixture part(int x, int y, int z, BlockState s) {
		this.parts.add(new Part(x, y, z, s, null));
		if (s.getLightEmission() > 0) {
			this.emitters.add(new int[]{x, y, z, s.getLightEmission()});
		}
		return this;
	}

	Fixture part(int x, int y, int z, BlockState s, @Nullable StageKind stage) {
		this.parts.add(new Part(x, y, z, s, stage));
		if (s.getLightEmission() > 0) {
			this.emitters.add(new int[]{x, y, z, s.getLightEmission()});
		}
		return this;
	}

	Fixture copyFrom(Fixture o) {
		this.parts.addAll(o.parts);
		this.emitters.addAll(o.emitters);
		this.bonus = o.bonus;
		return this;
	}

	/**
	 * Whether the fitting puts up something with a body where there was air (a bracket, a post, a stand, a cap): it can
	 * roof the cells under it or give folk a step, which changes what the light rule asks. Lanterns, chains, candles,
	 * torches, carpets and light blocks change nothing; a glowing floor block takes a floor block's place.
	 */
	boolean shapes() {
		for (Part p : this.parts) {
			var b = p.state().getBlock();
			if (this.kind == Kind.GLOW || b instanceof net.minecraft.world.level.block.LanternBlock || b instanceof net.minecraft.world.level.block.ChainBlock
				|| b instanceof net.minecraft.world.level.block.CandleBlock || b instanceof net.minecraft.world.level.block.BaseTorchBlock
				|| b instanceof net.minecraft.world.level.block.LightBlock || b instanceof net.minecraft.world.level.block.CarpetBlock) {
				continue;
			}
			return true;
		}
		return false;
	}

	public String why() {
		return this.why;
	}

	public Kind kind() {
		return this.kind;
	}

	public List<Part> parts() {
		return this.parts;
	}
}
