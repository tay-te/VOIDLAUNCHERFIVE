package dev.voidmc.hearth.npc.person;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.api.Resident;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * The NPC module's part of a resident: {@code Resident.extra("npc")}. A typed view over that compound, so the sim
 * module persists it without knowing what it means and nothing here holds state of its own.
 *
 * <pre>
 *   traits      [string]        personality ({@link Trait})
 *   parents     [uuid]          family links; children take a parent's family name
 *   spouse      uuid
 *   children    [uuid]
 *   friends     [uuid]          who they visit and chat with
 *   mood        int  -100..100  drifts with events, weather and how the player treats them
 *   pos         int[3]          where their entity was when it was folded away
 *   pos_time    long            the game time of that
 *   anchor      int[3]          home for those without a home plot (a folk in a player-built house)
 *   bed         int[3]          a bed found by scanning (no blueprint POIs)
 *   work_pos    int[3]          a job site found by scanning
 *   health      float           entity health when folded (wounds heal over a day)
 *   labour      int             work ticks done while embodied, not yet credited by the sim
 *   met         {uuid: int}     how many times each player has talked to them
 * </pre>
 */
public final class NpcData {
	public static final String KEY = "npc";
	private static final Codec<List<Trait>> TRAITS = Trait.CODEC.listOf();
	private static final Codec<List<UUID>> UUIDS = UUIDUtil.CODEC.listOf();

	private final CompoundTag tag;

	private NpcData(CompoundTag tag) {
		this.tag = tag;
	}

	/** The resident's npc compound (created if absent); changes write straight through to the record. */
	public static NpcData of(Resident resident) {
		return new NpcData(resident.extra(KEY));
	}

	public static NpcData wrap(CompoundTag tag) {
		return new NpcData(tag);
	}

	public CompoundTag tag() {
		return this.tag;
	}

	public Set<Trait> traits() {
		List<Trait> list = this.tag.read("traits", TRAITS).orElse(List.of());
		return list.isEmpty() ? EnumSet.noneOf(Trait.class) : EnumSet.copyOf(list);
	}

	public void setTraits(Set<Trait> traits) {
		this.tag.store("traits", TRAITS, new ArrayList<>(traits));
	}

	public boolean has(Trait trait) {
		return traits().contains(trait);
	}

	public List<UUID> parents() {
		return uuids("parents");
	}

	public void setParents(List<UUID> parents) {
		this.tag.store("parents", UUIDS, parents);
	}

	public Optional<UUID> spouse() {
		return this.tag.read("spouse", UUIDUtil.CODEC);
	}

	public void setSpouse(@Nullable UUID spouse) {
		if (spouse == null) {
			this.tag.remove("spouse");
		} else {
			this.tag.store("spouse", UUIDUtil.CODEC, spouse);
		}
	}

	public List<UUID> children() {
		return uuids("children");
	}

	public void addChild(UUID child) {
		addUuid("children", child);
	}

	public List<UUID> friends() {
		return uuids("friends");
	}

	public void addFriend(UUID friend) {
		addUuid("friends", friend);
	}

	public int mood() {
		return this.tag.getIntOr("mood", 0);
	}

	public void addMood(int delta) {
		this.tag.putInt("mood", Math.clamp(mood() + delta, -100, 100));
	}

	public Optional<BlockPos> lastPos() {
		return pos("pos");
	}

	public long lastPosTime() {
		return this.tag.getLongOr("pos_time", Long.MIN_VALUE);
	}

	public void setLastPos(BlockPos pos, long gameTime) {
		putPos("pos", pos);
		this.tag.putLong("pos_time", gameTime);
	}

	public Optional<BlockPos> anchor() {
		return pos("anchor");
	}

	public void setAnchor(@Nullable BlockPos pos) {
		putPos("anchor", pos);
	}

	public Optional<BlockPos> bed() {
		return pos("bed");
	}

	public void setBed(@Nullable BlockPos pos) {
		putPos("bed", pos);
	}

	public Optional<BlockPos> workPos() {
		return pos("work_pos");
	}

	public void setWorkPos(@Nullable BlockPos pos) {
		putPos("work_pos", pos);
	}

	/** Health when folded away, or -1 if unhurt. */
	public float health() {
		return this.tag.getFloatOr("health", -1.0F);
	}

	public void setHealth(float health) {
		if (health < 0) {
			this.tag.remove("health");
		} else {
			this.tag.putFloat("health", health);
		}
	}

	public int labour() {
		return this.tag.getIntOr("labour", 0);
	}

	public void addLabour(int ticks) {
		this.tag.putInt("labour", labour() + ticks);
	}

	/** Takes the labour not yet credited (the sim or the module crediting it calls this). */
	public int drainLabour() {
		int l = labour();
		this.tag.remove("labour");
		return l;
	}

	public int timesMet(UUID player) {
		return this.tag.getCompoundOrEmpty("met").getIntOr(player.toString(), 0);
	}

	public void recordMeeting(UUID player) {
		CompoundTag met = this.tag.getCompoundOrEmpty("met");
		met.putInt(player.toString(), met.getIntOr(player.toString(), 0) + 1);
		// Bound it: a resident in a busy server town remembers the last few dozen people, not everyone.
		if (met.size() > 48) {
			met.remove(met.keySet().iterator().next());
		}
		this.tag.put("met", met);
	}

	private List<UUID> uuids(String key) {
		return this.tag.read(key, UUIDS).orElse(List.of());
	}

	private void addUuid(String key, UUID id) {
		List<UUID> list = new ArrayList<>(uuids(key));
		if (!list.contains(id)) {
			list.add(id);
			this.tag.store(key, UUIDS, list);
		}
	}

	private Optional<BlockPos> pos(String key) {
		return this.tag.getIntArray(key).filter(a -> a.length == 3).map(a -> new BlockPos(a[0], a[1], a[2]));
	}

	private void putPos(String key, @Nullable BlockPos pos) {
		if (pos == null) {
			this.tag.remove(key);
		} else {
			this.tag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
		}
	}
}
