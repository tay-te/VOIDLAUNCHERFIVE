package dev.voidmc.hearth.standing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.standing.rules.StandingData;
import dev.voidmc.hearth.standing.rules.StandingRules;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/** Shared helpers for the standing tests: the shipped data, residents with traits and family. */
final class StandingTestKit {
	private static boolean booted;

	private StandingTestKit() {
	}

	static synchronized void boot() {
		if (!booted) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			booted = true;
		}
	}

	static JsonObject json(String resource) {
		try (InputStream in = StandingTestKit.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + resource);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The rules as shipped in data/hearth/hearth_standing. */
	static StandingData.Builder shipped() {
		boot();
		return new StandingData.Builder()
			.deeds(Hearth.id("default"), json("/data/hearth/hearth_standing/deeds/default.json"))
			.gossip(Hearth.id("default"), json("/data/hearth/hearth_standing/gossip/default.json"))
			.attire(Hearth.id("default"), json("/data/hearth/hearth_standing/attire/default.json"));
	}

	static void useShippedRules() {
		StandingRules.set(shipped().build());
	}

	static Resident resident(String name, JobType job, String... traits) {
		Resident r = new Resident(UUID.randomUUID(), name, "Miller", false, AgeStage.ADULT, -9000, job, Resident.NO_PLOT, Resident.NO_PLOT, 7, new CompoundTag());
		if (traits.length > 0) {
			r.extra("npc").store("traits", com.mojang.serialization.Codec.STRING.listOf(), List.of(traits));
		}
		return r;
	}

	static void marry(Resident a, Resident b) {
		a.extra("npc").store("spouse", net.minecraft.core.UUIDUtil.CODEC, b.id);
		b.extra("npc").store("spouse", net.minecraft.core.UUIDUtil.CODEC, a.id);
	}
}
