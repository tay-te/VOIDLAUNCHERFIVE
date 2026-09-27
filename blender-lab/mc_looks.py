"""
MC LOOKS - stylization on top of a lit, filmed scene (the ceiling a shader pack can't reach).

  toon   Shader to RGB banding (EEVEE only): three light bands that keep each light's colour, shadows
         pushed toward a cool tint, a hard specular band. Glowing materials are left alone.
  ink    Line Art outlines (Grease Pencil, Blender 5.x): silhouettes and block creases in a warm
         near-black. On 4.x, where Line Art can't be created from Python, a compositor edge ink from
         the normal and depth passes stands in.
  paint  anisotropic Kuwahara in the compositor, placed before the lens effects so grain and
         aberration sit on top of the "paint".

Used by mc_film_lab.py as the opt-in stage "look" (collection LAB_07_Look, toggle like any lever):
    blender -b World_LAB.blend --python mc_film_lab.py -- '{"stages": "look", "look": "toon+ink"}'
"""
import json
import math

import bpy
from mathutils import Vector

from mc_shaderpack import G, new_group

IS5 = bpy.app.version >= (5, 0, 0)


# ============================================================================== toon
def group_toon():
    ng, gi, go = new_group("LAB7_Toon", "ShaderNodeTree", [
        ("Albedo", "NodeSocketColor", (0.8, 0.8, 0.8, 1.0)), ("Alpha", "NodeSocketFloat", 1.0),
        ("Shadow Tint", "NodeSocketColor", (0.42, 0.46, 0.68, 1.0)), ("Softness", "NodeSocketFloat", 0.08),
        ("Specular", "NodeSocketFloat", 0.22), ("Key", "NodeSocketFloat", 1.0)], [("Shader", "NodeSocketShader")])
    g, I = G(ng), gi.outputs
    diff = g.node("ShaderNodeBsdfDiffuse")
    diff.inputs["Color"].default_value = (1, 1, 1, 1)
    s2r = g.node("ShaderNodeShaderToRGB")
    ng.links.new(diff.outputs[0], s2r.inputs[0])
    light = s2r.outputs["Color"]                                  # what the lights deliver here, in colour
    bw = g.node("ShaderNodeRGBToBW")
    ng.links.new(light, bw.inputs[0])
    lum = bw.outputs[0]

    # bands are fractions of the key light (a white surface in full sun), so the shot keeps its exposure:
    # deep shadow 0.035, shadow-side 0.2, lit 0.8, hot 1.2 x key
    rel = g.math("DIVIDE", lum, g.math("MAXIMUM", I["Key"], 0.001))

    def step(edge):
        return g.maprange(rel, edge, g.math("MULTIPLY", edge, g.math("ADD", 1.0, I["Softness"])), 0.0, 1.0)
    s1, s2, s3 = step(0.06), step(0.3), step(1.1)
    level = g.math("MULTIPLY", I["Key"], g.math("ADD", g.math("ADD", g.math("MULTIPLY", s1, 0.165),
                                                              g.math("MULTIPLY", s2, 0.6)),
                                                 g.math("MULTIPLY_ADD", s3, 0.4, 0.035)))
    hue = g.vmath("DIVIDE", light, g.comb(*[g.math("MAXIMUM", lum, 0.001)] * 3))      # the light's colour, unit brightness
    hue = g.vmath("MINIMUM", hue, (3.0, 3.0, 3.0))
    tone = g.mix("RGBA", s1, I["Shadow Tint"], hue)
    col = g.mix("RGBA", 1.0, g.mix("RGBA", 1.0, I["Albedo"], tone, blend="MULTIPLY"),
                g.comb(level, level, level), blend="MULTIPLY")
    gl = g.node("ShaderNodeBsdfGlossy")
    gl.inputs["Roughness"].default_value = 0.25
    s2r2 = g.node("ShaderNodeShaderToRGB")
    ng.links.new(gl.outputs[0], s2r2.inputs[0])
    bw2 = g.node("ShaderNodeRGBToBW")
    ng.links.new(s2r2.outputs["Color"], bw2.inputs[0])
    spec = g.math("MULTIPLY", g.maprange(g.math("DIVIDE", bw2.outputs[0], g.math("MAXIMUM", I["Key"], 0.001)),
                                         0.9, 0.95, 0.0, 1.0), I["Specular"])
    col = g.mix("RGBA", spec, col, hue, blend="ADD")
    em = g.node("ShaderNodeEmission")
    ng.links.new(col, em.inputs["Color"])
    cut = g.node("ShaderNodeMixShader")
    tr = g.node("ShaderNodeBsdfTransparent")
    ng.links.new(I["Alpha"], cut.inputs[0])
    ng.links.new(tr.outputs[0], cut.inputs[1])
    ng.links.new(em.outputs[0], cut.inputs[2])
    ng.links.new(cut.outputs[0], go.inputs[0])
    go.location = (g.col * 170 + 200, 0)
    return ng


