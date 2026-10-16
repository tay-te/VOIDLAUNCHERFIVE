package dev.voidpvp.client.cosmetic.dev;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * The cosmetics style test (pvp/design/cosmetics-style-test/): the same cape, back item and hat in
 * three sets — the shipped VOID luxe Atelier pieces ({@code lux}, the test's C taken to the final
 * bar), the test's C (VOID luxe) and D (collectible toy) — staged identically so each view can be
 * laid side by side in three columns. Scenes are named {@code st-<view>-<set>};
 * they never run in the default showcase, only when asked for ({@code -Pvoid.showcase.only=st-*}).
 *
 * <p>Every mannequin here wears the default Steve skin (the showcase picks a uuid that gives it),
 * so the only thing that differs between columns is the set.</p>
 */
final class ShowcaseStyle {

    private static final double G = Showcase.GROUND;

    /** The sets: name, cape, back, head. */
    static final String[][] SETS = {
        {"lux", "atelier-cape", "blade-wings", "atelier-fedora"},
        {"c", "style-c-cape", "style-c-wings", "style-c-hat"},
        {"d", "style-d-cape", "style-d-wings", "style-d-hat"},
    };

    private ShowcaseStyle() {
    }

    static JsonObject outfit(String[] set, boolean cape, boolean back, boolean head) {
        return Showcase.set(cape ? set[1] : null, back ? set[2] : null, head ? set[3] : null, null, null, null, null);
    }

    static void add(List<Showcase.Scene> scenes) {
        for (String[] set : SETS) {
            String s = set[0];
            // One wearer in the full set, framed so a column is the middle 640 px of the frame.
            scenes.add(single("st-front-day-" + s, set, 6000, false, 4.2));
            scenes.add(single("st-back-day-" + s, set, 6000, true, 4.2));
            scenes.add(single("st-front-night-" + s, set, 18000, false, 4.2));
            scenes.add(single("st-back-night-" + s, set, 18000, true, 4.2));
            // At ten blocks: the distance an item has to read at in a fight.
            scenes.add(single("st-far-front-day-" + s, set, 6000, false, 10));
            scenes.add(single("st-far-back-day-" + s, set, 6000, true, 10));
            scenes.add(single("st-far-front-night-" + s, set, 18000, false, 10));
            scenes.add(single("st-far-back-night-" + s, set, 18000, true, 10));
            // Close-ups, each item alone.
            scenes.add(close("st-close-cape-" + s, outfit(set, true, false, false), 6000, true));
            scenes.add(close("st-close-back-" + s, outfit(set, false, true, false), 6000, true));
            scenes.add(close("st-close-head-" + s, outfit(set, false, false, true), 6000, false));
            scenes.add(close("st-close-back-night-" + s, outfit(set, true, true, true), 18000, true));
            scenes.add(run("st-run-" + s, set));
            scenes.add(fight("st-fight-" + s, set));
        }
        // The three sets in one shot, same light, same ground.
        scenes.add(all("st-all-front-day", 6000, false));
        scenes.add(all("st-all-back-day", 6000, true));
        scenes.add(all("st-all-front-night", 18000, false));
        scenes.add(all("st-all-back-night", 18000, true));
    }

    /** One wearer at the origin, three-quarter turned a touch, seen from {@code dist} blocks. */
    private static Showcase.Scene single(String name, final String[] set, long time, final boolean back, final double dist) {
        Showcase.Scene sc = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                // Everyone faces -z; the camera moves round them (front from -z, back from +z).
                final float yaw = 180;
                sh.spawn(mc, 0, outfit(set, true, true, true), new ShowcasePlayer.Script() {
                    @Override
                    public void at(int tick, double[] o) {
                        o[0] = 0;
                        o[1] = G;
                        o[2] = 0;
                        o[3] = yaw + 14;
                        o[4] = 0;
                        o[5] = Math.sin(tick * 0.03) * 8;
                        o[6] = 1;
                        o[7] = 0;
                    }
                });
            }

