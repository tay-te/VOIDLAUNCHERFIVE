package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A compiled cuboid model ({@code assets/void/cosmetics/<id>/model.json}): bones with a parent,
 * a pivot and a rest rotation, each carrying cubes whose six faces already have their UV rects
 * resolved, plus optional glow anchors and keyframe animations.
 *
 * <p>Coordinates are model pixels with y pointing down, relative to the attachment point (the
 * body or head pivot). Cubes are absolute, the way Blockbench stores them; a bone rotates its
 * cubes and its children about its own pivot. This class is the parse and the geometry; drawing
 * it is {@link ModelDraw}, which owns the GL side.</p>
 */
public final class CosmeticModel {

    /** Face order everywhere: north, south, east, west, up, down. */
    static final String[] FACES = {"n", "s", "e", "w", "u", "d"};

    public static final class Cube {
        public final float x0, y0, z0, x1, y1, z1;
        /** Per face [u0, v0, u1, v1] in texels, or null for an undrawn face. */
        public final float[][] uv = new float[6][];
        public final boolean emissiveOnly;

        Cube(float[] f, float inflate, boolean emissiveOnly) {
            x0 = f[0] - inflate;
            y0 = f[1] - inflate;
            z0 = f[2] - inflate;
            x1 = f[3] + inflate;
            y1 = f[4] + inflate;
            z1 = f[5] + inflate;
            this.emissiveOnly = emissiveOnly;
        }
    }

    public static final class Bone {
        public final String name;
        public final int parent;
        public final float px, py, pz;
        /** Rest rotation, radians. */
        public final float rx, ry, rz;
        public final Cube[] cubes;
        public final boolean billboard;
        int[] children = new int[0];

        Bone(String name, int parent, float[] pv, float[] r, Cube[] cubes, boolean billboard) {
            this.name = name;
            this.parent = parent;
            this.px = pv[0];
            this.py = pv[1];
            this.pz = pv[2];
            this.rx = r[0];
            this.ry = r[1];
            this.rz = r[2];
            this.cubes = cubes;
            this.billboard = billboard;
        }
    }

    /** A glow anchor: where a bloom sprite sits, in its bone's space. */
    public static final class Glow {
        public final int bone;
        public final float x, y, z;
        public final int rgb;
        public final float size, strength;

        Glow(int bone, float x, float y, float z, int rgb, float size, float strength) {
            this.bone = bone;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rgb = rgb;
            this.size = size;
            this.strength = strength;
        }
    }

    /** One looping or one-shot keyframe animation over some bones. */
    public static final class Anim {
        public final float length;
        public final boolean loop;
        final int[] bone;
        final char[] prop;
        final Keyframes[] keys;

        Anim(float length, boolean loop, int[] bone, char[] prop, Keyframes[] keys) {
            this.length = length;
            this.loop = loop;
            this.bone = bone;
            this.prop = prop;
            this.keys = keys;
        }
    }

    public final String attach;
    public final int texW, texH;
    public final int frames;
    public final float fps;
    public final String procedural;
    /** Head items: grow to clear a helmet. */
    public final boolean helmetFit;
    public final Bone[] bones;
    public final int[] roots;
    public final Glow[] glow;
    public final Map<String, Anim> anims;

    private CosmeticModel(String attach, int texW, int texH, int frames, float fps, String proc,
                          boolean helmetFit, Bone[] bones, Glow[] glow, Map<String, Anim> anims) {
        this.attach = attach;
        this.texW = texW;
        this.texH = texH;
        this.frames = frames;
        this.fps = fps;
        this.procedural = proc;
        this.helmetFit = helmetFit;
        this.bones = bones;
        this.glow = glow;
        this.anims = anims;
        List<Integer> r = new ArrayList<Integer>();
        List<List<Integer>> kids = new ArrayList<List<Integer>>();
        for (int i = 0; i < bones.length; i++) {
            kids.add(new ArrayList<Integer>());
        }
        for (int i = 0; i < bones.length; i++) {
            if (bones[i].parent < 0) {
                r.add(Integer.valueOf(i));
            } else {
                kids.get(bones[i].parent).add(Integer.valueOf(i));
            }
        }
        roots = toInts(r);
        for (int i = 0; i < bones.length; i++) {
            bones[i].children = toInts(kids.get(i));
        }
    }

