package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * One scene running in an embodied town (docs/LIVING_WORLD_2.md §3.3): its event and rule, who plays which part, the
 * minute its timeline has got to, what players have done to it, and what it put into the world (spawned beasts, display
 * entities, dressings), all taken away when it ends. Never saved.
 */
public final class SceneRun {
	final HearthWorld world;
	final long settlementId;
	Scheduled event;
	final EventRule rule;
	final Map<String, List<UUID>> cast;
	final BlockPos anchor;
	final Interventions iv = new Interventions();
	final SceneDirector director;
	final List<Entity> spawned = new ArrayList<>();
	final Set<String> dressings = new LinkedHashSet<>();
	final Set<UUID> attended = new LinkedHashSet<>();
	final Map<String, BlockPos> venues = new HashMap<>();
	/** Free for the director (scores, the order of bouts). */
	final Map<String, Object> state = new HashMap<>();
	int minute = -1;
	final long startedAt;
	boolean ended;

	SceneRun(HearthWorld world, Settlement s, Scheduled event, EventRule rule, Map<String, List<UUID>> cast, BlockPos anchor, SceneDirector director,
		long startedAt) {
		this.world = world;
		this.settlementId = s.id();
		this.event = event;
		this.rule = rule;
		this.cast = cast;
		this.anchor = anchor;
		this.director = director;
		this.startedAt = startedAt;
	}

	public Scheduled event() {
		return this.event;
	}

	public EventRule rule() {
		return this.rule;
	}

	public Interventions interventions() {
		return this.iv;
	}

	public ServerLevel level() {
		return this.world.level();
	}

	public @Nullable Settlement settlement() {
		return this.world.settlement(this.settlementId);
	}

	public BlockPos anchor() {
		return this.anchor;
	}

	/** The residents in a role (empty if nobody plays it). */
	public List<UUID> role(String name) {
		List<UUID> r = this.cast.get(name);
		return r == null ? List.of() : r;
	}

	/** Everyone with a part. */
	public List<UUID> everyone() {
		List<UUID> out = new ArrayList<>();
		for (List<UUID> l : this.cast.values()) {
			out.addAll(l);
		}
		return out;
	}

	/** A venue's position for this scene (found once). */
	public BlockPos venue(String name) {
		Settlement s = settlement();
		if (s == null) {
			return this.anchor;
		}
		return this.venues.computeIfAbsent(name, n -> SceneVenues.at(s, this.event, n));
	}

	@SuppressWarnings("unchecked")
	public <T> @Nullable T get(String key) {
		return (T) this.state.get(key);
	}

	public void put(String key, Object value) {
		this.state.put(key, value);
	}

	/** Keeps an entity the scene made (taken away when it ends; it must be one that is never saved). */
	public void spawned(Entity e) {
		this.spawned.add(e);
	}

	public String key() {
		return this.event.key();
	}
}
