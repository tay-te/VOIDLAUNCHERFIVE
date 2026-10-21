package dev.voidmc.hearth.social.events;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;

/**
 * An event in a town's book (docs/LIVING_WORLD_2.md §3.2): what, when (town ticks, {@code SocialTime}), who, where, and
 * the <b>outcome seed</b> with the facts it is decided from, taken when it was scheduled — so the outcome is the same
 * whether the town is abstract or embodied when it comes round.
 *
 * @param plot  the plot it is about (the burning house, the finished hall), or -1
 * @param args  the facts the outcome is decided from (a band's strength, the stores, the offence), and the words for it
 */
public record Scheduled(int id, EventKind kind, long day, long start, long end, List<UUID> principals, List<UUID> family, int plot, long seed,
	CompoundTag args, Phase phase) {

	public enum Phase implements StringRepresentable {
		SCHEDULED, BEGUN, ENDED;

		public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final Codec<Scheduled> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(Scheduled::id),
		EventKind.CODEC.fieldOf("kind").forGetter(Scheduled::kind),
		Codec.LONG.fieldOf("day").forGetter(Scheduled::day),
		Codec.LONG.fieldOf("start").forGetter(Scheduled::start),
		Codec.LONG.fieldOf("end").forGetter(Scheduled::end),
		UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("principals", List.of()).forGetter(Scheduled::principals),
		UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("family", List.of()).forGetter(Scheduled::family),
		Codec.INT.optionalFieldOf("plot", -1).forGetter(Scheduled::plot),
		Codec.LONG.optionalFieldOf("seed", 0L).forGetter(Scheduled::seed),
		CompoundTag.CODEC.optionalFieldOf("args", new CompoundTag()).forGetter(Scheduled::args),
		Phase.CODEC.optionalFieldOf("phase", Phase.SCHEDULED).forGetter(Scheduled::phase)
	).apply(i, Scheduled::new));

	public Scheduled withPhase(Phase p) {
		return new Scheduled(this.id, this.kind, this.day, this.start, this.end, this.principals, this.family, this.plot, this.seed, this.args, p);
	}

	public Scheduled withTimes(long start, long end) {
		return new Scheduled(this.id, this.kind, this.day, start, end, this.principals, this.family, this.plot, this.seed, this.args, this.phase);
	}

	public boolean activeAt(long townTick) {
		return townTick >= this.start && townTick < this.end;
	}

	/** A stable key for dressings and attendance ({@code events:<kind>:<id>}). */
	public String key() {
		return "events:" + this.kind.id() + ":" + this.id;
	}

	public boolean involves(UUID resident) {
		return this.principals.contains(resident) || this.family.contains(resident);
	}
}
