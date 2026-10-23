package dev.voidmc.bestiary.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.bestiary.Bestiary;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * What a boss saves (a Fabric attachment, so a fight survives a relog, a chunk unload and a server restart): its arena,
 * whether a fight is on, its phase, the Gallows Knight's cage, the participants with their damage and time in the arena,
 * when the arena last emptied, the party size its health was scaled for, the Break meter and the lair it belongs to.
 * Mutable; saved whenever the entity is.
 */
public final class BossData {
	public static final int VERSION = 1;

	/** A participant's share: damage dealt to the boss and ticks spent in the arena during the fight. */
	public static final class Participant {
		public static final Codec<Participant> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("damage", 0F).forGetter(p -> p.damage),
			Codec.INT.optionalFieldOf("present", 0).forGetter(p -> p.present),
			Codec.STRING.optionalFieldOf("name", "").forGetter(p -> p.name)).apply(i, Participant::new));

		public float damage;
		public int present;
		public String name;

		public Participant(float damage, int present, String name) {
			this.damage = damage;
			this.present = present;
			this.name = name;
		}
	}

	public static final Codec<BossData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("version", VERSION).forGetter(d -> VERSION),
		Arena.CODEC.fieldOf("arena").forGetter(d -> d.arena),
		Codec.BOOL.optionalFieldOf("active", false).forGetter(d -> d.active),
		Codec.INT.optionalFieldOf("phase", 1).forGetter(d -> d.phase),
		Codec.BOOL.optionalFieldOf("caged", true).forGetter(d -> d.caged),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Participant.CODEC).optionalFieldOf("participants", Map.of()).forGetter(d -> d.participants),
		Codec.LONG.optionalFieldOf("empty_since", 0L).forGetter(d -> d.emptySince),
		Codec.INT.optionalFieldOf("party", 1).forGetter(d -> d.party),
		Codec.FLOAT.optionalFieldOf("break", 0F).forGetter(d -> d.breakMeter),
		UUIDUtil.CODEC.optionalFieldOf("lair").forGetter(d -> Optional.ofNullable(d.lair)),
		Codec.LONG.optionalFieldOf("started", 0L).forGetter(d -> d.started),
		Codec.INT.optionalFieldOf("resets", 0).forGetter(d -> d.resets)).apply(i, BossData::new));

	public static final AttachmentType<BossData> TYPE = AttachmentRegistry.create(Bestiary.id("boss"), b -> b.persistent(CODEC));

	public Arena arena;
	public boolean active;
	public int phase;
	public boolean caged;
	public final Map<UUID, Participant> participants;
	public long emptySince;
	public int party;
	public float breakMeter;
	public @Nullable UUID lair;
	public long started;
	public int resets;

	BossData(int version, Arena arena, boolean active, int phase, boolean caged, Map<UUID, Participant> participants, long emptySince, int party,
		float breakMeter, Optional<UUID> lair, long started, int resets) {
		this.arena = arena;
		this.active = active;
		this.phase = phase;
		this.caged = caged;
		this.participants = new HashMap<>(participants);
		this.emptySince = emptySince;
		this.party = party;
		this.breakMeter = breakMeter;
		this.lair = lair.orElse(null);
		this.started = started;
		this.resets = resets;
	}

	public static BossData fresh(Arena arena) {
		return new BossData(VERSION, arena, false, 1, true, Map.of(), 0, 1, 0, Optional.empty(), 0, 0);
	}

	public static @Nullable BossData get(Mob mob) {
		return mob.getAttached(TYPE);
	}

	/** Stores it (and marks the entity dirty: attachments are saved with the entity). */
	public static void set(Mob mob, BossData d) {
		mob.setAttached(TYPE, d);
	}

	public float totalDamage() {
		float t = 0;
		for (Participant p : this.participants.values()) {
			t += p.damage;
		}
		return t;
	}

	public Participant participant(UUID id, String name) {
		return this.participants.computeIfAbsent(id, k -> new Participant(0, 0, name));
	}
}
