package dev.voidmc.mettle.core;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.voidmc.mettle.Mettle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Codecs for saved and synced player data that never fail. Fabric saves every mod's attachments in one map
 * ({@code fabric:attachments}); one value that fails to encode loses the whole map, every mod's data with it, and one
 * that fails to decode loses that attachment. So a number out of range is clamped both ways, and a list or map entry that
 * can't be read is skipped (and logged) instead of failing its record. Datapack registry codecs (perks, backgrounds, gear)
 * keep their strict ranges: those reject bad data files at load, they never hold a player's data.
 */
public final class MettleCodecs {
	private MettleCodecs() {
	}

	/** An int kept within [min, max] when read and when written: never an error. */
	public static Codec<Integer> clampedInt(int min, int max) {
		return Codec.INT.xmap(v -> Math.clamp(v, min, max), v -> Math.clamp(v, min, max));
	}

	/** A list whose unreadable elements are skipped (logged), never failing the list. */
	public static <T> Codec<List<T>> lenientList(Codec<T> element, String what) {
		Codec<List<T>> strict = element.listOf();
		return new Codec<>() {
			@Override
			public <O> DataResult<Pair<List<T>, O>> decode(DynamicOps<O> ops, O input) {
				DataResult<java.util.stream.Stream<O>> list = ops.getStream(input);
				if (list.error().isPresent()) {
					Mettle.LOGGER.error("Mettle: skipped an unreadable {} list: {}", what, list.error().get().message());
					return DataResult.success(Pair.of(List.of(), input));
				}
				List<T> out = new ArrayList<>();
				list.getOrThrow().forEach(o -> {
					DataResult<T> r = element.parse(ops, o);
					if (r.isSuccess()) {
						out.add(r.getOrThrow());
					} else {
						Mettle.LOGGER.error("Mettle: skipped an unreadable {}: {}", what, r.error().map(x -> x.message()).orElse("?"));
					}
				});
				return DataResult.success(Pair.of(out, input));
			}

			@Override
			public <O> DataResult<O> encode(List<T> input, DynamicOps<O> ops, O prefix) {
				return strict.encode(input, ops, prefix);
			}
		};
	}

	/** A map whose unreadable entries (a key or value that can't be read) are skipped (logged), never failing the map. */
	public static <K, V> Codec<Map<K, V>> lenientMap(Codec<K> key, Codec<V> value, String what) {
		Codec<Map<K, V>> strict = Codec.unboundedMap(key, value);
		return new Codec<>() {
			@Override
			public <O> DataResult<Pair<Map<K, V>, O>> decode(DynamicOps<O> ops, O input) {
				DataResult<com.mojang.serialization.MapLike<O>> map = ops.getMap(input);
				if (map.error().isPresent()) {
					Mettle.LOGGER.error("Mettle: skipped an unreadable {} map: {}", what, map.error().get().message());
					return DataResult.success(Pair.of(Map.of(), input));
				}
				Map<K, V> out = new LinkedHashMap<>();
				map.getOrThrow().entries().forEach(e -> {
					DataResult<K> k = key.parse(ops, e.getFirst());
					DataResult<V> v = value.parse(ops, e.getSecond());
					if (k.isSuccess() && v.isSuccess()) {
						out.put(k.getOrThrow(), v.getOrThrow());
					} else {
						Mettle.LOGGER.error("Mettle: skipped an unreadable {} entry {}: {}", what, e.getFirst(),
							k.error().map(x -> x.message()).orElseGet(() -> v.error().map(x -> x.message()).orElse("?")));
					}
				});
				return DataResult.success(Pair.of(out, input));
			}

			@Override
			public <O> DataResult<O> encode(Map<K, V> input, DynamicOps<O> ops, O prefix) {
				return strict.encode(input, ops, prefix);
			}
		};
	}
}
