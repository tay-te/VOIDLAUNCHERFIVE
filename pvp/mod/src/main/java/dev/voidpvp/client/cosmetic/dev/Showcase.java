package dev.voidpvp.client.cosmetic.dev;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.cosmetic.CosmeticState;
import dev.voidpvp.client.cosmetic.Emotes;
import dev.voidpvp.client.cosmetic.EquippedSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The dev showcase: {@code -Dvoid.dev.showcase=<outdir>} opens a flat creative world, dresses a
 * cast of mannequins in every cosmetic, walks the camera through a list of scenes and writes
 * stills and short frame sequences to {@code outdir}, then quits. It exists so the cosmetics can be
 * looked at — by a person or by an agent reading the PNGs — without anyone at the keyboard.
 *
 * <p>{@code -Dvoid.dev.showcase.only=a,b} runs only the named scenes ({@code st-*}: every scene
 * whose name starts {@code st-}). The style-test scenes ({@link ShowcaseStyle}, {@code st-*}) run
 * only when named that way. Every scene is a few seconds;
 * the whole run is about a minute and a half on a laptop. Nothing here runs unless the property is
 * set, and the mannequins never exist outside this world.</p>
 */
public final class Showcase {

    private static final String WORLD = "void-showcase";
    static final double GROUND = 4.0;

    private final File out;
    private final Set<String> only;
    private final List<Scene> scenes = new ArrayList<Scene>();
    private final List<ShowcasePlayer> cast = new ArrayList<ShowcasePlayer>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private int phase;
    private int wait;
    private int sceneIndex = -1;
    private int sceneTick;
    private Scene scene;
    private int shotFrames;
    private long nextShotNanos;
    private int nextId = -4000;
    /** Set by a scene: the local player plays this emote a few ticks in. */
    String selfEmote;

