package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.place.BlueprintSource;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import dev.voidmc.hearth.npc.work.field.Handcarts;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Haulers (docs/LIVING_WORLD.md §7.4–7.5): they carry the town's goods along its chains — stone from the quarry to the
 * mason, ore from the mine to the smelter, logs from the logging camp to the sawmill and the charcoal kiln, grain from
 * the fields to the granary and the mill, the catch to the store — with the load in their arms, walking slower under it.
 * Routes are read from the town's plots (what each produces and wants), refreshed every couple of minutes. Within a town
 * the goods themselves move instantly in the sim; the haulers are what it looks like, and their working time is
 * credited as labour.
 *
 * <p>On a longer haul (docs/LIVING_WORLD_2.md §6.1, lane A4) the hauler draws a handcart (lane A1's {@code hearth:cart},
 * through {@link Handcarts}, capped and never saved): out empty, parked by the door while loading, back with the load on
 * its bed — logs from the logging camp to the sawmill, stone to the mason, sacks and barrels to the stores. When the caps
 * say no, the load is carried in the arms as before.
 */
final class HaulerWork implements WorkBehaviour {
	private static final int REFRESH = 20 * 120;

	/** Where goods come from and go to. */
	record Stop(int plotId, BlockPos pos, Kind kind) {
	}

	/** What moves along a route, and so what is carried. */
	enum Kind {
		STONE, ORE, CLAY, SAND, LOGS, GRAIN, FISH, CHARCOAL, BRICK, METAL, GLASS, WAX, LEATHER, SALT, GOODS
	}

