package dev.voidmc.hearth.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The settlements one player knows of, for maps: those they have sighted (come within sight of) and those they have
 * discovered (walked up to). Only ever grows, sighted then discovered, as a map's places do (VOID Atlas's discovery
 * model). Immutable: {@link #seen} returns a new value when something changed, or this one.
 */
public record MapKnowledge(Set<Long> sighted, Set<Long> discovered) {
	public static final MapKnowledge EMPTY = new MapKnowledge(Set.of(), Set.of());

	public static final Codec<MapKnowledge> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.listOf().optionalFieldOf("sighted", List.of()).forGetter(k -> List.copyOf(k.sighted)),
		Codec.LONG.listOf().optionalFieldOf("discovered", List.of()).forGetter(k -> List.copyOf(k.discovered))
	).apply(i, (s, d) -> new MapKnowledge(Set.copyOf(s), Set.copyOf(d))));

	public MapKnowledge {
		sighted = Set.copyOf(sighted);
		discovered = Set.copyOf(discovered);
	}

	public boolean knows(long id) {
		return this.sighted.contains(id) || this.discovered.contains(id);
	}

	public boolean discovered(long id) {
		return this.discovered.contains(id);
	}

	/**
	 * The player is {@code distance} blocks from a settlement's bounds: within {@code reach} it is discovered, within
	 * {@code sight} sighted. Never moves back.
	 */
	public MapKnowledge seen(long id, int distance, int reach, int sight) {
		if (distance <= reach && !this.discovered.contains(id)) {
			Set<Long> d = new HashSet<>(this.discovered);
			d.add(id);
			Set<Long> s = new HashSet<>(this.sighted);
			s.remove(id);
			return new MapKnowledge(s, d);
		}
		if (distance <= sight && !this.knows(id)) {
			Set<Long> s = new HashSet<>(this.sighted);
			s.add(id);
			return new MapKnowledge(s, this.discovered);
		}
		return this;
	}
}