    private static int[] toInts(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i).intValue();
        }
        return a;
    }

    public int boneIndex(String name) {
        for (int i = 0; i < bones.length; i++) {
            if (bones[i].name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = a.get(i).getAsFloat();
        }
        return f;
    }

    public static CosmeticModel parse(JsonObject o) {
        if (!o.has("v") || o.get("v").getAsInt() != 1) {
            throw new IllegalArgumentException("unsupported model version");
        }
        String attach = o.has("attach") ? o.get("attach").getAsString() : "body";
        JsonArray tex = o.getAsJsonArray("tex");
        int frames = 1;
        float fps = 0;
        if (o.has("frames")) {
            JsonArray fr = o.getAsJsonArray("frames");
            frames = Math.max(1, fr.get(0).getAsInt());
            fps = fr.get(1).getAsFloat();
        }
        JsonArray jb = o.getAsJsonArray("bones");
        Bone[] bones = new Bone[jb.size()];
        for (int i = 0; i < bones.length; i++) {
            JsonObject b = jb.get(i).getAsJsonObject();
            List<Cube> cubes = new ArrayList<Cube>();
            if (b.has("c")) {
                for (JsonElement ce : b.getAsJsonArray("c")) {
                    JsonObject c = ce.getAsJsonObject();
                    Cube cube = new Cube(floats(c.getAsJsonArray("f")),
                            c.has("i") ? c.get("i").getAsFloat() : 0,
                            c.has("l") && "emissive-only".equals(c.get("l").getAsString()));
                    JsonObject uv = c.getAsJsonObject("uv");
                    for (int f = 0; f < 6; f++) {
                        if (uv.has(FACES[f])) {
                            cube.uv[f] = floats(uv.getAsJsonArray(FACES[f]));
                        }
                    }
                    cubes.add(cube);
                }
            }
            int parent = b.get("p").getAsInt();
            if (parent >= i) {
                throw new IllegalArgumentException("bone " + i + " has a parent that is not before it");
            }
            bones[i] = new Bone(b.get("n").getAsString(), parent, floats(b.getAsJsonArray("pv")),
                    floats(b.getAsJsonArray("r")), cubes.toArray(new Cube[0]), b.has("bb"));
        }
        List<Glow> glow = new ArrayList<Glow>();
        if (o.has("glow")) {
            for (JsonElement ge : o.getAsJsonArray("glow")) {
                float[] g = floats(ge.getAsJsonArray());
                glow.add(new Glow((int) g[0], g[1], g[2], g[3], (int) g[4], g[5], g[6]));
            }
        }
        Map<String, Anim> anims = new java.util.HashMap<String, Anim>();
        if (o.has("anims")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("anims").entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                JsonArray ch = a.getAsJsonArray("ch");
                int[] bone = new int[ch.size()];
                char[] prop = new char[ch.size()];
                Keyframes[] keys = new Keyframes[ch.size()];
                for (int i = 0; i < ch.size(); i++) {
                    JsonArray c = ch.get(i).getAsJsonArray();
                    bone[i] = c.get(0).getAsInt();
                    prop[i] = c.get(1).getAsString().charAt(0);
                    keys[i] = Keyframes.parse(c.get(2).getAsJsonArray());
                }
                anims.put(e.getKey(), new Anim(a.get("len").getAsFloat(), a.get("loop").getAsInt() != 0,
                        bone, prop, keys));
            }
        }
        return new CosmeticModel(attach, tex.get(0).getAsInt(), tex.get(1).getAsInt(), frames, fps,
                o.has("proc") ? o.get("proc").getAsString() : null,
                o.has("fit") && o.get("fit").getAsBoolean(), bones, glow.toArray(new Glow[0]), anims);
    }

    /**
     * A per-draw pose: rotation and translation added to each bone's rest state. Owned by the
     * caller (one per player per item) so nothing here is shared mutable state.
     */
    public static final class Pose {
        public final float[] rot;
        public final float[] pos;
        public final float[] scale;

        public Pose(int bones) {
            rot = new float[bones * 3];
            pos = new float[bones * 3];
            scale = new float[bones * 3];
            reset();
        }

        public void reset() {
            java.util.Arrays.fill(rot, 0);
            java.util.Arrays.fill(pos, 0);
            java.util.Arrays.fill(scale, 1);
        }
    }

    private final float[] tmp = new float[3];

    /** Adds animation {@code name} sampled at wall time {@code t} seconds into {@code pose}. */
    public void animate(String name, float t, Pose pose) {
        Anim a = anims.get(name);
        if (a == null) {
            return;
        }
        float time = a.loop && a.length > 0 ? t % a.length : Math.min(t, a.length);
        for (int i = 0; i < a.bone.length; i++) {
            a.keys[i].sample(time, tmp, 0);
            int b = a.bone[i] * 3;
            switch (a.prop[i]) {
                case 'r':
                    pose.rot[b] += tmp[0];
                    pose.rot[b + 1] += tmp[1];
                    pose.rot[b + 2] += tmp[2];
                    break;
                case 'p':
                    pose.pos[b] += tmp[0];
                    pose.pos[b + 1] += tmp[1];
                    pose.pos[b + 2] += tmp[2];
                    break;
                default:
                    pose.scale[b] *= tmp[0];
                    pose.scale[b + 1] *= tmp[1];
                    pose.scale[b + 2] *= tmp[2];
                    break;
            }
        }
    }
}