	private final List<Stop> sources = new ArrayList<>();
	private final List<Stop> sinks = new ArrayList<>();
	private long routesAt = Long.MIN_VALUE;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		long now = level.getGameTime();
		// (routesAt starts at MIN_VALUE: now - MIN_VALUE overflows, so the first read is asked for by name)
		if (this.routesAt == Long.MIN_VALUE || now - this.routesAt > REFRESH) {
			routes(brain, level);
			this.routesAt = now;
		}
		ResidentPlaces p = brain.places(level);
		if (this.sources.isEmpty() || this.sinks.isEmpty()) {
			// No chain to work: shifting crates about the yard.
			return new Tasks.Linger(p.work, null, FolkPose.CRAFT, 300, new ItemStack(Items.BARREL), ItemStack.EMPTY,
				Tasks.every(60, SoundEvents.WOOD_PLACE, 0.4F, null, false), "stacking crates");
		}
		Stop from = this.sources.get(brain.random.nextInt(this.sources.size()));
		Stop to = sinkFor(from);
		if (to == null) {
			return Tasks.Linger.at(from.pos(), 200, "waiting for a load");
		}
		return new Haul(from, to, load(from.kind(), brain.settlement.culture()), cartLoad(from.kind()));
	}

	private @Nullable Stop sinkFor(Stop from) {
		Stop best = null;
		double bestScore = Double.MAX_VALUE;
		for (Stop s : this.sinks) {
			if (s.plotId() == from.plotId()) {
				continue;
			}
			// a workshop that wants this kind beats a general store; then the nearer
			double score = Math.sqrt(s.pos().distSqr(from.pos())) + (s.kind() == from.kind() ? 0 : s.kind() == Kind.GOODS ? 60 : 10_000);
			if (score < bestScore) {
				best = s;
				bestScore = score;
			}
		}
		return best != null && bestScore < 10_000 ? best : null;
	}

	/** Reads the town's chains from its plots: who produces what, who wants it, where their doors and stores are. */
	private void routes(FolkBrain brain, ServerLevel level) {
		this.sources.clear();
		this.sinks.clear();
		for (PlotView plot : brain.settlement.plots()) {
			boolean extraction = Extraction.is(plot);
			if (plot.state() != PlotState.BUILT && !(extraction && plot.state() == PlotState.BUILDING)) {
				continue;
			}
			String path = plot.spec().archetype().getPath();
			Kind out = extraction ? fromSite(Extraction.kind(plot)) : produces(path);
			Set<Kind> in = wants(path);
			if (out == null && in.isEmpty()) {
				continue;
			}
			BlockPos at = stopOf(brain, level, plot, extraction);
			if (at == null) {
				continue;
			}
			if (out != null) {
				this.sources.add(new Stop(plot.id(), at, out));
			}
			for (Kind k : in) {
				this.sinks.add(new Stop(plot.id(), at, k));
			}
		}
	}

	private static @Nullable BlockPos stopOf(FolkBrain brain, ServerLevel level, PlotView plot, boolean extraction) {
		if (extraction) {
			Blueprint bp = BlueprintSource.of(level, brain.settlement, plot);
			if (bp != null) {
				Poi store = Extraction.storage(bp);
				if (store != null) {
					return store.pos();
				}
				if (!bp.entrances().isEmpty()) {
					return bp.entrances().get(0);
				}
			}
			return ResidentPlaces.centre(plot.spec());
		}
		List<Poi> pois = brain.town.places.plot(level, brain.settlement, plot);
		Poi door = null;
		for (Poi poi : pois) {
			if (poi.type() == PoiType.STORAGE) {
				return poi.pos();
			}
			if (poi.type() == PoiType.DOOR && door == null) {
				door = poi;
			}
		}
		return door != null ? door.pos() : ResidentPlaces.centre(plot.spec());
	}

	static @Nullable Kind fromSite(ResourceSite.@Nullable Kind kind) {
		if (kind == null) {
			return Kind.STONE;
		}
		return switch (kind) {
			case STONE -> Kind.STONE;
			case ORE, SPECIALTY -> Kind.ORE;
			case CLAY -> Kind.CLAY;
			case SAND -> Kind.SAND;
			case SALT -> Kind.SALT;
			case WOODS -> Kind.LOGS;
			case FERTILE, PASTURE -> Kind.GRAIN;
			case FISH -> Kind.FISH;
		};
	}

	/** What a workplace sends out, by its archetype's name. */
	static @Nullable Kind produces(String path) {
		String n = path.substring(path.lastIndexOf('/') + 1);
		if (path.startsWith("farm/") || n.equals("barn")) {
			return Kind.GRAIN;
		}
		return switch (n) {
			case "sawmill", "logging_camp" -> Kind.LOGS;
			case "quarry" -> Kind.STONE;
			case "fishery" -> Kind.FISH;
			case "charcoal_kiln" -> Kind.CHARCOAL;
			case "brick_kiln" -> Kind.BRICK;
			case "smelter" -> Kind.METAL;
			case "glassworks" -> Kind.GLASS;
			case "apiary" -> Kind.WAX;
			case "tannery" -> Kind.LEATHER;
			case "salt_works" -> Kind.SALT;
			default -> null;
		};
	}

	/** What a building takes in, by its archetype's name ({@link Kind#GOODS} for a general store). */
	static Set<Kind> wants(String path) {
		if (path.startsWith("storage/")) {
			return Set.of(Kind.GOODS);
		}
		String n = path.substring(path.lastIndexOf('/') + 1);
		return switch (n) {
			case "masonry" -> Set.of(Kind.STONE);
			case "builders_yard" -> Set.of(Kind.STONE, Kind.LOGS, Kind.BRICK, Kind.GLASS);
			case "carpentry", "sawmill", "shipyard" -> Set.of(Kind.LOGS);
			case "charcoal_kiln" -> Set.of(Kind.LOGS);
			case "smelter" -> Set.of(Kind.ORE, Kind.CHARCOAL);
			case "smithy" -> Set.of(Kind.METAL, Kind.CHARCOAL);
			case "brick_kiln" -> Set.of(Kind.CLAY);
			case "glassworks" -> Set.of(Kind.SAND);
			case "windmill", "watermill", "bakery", "brewery" -> Set.of(Kind.GRAIN);
			case "chandler" -> Set.of(Kind.WAX);
			case "tailor", "butcher" -> Set.of(Kind.LEATHER);
			case "market_hall", "counting_house", "caravan_yard" -> Set.of(Kind.GOODS);
			default -> Set.of();
		};
	}

	/** What the load looks like in their arms. */
	static ItemStack load(Kind kind, Culture culture) {
		return new ItemStack(loadItem(kind, culture));
	}

	static Item loadItem(Kind kind, Culture culture) {
		return switch (kind) {
			case STONE -> Items.COBBLESTONE;
			case ORE -> Items.RAW_IRON_BLOCK;
			case CLAY -> Items.CLAY;
			case SAND -> Items.SAND;
			case LOGS -> switch (culture) {
				case NORTHERN, TUNDRA, ALPINE -> Items.SPRUCE_LOG;
				case TROPICAL, BAYOU -> Items.JUNGLE_LOG;
				case DESERT, MESA, STEPPE -> Items.ACACIA_LOG;
				case WISTERIA -> Items.CHERRY_LOG;
				default -> Items.OAK_LOG;
			};
			case GRAIN -> Items.HAY_BLOCK;
			case FISH -> Items.BARREL;
			case CHARCOAL -> Items.COAL_BLOCK;
			case BRICK -> Items.BRICKS;
			case METAL -> Items.IRON_BLOCK;
			case GLASS -> Items.GLASS;
			case WAX -> Items.HONEYCOMB_BLOCK;
			case LEATHER -> Items.LEATHER;
			case SALT -> Items.BARREL;
			case GOODS -> Items.BARREL;
		};
	}

	/** What a handcart's bed shows for a kind of load. */
	static CartEntity.Load cartLoad(Kind kind) {
		return switch (kind) {
			case LOGS -> CartEntity.Load.LOGS;
			case STONE, ORE, CLAY, BRICK, METAL -> CartEntity.Load.STONE;
			case GRAIN, WAX -> CartEntity.Load.PRODUCE;
			case SAND, CHARCOAL, SALT, LEATHER -> CartEntity.Load.SACKS;
			case FISH -> CartEntity.Load.BARRELS;
			case GLASS, GOODS -> CartEntity.Load.CRATES;
		};
	}

	/** Within this of a stop the hauler leaves the cart standing (by the door, not in the building). */
	static final double PARK = 5.0;

	/** One load: to the source, loaded, carried (slowly), set down at the sink; by handcart when the haul is long. */
	static final class Haul implements Task {
		private enum Phase { TO_SOURCE, LOAD, TO_SINK, UNLOAD }

		private final Stop from;
		private final Stop to;
		private final ItemStack load;
		private final CartEntity.Load cartLoad;
		private Phase phase = Phase.TO_SOURCE;
		private int timer;
		private boolean started;
		private @Nullable CartEntity cart;

		Haul(Stop from, Stop to, ItemStack load, CartEntity.Load cartLoad) {
			this.from = from;
			this.to = to;
			this.load = load;
			this.cartLoad = cartLoad;
		}

		/** The cart, if it is still out (the sweep may have taken it when the server slowed). */
		private @Nullable CartEntity cart() {
			if (this.cart != null && this.cart.isRemoved()) {
				this.cart = null;
			}
			return this.cart;
		}

		/** Hitched while walking, left standing within {@link #PARK} of the stop it walks to or while the hauler is under a roof. */
		private void drawTowards(FolkBrain brain, BlockPos stop) {
			CartEntity c = cart();
			if (c == null) {
				return;
			}
			// parked by the door, and whenever the hauler is under a roof (indoors, up a stair, under eaves): a cart keeps to
			// the open street and never follows them in or onto a roof
			boolean park = brain.folk.blockPosition().distSqr(stop) < PARK * PARK || !brain.folk.level().canSeeSky(brain.folk.blockPosition().above());
			if (park && c.puller() != null) {
				c.hitchTo(null);
			} else if (!park && c.puller() == null && c.distanceToSqr(brain.folk) < 6 * 6) {
				c.hitchTo(brain.folk);
			}
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.timer += elapsed;
			if (!this.started) {
				this.started = true;
				if (Handcarts.worthACart(Math.sqrt(this.from.pos().distSqr(this.to.pos())))) {
					this.cart = Handcarts.take(level, brain.settlement.id(), brain.folk);
				}
			}
			switch (this.phase) {
				case TO_SOURCE -> {
					brain.hold(ItemStack.EMPTY, ItemStack.EMPTY);
					drawTowards(brain, this.from.pos());
					if (brain.walk(level, this.from.pos(), 1.8, 0.65) || brain.walkFailed() || this.timer > 20 * 90) {
						brain.resetWalk();
						this.phase = Phase.LOAD;
						this.timer = 0;
						level.playSound(null, this.from.pos(), SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
					}
				}
				case LOAD -> {
					brain.faceTowards(this.from.pos());
					brain.folk.setFolkPose(FolkPose.TEND);
					if (this.timer >= 40) {
						brain.swing();
						CartEntity c = cart();
						if (c != null) {
							c.setup(CartEntity.Kind.HANDCART, this.cartLoad);
						}
						this.phase = Phase.TO_SINK;
						this.timer = 0;
					}
				}
				case TO_SINK -> {
					if (cart() != null) {
						// the load is on the cart's bed; the hauler draws it with empty hands
						brain.hold(ItemStack.EMPTY, ItemStack.EMPTY);
						brain.folk.setFolkPose(FolkPose.NONE);
						drawTowards(brain, this.to.pos());
					} else {
						brain.hold(this.load, ItemStack.EMPTY);
						brain.folk.setFolkPose(FolkPose.CRAFT);
					}
					if (brain.walk(level, this.to.pos(), 1.8, 0.5) || brain.walkFailed() || this.timer > 20 * 120) {
						brain.resetWalk();
						this.phase = Phase.UNLOAD;
						this.timer = 0;
					}
				}
				case UNLOAD -> {
					brain.faceTowards(this.to.pos());
					if (this.timer >= 30) {
						brain.swing();
						level.playSound(null, this.to.pos(), SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
						Handcarts.release(this.cart);
						this.cart = null;
						return Status.DONE;
					}
				}
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
			Handcarts.release(this.cart);
			this.cart = null;
		}

		@Override
		public String describe() {
			return switch (this.phase) {
				case TO_SOURCE, LOAD -> "fetching " + this.from.kind().name().toLowerCase(java.util.Locale.ROOT);
				case TO_SINK, UNLOAD -> "hauling " + this.from.kind().name().toLowerCase(java.util.Locale.ROOT) + (this.cart != null ? " by handcart" : "")
					+ " to plot " + this.to.plotId();
			};
		}
	}
}
