package dev.voidmc.gloam.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.JOrbisAudioStream;
import org.junit.jupiter.api.Test;

/** Every clip sounds.json names decodes with Minecraft's own Ogg decoder, and every bed has a clip. */
class ClipsTest {
	private static JsonObject sounds() throws IOException {
		try (InputStream in = ClipsTest.class.getResourceAsStream("/assets/gloam/sounds.json")) {
			assertNotNull(in, "sounds.json is in the resources");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@Test
	void everyBedHasAnEvent() throws IOException {
		JsonObject sounds = sounds();
		for (Bed bed : Bed.values()) {
			assertTrue(sounds.has("bed." + bed.key()), bed.key());
		}
		assertTrue(sounds.has("thunder"));
	}

	@Test
	void everyClipDecodesMonoUnclippedAndTheRightLength() throws IOException {
		int clips = 0;
		for (Map.Entry<String, JsonElement> event : sounds().entrySet()) {
			for (JsonElement sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
				String name = sound.getAsJsonObject().get("name").getAsString();
				String path = "/assets/gloam/sounds/" + name.substring("gloam:".length()) + ".ogg";
				try (InputStream in = ClipsTest.class.getResourceAsStream(path)) {
					assertNotNull(in, path);
					JOrbisAudioStream stream = new JOrbisAudioStream(in);
					AudioFormat format = stream.getFormat();
					ByteBuffer pcm = stream.readAll().order(ByteOrder.LITTLE_ENDIAN);
					assertEquals(1, format.getChannels(), path + " is mono (stereo would not pan)");
					float seconds = pcm.remaining() / 2.0F / format.getSampleRate();
					boolean bed = event.getKey().startsWith("bed.");
					assertTrue(bed ? seconds >= 10 && seconds <= 25 : seconds >= 4 && seconds <= 15, path + " lasts " + seconds + " s");
					int peak = 0;
					while (pcm.remaining() >= 2) {
						peak = Math.max(peak, Math.abs(pcm.getShort()));
					}
					assertTrue(peak < 32700, path + " clips");
					assertTrue(peak > 3000, path + " is nearly silent");
					clips++;
				}
			}
		}
		assertEquals(Bed.values().length + 4, clips);
	}
}
