package dev.voidmc.hearth.social;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Small builders for the social tests: residents with traits and family, settlements with built plots. */
final class SocialTestKit {
	private static int next;

	private SocialTestKit() {
	}

	static UUID id(int n) {
		return new UUID(0x50C1A1L, n);
	}

	static Resident resident(String name, boolean female, AgeStage age, JobType job, long born, String... traits) {
		Resident r = new Resident(id(++next), name, "Smith", female, age, born, job, Resident.NO_PLOT, Resident.NO_PLOT, next, new CompoundTag());
		if (traits.length > 0) {
			r.extra("npc").store("traits", com.mojang.serialization.Codec.STRING.listOf(), List.of(traits));
		}
		return r;
	}

	static void parents(Resident child, Resident... parents) {
		List<UUID> ids = java.util.Arrays.stream(parents).map(p -> p.id).toList();
		child.extra("npc").store("parents", UUIDUtil.CODEC.listOf(), ids);
		for (Resident p : parents) {
			List<UUID> kids = new java.util.ArrayList<>(FolkFacts.children(p));
			kids.add(child.id);
			p.extra("npc").store("children", UUIDUtil.CODEC.listOf(), kids);
		}
	}

	static SimpleSettlement town(long id, Culture culture) {
		return new SimpleSettlement(id, "Ashbrook", culture, new BlockPos(0, 64, 0), -100);
	}

	/** Adds a finished plot of an archetype. */
	static SimpleSettlement.SimplePlot built(SimpleSettlement s, String archetype, int x, int z) {
		PlotSpec spec = new PlotSpec(Identifier.fromNamespaceAndPath("hearth", archetype), Identifier.fromNamespaceAndPath("hearth", "temperate"), 1,
			new BlockPos(x, 64, z), 9, 9, Direction.SOUTH, 16, 1, new CompoundTag());
		SimpleSettlement.SimplePlot p = s.addPlot(spec);
		p.state = PlotState.BUILT;
		return p;
	}
}
