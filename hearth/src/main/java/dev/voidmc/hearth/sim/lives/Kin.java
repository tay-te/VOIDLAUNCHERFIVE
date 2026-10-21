package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.Resident;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * What the lives read of a resident's family and nature from the npc module's documented part of
 * {@code Resident.extra("npc")} (docs/FOLK.md §3: {@code spouse}, {@code parents}, {@code children}, {@code traits}) and
 * the sim's record of a birth ({@code extra("sim").parent_a/b}). Read straight off the tags, without codecs, because the
 * daily pass reads them for every resident. Read-only.
 */
public final class Kin {
	private Kin() {
	}

	private static @Nullable CompoundTag npc(Resident r) {
		return r.extra.get("npc") instanceof CompoundTag t ? t : null;
	}

	static @Nullable UUID uuid(@Nullable Tag tag) {
		if (tag instanceof IntArrayTag a) {
			int[] i = a.getAsIntArray();
			if (i.length == 4) {
				return new UUID((long) i[0] << 32 | i[1] & 0xFFFFFFFFL, (long) i[2] << 32 | i[3] & 0xFFFFFFFFL);
			}
		} else if (tag instanceof StringTag s) {
			try {
				return UUID.fromString(s.value());
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		return null;
	}

	public static @Nullable UUID spouse(Resident r) {
		CompoundTag t = npc(r);
		return t == null ? null : uuid(t.get("spouse"));
	}

	public static boolean married(Resident r) {
		CompoundTag t = npc(r);
		return t != null && t.contains("spouse");
	}

	private static void uuids(@Nullable CompoundTag t, String key, List<UUID> out) {
		if (t != null && t.get(key) instanceof ListTag list) {
			for (Tag e : list) {
				UUID u = uuid(e);
				if (u != null && !out.contains(u)) {
					out.add(u);
				}
			}
		}
	}

	public static List<UUID> parents(Resident r) {
		List<UUID> out = new ArrayList<>(2);
		uuids(npc(r), "parents", out);
		if (r.extra.get("sim") instanceof CompoundTag sim) {
			for (String k : new String[] {"parent_a", "parent_b"}) {
				UUID u = uuid(sim.get(k));
				if (u != null && !out.contains(u)) {
					out.add(u);
				}
			}
		}
		return out;
	}

	public static List<UUID> children(Resident r) {
		List<UUID> out = new ArrayList<>(2);
		uuids(npc(r), "children", out);
		return out;
	}

	/** Whether the resident has this trait ({@code sociable}, {@code grumpy}, {@code pious}...). */
	public static boolean has(Resident r, String trait) {
		CompoundTag t = npc(r);
		if (t != null && t.get("traits") instanceof ListTag list) {
			for (Tag e : list) {
				if (e instanceof StringTag s && s.value().equals(trait)) {
					return true;
				}
			}
		}
		return false;
	}

	/** {@link #nature}: sociable or cheerful. */
	static final int SOCIABLE = 1;
	/** {@link #nature}: shy or grumpy. */
	static final int SHY = 2;

	/** The traits the daily pass weighs, read in one pass: {@link #SOCIABLE}, {@link #SHY}. */
	static int nature(Resident r) {
		CompoundTag t = npc(r);
		int bits = 0;
		if (t != null && t.get("traits") instanceof ListTag list) {
			for (Tag e : list) {
				if (e instanceof StringTag s) {
					switch (s.value()) {
						case "sociable", "cheerful" -> bits |= SOCIABLE;
						case "shy", "grumpy" -> bits |= SHY;
						default -> {
						}
					}
				}
			}
		}
		return bits;
	}

	public static List<String> traits(Resident r) {
		List<String> out = new ArrayList<>(4);
		CompoundTag t = npc(r);
		if (t != null && t.get("traits") instanceof ListTag list) {
			for (Tag e : list) {
				if (e instanceof StringTag s) {
					out.add(s.value());
				}
			}
		}
		return out;
	}

	/** Close kin (parent, child, sibling, spouse): no sweethearts, no feuds. */
	public static boolean close(Resident a, Resident b) {
		if (a.id.equals(b.id) || a.id.equals(spouse(b)) || b.id.equals(spouse(a))) {
			return true;
		}
		List<UUID> pa = parents(a);
		List<UUID> pb = parents(b);
		if (pa.contains(b.id) || pb.contains(a.id) || children(a).contains(b.id) || children(b).contains(a.id)) {
			return true;
		}
		for (UUID p : pa) {
			if (pb.contains(p)) {
				return true;
			}
		}
		return false;
	}
}
