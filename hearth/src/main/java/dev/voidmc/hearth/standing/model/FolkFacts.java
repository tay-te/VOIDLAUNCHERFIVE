package dev.voidmc.hearth.standing.model;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.api.Resident;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

/**
 * What the standing module reads of the NPC module's part of a resident ({@code Resident.extra("npc")}, documented in
 * docs/FOLK.md): traits, mood and family. Read straight from the record so the two modules stay independent; missing
 * fields read as none.
 */
public final class FolkFacts {
	private static final Codec<List<String>> STRINGS = Codec.STRING.listOf();
	private static final Codec<List<UUID>> UUIDS = UUIDUtil.CODEC.listOf();

	private FolkFacts() {
	}

	private static CompoundTag npc(Resident r) {
		return r.extra.getCompoundOrEmpty("npc");
	}

	/** Trait names ({@code brave}, {@code greedy}…). */
	public static Set<String> traits(Resident r) {
		return new HashSet<>(npc(r).read("traits", STRINGS).orElse(List.of()));
	}

	public static int mood(Resident r) {
		return npc(r).getIntOr("mood", 0);
	}

	/** Spouse, children and parents (whoever of them is recorded). */
	public static List<UUID> family(Resident r) {
		CompoundTag tag = npc(r);
		List<UUID> out = new ArrayList<>();
		tag.read("spouse", UUIDUtil.CODEC).ifPresent(out::add);
		out.addAll(tag.read("children", UUIDS).orElse(List.of()));
		out.addAll(tag.read("parents", UUIDS).orElse(List.of()));
		out.remove(r.id);
		return out;
	}
}
