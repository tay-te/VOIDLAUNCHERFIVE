package dev.voidmc.hearth.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.gen.GameFacts;
import dev.voidmc.hearth.quest.gen.GenerationContext;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.TemplateParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;

/** Shared fixtures for the quest tests: settlements, contexts and the shipped templates. */
final class QuestTestKit {
	static final Path TEMPLATE_DIR = Path.of("src/main/resources/data/hearth/hearth_quest");
	private static boolean bootstrapped;

	private QuestTestKit() {
	}

	static synchronized void bootstrap() {
		if (!bootstrapped) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			bootstrapped = true;
		}
	}

	/** A village (20+ folk) with a builder, a smith, a scholar, a guard and the rest, and coin in the treasury. */
	static SimpleSettlement village(long id, String name, Culture culture, BlockPos center) {
		SimpleSettlement s = new SimpleSettlement(id, name, culture, center, -100);
		JobType[] jobs = {JobType.BUILDER, JobType.SMITH, JobType.SCHOLAR, JobType.GUARD, JobType.FARMER, JobType.BAKER, JobType.MERCHANT,
			JobType.PRIEST, JobType.HUNTER, JobType.TAILOR, JobType.INNKEEPER, JobType.MASON};
		for (int i = 0; i < 24; i++) {
			s.addResident(resident(new UUID(id, i), "Folk" + i, jobs[i % jobs.length], i % 8 == 7 ? AgeStage.CHILD : AgeStage.ADULT));
		}
		s.deposit(10_000);
		return s;
	}

	static Resident resident(UUID id, String name, JobType job, AgeStage age) {
		return new Resident(id, name, "Tester", false, age, -9000, job, -1, -1, 7, new CompoundTag());
	}

	static GenerationContext context(Settlement s, SettlementQuests data, long day, List<? extends Settlement> others, List<List<Identifier>> located) {
		return new GenerationContext(s, data, day, RandomSource.create(42 + day), others, GameFacts.PLAIN, QuestConfig.DEFAULTS, located::add);
	}

	static GenerationContext context(Settlement s, SettlementQuests data) {
		return context(s, data, 10, List.of(), new ArrayList<>());
	}

	/** Every template shipped in the mod's data, parsed. */
	static Map<Identifier, QuestTemplate> shippedTemplates() throws IOException {
		Map<Identifier, QuestTemplate> out = new LinkedHashMap<>();
		try (Stream<Path> files = Files.walk(TEMPLATE_DIR)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				String rel = TEMPLATE_DIR.relativize(file).toString().replace('\\', '/');
				Identifier id = Identifier.fromNamespaceAndPath("hearth", rel.substring(0, rel.length() - 5));
				JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				out.put(id, TemplateParser.parse(id, json));
			}
		}
		return out;
	}

	static QuestTemplate parse(String json) {
		return TemplateParser.parse(Identifier.fromNamespaceAndPath("test", "t"), JsonParser.parseString(json).getAsJsonObject());
	}
}
