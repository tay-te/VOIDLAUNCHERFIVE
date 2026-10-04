package dev.voidmc.hearth.social;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * What the social module reads of a resident from the other modules' documented parts of {@link Resident#extra}: the
 * npc module's traits and family ({@code extra("npc")}, docs/FOLK.md §3) and the sim's parents of children born in
 * town ({@code extra("sim").parent_a/b}). Read-only, except {@link #marry}, which records a wedding the way the npc
 * module records the couples it makes (requested as an api in docs/requests/p3-social.md).
 */
public final class FolkFacts {
	private static final Codec<List<UUID>> UUIDS = UUIDUtil.CODEC.listOf();
	private static final Codec<List<String>> STRINGS = Codec.STRING.listOf();

	private FolkFacts() {
	}

	private static CompoundTag npc(Resident r) {
		return r.extra.getCompoundOrEmpty("npc");
	}

	public static List<String> traits(Resident r) {
		return npc(r).read("traits", STRINGS).orElse(List.of());
	}

	public static boolean has(Resident r, String trait) {
		return traits(r).contains(trait);
	}

	public static @Nullable UUID spouse(Resident r) {
		return npc(r).read("spouse", UUIDUtil.CODEC).orElse(null);
	}

	public static List<UUID> children(Resident r) {
		return npc(r).read("children", UUIDS).orElse(List.of());
	}

	/** Parents from the npc module's links and the sim's record of a birth. */
	public static List<UUID> parents(Resident r) {
		Set<UUID> out = new LinkedHashSet<>(npc(r).read("parents", UUIDS).orElse(List.of()));
		CompoundTag sim = r.extra.getCompoundOrEmpty("sim");
		for (String k : new String[] {"parent_a", "parent_b"}) {
			String s = sim.getStringOr(k, "");
			if (!s.isEmpty()) {
				try {
					out.add(UUID.fromString(s));
				} catch (IllegalArgumentException ignored) {
					// not a uuid: ignore
				}
			}
		}
		return new ArrayList<>(out);
	}

	/** Whether two residents are close kin (parent, child, sibling, spouse) and so cannot marry. */
	public static boolean kin(Resident a, Resident b) {
		if (a.id.equals(b.id)) {
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

	/** The resident's close family who live in the settlement: spouse, children and parents. */
	public static List<UUID> family(Settlement s, Resident r) {
		Set<UUID> out = new LinkedHashSet<>();
		UUID sp = spouse(r);
		if (sp != null) {
			out.add(sp);
		}
		out.addAll(children(r));
		out.addAll(parents(r));
		// children born in town carry the link on their side only
		for (Resident other : s.residents()) {
			if (parents(other).contains(r.id)) {
				out.add(other.id);
			}
		}
		out.removeIf(id -> s.resident(id) == null || id.equals(r.id));
		return new ArrayList<>(out);
	}

	/** Records a marriage on both residents (the npc module's spouse link), if both are free. */
	public static boolean marry(Resident a, Resident b, long day) {
		if (spouse(a) != null || spouse(b) != null || kin(a, b)) {
			return false;
		}
		a.extra("npc").store("spouse", UUIDUtil.CODEC, b.id);
		b.extra("npc").store("spouse", UUIDUtil.CODEC, a.id);
		a.extra("social").putLong("married_day", day);
		b.extra("social").putLong("married_day", day);
		return true;
	}

	/** The office a resident holds (social's part of the record), or empty. */
	public static Optional<String> office(Resident r) {
		String o = r.extra.getCompoundOrEmpty("social").getStringOr("office", "");
		return o.isEmpty() ? Optional.empty() : Optional.of(o);
	}
}
