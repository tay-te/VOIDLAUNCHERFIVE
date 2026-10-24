"""
Brings a `reel.sh export` (renders/_export/NAME: world.obj, world.mtl, textures/) into Blender as the real blocks with
the game's own textures: crisp pixels, cut-out leaves and plants, biome tints from the vertex colours, glowing light
blocks, and water as clear water. Same space as reel_camera.py when given the export's origin.

    import reel_world; obj = reel_world.load(folder)   # returns the imported object; origin in obj["reel_origin"]
"""
import json
import os

import bpy


def load(folder, glow=6.0, style="real"):
    """style "real": the game's textures under physical light. "toon": the same textures, saturated, on light that
    falls in soft bands (Cycles' Toon BSDF), for a storybook look."""
    meta = json.load(open(os.path.join(folder, "meta.json")))
    before = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=os.path.join(folder, "world.obj"), forward_axis="NEGATIVE_Z", up_axis="Y")
    new = [o for o in bpy.data.objects if o not in before]
    for o in new:
        o["reel_origin"] = meta["origin"]
        for slot in o.material_slots:
            if slot.material:
                rebuild(slot.material, folder, glow, style)
    bpy.context.scene["reel_origin"] = meta["origin"]
    print("[reel_world] %s: %d faces, %d materials" % (meta["name"], meta["faces"], meta["materials"]))
    return new[0] if new else None


def rebuild(m, folder, glow, style="real"):
    name = m.name.split(".")[0]
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    if name == "water":
        bsdf.inputs["Base Color"].default_value = (0.05, 0.16, 0.22, 1)
        bsdf.inputs["Roughness"].default_value = 0.04
        bsdf.inputs["IOR"].default_value = 1.33
        for key in ("Transmission Weight", "Transmission"):
            if key in bsdf.inputs:
                bsdf.inputs[key].default_value = 0.92
                break
        return
    if name == "lava":
        bsdf.inputs["Base Color"].default_value = (1.0, 0.35, 0.05, 1)
        bsdf.inputs["Emission Color"].default_value = (1.0, 0.35, 0.05, 1)
        bsdf.inputs["Emission Strength"].default_value = glow * 2
        return
    path = os.path.join(folder, "textures", name + ".png")
    if not os.path.isfile(path):
        bsdf.inputs["Base Color"].default_value = (0.5, 0.5, 0.5, 1)
        return
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(path, check_existing=True)
    tex.interpolation = "Closest"
    col = nt.nodes.new("ShaderNodeVertexColor")
    col.layer_name = "Color"
    mix = nt.nodes.new("ShaderNodeMix")
    mix.data_type = "RGBA"
    mix.blend_type = "MULTIPLY"
    mix.inputs["Factor"].default_value = 1.0
    nt.links.new(tex.outputs["Color"], mix.inputs["A"])
    nt.links.new(col.outputs["Color"], mix.inputs["B"])
    nt.links.new(mix.outputs["Result"], bsdf.inputs["Base Color"])
    nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    bsdf.inputs["Roughness"].default_value = 0.85
    if hasattr(m, "surface_render_method"):
        m.surface_render_method = "DITHERED"
    if style == "toon":
        # saturated colour on banded light, cut out by the texture's alpha
        hs = nt.nodes.new("ShaderNodeHueSaturation")
        hs.inputs["Saturation"].default_value = 1.25
        hs.inputs["Value"].default_value = 1.05
        nt.links.new(mix.outputs["Result"], hs.inputs["Color"])
        toon = nt.nodes.new("ShaderNodeBsdfToon")
        toon.component = "DIFFUSE"
        toon.inputs["Size"].default_value = 0.78
        toon.inputs["Smooth"].default_value = 0.12
        nt.links.new(hs.outputs["Color"], toon.inputs["Color"])
        glossy = nt.nodes.new("ShaderNodeBsdfToon")
        glossy.component = "GLOSSY"
        glossy.inputs["Size"].default_value = 0.25
        glossy.inputs["Smooth"].default_value = 0.2
        glossy.inputs["Color"].default_value = (1.0, 0.92, 0.8, 1)
        both = nt.nodes.new("ShaderNodeMixShader")
        both.inputs["Fac"].default_value = 0.06
        nt.links.new(toon.outputs["BSDF"], both.inputs[1])
        nt.links.new(glossy.outputs["BSDF"], both.inputs[2])
        clear = nt.nodes.new("ShaderNodeBsdfTransparent")
        cut = nt.nodes.new("ShaderNodeMixShader")
        nt.links.new(tex.outputs["Alpha"], cut.inputs["Fac"])
        nt.links.new(clear.outputs["BSDF"], cut.inputs[1])
        nt.links.new(both.outputs["Shader"], cut.inputs[2])
        final = cut.outputs["Shader"]
        if any(k in name for k in ("lantern", "torch", "glowstone", "lamp", "shroomlight", "candle", "magma")):
            em = nt.nodes.new("ShaderNodeEmission")
            em.inputs["Strength"].default_value = glow
            nt.links.new(hs.outputs["Color"], em.inputs["Color"])
            add = nt.nodes.new("ShaderNodeAddShader")
            nt.links.new(final, add.inputs[0])
            nt.links.new(em.outputs["Emission"], add.inputs[1])
            final = add.outputs["Shader"]
        nt.links.new(final, out.inputs["Surface"])
        return
    emissive = m.get("reel_glow") or any(k in name for k in ("lantern", "torch", "glowstone", "lamp", "shroomlight", "campfire_fire", "fire_0",
                                                               "candle", "sea_lantern", "jack_o_lantern", "magma"))
    if emissive:
        nt.links.new(mix.outputs["Result"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = glow