def group_paint():
    g_ = bpy.data.node_groups.new("LAB7_Paint", "CompositorNodeTree")
    g_.interface.new_socket("Image", in_out="INPUT", socket_type="NodeSocketColor")
    g_.interface.new_socket("Image", in_out="OUTPUT", socket_type="NodeSocketColor")
    N, L = g_.nodes, g_.links.new
    gi, go = N.new("NodeGroupInput"), N.new("NodeGroupOutput")
    r2p = N.new("CompositorNodeRelativeToPixel")
    r2p.data_type, r2p.reference_dimension = "FLOAT", "X"
    next(s for s in r2p.inputs if s.name == "Value" and s.type == "VALUE").default_value = 0.0045
    L(gi.outputs[0], r2p.inputs["Image"])
    kw = N.new("CompositorNodeKuwahara")
    if "Type" in kw.inputs and kw.inputs["Type"].type == "MENU":
        kw.inputs["Type"].default_value = "Anisotropic"
    elif hasattr(kw, "variation"):
        kw.variation = "ANISOTROPIC"
    for nm, val in (("Uniformity", 4), ("Sharpness", 0.65), ("Eccentricity", 1.0), ("High Precision", True)):
        if nm in kw.inputs:
            kw.inputs[nm].default_value = val
    L(gi.outputs[0], kw.inputs["Image"])
    L(next(s for s in r2p.outputs if s.type == "VALUE"), kw.inputs["Size"])
    sw = N.new("CompositorNodeSwitch")
    sw.name = "LAB7_Switch"
    L(gi.outputs[0], sw.inputs["Off"])
    L(kw.outputs[0], sw.inputs["On"])
    L(sw.outputs[0], go.inputs[0])
    for i, n in enumerate((gi, r2p, kw, sw, go)):
        n.location = (220 * i, 0)
    return g_


def group_edge_ink():
    """Compositor ink for Blender 4.x: edges where normals or depth jump, multiplied over the image."""
    g_ = bpy.data.node_groups.new("LAB7_EdgeInk", "CompositorNodeTree")
    for nm, st in (("Image", "NodeSocketColor"), ("Normal", "NodeSocketVector"), ("Depth", "NodeSocketFloat")):
        g_.interface.new_socket(nm, in_out="INPUT", socket_type=st)
    g_.interface.new_socket("Image", in_out="OUTPUT", socket_type="NodeSocketColor")
    N, L = g_.nodes, g_.links.new
    gi, go = N.new("NodeGroupInput"), N.new("NodeGroupOutput")
    fn = N.new("CompositorNodeFilter")
    if hasattr(fn, "filter_type"):
        fn.filter_type = "SOBEL"
    elif "Type" in fn.inputs:
        fn.inputs["Type"].default_value = "Sobel"
    L(gi.outputs["Normal"], fn.inputs["Image"])
    bw = N.new("CompositorNodeRGBToBW")
    L(fn.outputs[0], bw.inputs[0])
    edge = N.new("ShaderNodeMapRange")
    edge.clamp = True
    edge.inputs["From Min"].default_value, edge.inputs["From Max"].default_value = 0.35, 0.9
    edge.inputs["To Min"].default_value, edge.inputs["To Max"].default_value = 1.0, 0.12
    L(bw.outputs[0], edge.inputs["Value"])
    mix = N.new("ShaderNodeMix")
    mix.data_type, mix.blend_type = "RGBA", "MULTIPLY"
    next(s for s in mix.inputs if s.identifier == "Factor_Float").default_value = 1.0
    L(gi.outputs["Image"], next(s for s in mix.inputs if s.identifier == "A_Color"))
    L(edge.outputs["Result"], next(s for s in mix.inputs if s.identifier == "B_Color"))
    sw = N.new("CompositorNodeSwitch")
    sw.name = "LAB7_Switch"
    L(gi.outputs["Image"], sw.inputs["Off"])
    L(next(s for s in mix.outputs if s.identifier == "Result_Color"), sw.inputs["On"])
    L(sw.outputs[0], go.inputs[0])
    for i, n in enumerate((gi, fn, bw, edge, mix, sw, go)):
        n.location = (200 * i, 0)
    return g_


