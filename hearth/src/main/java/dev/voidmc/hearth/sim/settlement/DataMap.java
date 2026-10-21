package dev.voidmc.hearth.sim.settlement;

import com.mojang.serialization.DynamicOps;
import dev.voidmc.hearth.api.HearthDataKey;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Other modules' state in a settlement or a world, under their {@link HearthDataKey}s. Saved data is decoded lazily, on
 * first use of its key, so a module that is slow to decode costs nothing until it asks; data under keys nobody has
 * registered is kept as it was read and written back unchanged.
 */
public final class DataMap {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");

	private final HearthDataKey.Scope scope;
	private final Map<Identifier, Object> live = new LinkedHashMap<>();
	private final Map<Identifier, Tag> raw = new LinkedHashMap<>();
	private DynamicOps<Tag> ops = NbtOps.INSTANCE;

	public DataMap(HearthDataKey.Scope scope) {
		this.scope = scope;
	}

	/** The ops data is decoded and encoded with (registry ops in a running world, so item stacks and the like work). */
	public void setOps(DynamicOps<Tag> ops) {
		this.ops = ops;
	}

	@SuppressWarnings("unchecked")
	public <T> T get(HearthDataKey<T> key) {
		if (key.scope() != this.scope) {
			throw new IllegalArgumentException("data key " + key.id() + " is " + key.scope() + " scoped, not " + this.scope);
		}
		Object value = this.live.get(key.id());
		if (value == null) {
			Tag tag = this.raw.remove(key.id());
			if (tag != null) {
				value = key.codec().parse(this.ops, tag).resultOrPartial(e -> LOG.warn("hearth data {}: {}", key.id(), e)).orElse(null);
			}
			if (value == null) {
				value = key.factory().get();
			}
			this.live.put(key.id(), value);
		}
		return (T) value;
	}

	/** Whether the key holds anything (loaded or still raw), without creating it. */
	public boolean has(HearthDataKey<?> key) {
		return this.live.containsKey(key.id()) || this.raw.containsKey(key.id());
	}

	public boolean isEmpty() {
		return this.live.isEmpty() && this.raw.isEmpty();
	}

	@SuppressWarnings("unchecked")
	public CompoundTag save() {
		CompoundTag out = new CompoundTag();
		this.raw.forEach((id, tag) -> out.put(id.toString(), tag.copy()));
		this.live.forEach((id, value) -> {
			HearthDataKey<Object> key = (HearthDataKey<Object>) HearthDataKey.get(id);
			if (key == null) {
				return;
			}
			key.codec().encodeStart(this.ops, value).resultOrPartial(e -> LOG.warn("hearth data {}: could not save: {}", id, e))
				.ifPresent(tag -> out.put(id.toString(), tag));
		});
		return out;
	}

	public void load(CompoundTag tag) {
		this.live.clear();
		this.raw.clear();
		for (String k : tag.keySet()) {
			Identifier id = Identifier.tryParse(k);
			Tag value = tag.get(k);
			if (id != null && value != null) {
				this.raw.put(id, value.copy());
			}
		}
	}
}
