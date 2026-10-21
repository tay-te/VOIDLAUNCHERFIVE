package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.Dressings;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Children's games by daylight (docs/LIVING_WORLD_2.md §2.3), priority 25, on top of the routine's tag: the town's
 * children play one game together each hour — hide-and-seek (one counts at the square, the others hide round plot corners
 * and are found one by one), hoop and stick round the square, play fights with sticks, snowballs in a cold winter — and a
 * few go their own way: skipping stones at the water, chasing the chickens of a pen, watching the smith. Some are left to
 * the routine's tag.
 */
public final class PlayBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("rhythm/play");
	public static final int PRIORITY = 25;
	static final int FROM = 8 * 60;
	static final int TO = 18 * 60 + 30;

	public enum Game {
		TAG, HIDE, HOOP, STICKS, SNOW, STONES, CHICKENS, SMITH
	}

	static final Game[] TOGETHER = {Game.HIDE, Game.HOOP, Game.STICKS, Game.HIDE};
	static final Game[] ALONE = {Game.STONES, Game.CHICKENS, Game.SMITH, Game.TAG};

	private final Map<UUID, State> states = new HashMap<>();
	/** Hide-and-seek per settlement and hour: who seeks, where each hides, who has been found. */
	private static final Map<Long, Hide> HIDES = new HashMap<>();

	static final class Hide {
		final long hour;
		@Nullable UUID seeker;
		final Map<UUID, BlockPos> hiding = new HashMap<>();
		final java.util.Set<UUID> found = new java.util.HashSet<>();

		Hide(long hour) {
			this.hour = hour;
		}
	}

	static final class State {
		final Game game;
		final BlockPos home;
		BlockPos target;
		int step;
		long nextMove;
		long nextAct;
		@Nullable Chicken chicken;

		State(Game game, BlockPos home, BlockPos target) {
			this.game = game;
			this.home = home;
			this.target = target;
		}
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	/** Which game a child plays this hour (pure). */
	public static Game game(long settlementId, UUID child, long day, int hour, boolean winter) {
		long town = TownCalendar.mix(settlementId * 0x9E3779B97F4A7C15L + day * 24 + hour);
		Game together = winter && (town & 1) == 0 ? Game.SNOW : TOGETHER[(int) Math.floorMod(town >>> 3, (long) TOGETHER.length)];
		long own = TownCalendar.mix(child.getLeastSignificantBits() ^ town);
		if (Math.floorMod(own, 3L) != 0) {
			return together;
		}
		return ALONE[(int) Math.floorMod(own >>> 5, (long) ALONE.length)];
	}

	static Game gameNow(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		int minute = RhythmFolk.minute(s);
		long day = RhythmFolk.today(s);
		boolean winter = RhythmCalendar.winter(RhythmData.of(s.culture()), day, SocialModule.config().festivals().yearDays());
		return game(s.id(), folk.resident().id, day, minute / 60, winter);
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		long t0 = RhythmFolk.t0();
		try {
			Settlement s = folk.settlement();
			if (folk.resident().age != AgeStage.CHILD || !Rhythm.config().rhythm().enabled() || !s.embodied() || !folk.awake()) {
				return false;
			}
			int minute = RhythmFolk.minute(s);
			if (minute < FROM || minute >= TO) {
				return false;
			}
			Game g = gameNow(folk);
			return switch (g) {
				case TAG -> false;
				case STONES -> !WashPlaces.of(s).isEmpty();
				case SMITH -> smithy(s) != null;
				default -> true;
			};
		} finally {
			RhythmFolk.t1(t0);
		}
	}

	static @Nullable BlockPos smithy(Settlement s) {
		for (Poi p : s.pois(PoiType.JOB_SITE)) {
			if (p.job() == JobType.SMITH) {
				return p.pos();
			}
		}
		return null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		ServerLevel level = (ServerLevel) folk.entity().level();
		Game g = gameNow(folk);
		BlockPos square = Venues.square(s);
		UUID id = folk.resident().id;
		BlockPos target = switch (g) {
			case HIDE -> hidePlace(level, s, folk, square);
			case STONES -> {
				List<WashPlaces.Spot> spots = WashPlaces.of(s);
				yield spots.isEmpty() ? square : spots.get((int) Math.floorMod(id.getLeastSignificantBits(), (long) Math.min(3, spots.size()))).bank();
			}
			case SMITH -> {
				BlockPos sm = smithy(s);
				yield sm == null ? square : Venues.standFor(level, new Venues.Venue(sm, sm, List.of(sm), 4), id, 3);
			}
			default -> Venues.standFor(level, new Venues.Venue(square, square, List.of(square), 5), id, 25);
		};
		State st = new State(g, square, target);
		this.states.put(id, st);
		folk.hold(switch (g) {
			case HOOP, STICKS -> new ItemStack(Items.STICK);
			case SNOW -> new ItemStack(Items.SNOWBALL);
			case STONES -> new ItemStack(Items.FLINT);
			default -> ItemStack.EMPTY;
		});
		RhythmFolk.walk(folk, target, g == Game.HIDE || g == Game.CHICKENS ? 0.8 : 0.65);
		st.nextMove = folk.gameTime() + 60;
		st.nextAct = folk.gameTime() + 40 + Math.floorMod(id.hashCode(), 40);
	}

	/** Hide-and-seek: the seeker stands at the square; everyone else picks a plot corner near it. */
	private static BlockPos hidePlace(ServerLevel level, Settlement s, NpcService.FolkHandle folk, BlockPos square) {
		long hour = RhythmFolk.today(s) * 24 + RhythmFolk.minute(s) / 60;
		Hide h = HIDES.get(s.id());
		if (h == null || h.hour != hour) {
			h = new Hide(hour);
			HIDES.put(s.id(), h);
		}
		UUID id = folk.resident().id;
		if (h.seeker == null) {
			h.seeker = id;
			return Venues.standFor(level, new Venues.Venue(square, square, List.of(square), 1), id, 1);
		}
		if (id.equals(h.seeker)) {
			return Venues.standFor(level, new Venues.Venue(square, square, List.of(square), 1), id, 1);
		}
		BlockPos spot = h.hiding.get(id);
		if (spot != null) {
			return spot;
		}
		List<PlotView> near = Dressings.plotsNear(s, square, 28, 10);
		if (!near.isEmpty()) {
			PlotView p = near.get((int) Math.floorMod(TownCalendar.mix(id.getMostSignificantBits() + hour), (long) near.size()));
			BlockPos o = p.spec().origin();
			int corner = (int) Math.floorMod(TownCalendar.mix(id.getLeastSignificantBits() + hour), 4L);
			BlockPos c = o.offset(corner % 2 == 0 ? -1 : p.spec().sizeX(), 0, corner < 2 ? -1 : p.spec().sizeZ());
			BlockPos ok = Venues.standable(level, c);
			spot = ok != null ? ok : square;
		} else {
			spot = square;
		}
		h.hiding.put(id, spot);
		return spot;
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		long t0 = RhythmFolk.t0();
		try {
			return step(folk);
		} finally {
			RhythmFolk.t1(t0);
		}
	}

	private boolean step(NpcService.FolkHandle folk) {
		State st = this.states.get(folk.resident().id);
		if (st == null) {
			return false;
		}
		long t = folk.gameTime();
		if ((t & 63) == 0 && (!wants(folk) || gameNow(folk) != st.game)) {
			return false;
		}
		ServerLevel level = (ServerLevel) folk.entity().level();
		Settlement s = folk.settlement();
		switch (st.game) {
			case HIDE -> hide(folk, st, level, s, t);
			case HOOP -> {
				// running loops round the square, the hoop bowled ahead with the stick
				if (t >= st.nextMove || RhythmFolk.near(folk, st.target, 1.5)) {
					st.step++;
					double a = st.step * Math.PI / 4 + Math.floorMod(folk.resident().id.hashCode(), 8);
					BlockPos p = st.home.offset((int) Math.round(Math.cos(a) * 6), 0, (int) Math.round(Math.sin(a) * 6));
					BlockPos ok = Venues.standable(level, p);
					st.target = ok == null ? st.home : ok;
					RhythmFolk.walk(folk, st.target, 0.9);
					st.nextMove = t + 60;
					if (st.step % 3 == 0) {
						folk.swing();
					}
				}
			}
			case STICKS, SNOW -> {
				if (!RhythmFolk.near(folk, st.target, 2.0)) {
					walk(folk, st, t, 0.7);
					break;
				}
				folk.stop();
				NpcService.FolkHandle other = playmate(level, folk, st.game);
				if (other != null) {
					folk.lookAt(other.entity());
				}
				if (t >= st.nextAct) {
					folk.swing();
					folk.pose("cheer", 20);
					if (st.game == Game.SNOW && other != null) {
						throwSnow(level, folk.entity().position(), other.entity().position());
					} else {
						level.playSound(null, folk.entity().blockPosition(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.3F, 1.4F);
					}
					if (TownCalendar.chance(folk.resident().id.hashCode(), t, 25) < 0.15) {
						RhythmBarks.say(folk, st.game == Game.SNOW ? RhythmBarks.SNOW : RhythmBarks.PLAY, Map.of(), false);
					}
					st.nextAct = t + 18 + Math.floorMod(TownCalendar.mix(t ^ folk.resident().id.hashCode()), 30L);
				}
			}
			case STONES -> {
				if (!RhythmFolk.near(folk, st.target, 1.8)) {
					walk(folk, st, t, 0.65);
					break;
				}
				folk.stop();
				if (t >= st.nextAct) {
					folk.swing();
					skip(level, folk);
					st.nextAct = t + 60 + Math.floorMod(TownCalendar.mix(t), 60L);
				}
			}
			case CHICKENS -> {
				if (st.chicken == null || !st.chicken.isAlive() || st.chicken.distanceToSqr(folk.entity()) > 40 * 40) {
					List<Chicken> hens = level.getEntitiesOfClass(Chicken.class, new AABB(folk.entity().blockPosition()).inflate(32));
					st.chicken = hens.isEmpty() ? null : hens.get((int) Math.floorMod(folk.resident().id.hashCode(), hens.size()));
					if (st.chicken == null) {
						return false;
					}
				}
				if (t >= st.nextMove) {
					RhythmFolk.walk(folk, st.chicken.blockPosition(), 0.95);
					st.nextMove = t + 20;
					if (folk.entity().distanceToSqr(st.chicken) < 4) {
						folk.pose("cheer", 20);
						Vec3 away = st.chicken.position().subtract(folk.entity().position()).normalize().scale(0.4);
						st.chicken.push(away.x, 0.2, away.z);
						level.playSound(null, st.chicken.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.4F, 1.3F);
					}
				}
			}
			case SMITH -> {
				if (!RhythmFolk.near(folk, st.target, 1.8)) {
					walk(folk, st, t, 0.6);
					break;
				}
				folk.stop();
				BlockPos sm = smithy(s);
				if (sm != null) {
					folk.lookAt(sm);
				}
			}
			case TAG -> {
				return false;
			}
		}
		return true;
	}

	private static void walk(NpcService.FolkHandle folk, State st, long t, double speed) {
		if (t >= st.nextMove) {
			RhythmFolk.walk(folk, st.target, speed);
			st.nextMove = t + 80;
		}
	}

	private void hide(NpcService.FolkHandle folk, State st, ServerLevel level, Settlement s, long t) {
		Hide h = HIDES.get(s.id());
		if (h == null) {
			return;
		}
		UUID id = folk.resident().id;
		int into = RhythmFolk.minute(s) % 60;
		if (id.equals(h.seeker)) {
			if (into < 10) {
				// counting at the wall, face to it
				if (!RhythmFolk.near(folk, st.target, 1.5)) {
					walk(folk, st, t, 0.7);
					return;
				}
				folk.stop();
				folk.lookAt(st.home.above());
				if (t >= st.nextAct) {
					folk.pose("pray", 40);
					if (into >= 8) {
						RhythmBarks.say(folk, RhythmBarks.SEEK_COUNT, Map.of(), true);
					}
					st.nextAct = t + 100;
				}
				return;
			}
			// seeking: to each hiding place not yet found
			UUID next = null;
			for (Map.Entry<UUID, BlockPos> e : h.hiding.entrySet()) {
				if (!h.found.contains(e.getKey())) {
					next = e.getKey();
					st.target = e.getValue();
					break;
				}
			}
			if (next == null) {
				folk.stop();
				folk.pose("cheer", 30);
				return;
			}
			if (RhythmFolk.near(folk, st.target, 2.0)) {
				h.found.add(next);
				folk.pose("cheer", 30);
				RhythmBarks.say(folk, RhythmBarks.SEEK_FOUND, Map.of(), false);
			} else {
				walk(folk, st, t, 0.75);
			}
			return;
		}
		if (h.found.contains(id)) {
			// found: back to the square to watch the rest be found
			if (!RhythmFolk.near(folk, st.home, 4)) {
				st.target = st.home;
				walk(folk, st, t, 0.8);
			} else {
				folk.stop();
				if (h.seeker != null && t >= st.nextAct) {
					folk.pose("cheer", 20);
					st.nextAct = t + 120;
				}
			}
			return;
		}
		if (!RhythmFolk.near(folk, st.target, 1.5)) {
			walk(folk, st, t, into < 10 ? 0.85 : 0.6);
			return;
		}
		folk.stop();
		if ((t & 31) == 0) {
			folk.pose("tend", 40); // crouched behind the corner
		}
	}

	private NpcService.@Nullable FolkHandle playmate(ServerLevel level, NpcService.FolkHandle folk, Game g) {
		NpcService npc = dev.voidmc.hearth.api.HearthServices.npc();
		if (npc == null) {
			return null;
		}
		for (NpcService.FolkHandle o : npc.folkNear(level, folk.entity().position(), 8)) {
			State os = this.states.get(o.resident().id);
			if (o.resident().id.equals(folk.resident().id) || os == null || os.game != g) {
				continue;
			}
			return o;
		}
		return null;
	}

	private static void throwSnow(ServerLevel level, Vec3 from, Vec3 to) {
		ItemParticleOption snow = new ItemParticleOption(ParticleTypes.ITEM, Items.SNOWBALL);
		Vec3 d = to.subtract(from);
		for (int i = 1; i <= 4; i++) {
			Vec3 p = from.add(d.scale(i / 5.0)).add(0, 1.3 + Math.sin(i / 5.0 * Math.PI) * 0.6, 0);
			level.sendParticles(snow, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, to.x, to.y + 1.2, to.z, 6, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.4F, 1.2F);
	}

	/** A stone skipped across the water: a row of splashes ahead of the child. */
	private static void skip(ServerLevel level, NpcService.FolkHandle folk) {
		Vec3 at = folk.entity().position();
		Vec3 look = folk.entity().getLookAngle();
		Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
		if (dir.lengthSqr() < 0.1) {
			return;
		}
		for (int i = 1; i <= 4; i++) {
			Vec3 p = at.add(dir.scale(2 + i * 1.6));
			level.sendParticles(ParticleTypes.SPLASH, p.x, at.y - 0.3, p.z, 4, 0.1, 0.02, 0.1, 0.05);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.6F);
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		if (this.states.remove(folk.resident().id) != null) {
			folk.pose("none", 0);
			folk.hold(ItemStack.EMPTY);
		}
		if (this.states.isEmpty()) {
			HIDES.clear();
		}
	}
}
