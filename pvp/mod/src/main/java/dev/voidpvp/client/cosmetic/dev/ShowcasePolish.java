package dev.voidpvp.client.cosmetic.dev;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * The polish views (pvp/design/cosmetics-shots/compare/): every worn item alone on a Steve
 * mannequin, staged identically in a fixed set of views so a run before an art change and a run
 * after it can be laid side by side item by item ({@code make-compare.sh}). Scenes are named
 * {@code pv-<id>-<view>} — {@code day}, {@code night}, {@code far} and {@code farnight} (at ten
 * blocks), {@code close}, {@code side}, and for head items {@code back} — plus the runs, trails
 * and badges below. They never run in the default showcase, only when asked for
 * ({@code -Pvoid.showcase.only=pv-*}, or {@code pv-cloister-hood-*} for one item's views).
 */
final class ShowcasePolish {

    private static final double G = Showcase.GROUND;
    private static final long DAY = 6000, NIGHT = 18000;

    /** The cape outfits of the six sets, each with its set's trail, for the run clips. */
    private static final String[][] CAPE_RUN = {
        {"atelier-cape", "amethyst-trail"}, {"regent-cape", "gilt-trail"}, {"nocturne-cape", "topaz-trail"},
        {"meridian-cape", "jade-trail"}, {"bespoke-cape", null}, {"origin-cape", null},
    };

    private ShowcasePolish() {
    }

    static void add(List<Showcase.Scene> scenes) {
        for (String[] it : Showcase.ITEMS) {
            String slot = it[0], id = it[1];
            boolean back = "cape".equals(slot) || "back".equals(slot);
            scenes.add(view("pv-" + id + "-day", slot, id, DAY, back ? "back34" : "front34"));
            scenes.add(view("pv-" + id + "-night", slot, id, NIGHT, back ? "back34" : "front34"));
            scenes.add(view("pv-" + id + "-far", slot, id, DAY, "far"));
            scenes.add(view("pv-" + id + "-farnight", slot, id, NIGHT, "far"));
            scenes.add(view("pv-" + id + "-close", slot, id, DAY, "close"));
            scenes.add(view("pv-" + id + "-side", slot, id, DAY, "side"));
            if (!back) {
                scenes.add(view("pv-" + id + "-back", slot, id, DAY, "behind"));
            }
        }
        scenes.add(runCapes("pv-run-capes", DAY));
        scenes.add(runCapes("pv-run-capes-night", NIGHT));
        scenes.add(trails("pv-trails", DAY));
        scenes.add(trails("pv-trails-night", NIGHT));
        scenes.add(emotes("pv-emotes-a", new String[] {"emote-wave", "emote-clap", "emote-bow"}));
        scenes.add(emotes("pv-emotes-b", new String[] {"emote-salute", "emote-shrug", "emote-sit"}));
        scenes.add(badges("pv-badges-close", 2.3));
        scenes.add(badges("pv-badges-far", 7.0));
    }

    /**
     * One item alone, one view. The wearer stands at the origin facing -z (turned for the
     * three-quarter views); the camera moves round them.
     */
    private static Showcase.Scene view(String name, final String slot, final String id, long time, final String v) {
        final boolean backItem = "cape".equals(slot) || "back".equals(slot);
        final boolean halo = "halo".equals(slot);
        final boolean wide = "back".equals(slot);
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                JsonObject o = Showcase.set(null, null, null, null, null, null, null);
                o.addProperty(slot, id);
                sh.spawn(mc, 0, o, new ShowcasePlayer.Script() {
                    @Override
                    public void at(int tick, double[] p) {
                        float yaw;
                        if ("side".equals(v) || "behind".equals(v)) {
                            yaw = 180;
                        } else if ("far".equals(v)) {
                            yaw = backItem ? 194 : 200;
                        } else {
                            yaw = backItem ? 160 : 205;
                        }
                        p[0] = 0;
                        p[1] = G;
                        p[2] = 0;
                        p[3] = yaw;
                        p[4] = backItem ? 0 : 6;
                        p[5] = backItem || "side".equals(v) ? 0 : Math.sin(tick * 0.04) * 5;
                        p[6] = 1;
                        p[7] = 0;
                    }
                });
            }

            @Override
            void camera(int tick, double[] o) {
                if (backItem) {
                    if ("far".equals(v)) {
                        cam(o, 0, G + 1.1, 10.0, 180, 4);
                    } else if ("close".equals(v)) {
                        cam(o, 0.25, G - 0.12, wide ? 2.1 : 1.55, 172, 11);
                    } else if ("side".equals(v)) {
                        cam(o, wide ? 2.7 : 2.1, G + 0.1, wide ? 0.8 : 0.6, wide ? 107 : 101, 6);
                    } else {
                        cam(o, 0.35, G + 0.1, wide ? 3.0 : 2.4, 172, 8);
                    }
                } else {
                    if ("far".equals(v)) {
                        cam(o, 0, G + 0.2, -10.0, 0, 2);
                    } else if ("close".equals(v)) {
                        cam(o, -0.15, G + (halo ? 0.5 : 0.32), -1.1, -8, halo ? 22 : 8);
                    } else if ("side".equals(v)) {
                        cam(o, 1.5, G + (halo ? 0.5 : 0.3), -0.05, 90, halo ? 14 : 6);
                    } else if ("behind".equals(v)) {
                        cam(o, 0.55, G + (halo ? 0.6 : 0.35), 1.45, 159, halo ? 22 : 10);
                    } else {
                        cam(o, -0.25, G + (halo ? 0.62 : 0.4), -1.55, -10, halo ? 22 : 9);
                    }
                }
            }
        };
        s.time = time;
        s.settle = 40;
        s.steve = true;
        return s;
    }

    private static void cam(double[] o, double x, double y, double z, double yaw, double pitch) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
        o[3] = yaw;
        o[4] = pitch;
    }

    /** The six capes running round a circle, each with its set's trail, the camera high. */
    private static Showcase.Scene runCapes(String name, long time) {
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < CAPE_RUN.length; i++) {
                    final int k = i;
                    final double phase0 = i * Math.PI / 3;
                    final double r = 3.0;
                    final double w = 0.07;
                    sh.spawn(mc, k, Showcase.set(CAPE_RUN[i][0], null, null, null, null, CAPE_RUN[i][1], null),
                            new ShowcasePlayer.Script() {
                                @Override
                                public void at(int tick, double[] o) {
                                    double a = phase0 + tick * w;
                                    o[0] = Math.cos(a) * r;
                                    o[1] = G;
                                    o[2] = Math.sin(a) * r;
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
                cam(o, 0, G + 2.6, -6.4, 0, 22);
            }
        };
        s.time = time;
        s.settle = 60;
        s.frames = 60;
        s.fps = 30;
        s.steve = true;
        return s;
    }

    /**
     * The four trails, one runner each, sprinting across the frame in lanes and back, seen from
     * the side at five to eight blocks: how a trail reads behind someone in a fight.
     */
    private static Showcase.Scene trails(String name, long time) {
        final String[] ids = {"amethyst-trail", "gilt-trail", "topaz-trail", "jade-trail"};
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < ids.length; i++) {
                    final int k = i;
                    final double z = -0.6 + i * 1.3;
                    sh.spawn(mc, k, Showcase.set(null, null, null, null, null, ids[i], null), new ShowcasePlayer.Script() {
                        @Override
                        public void at(int tick, double[] o) {
                            // Sprint speed (0.28 blocks a tick) along x, turning at the ends.
                            double period = 70, t = (tick + k * 9) % period;
                            double half = period / 2, span = 9.0;
                            boolean out = t < half;
                            double u = out ? t / half : (t - half) / half;
                            double x = out ? -span / 2 + u * span : span / 2 - u * span;
                            o[0] = x;
                            o[1] = G;
                            o[2] = z;
                            o[3] = out ? 270 : 90;
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
                cam(o, 0, G + 1.4, -6.6, 0, 14);
            }
        };
        s.time = time;
        s.settle = 50;
        s.frames = 48;
        s.fps = 24;
        s.steve = true;
        return s;
    }

    /**
     * Three emotes side by side, close, captured from the moment they start (the default
     * showcase's emote scene begins capturing after its settle, so it shows only their tails).
     */
    private static Showcase.Scene emotes(String name, final String[] ids) {
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int i = 0; i < ids.length; i++) {
                    final double x = (i - 1) * 1.7;
                    ShowcasePlayer p = sh.spawn(mc, i, Showcase.set(null, null, null, null, null, null, null), new ShowcasePlayer.Script() {
                        @Override
                        public void at(int tick, double[] o) {
                            o[0] = x;
                            o[1] = G;
                            o[2] = 0;
                            o[3] = 180 - x * 6;
                            o[4] = 0;
                            o[5] = 0;
                            o[6] = 1;
                            o[7] = 0;
                        }
                    });
                    sh.emoteLater(p, ids[i]);
                }
            }

            @Override
            void camera(int tick, double[] o) {
                cam(o, 0, G + 0.35, -4.4, 0, 6);
            }
        };
        s.time = DAY;
        s.settle = 6;
        s.frames = 76;
        s.fps = 20;
        s.steve = true;
        return s;
    }

    /** The four badges beside their nametags, seen from {@code dist} blocks, HUD on. */
    private static Showcase.Scene badges(String name, final double dist) {
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                String[] b = {"void-badge-founder", "void-badge-player", "atelier-badge", "laurel-badge"};
                for (int i = 0; i < b.length; i++) {
                    final double x = (i - 1.5) * (dist < 4 ? 0.9 : 1.1);
                    sh.spawn(mc, i, Showcase.set(null, null, null, null, null, null, b[i]), new ShowcasePlayer.Script() {
                        @Override
                        public void at(int tick, double[] o) {
                            o[0] = x;
                            o[1] = G;
                            o[2] = 0;
                            o[3] = 180;
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
                cam(o, 0, G + (dist < 4 ? 0.6 : 0.75), -dist, 0, dist < 4 ? -4 : -3);
            }
        };
        s.time = DAY;
        s.settle = 30;
        s.hud = true;
        return s;
    }
}
