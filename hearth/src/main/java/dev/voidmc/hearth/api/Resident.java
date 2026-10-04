package dev.voidmc.hearth.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

/**
 * One person who lives in a settlement. This record is the person: it is persisted with the settlement (sim module)
 * whether or not an entity for them exists. The NPC module embodies a resident as an entity near players and writes
 * what happened back here when the entity goes away.
 *
 * <p>The core fields are what every module needs. Module-specific state (personality, relationships, schedule, purse,
 * memories of the player) goes in {@link #extra} under the owning module's own key (e.g. {@code extra.getCompound("npc")}),
 * so the sim module can persist it without knowing what it means.
 *
 * <p>Mutated on the server thread only.
 */
public final class Resident {
	public static final int NO_PLOT = -1;

	public static final Codec<Resident> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(r -> r.id),
		Codec.STRING.fieldOf("given").forGetter(r -> r.givenName),
		Codec.STRING.fieldOf("family").forGetter(r -> r.familyName),
		Codec.BOOL.fieldOf("female").forGetter(r -> r.female),
		AgeStage.CODEC.fieldOf("age").forGetter(r -> r.age),
		Codec.LONG.fieldOf("born").forGetter(r -> r.bornDay),
		JobType.CODEC.fieldOf("job").forGetter(r -> r.job),
		Codec.INT.optionalFieldOf("home", NO_PLOT).forGetter(r -> r.home),
		Codec.INT.optionalFieldOf("work", NO_PLOT).forGetter(r -> r.work),
		Codec.INT.fieldOf("appearance").forGetter(r -> r.appearance),
		CompoundTag.CODEC.optionalFieldOf("extra", new CompoundTag()).forGetter(r -> r.extra),
		Station.CODEC.optionalFieldOf("station").forGetter(r -> java.util.Optional.of(r.station))
	).apply(i, (id, given, family, female, age, born, job, home, work, appearance, extra, station) -> {
		Resident r = new Resident(id, given, family, female, age, born, job, home, work, appearance, extra);
		station.ifPresent(st -> r.station = st);
		return r;
	}));

	public final UUID id;
	public String givenName;
	public String familyName;
	public boolean female;
	public AgeStage age;
	/** The sim day they were born (may be negative for those born before the world began). */
	public long bornDay;
	public JobType job;
	/** Plot id of their home, or {@link #NO_PLOT}. */
	public int home;
	/** Plot id of their workplace, or {@link #NO_PLOT}. */
	public int work;
	/** Seed for their looks (skin tone, hair, build, clothes are derived from it with the culture and job). */
	public int appearance;
	public CompoundTag extra;
	/** The household's place in society (docs/LIVING_WORLD.md §8.1); starts from the job, then follows the purse. */
	public Station station;

	public Resident(UUID id, String givenName, String familyName, boolean female, AgeStage age, long bornDay, JobType job,
		int home, int work, int appearance, CompoundTag extra) {
		this.id = id;
		this.givenName = givenName;
		this.familyName = familyName;
		this.female = female;
		this.age = age;
		this.bornDay = bornDay;
		this.job = job;
		this.home = home;
		this.work = work;
		this.appearance = appearance;
		this.extra = extra.copy();
		this.station = Station.of(job);
	}

	public String fullName() {
		return this.familyName.isEmpty() ? this.givenName : this.givenName + " " + this.familyName;
	}

	/** The module's own compound in {@link #extra}, created if absent. */
	public CompoundTag extra(String module) {
		CompoundTag tag = this.extra.getCompoundOrEmpty(module);
		this.extra.put(module, tag);
		return tag;
	}

	@Override
	public String toString() {
		return "Resident[" + fullName() + ", " + this.age.getSerializedName() + " " + this.job.getSerializedName() + "]";
	}
}
