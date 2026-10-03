package dev.voidmc.hearth.quest.world;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.QuestRegistry;
import dev.voidmc.hearth.quest.block.NoticeBoardBlock;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.service.QuestManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds the dev sandbox: an in-memory settlement round the player with residents of every common trade, coin in the
 * treasury, a cottage stalled for stone and timber, and a notice board; plus a neighbouring hamlet a few hundred blocks
 * off for couriers and escorts to go to. Lets the whole quest loop run in game without the sim module.
 */
public final class QuestSandbox {
	private static final String[] GIVEN = {"Mara", "Tobin", "Elsie", "Corwin", "Hester", "Alder", "Wren", "Bram", "Isolde", "Fenn", "Maud",
		"Osric", "Linnea", "Jory", "Agnes", "Rook", "Tamsin", "Ewan", "Bryony", "Cael"};
	private static final String[] FAMILY = {"Fenwick", "Ashdown", "Marlow", "Thorne", "Hollis", "Brackett", "Wainwright", "Cobb", "Penhallow",
		"Merriweather", "Underhill", "Greaves"};
	private static final String[] PLACES = {"Ashford", "Millbrook", "Thornwick", "Fernhollow", "Harrowgate", "Kettlewell", "Oakhurst", "Larkspur"};
	private static final JobType[] JOBS = {JobType.BUILDER, JobType.BUILDER, JobType.FARMER, JobType.SMITH, JobType.BAKER, JobType.MERCHANT,
		JobType.GUARD, JobType.SCHOLAR, JobType.PRIEST, JobType.HUNTER, JobType.FISHER, JobType.INNKEEPER, JobType.TAILOR, JobType.CARPENTER,
		JobType.MASON, JobType.FORESTER};
	private static final String[] TRAITS = {"sweet_tooth", "bookish", "curious", "romantic", "devout", "green_thumb", "musical", "adventurous",
		"cheerful", "grumpy", "kind", "brave"};

	private QuestSandbox() {
	}

	/** Creates the sandbox town at {@code center} (and its neighbour); returns the town. */
	public static SimpleSettlement create(ServerLevel level, BlockPos center, Direction facing, Culture culture, int population) {
		RandomSource random = RandomSource.create(center.asLong());
		long day = QuestWorlds.day(level);
		String name = PLACES[random.nextInt(PLACES.length)];
		SimpleSettlement town = SandboxSettlements.add(id -> new SimpleSettlement(id, name, culture, center, day - 40));
		populate(town, population, random, day);
		town.deposit(4000);
		town.setEmbodied(true);
		town.setBeds(population);
		PlotSpec spec = new PlotSpec(Identifier.fromNamespaceAndPath(Hearth.MOD_ID, "house/cottage"), Hearth.id(culture.getSerializedName()),
			random.nextLong(), center.relative(facing, 12), 9, 9, facing.getOpposite(), 12, 1, new CompoundTag());
		SimpleSettlement.SimplePlot plot = town.addPlot(spec);
		plot.state = dev.voidmc.hearth.api.PlotState.BUILDING;
		plot.stage = 2;

		// A neighbour to carry parcels to, out of sight but within an evening's walk.
		double angle = random.nextDouble() * Math.PI * 2;
		int nx = center.getX() + (int) (Math.cos(angle) * 320);
		int nz = center.getZ() + (int) (Math.sin(angle) * 320);
		int ny = level.hasChunkAt(new BlockPos(nx, 0, nz)) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) : center.getY();
		String neighbourName = PLACES[(random.nextInt(PLACES.length - 1) + 1 + java.util.Arrays.asList(PLACES).indexOf(name)) % PLACES.length];
		SimpleSettlement neighbour = SandboxSettlements.add(id -> new SimpleSettlement(id, neighbourName, culture, new BlockPos(nx, ny, nz), day - 90));
		populate(neighbour, 8, random, day);
		neighbour.deposit(1500);

		BlockPos boardPos = center.relative(facing, 2);
		if (level.getBlockState(boardPos).canBeReplaced()) {
			level.setBlockAndUpdate(boardPos, QuestRegistry.NOTICE_BOARD.defaultBlockState().setValue(NoticeBoardBlock.FACING, facing.getOpposite()));
		}
		QuestManager.onStalled(level, town, new SettlementQuests.StalledNeed(plot.id(), Goods.STONE, 96, day, "cottage"));
		QuestManager.onStalled(level, town, new SettlementQuests.StalledNeed(plot.id(), Goods.WOOD, 48, day, "cottage"));
		QuestManager.board(level, town, true);
		QuestManager.board(level, neighbour, true);
		NoticeBoardBlock.updatePapers(level, boardPos, dev.voidmc.hearth.quest.service.QuestStore.board(town));
		return town;
	}

	private static void populate(SimpleSettlement s, int population, RandomSource random, long day) {
		for (int i = 0; i < population; i++) {
			AgeStage age = i % 7 == 6 ? AgeStage.CHILD : i % 9 == 8 ? AgeStage.ELDER : AgeStage.ADULT;
			JobType job = age == AgeStage.CHILD ? JobType.NONE : JOBS[i % JOBS.length];
			CompoundTag extra = new CompoundTag();
			CompoundTag npc = new CompoundTag();
			ListTag traits = new ListTag();
			traits.add(StringTag.valueOf(TRAITS[random.nextInt(TRAITS.length)]));
			traits.add(StringTag.valueOf(TRAITS[random.nextInt(TRAITS.length)]));
			npc.put("traits", traits);
			extra.put("npc", npc);
			s.addResident(new Resident(new UUID(random.nextLong(), random.nextLong()), GIVEN[random.nextInt(GIVEN.length)],
				FAMILY[random.nextInt(FAMILY.length)], random.nextBoolean(), age, day - 20L * 365 - random.nextInt(4000), job, -1, -1,
				random.nextInt(), extra));
		}
	}

	/** The residents' names, for command suggestions. */
	public static List<String> names(SimpleSettlement s) {
		return s.residents().stream().map(Resident::fullName).toList();
	}
}