# ============================================================================== applying a look
class Look:
    def __init__(self, lab, L, looks):
        self.lab, self.L, self.scene = lab, L, lab.scene
        self.looks = looks
        self.sec = lab.log.section("look", f"OPTIONAL - LOOK: {'+'.join(looks)} (stage 'look')")
        self.coll = L.lever_coll(self.scene, "look")

    def toon(self):
        L = self.L
        if self.scene.render.engine == "CYCLES":
            self.lab.log.warn("toon uses Shader to RGB, which only EEVEE supports - Cycles renders it black")
        grp = group_toon()
        suns = [o for o in self.scene.objects if o.type == "LIGHT" and o.data.type == "SUN" and not o.hide_render]
        key = max((o.data.energy for o in suns), default=math.pi) / math.pi   # white surface in full sun
        done = []
        used = sorted({sl.material for o in self.scene.objects if not self.lab.is_lab(o)
                       for sl in o.material_slots if sl.material and not sl.material.name.startswith("LAB")},
                      key=lambda m: m.name)
        for mat in used:
            if not mat.node_tree or L.is_emissive(mat):
                continue
            p = L.principled_of(mat)
            nt = mat.node_tree
            out = nt.get_output_node("EEVEE") or nt.get_output_node("ALL")
            if p is None or out is None or not out.inputs["Surface"].is_linked:
                continue
            surf = out.inputs["Surface"].links[0].from_socket
            L.record_link(mat, 7, out.inputs["Surface"], surf)
            g = G(nt)
            g.col = 8
            node = g.node("ShaderNodeGroup", name="LAB7_Toon", label="LAB Toon")
            node.node_tree = grp
            node.inputs["Key"].default_value = key
            for src_in, dst in (("Base Color", "Albedo"), ("Alpha", "Alpha")):
                s = p.inputs[src_in]
                if s.is_linked:
                    nt.links.new(s.links[0].from_socket, node.inputs[dst])
                else:
                    node.inputs[dst].default_value = s.default_value
            mix = g.node("ShaderNodeMixShader", name="LAB7_ToonMix")
            nt.links.new(surf, mix.inputs[1])
            nt.links.new(node.outputs[0], mix.inputs[2])
            nt.links.new(mix.outputs[0], out.inputs["Surface"])
            L.switch(nt, f'nodes["{mix.name}"].inputs[0].default_value', "look", on=1.0, off=0.0)
            done.append(mat.name)
        self.sec["changes"].append(
            f"toon shading on {len(done)} materials: Shader to RGB of a white diffuse -> 3 light bands that keep "
            "each light's colour, shadows toward a cool tint (0.42, 0.46, 0.68), hard specular band; glowing "
            f"materials untouched; bands relative to the key light ({key:.2f}); EEVEE only (no Shader to RGB in Cycles)")

    def ink(self):
        L = self.L
        if IS5:
            gp = bpy.data.grease_pencils.new("LAB7_Ink")
            ob = bpy.data.objects.new("LAB_Ink", gp)
            L.link(ob, self.coll)
            layer = gp.layers.new("Lines")
            if hasattr(layer, "frames"):
                layer.frames.new(self.scene.frame_current)
            mat = bpy.data.materials.new("LAB7_InkMat")
            bpy.data.materials.create_gpencil_data(mat)
            mat.grease_pencil.color = (0.07, 0.055, 0.05, 1.0)
            gp.materials.append(mat)
            md = ob.modifiers.new("LAB7_Lineart", "LINEART")
            md.source_type = "SCENE"
            md.target_layer = "Lines"
            md.target_material = mat
            for attr, val in (("thickness", 3), ("radius", 0.025), ("use_contour", True), ("use_crease", True),
                              ("crease_threshold", math.radians(140)), ("use_material", False),
                              ("use_intersection", False), ("use_loose", False), ("opacity", 0.85)):
                if hasattr(md, attr):
                    try:
                        setattr(md, attr, val)
                    except (TypeError, ValueError):
                        pass
            skipped = []
            for o in self.scene.objects:            # volumes, dust, lights and grass stay out of the ink
                if o.type != "MESH" or not hasattr(o, "lineart"):
                    continue
                cls = {L.classify_material(s.material) for s in o.material_slots if s.material}
                if self.lab.is_lab(o) or cls <= {"volume", "cutout"}:
                    store = json.loads(o.get("LAB7_restore", "{}"))
                    store.setdefault("lineart.usage", o.lineart.usage)
                    o["LAB7_restore"] = json.dumps(store)
                    o.lineart.usage = "EXCLUDE"
                    skipped.append(o.name)
            self.sec["changes"].append(
                "ink: Line Art (Grease Pencil) over the whole scene - silhouettes and block creases sharper than "
                f"140 deg, 2.5 cm world-space radius (heavier up close, finer far away - like a real pen line), "
                f"warm near-black (0.07, 0.055, 0.05), 85 % opacity; excluded: {len(skipped)} volume/"
                "grass/LAB objects")
        else:
            vl = self.scene.view_layers[0]
            vl.use_pass_normal = True
            vl.use_pass_z = True
            self.comp_insert(group_edge_ink(), with_passes=True)
            self.sec["changes"].append("ink: compositor edge ink from the normal pass (Blender 4.x - Line Art can't "
                                       "be created from Python there); edges multiplied over the image")

    def paint(self):
        self.comp_insert(group_paint())
        self.sec["changes"].append("paint: anisotropic Kuwahara (0.45 % of the frame width, uniformity 4, "
                                   "sharpness 0.65) before the lens effects, so grain and aberration sit on top")

    def comp_insert(self, grp, with_passes=False):
        """Insert a LAB7 group upstream of the LAB5 lens group (or right before the output)."""
        L, s = self.L, self.scene
        tree = L.comp_tree(s, create=True)
        s.render.use_compositing = True
        out, out_s = L.comp_output(tree)
        lens = tree.nodes.get("LAB5_Lens")
        dst = lens.inputs[0] if lens else out_s
        if not dst.is_linked:
            rl = next((n for n in tree.nodes if n.bl_idname == "CompositorNodeRLayers"), None) \
                or tree.nodes.new("CompositorNodeRLayers")
            tree.links.new(rl.outputs["Image"], dst)
        src = dst.links[0].from_socket
        L.record_link(s, 7, dst, src)
        node = tree.nodes.new("CompositorNodeGroup")
        node.node_tree = grp
        node.name = node.label = "LAB7_" + grp.name.split("_", 1)[1]
        node.location = (lens or out).location + Vector((-260, 140))
        tree.links.new(src, node.inputs[0])
        if with_passes:
            rl = next(n for n in tree.nodes if n.bl_idname == "CompositorNodeRLayers")
            tree.links.new(rl.outputs["Normal"], node.inputs["Normal"])
            tree.links.new(rl.outputs["Depth"], node.inputs["Depth"])
        tree.links.new(node.outputs[0], dst)
        L.switch(grp, 'nodes["LAB7_Switch"].inputs[0].default_value', "look", on=1, off=0)

    def run(self):
        for name in self.looks:
            getattr(self, name)()
        self.sec["why_title"] = "Why:"
        self.sec["why"] = ("A shader pack can only make Minecraft more realistic; it can't change what kind of "
                           "picture it is. Stylization is the one lever with no ceiling: banded light reads as "
                           "drawn, ink lines turn blocks into illustration, painterly filtering turns texels into "
                           "brush strokes - and all of it sits on top of the real light, air and lens underneath, "
                           "which is what keeps it from looking like a filter.")


def apply(lab, L, looks):
    Look(lab, L, looks).run()
