package dev.voidmc.hearth.plan.model;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Reads the planner's int arrays back whatever ops carried them: an int array from NBT, or a list of numbers when the
 * state went through JSON (where every number may come back as a double).
 */
final class Nbt {
	private Nbt() {
	}

	static Optional<int[]> ints(CompoundTag t, String key) {
		Tag tag = t.get(key);
		if (tag == null) {
			return Optional.empty();
		}
		Optional<int[]> direct = tag.asIntArray();
		if (direct.isPresent()) {
			return direct;
		}
		Optional<long[]> longs = tag.asLongArray();
		if (longs.isPresent()) {
			long[] l = longs.get();
			int[] out = new int[l.length];
			for (int i = 0; i < l.length; i++) {
				out[i] = (int) l[i];
			}
			return Optional.of(out);
		}
		Optional<byte[]> bytes = tag.asByteArray();
		if (bytes.isPresent()) {
			byte[] b = bytes.get();
			int[] out = new int[b.length];
			for (int i = 0; i < b.length; i++) {
				out[i] = b[i];
			}
			return Optional.of(out);
		}
		Optional<ListTag> list = tag.asList();
		if (list.isPresent()) {
			ListTag l = list.get();
			int[] out = new int[l.size()];
			for (int i = 0; i < out.length; i++) {
				out[i] = l.get(i).asNumber().map(Number::intValue).orElse(0);
			}
			return Optional.of(out);
		}
		return Optional.empty();
	}
}
