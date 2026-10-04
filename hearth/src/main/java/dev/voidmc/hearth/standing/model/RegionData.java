package dev.voidmc.hearth.standing.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.standing.logic.Curves;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * The standing module's world-level state ({@code hearth:standing/region}, one per Hearth world): news on the road
 * between towns (a delay queue ordered by arrival day), news waiting for towns not yet settled, each player's places of
 * fame, their reputation for violence, and the companies. All of it is small and touched only when something happens.
 */
public final class RegionData {
	public static final Codec<RegionData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Travelling.CODEC.listOf().optionalFieldOf("road", List.of()).forGetter(r -> List.copyOf(r.road)),
		Travelling.CODEC.listOf().optionalFieldOf("held", List.of()).forGetter(r -> List.copyOf(r.held)),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, FameSource.CODEC.listOf()).optionalFieldOf("fame", Map.of()).forGetter(r -> r.fame),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Violence.CODEC).optionalFieldOf("violence", Map.of()).forGetter(r -> r.violence),
		Company.CODEC.listOf().optionalFieldOf("companies", List.of()).forGetter(r -> List.copyOf(r.companies.values()))
	).apply(i, (road, held, fame, violence, companies) -> {
		RegionData r = new RegionData();
		road.forEach(r::schedule);
		r.held.addAll(held);
		fame.forEach((k, v) -> r.fame.put(k, new ArrayList<>(v)));
		r.violence.putAll(violence);
		for (Company c : companies) {
			r.companies.put(Company.key(c.name()), c);
		}
		return r;
	}));

	/** Sorted by arrival day, soonest first. */
	private final List<Travelling> road = new ArrayList<>();
	private final List<Travelling> held = new ArrayList<>();
	private final Map<UUID, List<FameSource>> fame = new HashMap<>();
	private final Map<UUID, Violence> violence = new HashMap<>();
	private final Map<String, Company> companies = new LinkedHashMap<>();

	/**
	 * News on its way to a town.
	 *
	 * @param strength signed strength left on arrival (already faded by distance and towns passed)
	 * @param origin   the settlement where it happened
	 * @param target   the settlement it is going to
	 */
	public record Travelling(UUID player, String playerName, String kind, double strength, long origin, String originName, long target, long arrival,
		long created) {
		static final Codec<Travelling> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Travelling::player),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Travelling::playerName),
			Codec.STRING.fieldOf("kind").forGetter(Travelling::kind),
			Codec.DOUBLE.fieldOf("strength").forGetter(Travelling::strength),
			Codec.LONG.fieldOf("origin").forGetter(Travelling::origin),
			Codec.STRING.optionalFieldOf("origin_name", "").forGetter(Travelling::originName),
			Codec.LONG.fieldOf("target").forGetter(Travelling::target),
			Codec.LONG.fieldOf("arrival").forGetter(Travelling::arrival),
			Codec.LONG.fieldOf("created").forGetter(Travelling::created)
		).apply(i, Travelling::new));
	}

	/** A place a player became known, and how strongly (signed: renown or infamy). */
	public record FameSource(int x, int z, double magnitude, long day) {
		static final Codec<FameSource> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("x").forGetter(FameSource::x),
			Codec.INT.fieldOf("z").forGetter(FameSource::z),
			Codec.DOUBLE.fieldOf("m").forGetter(FameSource::magnitude),
			Codec.LONG.fieldOf("day").forGetter(FameSource::day)
		).apply(i, FameSource::new));
	}

	/** A reputation for violence, 0…100, fading from {@code day}. */
	public record Violence(double value, long day) {
		static final Codec<Violence> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("v").forGetter(Violence::value),
			Codec.LONG.fieldOf("day").forGetter(Violence::day)
		).apply(i, Violence::new));

		public double now(long today, double halfLifeDays) {
			return this.value * Curves.fade(today - this.day, halfLifeDays);
		}
	}

	// ---------------------------------------------------------------- the road

	/** Puts news on the road; when the road is full the faintest news is dropped (possibly this one). */
	public void schedule(Travelling t) {
		int at = this.road.size();
		while (at > 0 && this.road.get(at - 1).arrival > t.arrival) {
			at--;
		}
		this.road.add(at, t);
	}

	/** Keeps the road within {@code max} items, dropping the faintest. */
	public void trim(int max) {
		while (this.road.size() > max) {
			int weakest = 0;
			for (int i = 1; i < this.road.size(); i++) {
				if (Math.abs(this.road.get(i).strength) < Math.abs(this.road.get(weakest).strength)) {
					weakest = i;
				}
			}
			this.road.remove(weakest);
		}
		while (this.held.size() > max) {
			this.held.removeFirst();
		}
	}

	/** Takes the news that has arrived by {@code today}, soonest first. */
	public List<Travelling> arrived(long today) {
		List<Travelling> out = new ArrayList<>();
		while (!this.road.isEmpty() && this.road.getFirst().arrival <= today) {
			out.add(this.road.removeFirst());
		}
		return out;
	}

	public boolean anyArrived(long today) {
		return !this.road.isEmpty() && this.road.getFirst().arrival <= today;
	}

	public List<Travelling> road() {
		return List.copyOf(this.road);
	}

	/** Keeps news for a town that is not settled yet. */
	public void hold(Travelling t) {
		this.held.add(t);
	}

	/** Takes the news held for a town that has just been settled. */
	public List<Travelling> release(long target) {
		List<Travelling> out = new ArrayList<>();
		this.held.removeIf(t -> {
			if (t.target == target) {
				out.add(t);
				return true;
			}
			return false;
		});
		return out;
	}

	/** Forgets held news older than {@code days}; returns whether anything was forgotten. */
	public boolean expireHeld(long today, int days) {
		return this.held.removeIf(t -> today - t.arrival > days);
	}

	public List<Travelling> held() {
		return List.copyOf(this.held);
	}

	// ---------------------------------------------------------------- fame

	public List<FameSource> fame(UUID player) {
		List<FameSource> l = this.fame.get(player);
		return l == null ? List.of() : List.copyOf(l);
	}

	/**
	 * Remembers that a player became known at a place. A new deed near an old place of fame adds to it (what is left of the
	 * old, plus the new) instead of taking another slot; past {@code limit} places the faintest is forgotten.
	 */
	public void addFame(UUID player, int x, int z, double magnitude, long today, int mergeRadius, double halfLifeDays, int limit) {
		List<FameSource> list = this.fame.computeIfAbsent(player, k -> new ArrayList<>());
		long r2 = (long) mergeRadius * mergeRadius;
		for (int i = 0; i < list.size(); i++) {
			FameSource s = list.get(i);
			long dx = s.x - x;
			long dz = s.z - z;
			if (dx * dx + dz * dz <= r2) {
				double old = s.magnitude * Curves.fade(today - s.day, halfLifeDays);
				double total = old + magnitude;
				double wOld = Math.abs(old) / Math.max(1e-6, Math.abs(old) + Math.abs(magnitude));
				int nx = (int) Math.round(s.x * wOld + x * (1 - wOld));
				int nz = (int) Math.round(s.z * wOld + z * (1 - wOld));
				list.set(i, new FameSource(nx, nz, Curves.clamp(total, -500, 500), today));
				return;
			}
		}
		list.add(new FameSource(x, z, Curves.clamp(magnitude, -500, 500), today));
		while (list.size() > limit) {
			int weakest = 0;
			double least = Double.MAX_VALUE;
			for (int i = 0; i < list.size(); i++) {
				double v = Math.abs(list.get(i).magnitude * Curves.fade(today - list.get(i).day, halfLifeDays));
				if (v < least) {
					least = v;
					weakest = i;
				}
			}
			list.remove(weakest);
		}
	}

	/**
	 * How well known the player is at (x, z): every place of fame, faded with time and with distance (to nothing at
	 * {@code radius}). Signed: positive for renown, negative for infamy.
	 */
	public double renown(UUID player, int x, int z, long today, int radius, double halfLifeDays) {
		List<FameSource> list = this.fame.get(player);
		if (list == null) {
			return 0;
		}
		double sum = 0;
		for (FameSource s : list) {
			double dx = s.x - x;
			double dz = s.z - z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d >= radius) {
				continue;
			}
			double near = 1 - d / radius;
			sum += s.magnitude * near * near * Curves.fade(today - s.day, halfLifeDays);
		}
		return sum;
	}

	/** Unsigned fame 0…100 (infamy is fame too). */
	public double fameAt(UUID player, int x, int z, long today, int radius, double halfLifeDays) {
		List<FameSource> list = this.fame.get(player);
		if (list == null) {
			return 0;
		}
		double sum = 0;
		for (FameSource s : list) {
			double dx = s.x - x;
			double dz = s.z - z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d >= radius) {
				continue;
			}
			double near = 1 - d / radius;
			sum += Math.abs(s.magnitude) * near * near * Curves.fade(today - s.day, halfLifeDays);
		}
		return Curves.clamp(sum, 0, 100);
	}

	// ---------------------------------------------------------------- violence

	public double violence(UUID player, long today, double halfLifeDays) {
		Violence v = this.violence.get(player);
		return v == null ? 0 : v.now(today, halfLifeDays);
	}

	public void addViolence(UUID player, double amount, long today, double halfLifeDays) {
		double now = violence(player, today, halfLifeDays);
		this.violence.put(player, new Violence(Curves.clamp(now + amount, 0, 100), today));
	}

	// ---------------------------------------------------------------- companies

	public java.util.Collection<Company> companies() {
		return this.companies.values();
	}

	public @Nullable Company company(String name) {
		return this.companies.get(Company.key(name));
	}

	public @Nullable Company companyOf(UUID player) {
		for (Company c : this.companies.values()) {
			if (c.members().contains(player)) {
				return c;
			}
		}
		return null;
	}

	public void putCompany(Company c) {
		this.companies.put(Company.key(c.name()), c);
	}

	public void removeCompany(String name) {
		this.companies.remove(Company.key(name));
	}
}