    /**
     * Logs to {@code <outdir>/showcase.log} as well as the mod log: under Loom's dev launch the
     * log4j configuration does not always load, and a run whose log vanished is a run nobody can
     * read back.
     */
    public void say(String line) {
        VoidLog.info(line);
        try {
            java.nio.file.Files.write(new File(out, "showcase.log").toPath(),
                    (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
            // the mod log still has it
        }
    }

    public Showcase(File out) {
        this.out = out;
        String o = System.getProperty("void.dev.showcase.only", "");
        this.only = o.isEmpty() ? null : new HashSet<String>(Arrays.asList(o.split(",")));
        out.mkdirs();
        buildScenes();
        say("showcase: " + scenes.size() + " scene(s) into " + out.getAbsolutePath());
    }

    // ------------------------------------------------------------------------------ scenes

    /** One staged shot. */
    abstract static class Scene {
        final String name;
        /** World time (0 = sunrise, 6000 noon, 18000 midnight). */
        long time = 6000;
        /** Ticks to let the scene settle before capturing. */
        int settle = 50;
        /** 1 = a still; more = a sequence captured at {@link #fps}. */
        int frames = 1;
        int fps = 24;
        boolean hud;
        /** Scene tick at which the local player's emote (if any) starts. */
        int emoteAt = 8;
        /** Secondary motion on (the A/B "before" takes turn it off). */
        boolean motion = true;
        /** Reactive level pinned for this scene, or negative for the setting (full). */
        float reactive = -1f;
        /** The A/B measurement: cycles fixed-function, material shader, shader + bloom. */
        boolean ab;
        /** Every mannequin wears the default Steve skin (the style test compares sets, not skins). */
        boolean steve;

        Scene(String name) {
            this.name = name;
        }

        /** Spawns the cast. */
        abstract void stage(Showcase s, MinecraftClient mc);

        /** Camera: x, y, z, yaw, pitch, at a scene tick. */
        abstract void camera(int tick, double[] out);
    }

    private static final String[] NAMES = {"Kestrel", "Nyx", "Wren", "Sable", "Orin", "Lumen", "Vesper", "Ash"};

    static JsonObject set(String cape, String back, String head, String face, String halo,
                                  String trail, String badge) {
        JsonObject o = new JsonObject();
        o.addProperty("cape", cape);
        o.addProperty("back", back);
        o.addProperty("head", head);
        o.addProperty("face", face);
        o.addProperty("halo", halo);
        o.addProperty("trail", trail);
        o.addProperty("badge", badge);
        return o;
    }

    /** The six launch outfits, one per mannequin, between them wearing every item. */
    static final JsonObject[] OUTFITS = {
        set("void-cape", "void-wings", null, null, "void-halo", "spark-trail", "void-badge-founder"),
        set("cell-cape", null, "monarch-crown", null, null, "ember-trail", "cell-badge"),
        set("dusk-cape", "ember-wings", null, "ice-visor", "seraph-halo", "frost-trail", "frost-badge"),
        set("static-cape", null, "dapper-top-hat", null, null, "stair-trail", null),
        set("ring-cape", "orbit-ring", "wool-beanie", null, null, null, "void-badge-player"),
        set("origin-cape", null, null, "rift-mask", null, null, null),
    };

    private void buildScenes() {
        scenes.add(hero("hero-night", 18000));
        scenes.add(hero("hero-dusk", 12800));
        scenes.add(lineup("lineup-front", 6000, 180, 0, 2.0, -7.5, 6));
        scenes.add(lineup("lineup-back", 6000, 180, 180, 2.0, 7.5, 6));
        scenes.add(lineup("night-front", 18000, 180, 0, 2.0, -7.5, 6));
        Scene nb = lineup("night-back", 18000, 180, 180, 2.0, 7.5, 6);
        scenes.add(nb);
        Scene seq = lineup("night-loop", 18000, 180, 0, 2.0, -7.5, 6);
        seq.frames = 48;
        seq.fps = 16;
        scenes.add(seq);
        // At ten blocks, the distance a cosmetic has to read at in a fight.
        scenes.add(lineup("far-back", 6000, 180, 180, 2.0, 10.5, 6));
        scenes.add(lineup("far-back-night", 18000, 180, 180, 2.0, 10.5, 6));
        scenes.add(closeups("capes-close", 6000, new String[] {"cell-cape", "void-cape", "dusk-cape"}, true));
        scenes.add(closeups("capes-close-2", 6000, new String[] {"static-cape", "ring-cape", "origin-cape"}, true));
        scenes.add(closeups("back-close", 6000, new String[] {"ember-wings", "void-wings", "orbit-ring"}, false));
        scenes.add(closeups("back-close-night", 18000, new String[] {"ember-wings", "void-wings", "orbit-ring"}, false));
        scenes.add(closeups("capes-close-night", 18000, new String[] {"cell-cape", "void-cape", "dusk-cape"}, true));
        scenes.add(run("run-capes", 6000));
        scenes.add(run("run-dusk", 13000));
        scenes.add(wings("wings-glide", 6000));
        scenes.add(wings("wings-night", 17500));
        scenes.add(emotes("emotes", 6000));
        scenes.add(heads("heads-day", 6000));
        scenes.add(heads("heads-night", 18000));
        scenes.add(badges("badges", 6000));
        scenes.add(emoteCamera("emote-cam", 6000));
        scenes.add(crowd("perf-20", 6000));
        // Secondary motion and fight reactions (ShowcaseMotion); the -off takes are the same
        // scripts with both switched off, for side-by-side comparison.
        scenes.add(ShowcaseMotion.fight("fight", 6000, true));
        scenes.add(ShowcaseMotion.fight("fight-night", 18000, true));
        scenes.add(ShowcaseMotion.fight("fight-off", 6000, false));
        scenes.add(ShowcaseMotion.turns("turns", 6000, true));
        scenes.add(ShowcaseMotion.turns("turns-off", 6000, false));
        scenes.add(ShowcaseMotion.sprintJump("sprint-jump", 6000));
        scenes.add(ShowcaseMotion.sprintJump("sprint-jump-night", 17800));
        scenes.add(ShowcaseMotion.brawl("brawl-20", 18000));
        // The perf scene again with motion and reactions off: the same run's A/B of their cost.
        Scene off = crowd("perf-20-off", 6000);
        off.motion = false;
        off.reactive = 0f;
        scenes.add(off);
        // The cosmetics style test (ShowcaseStyle): only when asked for by name (st-*).
        ShowcaseStyle.add(scenes);
        // The same crowd, measured in alternating windows so machine load affects every mode alike.
        Scene ab = crowd("perf-ab", 6000);
        ab.ab = true;
        ab.settle = AB_START + AB_WINDOWS * AB_WINDOW;
        scenes.add(ab);
    }

    /** A row of the six outfits, facing {@code faceYaw}, the camera at {@code camZ} looking {@code camYaw}. */
    private Scene lineup(String name, long time, final float faceYaw, final float camYaw, final double spacing,
                         final double camZ, final int count) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < count; i++) {
                    final double x = (i - (count - 1) / 2.0) * spacing;
                    final int k = i;
                    sh.spawn(mc, k, OUTFITS[k % OUTFITS.length], new ShowcasePlayer.Script() {
                        @Override
                        public void at(int tick, double[] o) {
                            o[0] = x;
                            o[1] = GROUND;
                            o[2] = 0;
                            o[3] = faceYaw + (k % 2 == 0 ? 8 : -8);
                            o[4] = 0;
                            o[5] = Math.sin(tick * 0.03 + k) * 12;
                            o[6] = 1;
                            o[7] = 0;
                        }
                    });
                }
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = GROUND + 0.55;
                o[2] = camZ;
                o[3] = camYaw;
                o[4] = 2;
            }
        };
        s.time = time;
        return s;
    }

    /** The beauty shot: three full outfits, close, from three-quarters behind, breathing. */
    private Scene hero(String name, long time) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                sh.spawn(mc, 0, set("void-cape", "void-wings", null, null, "void-halo", null, "void-badge-founder"), fixed(-1.9, 0.6, 165, -20, false));
                sh.spawn(mc, 1, set("dusk-cape", "ember-wings", null, null, "seraph-halo", null, null), fixed(0, -0.4, 182, 10, false));
                sh.spawn(mc, 2, set("cell-cape", "orbit-ring", "monarch-crown", null, null, null, null), fixed(1.9, 0.5, 200, 15, false));
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 1.1;
                o[1] = GROUND + 0.2;
                o[2] = 4.4;
                o[3] = 167;
                o[4] = 2;
            }
        };
        s.time = time;
        s.settle = 60;
        s.frames = 48;
        s.fps = 16;
        return s;
    }

    /** Three players from three-quarters behind, close: capes or back items, one each. */
    private Scene closeups(String name, long time, final String[] ids, final boolean capes) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < ids.length; i++) {
                    JsonObject o = capes ? set(ids[i], null, null, null, null, null, null)
                            : set(null, ids[i], null, null, null, null, null);
                    sh.spawn(mc, i, o, fixed((i - 1) * 1.7, 0, 150 + i * 30 - 30, 0, false));
                }
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0.4;
                o[1] = GROUND + 0.45;
                o[2] = 3.9;
                o[3] = 175;
                o[4] = 4;
            }
        };
        s.time = time;
        s.settle = 45;
        return s;
    }

    private Scene run(String name, long time) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < 4; i++) {
                    final int k = i;
                    final double phase0 = i * Math.PI / 2;
                    final double r = 2.4 + (i % 2) * 0.9;
                    final double w = (i % 2 == 0 ? 1 : -1) * 0.065 * (k < 2 ? 1.25 : 0.85);
                    sh.spawn(mc, k, OUTFITS[k], new ShowcasePlayer.Script() {
                        @Override
                        public void at(int tick, double[] o) {
                            double a = phase0 + tick * w;
                            o[0] = Math.cos(a) * r;
                            o[1] = GROUND;
                            o[2] = Math.sin(a) * r;
                            // Facing along the direction of travel.
                            double vx = -Math.sin(a) * w, vz = Math.cos(a) * w;
                            o[3] = Math.toDegrees(Math.atan2(-vx, vz));
                            o[4] = 0;
                            o[5] = 0;
                            o[6] = 1;
                            o[7] = 0;
                        }
                    });
                }
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = GROUND + 2.4;
                o[2] = -5.6;
                o[3] = 0;
                o[4] = 22;
            }
        };
        s.time = time;
        s.settle = 60;
        s.frames = 60;
        s.fps = 30;
        return s;
    }

    private Scene wings(String name, long time) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                // A glider dropping slowly from height; one standing; one sneaking (folded).
                sh.spawn(mc, 0, set(null, "ember-wings", null, null, "seraph-halo", null, null), new ShowcasePlayer.Script() {
                    @Override
                    public void at(int tick, double[] o) {
                        int t = tick % 40;
                        o[0] = -2.6;
                        o[1] = GROUND + 7.5 - t * 0.17;
                        o[2] = 0.6;
                        o[3] = 200;
                        o[4] = 10;
                        o[5] = 0;
                        o[6] = 0;
                        o[7] = 0;
                    }
                });
                sh.spawn(mc, 1, set("void-cape", "void-wings", null, null, null, null, null), fixed(0.2, 0, 160, 0, false));
                sh.spawn(mc, 2, set(null, "void-wings", "wool-beanie", null, null, null, null), fixed(2.8, 0.4, 210, 0, true));
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = GROUND + 1.9;
                o[2] = 6.6;
                o[3] = 180;
                o[4] = 3;
            }
        };
        s.time = time;
        s.settle = 70;
        s.frames = 36;
        s.fps = 24;
        return s;
    }

    private static ShowcasePlayer.Script fixed(final double x, final double z, final float yaw,
                                               final float headYaw, final boolean sneak) {
        return new ShowcasePlayer.Script() {
            @Override
            public void at(int tick, double[] o) {
                o[0] = x;
                o[1] = GROUND;
                o[2] = z;
                o[3] = yaw;
                o[4] = 0;
                o[5] = headYaw;
                o[6] = 1;
                o[7] = sneak ? 1 : 0;
            }
        };
    }

    private static final int AB_START = 60, AB_WINDOW = 50, AB_WINDOWS = 12;
    private static final String[] AB_MODES = {"fixed-function", "material shader", "shader + bloom high"};
    private final double[] abSum = new double[3];
    private final int[] abN = new int[3];

    /** One tick of the A/B measurement (perf-ab). */
    private void abTick() {
        int t = sceneTick - AB_START;
        if (t < 0 || t >= AB_WINDOWS * AB_WINDOW) {
            return;
        }
        int w = t / AB_WINDOW, k = t % AB_WINDOW, mode = w % 3;
        dev.voidpvp.client.state.LiveState live = dev.voidpvp.client.state.LiveState.get();
        if (k == 0) {
            dev.voidpvp.client.cosmetic.CosmeticsHub.devFixedFunction(mode == 0);
            live.cosmeticsGlowLevel = mode == 2 ? 2 : 0;
            live.cosmeticsGlow = mode == 2;
        } else if (k == 10) {
            dev.voidpvp.client.cosmetic.Budget.window();
        } else if (k == AB_WINDOW - 1) {
            double us = dev.voidpvp.client.cosmetic.Budget.window();
            abSum[mode] += us;
            abN[mode]++;
            say(String.format(java.util.Locale.ROOT, "showcase: perf-ab window %d %s %.0f us/frame", w, AB_MODES[mode], us));
            if (w == AB_WINDOWS - 1) {
                for (int m = 0; m < 3; m++) {
                    say(String.format(java.util.Locale.ROOT, "showcase: perf-ab mean %s %.0f us/frame over %d windows",
                            AB_MODES[m], abSum[m] / Math.max(1, abN[m]), abN[m]));
                }
                dev.voidpvp.client.cosmetic.CosmeticsHub.devFixedFunction(false);
                live.cosmeticsGlowLevel = 2;
                live.cosmeticsGlow = true;
            }
        }
    }

    private static final String[] EMOTE_IDS = {"emote-wave", "emote-clap", "emote-bow", "emote-salute", "emote-shrug", "emote-sit"};

    private Scene emotes(String name, long time) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < 6; i++) {
                    double x = (i - 2.5) * 1.9;
                    ShowcasePlayer p = sh.spawn(mc, i, OUTFITS[i], fixed(x, 0, 180, 0, false));
                    sh.emoteLater(p, EMOTE_IDS[i]);
                }
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = GROUND + 0.9;
                o[2] = -7.2;
                o[3] = 0;
                o[4] = 4;
            }
        };
        s.time = time;
        s.settle = 30;
        s.frames = 64;
        s.fps = 20;
        return s;
    }

    private Scene heads(String name, long time) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                sh.spawn(mc, 0, set(null, null, "monarch-crown", null, "void-halo", null, null), fixed(-1.3, 0, 200, 0, false));
                sh.spawn(mc, 1, set(null, null, "dapper-top-hat", "ice-visor", null, null, null), fixed(0, 0.3, 180, 0, false));
                sh.spawn(mc, 2, set(null, null, "wool-beanie", "rift-mask", "seraph-halo", null, null), fixed(1.3, 0, 160, 0, false));
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = GROUND + 0.35;
                o[2] = -2.6;
                o[3] = 0;
                o[4] = -2;
            }
        };
        s.time = time;
        s.settle = 30;
        return s;
    }

    /**
     * The local player's own emote: the camera eases out of first person into the orbit and back.
     * The local player is the camera here, so this is the one scene where it wears things itself.
     */
    private Scene emoteCamera(String name, long time) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                CosmeticState.get().setOwnsEverything(true);
                JsonObject o = set("void-cape", "ember-wings", null, null, "seraph-halo", null, "void-badge-founder");
                o.addProperty("uuid", mc.player.getUuid().toString());
                CosmeticState.get().applyLauncher(EquippedSet.fromJson(o), null, null);
                sh.selfEmote = "emote-wave";
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0.5;
                o[1] = GROUND;
                o[2] = 0.5;
                // Look all the way round first, so the chunks the orbit will show are built.
                o[3] = tick < 60 ? tick * 6 : 0;
                o[4] = 8;
            }
        };
        s.time = time;
        s.settle = 66;
        s.emoteAt = 70;
        s.frames = 72;
        s.fps = 20;
        return s;
    }

    /** Twenty fully dressed players walking in place, to read the layer's cost off the log. */
    private Scene crowd(String name, long time) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < 20; i++) {
                    final double x0 = (i % 5 - 2) * 2.2, z0 = (i / 5) * 2.4;
                    final int k = i;
                    sh.spawn(mc, i, OUTFITS[i % OUTFITS.length], new ShowcasePlayer.Script() {
                        @Override
                        public void at(int tick, double[] o) {
                            double a = tick * 0.05 + k;
                            o[0] = x0 + Math.cos(a) * 0.6;
                            o[1] = GROUND;
                            o[2] = z0 + Math.sin(a) * 0.6;
                            o[3] = Math.toDegrees(a) + 180;
                            o[4] = 0;
                            o[5] = 0;
                            o[6] = 1;
                            o[7] = 0;
                        }
                    });
                }
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = GROUND + 2.2;
                o[2] = -6.5;
                o[3] = 0;
                o[4] = 14;
            }
        };
        s.time = time;
        s.settle = 160;
        return s;
    }

    private Scene badges(String name, long time) {
        Scene s = new Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                String[] b = {"void-badge-founder", "void-badge-player", "cell-badge", "frost-badge"};
                for (int i = 0; i < 4; i++) {
                    sh.spawn(mc, i, set(null, null, null, null, null, null, b[i]), fixed((i - 1.5) * 1.1, 0, 180, 0, false));
                }
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = GROUND + 0.75;
                o[2] = -4.6;
                o[3] = 0;
                o[4] = -6;
            }
        };
        s.time = time;
        s.settle = 25;
        s.hud = true;
        return s;
    }

    // ----------------------------------------------------------------------------- cast

    ShowcasePlayer spawn(MinecraftClient mc, int index, JsonObject outfit, ShowcasePlayer.Script script) {
        UUID id = UUID.nameUUIDFromBytes(("void-showcase-" + scene.name + "-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // The default skin is Alex when the uuid's hash is odd (DefaultSkinHelper): salt until Steve.
        for (int salt = 0; scene.steve && (id.hashCode() & 1) == 1; salt++) {
            id = UUID.nameUUIDFromBytes(("void-showcase-" + scene.name + "-" + index + "-" + salt).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        GameProfile profile = new GameProfile(id, NAMES[index % NAMES.length]);
        ShowcasePlayer p = new ShowcasePlayer(mc.world, profile, script);
        JsonObject o = new JsonObject();
        o.addProperty("uuid", id.toString());
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : outfit.entrySet()) {
            o.add(e.getKey(), e.getValue());
        }
        CosmeticState.get().putPlayer(EquippedSet.fromJson(o));
        mc.world.addEntity(nextId--, p);
        if (!mc.world.loadedEntities.contains(p)) {
            say("showcase: " + profile.getName() + " was not added to the world (chunk not loaded?)");
        }
        cast.add(p);
        return p;
    }

    private final List<Object[]> pendingEmotes = new ArrayList<Object[]>();

    void emoteLater(ShowcasePlayer p, String id) {
        pendingEmotes.add(new Object[] {p, id});
    }

    private void clearCast(MinecraftClient mc) {
        for (ShowcasePlayer p : cast) {
            mc.world.removeEntity(p);
        }
        cast.clear();
        pendingEmotes.clear();
        CosmeticState.get().clearPlayers();
        CosmeticState.get().applyLauncher(EquippedSet.empty(mc.player == null ? "" : mc.player.getUuid().toString()), null, null);
        Emotes.get().clear();
    }

    // ----------------------------------------------------------------------------- drive

    public void tick(MinecraftClient mc) {
        switch (phase) {
            case 0: // wait for the title screen, then make the world
                if (mc.world == null && mc.currentScreen != null && ++wait > 30) {
                    mc.options.pauseOnLostFocus = false;
                    deleteWorld(new File(new File(mc.runDirectory, "saves"), WORLD));
                    LevelInfo info = new LevelInfo(1L, LevelInfo.GameMode.CREATIVE, false, false, LevelGeneratorType.FLAT);
                    info.setGeneratorOptions("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;");
                    say("showcase: creating world");
                    mc.startIntegratedServer(WORLD, WORLD, info);
                    phase = 1;
                    wait = 0;
                }
                break;
            case 1: // stand at the stage, then wait for its chunks to arrive and the world to settle
                if (mc.world != null && mc.player != null && mc.currentScreen == null) {
                    mc.player.abilities.flying = true;
                    mc.player.refreshPositionAndAngles(0.5, GROUND + 1, 0.5, 0, 10);
                }
                if (mc.world != null && mc.player != null && mc.currentScreen == null && ++wait > 80) {
                    mc.options.hudHidden = true;
                    mc.options.fov = 60f;
                    mc.options.perspective = 0;
                    mc.options.gamma = 0.6f;
                    mc.player.abilities.flying = true;
                    IntegratedServer server = mc.getServer();
                    if (server != null) {
                        for (ServerWorld w : server.worlds) {
                            w.getGameRules().setGameRule("doDaylightCycle", "false");
                            w.getGameRules().setGameRule("doMobSpawning", "false");
                        }
                    }
                    phase = 2;
                    nextScene(mc);
                }
                break;
            case 2:
                if (scene == null) {
                    break;
                }
                sceneTick++;
                place(mc);
                if (scene.ab) {
                    abTick();
                }
                if (sceneTick == scene.emoteAt && selfEmote != null) {
                    say("showcase: local emote " + selfEmote + " started=" + Emotes.get().playLocal(selfEmote));
                    selfEmote = null;
                }
                if (sceneTick == scene.settle - 1) {
                    say(String.format(java.util.Locale.ROOT, "showcase: %s cosmetics layer %.0f us/frame (quality %.2f)",
                            scene.name, dev.voidpvp.client.cosmetic.Budget.averageMicros(),
                            dev.voidpvp.client.cosmetic.Budget.quality()));
                    say("showcase: " + scene.name + " " + dev.voidpvp.client.cosmetic.Budget.percentiles());
                }
                if (sceneTick == 6) {
                    for (Object[] e : pendingEmotes) {
                        ShowcasePlayer p = (ShowcasePlayer) e[0];
                        Emotes.get().startDirect(EquippedSet.normalise(p.getUuid().toString()), (String) e[1], 0);
                    }
                }
                if (sceneTick % 20 == 0) {
                    StringBuilder b = new StringBuilder("showcase: cast");
                    for (ShowcasePlayer p : cast) {
                        b.append(' ').append(p.getGameProfile().getName()).append(" hp=").append(p.getHealth())
                                .append(" death=").append(p.deathTime).append(" sleep=").append(p.isSleeping())
                                .append(" y=").append(String.format(java.util.Locale.ROOT, "%.2f", p.y));
                    }
                    say(b.toString());
                }
                if (sceneTick == scene.settle) {
                    shotFrames = 0;
                    nextShotNanos = 0;
                }
                break;
            default:
                break;
        }
    }

    private void nextScene(MinecraftClient mc) {
        clearCast(mc);
        do {
            sceneIndex++;
        } while (sceneIndex < scenes.size() && !wanted(scenes.get(sceneIndex).name));
        if (sceneIndex >= scenes.size()) {
            scene = null;
            phase = 3;
            say("showcase: done, " + out.getAbsolutePath());
            writer.shutdown();
            try {
                // A busy machine can be well behind on PNG writes; the last scene must not lose its tail.
                writer.awaitTermination(300, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            mc.scheduleStop();
            return;
        }
        scene = scenes.get(sceneIndex);
        sceneTick = 0;
        shotFrames = -1;
        mc.options.hudHidden = !scene.hud;
        dev.voidpvp.client.cosmetic.Reactive.devMotion(scene.motion, scene.reactive);
        setTime(mc, scene.time);
        clearMobs(mc);
        scene.stage(this, mc);
        place(mc);
        say("showcase: scene " + scene.name);
    }

    /**
     * Removes whatever the flat world spawned before mob spawning was switched off (superflat
     * slimes wander into shots), on the server thread.
     */
    private static void clearMobs(MinecraftClient mc) {
        final IntegratedServer server = mc.getServer();
        if (server == null) {
            return;
        }
        server.submit(new Runnable() {
            @Override
            public void run() {
                for (ServerWorld w : server.worlds) {
                    for (Object o : new ArrayList<Object>(w.loadedEntities)) {
                        if (!(o instanceof net.minecraft.entity.player.PlayerEntity)) {
                            ((net.minecraft.entity.Entity) o).remove();
                        }
                    }
                }
            }
        });
    }

    /** Whether a scene runs: every non-style scene by default, else what {@code only} names. */
    private boolean wanted(String name) {
        if (only == null) {
            return !name.startsWith("st-");
        }
        if (only.contains(name)) {
            return true;
        }
        for (String o : only) {
            if (o.endsWith("*") && name.startsWith(o.substring(0, o.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private void setTime(MinecraftClient mc, long time) {
        IntegratedServer server = mc.getServer();
        if (server != null) {
            for (ServerWorld w : server.worlds) {
                w.setTimeOfDay(time);
            }
        }
        if (mc.world != null) {
            mc.world.setTimeOfDay(time);
        }
    }

    private final double[] cam = new double[5];
    private boolean camPlaced;

    private void place(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || scene == null) {
            return;
        }
        scene.camera(sceneTick, cam);
        // A moving camera interpolates from where it was last tick, like a player's view does;
        // a still one has nothing to interpolate.
        double[] was = new double[] {p.x, p.y, p.z, p.yaw, p.pitch};
        boolean moving = camPlaced && sceneTick > 1
                && Math.abs(cam[0] - was[0]) + Math.abs(cam[1] - was[1]) + Math.abs(cam[2] - was[2]) < 1.5;
        p.refreshPositionAndAngles(cam[0], cam[1], cam[2], (float) cam[3], (float) cam[4]);
        p.prevX = moving ? was[0] : p.x;
        p.prevY = moving ? was[1] : p.y;
        p.prevZ = moving ? was[2] : p.z;
        p.prevTickX = p.prevX;
        p.prevTickY = p.prevY;
        p.prevTickZ = p.prevZ;
        p.prevYaw = moving ? (float) was[3] : p.yaw;
        p.prevPitch = moving ? (float) was[4] : p.pitch;
        camPlaced = true;
        p.velocityX = p.velocityY = p.velocityZ = 0;
        p.abilities.flying = true;
    }

    /** End of a rendered frame: capture if the scene is due. */
    public void frame(MinecraftClient mc) {
        if (phase != 2 || scene == null || shotFrames < 0) {
            return;
        }
        long now = System.nanoTime();
        if (now < nextShotNanos) {
            return;
        }
        nextShotNanos = now + 1_000_000_000L / scene.fps;
        String file = scene.frames == 1 ? scene.name + ".png"
                : scene.name + "/" + String.format(java.util.Locale.ROOT, "%03d.png", Integer.valueOf(shotFrames));
        capture(mc, new File(out, file));
        shotFrames++;
        if (shotFrames >= scene.frames) {
            shotFrames = -1;
            nextScene(mc);
        }
    }

    private void capture(MinecraftClient mc, final File file) {
        final int w = mc.width, h = mc.height;
        final ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        writer.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int i = ((h - 1 - y) * w + x) * 4;
                            int r = buf.get(i) & 255, g = buf.get(i + 1) & 255, b = buf.get(i + 2) & 255;
                            img.setRGB(x, y, (r << 16) | (g << 8) | b);
                        }
                    }
                    file.getParentFile().mkdirs();
                    javax.imageio.ImageIO.write(img, "png", file);
                } catch (Exception e) {
                    say("showcase: could not write " + file + ": " + e);
                }
            }
        });
    }

    private static void deleteWorld(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteWorld(f);
                } else if (!f.delete()) {
                    f.deleteOnExit();
                }
            }
        }
        dir.delete();
    }
}
