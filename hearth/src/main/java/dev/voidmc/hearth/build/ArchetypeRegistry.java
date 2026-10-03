package dev.voidmc.hearth.build;

import dev.voidmc.hearth.api.ArchetypeInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The archetypes this build module can generate, by id. Filled once during init; read from any thread after that. */
public final class ArchetypeRegistry {
	private final Map<Identifier, Archetype> archetypes = new LinkedHashMap<>();
	private volatile boolean frozen;

	public synchronized void register(Archetype archetype) {
		if (this.frozen) {
			throw new IllegalStateException("archetypes are registered during init");
		}
		Identifier id = archetype.info().id();
		if (this.archetypes.putIfAbsent(id, archetype) != null) {
			throw new IllegalArgumentException("archetype " + id + " registered twice");
		}
	}

	/** No more registrations; reads need no locking after this. */
	public synchronized ArchetypeRegistry freeze() {
		this.frozen = true;
		return this;
	}

	public @Nullable Archetype get(Identifier id) {
		return this.archetypes.get(id);
	}

	public Collection<Archetype> all() {
		return Collections.unmodifiableCollection(this.archetypes.values());
	}

	public List<ArchetypeInfo> infos() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (Archetype a : this.archetypes.values()) {
			out.add(a.info());
		}
		return Collections.unmodifiableList(out);
	}
}
