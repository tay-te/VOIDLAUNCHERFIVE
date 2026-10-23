package dev.voidmc.bestiary.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.bestiary.Bestiary;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * What Bestiary saves on a creature (a Fabric attachment; vanilla ignores it, so a world keeps loading without
 * Bestiary): its danger, whether it was rolled for elite, its affixes, its champion pack. Versioned; a creature from a
 * world made before Bestiary has none and gets one on first load (danger from where it stands, never elite).
 *
 * @param version   format version (1)
 * @param danger    danger 1-5 computed at spawn (or first load)
 * @param reason    the spawn reason ({@code natural}, {@code spawner}, ... or {@code legacy} for creatures older than Bestiary)
 * @param rolled    elite and pack rolls are done
 * @param elite     an elite
 * @param affixes   affix ids (path of {@code bestiary:<affix>})
 * @param leader    the champion pack's leader (members), empty otherwise
 * @param packSize  a champion pack leader's number of members
 * @param packSpawned the leader's members have been spawned
 */
public record CreatureData(int version, float danger, String reason, boolean rolled, boolean elite, List<String> affixes, Optional<java.util.UUID> leader,
	int packSize, boolean packSpawned) {
	public static final int VERSION = 1;
	public static final Codec<CreatureData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("version", VERSION).forGetter(CreatureData::version),
		Codec.FLOAT.optionalFieldOf("danger", -1F).forGetter(CreatureData::danger),
		Codec.STRING.optionalFieldOf("reason", "legacy").forGetter(CreatureData::reason),
		Codec.BOOL.optionalFieldOf("rolled", false).forGetter(CreatureData::rolled),
		Codec.BOOL.optionalFieldOf("elite", false).forGetter(CreatureData::elite),
		Codec.STRING.listOf().optionalFieldOf("affixes", List.of()).forGetter(CreatureData::affixes),
		UUIDUtil.CODEC.optionalFieldOf("leader").forGetter(CreatureData::leader),
		Codec.INT.optionalFieldOf("pack_size", 0).forGetter(CreatureData::packSize),
		Codec.BOOL.optionalFieldOf("pack_spawned", false).forGetter(CreatureData::packSpawned)).apply(i, CreatureData::new));

	public static final AttachmentType<CreatureData> TYPE = AttachmentRegistry.create(Bestiary.id("creature"), b -> b.persistent(CODEC));

	public static final CreatureData FRESH = new CreatureData(VERSION, -1, "legacy", false, false, List.of(), Optional.empty(), 0, false);

	public static @Nullable CreatureData get(Mob mob) {
		return mob.getAttached(TYPE);
	}

	public static CreatureData of(Mob mob) {
		CreatureData d = mob.getAttached(TYPE);
		return d == null ? FRESH : d;
	}

	public static void set(Mob mob, CreatureData d) {
		mob.setAttached(TYPE, d);
	}

	public boolean has(String affix) {
		return this.affixes.contains(affix);
	}

	public CreatureData withDanger(float d) {
		return new CreatureData(this.version, d, this.reason, this.rolled, this.elite, this.affixes, this.leader, this.packSize, this.packSpawned);
	}

	public CreatureData withReason(String r) {
		return new CreatureData(this.version, this.danger, r, this.rolled, this.elite, this.affixes, this.leader, this.packSize, this.packSpawned);
	}

	public CreatureData rolled(boolean elite, List<String> affixes, int packSize) {
		return new CreatureData(this.version, this.danger, this.reason, true, elite, List.copyOf(affixes), this.leader, packSize, false);
	}

	public CreatureData withLeader(java.util.UUID leader, List<String> affixes) {
		return new CreatureData(this.version, this.danger, this.reason, true, false, List.copyOf(affixes), Optional.of(leader), 0, false);
	}

	public CreatureData markPackSpawned() {
		return new CreatureData(this.version, this.danger, this.reason, this.rolled, this.elite, this.affixes, this.leader, this.packSize, true);
	}
}
