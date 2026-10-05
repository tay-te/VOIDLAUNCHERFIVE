package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.plan.catalogue.DefaultCatalogue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The planner's default catalogue (what it assumes without the build module: the sim's tests, dev tools, and the
 * templates it falls back from) says of every building of the phase 3 catalogue (LIVING_WORLD.md §8.5) what the build
 * module registers for it: category, jobs, beds, frontage, depth and height ranges, least tier, cultures, site flags and
 * how many a settlement may have. Every registered phase 3 archetype is in the default catalogue.
 */
class CatalogueMatchesBuildTest {
	static BlueprintServiceImpl service;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		service = BuildModule.standalone();
	}

	@Test
	void theDefaultCatalogueSaysWhatTheBuildModuleRegisters() {
		Map<Identifier, ArchetypeInfo> registered = new TreeMap<>();
		for (ArchetypeInfo i : service.archetypes()) {
			registered.put(i.id(), i);
		}
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo want : DefaultCatalogue.all()) {
			if (!DefaultCatalogue.isPhase3(want.id())) {
				continue;
			}
			ArchetypeInfo got = registered.get(want.id());
			if (got == null) {
				problems.add(want.id() + ": not registered by the build module");
				continue;
			}
			List<String> diff = diff(want, got);
			if (!diff.isEmpty()) {
				problems.add(want.id() + ": " + String.join("; ", diff) + "\n    registered: " + line(got));
			}
		}
		for (ArchetypeInfo got : registered.values()) {
			String path = got.id().getPath();
			boolean phase3Family = path.startsWith("extract/") || path.startsWith("deep/") || path.startsWith("noble/") || path.startsWith("law/");
			if (phase3Family && DefaultCatalogue.all().stream().noneMatch(i -> i.id().equals(got.id()))) {
				problems.add(got.id() + ": registered but missing from the default catalogue\n    registered: " + line(got));
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " archetypes differ:\n" + String.join("\n", problems));
	}

	static List<String> diff(ArchetypeInfo a, ArchetypeInfo b) {
		List<String> out = new ArrayList<>();
		if (a.category() != b.category()) {
			out.add("category " + a.category() + " / " + b.category());
		}
		if (!a.jobs().equals(b.jobs())) {
			out.add("jobs " + a.jobs() + " / " + b.jobs());
		}
		if (a.beds() != b.beds()) {
			out.add("beds " + a.beds() + " / " + b.beds());
		}
		if (a.minFrontage() != b.minFrontage() || a.maxFrontage() != b.maxFrontage()) {
			out.add("frontage " + a.minFrontage() + "-" + a.maxFrontage() + " / " + b.minFrontage() + "-" + b.maxFrontage());
		}
		if (a.minDepth() != b.minDepth() || a.maxDepth() != b.maxDepth()) {
			out.add("depth " + a.minDepth() + "-" + a.maxDepth() + " / " + b.minDepth() + "-" + b.maxDepth());
		}
		if (a.minHeight() != b.minHeight() || a.maxHeight() != b.maxHeight()) {
			out.add("height " + a.minHeight() + "-" + a.maxHeight() + " / " + b.minHeight() + "-" + b.maxHeight());
		}
		if (a.minTier() != b.minTier()) {
			out.add("tier " + a.minTier() + " / " + b.minTier());
		}
		if (!a.cultures().equals(b.cultures())) {
			out.add("cultures " + a.cultures() + " / " + b.cultures());
		}
		if (!a.flags().equals(b.flags())) {
			out.add("flags " + a.flags() + " / " + b.flags());
		}
		if (a.maxPerSettlement() != b.maxPerSettlement()) {
			out.add("max " + a.maxPerSettlement() + " / " + b.maxPerSettlement());
		}
		return out;
	}

	/** The registered info as the default catalogue's {@code info(...)} arguments (for bringing it in line). */
	static String line(ArchetypeInfo i) {
		return "jobs(" + i.jobs().stream().map(j -> "JobType." + j.name()).sorted().collect(Collectors.joining(", ")) + "), " + i.beds() + ", "
			+ i.minFrontage() + ", " + i.maxFrontage() + ", " + i.minDepth() + ", " + i.maxDepth() + ", " + i.minHeight() + ", " + i.maxHeight()
			+ ", SettlementTier." + i.minTier() + ", cultures(" + i.cultures().stream().map(c -> "Culture." + c.name()).sorted().collect(Collectors.joining(", "))
			+ "), flags(" + i.flags().stream().map(Enum::name).sorted().collect(Collectors.joining(", ")) + "), upgrades " + i.upgradesTo() + ", max "
			+ i.maxPerSettlement() + ", weight " + i.weight();
	}
}
