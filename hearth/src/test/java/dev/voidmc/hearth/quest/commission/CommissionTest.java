package dev.voidmc.hearth.quest.commission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LivesService;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.quest.QuestConfig;
import dev.voidmc.hearth.quest.gen.GameFacts;
import dev.voidmc.hearth.quest.gen.GenerationContext;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.TemplateParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A lord's commissions (docs/LIVING_WORLD_2.md §8.8): each shipped template binds to the real state of a test world (or is
 * skipped where the world does not bear it out), ranks advance as the design says, and an heir keeps a service one rank
 * lower until the player pledges again.
 */
class CommissionTest {
	static final Path DIR = Path.of("src/main/resources/data/hearth/hearth_quest/commission");
	private static final PlaceConfig.Commissions CFG = PlaceConfig.Commissions.DEFAULTS;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	record Shipped(QuestTemplate template, CommissionSpecs.Spec spec) {
	}

	static Map<Identifier, Shipped> shipped() throws Exception {
		Map<Identifier, Shipped> out = new LinkedHashMap<>();
		try (Stream<Path> files = Files.list(DIR)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				String name = f.getFileName().toString().replace(".json", "");
				Identifier id = Hearth.id("commission/" + name);
				JsonObject json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				QuestTemplate t = TemplateParser.parse(id, json);
				CommissionSpecs.Spec spec = CommissionSpecs.parse(id, json);
				assertNotNull(spec, id + " has a commission block");
				out.put(id, new Shipped(t, spec));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- a test world

	private static Resident person(String name, JobType job) {
		return new Resident(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, "Kunstat", false, AgeStage.ADULT, -9000, job, -1, -1, 3,
			new CompoundTag());
	}

	private static SimpleSettlement town(long id, String name, BlockPos at, int folk) {
		SimpleSettlement s = new SimpleSettlement(id, name, Culture.TEMPERATE, at, -100);
		for (int i = 0; i < folk; i++) {
			s.addResident(person(name + i, i % 5 == 0 ? JobType.FARMER : JobType.MASON));
		}
		return s;
	}

	/** The seat, two villages of its domain (one with a great mine and a toll gate), and a neighbouring lord's seat. */
	static class TestWorld implements Binder.World {
		final SimpleSettlement seat = town(1, "Hradec", new BlockPos(0, 64, 0), 40);
		final SimpleSettlement village;
		final SimpleSettlement neighbour = town(3, "Zbraslav", new BlockPos(2600, 64, 0), 30);
		final SimpleSettlement hamlet = town(4, "Lhota", new BlockPos(0, 64, 900), 12);
		final Resident lord = person("Lord Hynek", JobType.LORD);
		final Resident otherLord = person("Lady Anezka", JobType.LORD);
		final Resident reeve = person("Reeve Matej", JobType.REEVE);
		final Resident headA = person("Old Bohuslav", JobType.FARMER);
		final Resident headB = person("Widow Ludmila", JobType.FARMER);
		final List<String> raidDay = new ArrayList<>(List.of("raid"));
		final List<String> tournament = new ArrayList<>(List.of("tournament"));
		boolean roads = true;
		int crime = 2;

		TestWorld() {
			ResourceSite mine = new ResourceSite(1, ResourceSite.Kind.ORE, new BlockPos(600, 64, 50), new BoundingBox(590, 0, 40, 610, 80, 60), 1000, 1000,
				0, new float[0], dev.voidmc.hearth.sim.land.GreatMines.MATERIAL, -1);
			this.village = new SimpleSettlement(2, "Kourim", Culture.TEMPERATE, new BlockPos(600, 64, 0), -100) {
				@Override
				public List<ResourceSite> sites() {
					return List.of(mine);
				}
			};
			for (int i = 0; i < 24; i++) {
				this.village.addResident(person("Kourim" + i, JobType.FARMER));
			}
			this.seat.addResident(this.lord);
			this.seat.deposit(5000);
			this.neighbour.addResident(this.otherLord);
			this.village.addResident(this.reeve);
			this.village.addResident(this.headA);
			this.village.addResident(this.headB);
			SimpleSettlement.SimplePlot toll = this.village.addPlot(new PlotSpec(Hearth.id("law/toll_gate"), Hearth.id("temperate"), 1,
				new BlockPos(700, 64, 0), 9, 7, Direction.EAST, 12, 1, new CompoundTag()));
			toll.state = PlotState.BUILT;
		}

		@Override
		public long day() {
			return 30;
		}

		@Override
		public List<? extends Settlement> others() {
			return List.of(this.seat, this.village, this.neighbour, this.hamlet);
		}

		@Override
		public @Nullable Resident lord(Settlement s) {
			return s.id() == 1 ? this.lord : s.id() == 3 ? this.otherLord : null;
		}

		@Override
		public @Nullable Resident reeve(Settlement s) {
			return s.id() == 2 ? this.reeve : null;
		}

		@Override
		public RoadNetwork roads() {
			if (!this.roads) {
				return RoadNetwork.NONE;
			}
			RoadNetwork.RoadView road = new RoadNetwork.RoadView(11, 1, 2, RoadNetwork.RoadClass.TRACK, new int[]{0, 64, 0, 300, 64, 0, 600, 64, 0}, 1f,
				600);
			return new RoadNetwork() {
				@Override
				public List<RoadView> near(ResourceKey<Level> dimension, BlockPos at, int radius) {
					return List.of(road);
				}

				@Override
				public List<RoadView> from(long settlementId) {
					return settlementId == 1 || settlementId == 2 ? List.of(road) : List.of();
				}

				@Override
				public @Nullable RoadView between(long a, long b) {
					return a + b == 3 ? road : null;
				}

				@Override
				public double roadDistance(long a, long b) {
					return a + b == 3 ? 600 : -1;
				}

				@Override
				public float danger(long roadId, double along) {
					return along > 250 && along < 350 ? 0.7f : 0.1f;
				}
			};
		}

		@Override
		public List<LivesService.Feud> feuds(Settlement s) {
			return s.id() == 2 ? List.of(new LivesService.Feud(this.headA.id, this.headB.id, "a boundary stone", 12)) : List.of();
		}

		@Override
		public List<String> events(Settlement s, long day) {
			if (s.id() == 2 && day == 30) {
				return this.raidDay;
			}
			if (s.id() == 1 && day == 31) {
				return this.tournament;
			}
			return List.of();
		}

		@Override
		public int crime(Settlement s) {
			return s.id() == 1 ? this.crime : 0;
		}

		@Override
		public int domainRadius() {
			return 1500;
		}
	}

	private static GenerationContext context(TestWorld w) {
		List<Settlement> others = new ArrayList<>(List.of(w.village, w.neighbour, w.hamlet));
		return new GenerationContext(w.seat, new SettlementQuests(), w.day(), RandomSource.create(7), others, GameFacts.PLAIN, QuestConfig.DEFAULTS,
			ids -> {
			});
	}

	// ---------------------------------------------------------------- the tests

	@Test
	void twelveCommissionsShipAndNoBoardPostsThem() throws Exception {
		Map<Identifier, Shipped> all = shipped();
		assertEquals(12, all.size(), "twelve commission templates");
		Set<Binder.Kind> kinds = EnumSet.noneOf(Binder.Kind.class);
		for (Shipped s : all.values()) {
			kinds.add(s.spec.kind());
			assertEquals(0.0, s.template.weight(), 0.0, s.template.id() + ": weight 0, never on a board");
			assertTrue(s.template.text().title().size() >= 2, s.template.id() + ": two or more variants");
			assertTrue(s.template.giverJobs().contains(JobType.LORD), s.template.id() + ": the lord gives it");
		}
		assertEquals(EnumSet.allOf(Binder.Kind.class), kinds, "every kind of commission has a template");
	}

	@Test
	void eachTemplateBindsToTheDomainsRealStateOrIsSkipped() throws Exception {
		TestWorld w = new TestWorld();
		GenerationContext ctx = context(w);
		for (Shipped s : shipped().values()) {
			Binder.Binding b = Binder.bind(s.spec.kind(), w.seat, w, 99);
			assertNotNull(b, s.spec.kind() + " binds in the test world");
			QuestInstance q = QuestGenerator.bind(s.template, ctx, null, b.happening());
			assertNotNull(q, s.template.id() + " binds through the generator");
			Commissions.speak(q, b);
			switch (s.spec.kind()) {
				case BAND -> {
					Objective o = q.objectives.getFirst();
					assertEquals(ObjectiveKind.AMBUSH, o.kind);
					assertTrue(o.pos.getX() > 250 && o.pos.getX() < 350, "at the road's most dangerous point: " + o.pos);
				}
				case REEVE -> {
					assertEquals(w.reeve.id, q.objectives.getFirst().entity, "the reeve of the poor village");
					assertEquals(w.village.id(), q.objectives.getFirst().settlement);
					assertTrue(q.objectives.getFirst().label.contains(w.reeve.fullName()), q.objectives.getFirst().label);
				}
				case FEUD -> {
					assertEquals(w.headA.id, q.objectives.get(0).entity);
					assertEquals(w.headB.id, q.objectives.get(1).entity);
				}
				case LETTER -> {
					assertEquals(w.otherLord.id, q.objectives.getFirst().entity, "into the neighbouring lord's hand");
					assertEquals(w.neighbour.id(), q.objectives.getFirst().settlement);
				}
				case TAX -> assertEquals(w.neighbour.id(), q.objectives.getFirst().settlement, "the takings go to the neighbouring seat");
				case TOLL -> assertEquals(700, q.objectives.getFirst().pos.getX(), "at the domain's toll gate");
				case RAID -> assertEquals(w.village.id(), b.happening().other(), "the threatened village");
				case GREAT_MINE -> assertEquals(600, q.objectives.getFirst().pos.getX(), "at the Great Mine");
				default -> {
				}
			}
			assertTrue(q.type == dev.voidmc.hearth.api.QuestType.OFFICIAL);
		}
		// a world that does not bear them out: the road gone, no crime, no raid, no tournament, no lives service
		w.roads = false;
		w.crime = 0;
		w.raidDay.clear();
		w.tournament.clear();
		assertNull(Binder.bind(Binder.Kind.BAND, w.seat, w, 1), "no road, no band to clear");
		assertNull(Binder.bind(Binder.Kind.PATROL, w.seat, w, 1));
		assertNull(Binder.bind(Binder.Kind.POACHER, w.seat, w, 1), "no crime, no poacher");
		assertNull(Binder.bind(Binder.Kind.TOURNAMENT, w.seat, w, 1), "no tournament on");
		assertNotNull(Binder.bind(Binder.Kind.RAID, w.seat, w, 1), "without the events' raids, the worst-guarded village");
		SimpleSettlement lone = town(9, "Samota", new BlockPos(9000, 64, 9000), 6);
		Binder.World empty = new TestWorld() {
			@Override
			public List<? extends Settlement> others() {
				return List.of(lone);
			}

			@Override
			public List<LivesService.Feud> feuds(Settlement s) {
				return List.of();
			}
		};
		for (Binder.Kind k : List.of(Binder.Kind.TAX, Binder.Kind.REEVE, Binder.Kind.FEUD, Binder.Kind.LETTER, Binder.Kind.TOLL, Binder.Kind.GREAT_MINE,
			Binder.Kind.NEW_VILLAGE)) {
			assertNull(Binder.bind(k, lone, empty, 1), k + " is skipped in a lone camp");
		}
	}

	@Test
	void ranksAdvanceAsTheDesignSays() {
		Retinue.Service s = Retinue.Service.start(UUID.randomUUID(), 3);
		assertEquals(Rank.PETITIONER, s.rank());
		assertTrue(RetinueRules.mayPetition(Title.KNOWN));
		assertFalse(RetinueRules.mayPetition(Title.STRANGER), "a stranger is not heard");
		// retainer: the pledge of service and one commission
		assertEquals(RetinueRules.Why.PLEDGE, RetinueRules.rise(s, false, Title.KNOWN, 20, CFG));
		assertEquals(RetinueRules.Why.COMMISSIONS, RetinueRules.rise(s, true, Title.KNOWN, 20, CFG));
		s = s.withDone(1, false);
		assertEquals(RetinueRules.Why.OK, RetinueRules.rise(s, true, Title.KNOWN, 20, CFG));
		s = s.withRank(Rank.RETAINER);
		// sworn man: three, and a Friend of the seat
		s = s.withDone(3, false);
		assertEquals(RetinueRules.Why.TITLE, RetinueRules.rise(s, true, Title.KNOWN, 30, CFG));
		assertEquals(RetinueRules.Why.OK, RetinueRules.rise(s, true, Title.FRIEND, 45, CFG));
		s = s.withRank(Rank.SWORN);
		// household knight: six, standing 70, and a feat
		s = s.withDone(6, false);
		assertEquals(RetinueRules.Why.STANDING, RetinueRules.rise(s, true, Title.FRIEND, 60, CFG));
		assertEquals(RetinueRules.Why.FEAT, RetinueRules.rise(s, true, Title.HERO, 85, CFG));
		s = s.withDone(7, true);
		assertEquals(RetinueRules.Why.OK, RetinueRules.rise(s, true, Title.HERO, 85, CFG));
		s = s.withRank(Rank.KNIGHT);
		assertEquals(RetinueRules.Why.TOP, RetinueRules.rise(s, true, Title.HERO, 85, CFG));
		// a server without knighthoods stops at sworn man
		PlaceConfig.Commissions noKnights = new PlaceConfig.Commissions(false, 1, 3, 6, 70, 1500);
		assertEquals(RetinueRules.Why.NO_KNIGHTHOOD, RetinueRules.rise(s.withRank(Rank.SWORN), true, Title.HERO, 85, noKnights));
		// commissions are given by rank
		assertTrue(RetinueRules.given(Rank.PETITIONER, Rank.PETITIONER));
		assertFalse(RetinueRules.given(Rank.SWORN, Rank.RETAINER));
	}

	@Test
	void anHeirKeepsTheServiceOneRankLowerUntilThePlayerPledgesAgain() {
		UUID oldLord = UUID.randomUUID();
		UUID heir = UUID.randomUUID();
		Retinue.Service s = Retinue.Service.start(oldLord, 0).withDone(4, false).withRank(Rank.SWORN);
		assertEquals(s, RetinueRules.confirm(s, oldLord, true), "the same lord: unchanged");
		Retinue.Service under = RetinueRules.confirm(s, heir, false);
		assertEquals(Rank.RETAINER, under.rank(), "one rank lower under the heir");
		assertEquals(Optional.of(Rank.SWORN), under.demotedFrom());
		assertEquals(Optional.of(heir), under.lord());
		assertEquals(4, under.done(), "deeds done are not forgotten");
		assertEquals(under, RetinueRules.confirm(under, heir, false), "still lower until the pledge");
		Retinue.Service again = RetinueRules.confirm(under, heir, true);
		assertEquals(Rank.SWORN, again.rank(), "pledged again: the old rank back");
		assertTrue(again.demotedFrom().isEmpty());
		// and the retinue saves
		Retinue r = Retinue.EMPTY.with(7, again);
		var json = Retinue.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
		Retinue back = Retinue.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(again, back.service(7));
		Map<Long, Retinue.Service> none = new HashMap<>();
		assertEquals(0, new Retinue(none).services().size());
	}
}
