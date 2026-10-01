package dev.voidmc.expanse.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Builds a model layer from assets/expanse/models/entity/&lt;name&gt;.json — the file tools/gen_entities.py
 * writes alongside the texture it paints, so geometry and UVs have one source.
 *
 * <p>It reads from the mod jar directly rather than through the resource manager: layer definitions
 * are needed before resources load, and these are the mod's own files, not something a resource pack
 * should be able to swap for a mismatched skin.
 */
public final class JsonEntityModels {
	private JsonEntityModels() {
	}

	public static LayerDefinition load(String name) {
		String path = "/assets/expanse/models/entity/" + name + ".json";
		try (InputStream in = JsonEntityModels.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing entity model " + path);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			MeshDefinition mesh = new MeshDefinition();
			Map<String, PartDefinition> parts = new HashMap<>();
			parts.put("root", mesh.getRoot());
			for (JsonElement e : json.getAsJsonArray("parts")) {
				JsonObject p = e.getAsJsonObject();
				CubeListBuilder cubes = CubeListBuilder.create();
				for (JsonElement c : p.getAsJsonArray("cubes")) {
					JsonObject cube = c.getAsJsonObject();
					JsonArray uv = cube.getAsJsonArray("uv");
					JsonArray o = cube.getAsJsonArray("origin");
					JsonArray s = cube.getAsJsonArray("size");
					cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
						.mirror(cube.get("mirror").getAsBoolean())
						.addBox(o.get(0).getAsFloat(), o.get(1).getAsFloat(), o.get(2).getAsFloat(),
							s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat(),
							new CubeDeformation(cube.get("inflate").getAsFloat()));
				}
				JsonArray pivot = p.getAsJsonArray("pivot");
				JsonArray rot = p.getAsJsonArray("rotation");
				PartPose pose = PartPose.offsetAndRotation(
					pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat(),
					rot.get(0).getAsFloat() * Mth.DEG_TO_RAD, rot.get(1).getAsFloat() * Mth.DEG_TO_RAD, rot.get(2).getAsFloat() * Mth.DEG_TO_RAD);
				PartDefinition parent = parts.get(p.get("parent").getAsString());
				if (parent == null) {
					throw new IllegalStateException(name + ": part " + p.get("name") + " names a parent defined after it");
				}
				parts.put(p.get("name").getAsString(), parent.addOrReplaceChild(p.get("name").getAsString(), cubes, pose));
			}
			return LayerDefinition.create(mesh, json.get("texture_width").getAsInt(), json.get("texture_height").getAsInt());
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read entity model " + path, e);
		}
	}
}
