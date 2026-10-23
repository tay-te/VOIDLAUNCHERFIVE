package dev.voidmc.bestiary.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.profile.CreatureProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lairs (BESTIARY.md §8.2, §12): a boss bound to a place. Without Expanse's lair anchors (not built yet: CONTRACTS.md
 * §18.2) a lair is made by command ({@code /bestiary summon gallows_knight yard}: the gallows yard and its knight). A
 * felled lair boss returns after {@link #respawnDays} in-game days (0 = once per world), when nobody is in its arena.
 * Saved per level.
 */
public final class Lairs extends SavedData {
	/** A lair: its id, its boss (profile id), its arena, the boss's entity when alive, when it was felled (0 = alive). */
	public static final class Lair {
		static final Codec<Lair> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(l -> l.id),
			Identifier.CODEC.fieldOf("boss").forGetter(l -> l.boss),
			Arena.CODEC.fieldOf("arena").forGetter(l -> l.arena),
			UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(l -> Optional.ofNullable(l.entity)),
			Codec.LONG.optionalFieldOf("felled", 0L).forGetter(l -> l.felled)).apply(i, Lair::new));

		public final UUID id;
		public final Identifier boss;
		public final Arena arena;
		public @Nullable UUID entity;
		public long felled;

		Lair(UUID id, Identifier boss, Arena arena, Optional<UUID> entity, long felled) {
			this.id = id;
			this.boss = boss;
			this.arena = arena;
			this.entity = entity.orElse(null);
			this.felled = felled;
		}
	}

	static final Codec<Lairs> CODEC = Lair.CODEC.listOf().xmap(Lairs::new, l -> l.lairs);
	public static final SavedDataType<Lairs> TYPE = new SavedDataType<>(Bestiary.id("lairs"), Lairs::new, CODEC, null);
	/** In-game days before a felled lair boss returns (BESTIARY §8.2: 3; 0 = once per world). */
	public static int respawnDays = 3;

	final List<Lair> lairs;

	Lairs() {
		this(List.of());
	}

	Lairs(List<Lair> lairs) {
		this.lairs = new ArrayList<>(lairs);
	}

	public static Lairs get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public List<Lair> all() {
		return List.copyOf(this.lairs);
	}

	public Lair add(Identifier boss, Arena arena, UUID entity) {
		Lair l = new Lair(UUID.randomUUID(), boss, arena, Optional.of(entity), 0);
		this.lairs.add(l);
		this.setDirty();
		return l;
	}

	public @Nullable Lair byId(@Nullable UUID id) {
		for (Lair l : this.lairs) {
			if (l.id.equals(id)) {
				return l;
			}
		}
		return null;
	}

	public static void init() {
		BossEncounter.FELLED.register((boss, data, level) -> {
			Lairs ls = get(level);
			Lair l = ls.byId(data.lair);
			if (l != null) {
				l.felled = Math.max(1, level.getGameTime());
				l.entity = null;
				ls.setDirty();
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 200 != 7) {
				return;
			}
			Lairs ls = level.getDataStorage().get(TYPE);
			if (ls == null || ls.lairs.isEmpty()) {
				return;
			}
			for (Lair l : ls.lairs) {
				if (l.felled > 0 && respawnDays > 0 && level.getGameTime() - l.felled >= respawnDays * 24000L && level.isLoaded(l.arena.home())
					&& BossEncounter.inside(BossData.fresh(l.arena), level).isEmpty()) {
					Mob m = spawn(level, l.boss, l.arena, l);
					if (m != null) {
						Bestiary.LOGGER.info("lair {} at {}: {} returns", l.id, l.arena.home().toShortString(), l.boss);
					}
				}
			}
		});
	}

	/** Creates a lair boss at its arena's home (the summon command, a respawn) and binds it to {@code lair} (or none). */
	public static @Nullable Mob spawn(ServerLevel level, Identifier profile, Arena arena, @Nullable Lair lair) {
		var p = CreatureProfiles.get(profile);
		if (p == null) {
			return null;
		}
		Vec3 h = arena.homeVec();
		Mob m = Bestiary.create(level, p, h, 3, false, List.of(), 0);
		if (m == null) {
			return null;
		}
		m.snapTo(h.x, h.y, h.z, arena.homeYaw(), 0);
		m.setYHeadRot(arena.homeYaw());
		m.setYBodyRot(arena.homeYaw());
		BossData d = BossEncounter.setup(m, arena);
		if (lair != null) {
			d.lair = lair.id;
			lair.entity = m.getUUID();
			lair.felled = 0;
			get(level).setDirty();
		}
		return m;
	}

	/** The lair's boss if it is loaded. */
	public static @Nullable Entity boss(ServerLevel level, Lair l) {
		return l.entity == null ? null : level.getEntity(l.entity);
	}
}