            @Override
            void camera(int tick, double[] o) {
                // Back views look from +z toward -z (yaw 180); front views from -z (yaw 0).
                o[0] = 0;
                o[1] = G + (dist > 6 ? 1.1 : 0.55);
                o[2] = back ? dist : -dist;
                o[3] = back ? 180 : 0;
                o[4] = dist > 6 ? 4 : 3;
            }
        };
        sc.time = time;
        sc.steve = true;
        return sc;
    }

    /** One wearer close: three-quarters behind for a cape or back item, three-quarters in front for a hat. */
    private static Showcase.Scene close(String name, final JsonObject outfit, long time, final boolean back) {
        Showcase.Scene sc = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                sh.spawn(mc, 0, outfit, new ShowcasePlayer.Script() {
                    @Override
                    public void at(int tick, double[] o) {
                        o[0] = 0;
                        o[1] = G;
                        o[2] = 0;
                        o[3] = back ? 160 : 200;
                        o[4] = back ? 0 : 4;
                        o[5] = back ? 0 : Math.sin(tick * 0.04) * 6;
                        o[6] = 1;
                        o[7] = 0;
                    }
                });
            }

            @Override
            void camera(int tick, double[] o) {
                if (back) {
                    o[0] = 0.35;
                    o[1] = G + 0.05;
                    o[2] = 2.5;
                    o[3] = 172;
                    o[4] = 7;
                } else {
                    o[0] = -0.3;
                    o[1] = G + 0.32;
                    o[2] = -1.75;
                    o[3] = -10;
                    o[4] = 7;
                }
            }
        };
        sc.time = time;
        sc.settle = 45;
        sc.steve = true;
        return sc;
    }

    /** The three sets abreast. */
    private static Showcase.Scene all(String name, long time, final boolean back) {
        Showcase.Scene sc = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < SETS.length; i++) {
                    final double x = (back ? i - 1 : 1 - i) * 3.6;
                    final float yaw = 180;
                    final int k = i;
                    sh.spawn(mc, i, outfit(SETS[i], true, true, true), new ShowcasePlayer.Script() {
                        @Override
                        public void at(int tick, double[] o) {
                            o[0] = x;
                            o[1] = G;
                            o[2] = 0;
                            o[3] = yaw + (k - 1) * 8;
                            o[4] = 0;
                            o[5] = Math.sin(tick * 0.03 + k) * 8;
                            o[6] = 1;
                            o[7] = 0;
                        }
                    });
                }
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = G + 0.9;
                o[2] = back ? 8.6 : -8.6;
                o[3] = back ? 180 : 0;
                o[4] = 3;
            }
        };
        sc.time = time;
        sc.steve = true;
        return sc;
    }

    /**
     * Running and turning: a sprint round a circle, a snap reversal, two sprint-jumps, a stop. The
     * camera stands off, high, and watches the whole loop.
     */
    private static Showcase.Scene run(String name, final String[] set) {
        final int n = 260;
        final ShowcaseMotion.Track a = new ShowcaseMotion.Track(n);
        double ang = Math.PI, dir = 1, y = G, vy = 0, yaw = 0;
        final double r = 2.2, speed = 0.27;
        for (int t = 0; t < n; t++) {
            double v = speed;
            if (t < 20) {
                v = speed * t / 20.0;
            }
            if (t >= 96 && t < 104) {
                v = 0.05; // plant and turn
            }
            if (t == 100) {
                dir = -dir;
            }
            if (t >= 200) {
                v = Math.max(0, speed * (1 - (t - 200) / 8.0));
            }
            ang += dir * v / r;
            if (t == 140 || t == 160) {
                vy = 0.42;
            }
            y += vy;
            vy -= 0.08;
            vy *= 0.98;
            if (y <= G) {
                y = G;
                vy = 0;
            }
            double x = Math.cos(ang) * r, z = Math.sin(ang) * r + 0.6;
            // Facing along the direction of travel; a reversal snaps round like a mouse flick.
            double vx = -Math.sin(ang) * dir, vz = Math.cos(ang) * dir;
            double want = Math.toDegrees(Math.atan2(-vx, vz));
            double d = ((want - yaw) % 360 + 540) % 360 - 180;
            yaw += t == 0 ? d : Math.max(-50, Math.min(50, d));
            a.x[t] = x;
            a.y[t] = y;
            a.z[t] = z;
            a.yaw[t] = yaw;
            a.pitch[t] = 4;
            a.ground[t] = y <= G + 1e-6;
        }
        Showcase.Scene sc = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                sh.spawn(mc, 0, outfit(set, true, true, true), a);
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = G + 2.3;
                o[2] = -6.2;
                o[3] = 0;
                o[4] = 19;
            }
        };
        sc.time = 6000;
        sc.settle = 30;
        sc.frames = 216;
        sc.fps = 24;
        sc.steve = true;
        return sc;
    }

    /** The scripted duel (a hit taken, a six-hit combo, a kill), both fighters in the set. */
    private static Showcase.Scene fight(String name, final String[] set) {
        final Showcase.Scene base = ShowcaseMotion.fight(name, 6000, true);
        final ShowcaseMotion.Track[] duel = ShowcaseMotion.duel(260, 56, 6, 12);
        Showcase.Scene sc = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                sh.spawn(mc, 0, outfit(set, true, true, true), duel[0]);
                sh.spawn(mc, 1, outfit(set, true, true, true), duel[1]);
            }

            @Override
            void camera(int tick, double[] o) {
                base.camera(tick, o);
            }
        };
        sc.time = base.time;
        sc.settle = base.settle;
        sc.frames = base.frames;
        sc.fps = base.fps;
        sc.motion = true;
        sc.reactive = 1f;
        sc.steve = true;
        return sc;
    }
}
