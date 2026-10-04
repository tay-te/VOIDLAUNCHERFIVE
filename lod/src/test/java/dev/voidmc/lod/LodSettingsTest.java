package dev.voidmc.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.lod.LodSettings.Preset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The settings file: read forgivingly, written whole, presets and system properties applied the way the README says. */
class LodSettingsTest {
	@TempDir
	Path dir;

	@Test
	void theDefaultsAreTheHighPreset() {
		LodSettings s = LodSettings.load(this.dir.resolve("missing.json"));
		assertEquals(Preset.HIGH, s.preset);
		assertEquals(8192, s.radius);
		assertEquals(5, s.detail);
		assertEquals(5, s.farDetail);
		assertEquals(512, s.budget);
		assertTrue(s.enabled && s.autoTune);
		assertEquals(0, s.haze, "haze follows the reach");
	}

	@Test
	void aFileThatIsNotJsonGivesTheDefaults() throws IOException {
		Path file = this.dir.resolve("void_lod.json");
		Files.writeString(file, "radius = lots\u0000{{{");
		LodSettings s = LodSettings.load(file);
		assertEquals(Preset.HIGH, s.preset);
		assertEquals(8192, s.radius);
		Files.writeString(file, "[1, 2, 3]");
		assertEquals(8192, LodSettings.load(file).radius);
	}

	@Test
	void eachValueFallsBackOrIsClampedOnItsOwn() throws IOException {
		Path file = this.dir.resolve("void_lod.json");
		Files.writeString(file, """
			{
			  // written by hand
			  "preset": "nonsense",
			  "radius": "far",
			  "detail": -3,
			  "farDetail": 1e9,
			  "threads": null,
			  "budget": "256",
			  "enabled": "maybe",
			  "autoTune": false,
			  "haze": 12,
			  "gpuTarget": "NaN",
			  "unknown": {"nested": true}
			}
			""");
		LodSettings s = LodSettings.load(file);
		assertEquals(8192, s.radius, "not a number: the default");
		assertEquals(LodSettings.MIN_DETAIL, s.detail, "clamped");
		assertEquals(LodSettings.MAX_DETAIL, s.farDetail, "clamped");
		assertEquals(256, s.budget, "a number in a string still counts");
		assertTrue(s.enabled, "not a boolean: the default");
		assertTrue(!s.autoTune);
		assertEquals(0, s.haze, "a reach of a few blocks means the default");
		assertEquals(1.5, s.gpuTarget);
		assertEquals(Preset.CUSTOM, s.preset, "no valid preset named, and the values match none");
	}

	@Test
	void aNamedPresetWinsOverTheValuesUnderIt() throws IOException {
		Path file = this.dir.resolve("void_lod.json");
		Files.writeString(file, "{\"preset\": \"LOW\", \"radius\": 30000, \"detail\": 3, \"haze\": 5000}");
		LodSettings s = LodSettings.load(file);
		assertEquals(Preset.LOW, s.preset);
		assertEquals(4096, s.radius);
		assertEquals(10, s.detail);
		assertEquals(5000, s.haze, "the haze is not the preset's");
	}

	@Test
	void customKeepsItsValuesAndAFileWithoutAPresetMeansItsValues() throws IOException {
		Path file = this.dir.resolve("void_lod.json");
		Files.writeString(file, "{\"preset\": \"custom\", \"radius\": 30000, \"detail\": 6.5}");
		LodSettings s = LodSettings.load(file);
		assertEquals(Preset.CUSTOM, s.preset);
		assertEquals(30000, s.radius);
		assertEquals(6.5, s.detail);
		assertEquals(6.5, s.farDetail, "far detail follows detail when it is not given");
		Files.writeString(file, "{\"radius\": 16384}");
		s = LodSettings.load(file);
		assertEquals(16384, s.radius);
		assertEquals(Preset.CUSTOM, s.preset);
	}

	@Test
	void savedSettingsReadBackTheSame() {
		Path file = this.dir.resolve("config").resolve("void_lod.json");
		LodSettings s = new LodSettings();
		s.apply(Preset.CUSTOM);
		s.radius = 12288;
		s.detail = 4.5;
		s.farDetail = 3.5;
		s.haze = 9000;
		s.threads = 3;
		s.budget = 768;
		s.enabled = false;
		s.autoTune = false;
		s.prepareTarget = 0.8;
		s.save(file);
		LodSettings back = LodSettings.load(file);
		assertEquals(Preset.CUSTOM, back.preset);
		assertEquals(12288, back.radius);
		assertEquals(4.5, back.detail);
		assertEquals(3.5, back.farDetail);
		assertEquals(9000, back.haze);
		assertEquals(3, back.threads);
		assertEquals(768, back.budget);
		assertTrue(!back.enabled && !back.autoTune);
		assertEquals(0.8, back.prepareTarget);
		assertTrue(!Files.exists(file.resolveSibling("void_lod.json.tmp")));
	}

	@Test
	void systemPropertiesOverrideForTheSession() {
		LodSettings s = new LodSettings();
		s.override(Map.of("void.lod.detail", "4", "void.lod.threads", "lots")::get);
		assertEquals(4, s.detail);
		assertEquals(4, s.farDetail, "far detail follows a detail given alone, as it always has");
		assertEquals(Preset.CUSTOM, s.preset);
		assertEquals(new LodSettings().threads, s.threads, "a value that is not a number is ignored");

		s = new LodSettings();
		s.override(Map.of("void.lod.preset", "ultra", "void.lod.autoTune", "false")::get);
		assertEquals(Preset.ULTRA, s.preset);
		assertEquals(16384, s.radius);
		assertEquals(3, s.farDetail);
		assertTrue(!s.autoTune);
	}

	@Test
	void everyPresetIsRecognisedByItsValues() {
		for (Preset preset : Preset.values()) {
			if (preset != Preset.CUSTOM) {
				LodSettings s = new LodSettings();
				s.apply(Preset.CUSTOM);
				s.apply(preset);
				s.preset = Preset.CUSTOM;
				assertEquals(preset, s.matching());
			}
		}
	}
}
