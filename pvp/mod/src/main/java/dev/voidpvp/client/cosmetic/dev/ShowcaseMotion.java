package dev.voidpvp.client.cosmetic.dev;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

/**
 * The showcase scenes for secondary motion and fight reactions: a scripted fight (a hit taken, a
 * six-hit combo, a kill), a turning-and-landing sequence, sprint-jumps, and a twenty-player brawl
 * for the frame budget. Each fighter's whole timeline is simulated up front, tick by tick, so the
 * mannequins move like players (knockback with a hop, chasing the opponent, strafing) and the
 * reactions are driven by exactly what a real fight gives the client: swing animations, hurt
 * flashes and a death.
 */
final class ShowcaseMotion {

    private static final double G = Showcase.GROUND;

    private ShowcaseMotion() {
    }

    /** One fighter's timeline: per tick x, y, z, yaw, pitch, swing, hurt, dead. */
    static final class Track implements ShowcasePlayer.Script {
        final double[] x, y, z, yaw, pitch;
        final boolean[] swing, hurt, dead, ground;

        Track(int n) {
            x = new double[n];
            y = new double[n];
            z = new double[n];
            yaw = new double[n];
            pitch = new double[n];
            swing = new boolean[n];
            hurt = new boolean[n];
            dead = new boolean[n];
            ground = new boolean[n];
        }

        @Override
        public void at(int tick, double[] o) {
            int t = Math.max(0, Math.min(x.length - 1, tick));
            o[0] = x[t];
            o[1] = y[t];
            o[2] = z[t];
            o[3] = yaw[t];
            o[4] = pitch[t];
            o[5] = 0;
            o[6] = ground[t] ? 1 : 0;
            o[7] = 0;
            o[8] = swing[t] && tick == t ? 1 : 0;
            o[9] = hurt[t] && tick == t ? 1 : 0;
            o[10] = dead[t] ? 1 : 0;
        }
    }

    /** Yaw (Minecraft degrees) looking from (x0, z0) toward (x1, z1). */
    static double yawTo(double x0, double z0, double x1, double z1) {
        return Math.toDegrees(Math.atan2(-(x1 - x0), z1 - z0));
    }

    /**
     * A duel: B lands one hit on A, then A strings a combo of {@code combo} hits on B, and the last
     * one kills. A is the hero (the camera sits behind A's shoulder).
     */
    static Track[] duel(int n, int start, int combo, int gap) {
        Track a = new Track(n), b = new Track(n);
        double ax = -1.5, bx = 1.5, avx = 0, bvx = 0;
        double ay = G, by = G, avy = 0, bvy = 0;
        boolean bDead = false;
        int bHits = 0;
        int[] aHitsAt = new int[combo];
        for (int k = 0; k < combo; k++) {
            aHitsAt[k] = start + 22 + k * gap;
        }
        int bHitAt = start;
        for (int t = 0; t < n; t++) {
            // Circle-strafing a little, as players do between exchanges.
            double az = 0.45 * Math.sin(t * 0.075), bz = 0.45 * Math.sin(t * 0.075 + 2.2);
            boolean aSwing = false, bSwing = false, aHurt = false, bHurt = false;
            if (t == bHitAt) {
                bSwing = true;
            }
            if (t == bHitAt + 1) {
                aHurt = true;
                avx = -0.36;
                avy = 0.3;
            }
            for (int k = 0; k < combo; k++) {
                if (t == aHitsAt[k]) {
                    aSwing = true;
                }
                if (t == aHitsAt[k] + 1 && !bDead) {
                    bHurt = true;
                    bHits++;
                    bvx = 0.34;
                    bvy = k == combo - 1 ? 0.3 : 0.32;
                    if (k == combo - 1) {
                        bDead = true;
                    }
                }
            }
            // A few idle swings from A while closing in: not every swing is a hit.
            if (t == start + 14) {
                aSwing = true;
            }
            // A chases to keep about 2.7 blocks, B stays roughly in place when not knocked back.
            if (!bDead || t < aHitsAt[combo - 1] + 6) {
                double want = (bx - ax) - 2.7;
                ax += Math.max(-0.22, Math.min(0.26, want * 0.35));
            }
            ax += avx;
            bx += bvx;
            avx *= 0.55;
            bvx *= 0.55;
            ay += avy;
            by += bvy;
            avy -= 0.08;
            bvy -= 0.08;
            if (ay <= G) {
                ay = G;
                avy = 0;
            }
            if (by <= G) {
                by = G;
                bvy = 0;
            }
            a.x[t] = ax;
            a.y[t] = ay;
            a.z[t] = az;
            b.x[t] = bx;
            b.y[t] = by;
            b.z[t] = bz;
            a.yaw[t] = yawTo(ax, az, bx, bz);
            b.yaw[t] = yawTo(bx, bz, ax, az);
            a.pitch[t] = 6;
            b.pitch[t] = 6;
            a.ground[t] = ay <= G + 1e-6;
            b.ground[t] = by <= G + 1e-6;
            a.swing[t] = aSwing;
            b.swing[t] = bSwing;
            a.hurt[t] = aHurt;
            b.hurt[t] = bHurt;
            // The killing blow lands (a hurt flash, the sixth of the combo), then the fall.
            b.dead[t] = bDead && t > aHitsAt[combo - 1] + 1;
        }
        return new Track[] {a, b};
    }

    static Showcase.Scene fight(String name, long time, final boolean motion) {
        final int start = 56;
        final Track[] duel = duel(260, start, 6, 12);
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                sh.spawn(mc, 0, Showcase.set("void-cape", "void-wings", null, null, "void-halo", "spark-trail", null), duel[0]);
                sh.spawn(mc, 1, Showcase.set("dusk-cape", "ember-wings", null, null, "seraph-halo", "frost-trail", null), duel[1]);
            }

            @Override
            void camera(int tick, double[] o) {
                // Over A's shoulder, three-quarters behind, following A (smoothed: a camera that
                // jerked with every knockback would hide the motion it is there to show).
                double ax = 0, bx = 0;
                int k = 0;
                for (int d = -6; d <= 6; d++) {
                    int t = Math.max(0, Math.min(duel[0].x.length - 1, tick + d));
                    ax += duel[0].x[t];
                    bx += duel[1].x[t];
                    k++;
                }
                ax /= k;
                bx /= k;
                double fx = ax + (bx - ax) * 0.35;
                o[0] = ax - 1.9;
                o[1] = G + 1.0;
                o[2] = -2.3;
                o[3] = yawTo(o[0], o[2], fx, 0.3);
                o[4] = 11;
            }
        };
        s.time = time;
        s.settle = 44;
        s.frames = 168;
        s.fps = 24;
        s.motion = motion;
        s.reactive = motion ? 1f : 0f;
        return s;
    }

    /** One turner: snap turns, strafe reversals, a jump, a high jump and its landing. */
    static Track turner(int n, double x0, double z0, double yaw0) {
        Track a = new Track(n);
        double x = x0, y = G, vy = 0, yaw = yaw0, vx = 0;
        for (int t = 0; t < n; t++) {
            double target = yaw0;
            if (t >= 50 && t < 78) {
                target = yaw0 + 130;
            } else if (t >= 78 && t < 104) {
                target = yaw0 - 40;
            }
            // Snap turns: about 45 degrees a tick, a mouse flick.
            double d = target - yaw;
            yaw += Math.max(-48, Math.min(48, d));
            double want = 0;
            if (t >= 108 && t < 148) {
                // Strafe reversals every 10 ticks, at strafe speed, with ground friction's ease.
                want = ((t - 108) / 10 % 2 == 0 ? 1 : -1) * 0.21;
            }
            vx += (want - vx) * 0.45;
            x += vx;
            if (t == 150) {
                vy = 0.42;
            }
            if (t == 172) {
                vy = 0.78;
            }
            y += vy;
            vy -= 0.08;
            vy *= 0.98;
            if (y <= G) {
                y = G;
                vy = 0;
            }
            a.x[t] = x;
            a.y[t] = y;
            a.z[t] = z0;
            a.yaw[t] = yaw;
            a.pitch[t] = 4;
            a.ground[t] = y <= G + 1e-6;
        }
        return a;
    }

    static Showcase.Scene turns(String name, long time, final boolean motion) {
        final Track a = turner(240, -1.0, 0, 180);
        final Track b = turner(240, 1.2, 0.6, 180);
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                sh.spawn(mc, 0, Showcase.set("void-cape", "void-wings", null, null, "void-halo", null, null), a);
                sh.spawn(mc, 1, Showcase.set("ring-cape", null, "dapper-top-hat", null, "seraph-halo", null, null), b);
            }

            @Override
            void camera(int tick, double[] o) {
                // Three-quarters behind, close; it rises with the high jump so it stays in frame.
                double lift = 0;
                for (int d = -5; d <= 5; d++) {
                    int t = Math.max(0, Math.min(a.y.length - 1, tick + d));
                    lift += a.y[t] - G;
                }
                lift /= 11;
                o[0] = 1.0;
                o[1] = G + 1.05 + lift * 0.7;
                o[2] = 3.3;
                o[3] = 166;
                o[4] = 8;
            }
        };
        s.time = time;
        s.settle = 44;
        s.frames = 156;
        s.fps = 24;
        s.motion = motion;
        s.reactive = motion ? 1f : 0f;
        return s;
    }

    /** Three runners in parallel lanes, sprint-jumping, the camera tracking them side-on. */
    static Showcase.Scene sprintJump(String name, long time) {
        final int n = 220;
        final Track[] lanes = new Track[3];
        final String[][] outfits = {
            {"void-cape", "void-wings", null, null, "void-halo", "spark-trail"},
            {"dusk-cape", "ember-wings", null, null, "seraph-halo", "ember-trail"},
            {"cell-cape", null, "monarch-crown", null, null, "frost-trail"},
        };
        for (int k = 0; k < 3; k++) {
            Track a = new Track(n);
            double x = -14 - k * 2.2, y = G, vy = 0;
            for (int t = 0; t < n; t++) {
                x += t > 30 ? 0.28 : 0;
                if (t > 40 && (t - 40 - k * 3) % 13 == 0) {
                    if (y <= G + 1e-6) {
                        vy = 0.42;
                    }
                }
                y += vy;
                vy -= 0.08;
                vy *= 0.98;
                if (y <= G) {
                    y = G;
                    vy = 0;
                }
                a.x[t] = x;
                a.y[t] = y;
                a.z[t] = k * 1.8;
                a.yaw[t] = -90;
                a.pitch[t] = 4;
                a.ground[t] = y <= G + 1e-6;
            }
            lanes[k] = a;
        }
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int k = 0; k < 3; k++) {
                    String[] o = outfits[k];
                    sh.spawn(mc, k, Showcase.set(o[0], o[1], o[2], o[3], o[4], o[5], null), lanes[k]);
                }
            }

            @Override
            void camera(int tick, double[] o) {
                int t = Math.max(0, Math.min(n - 1, tick));
                // From the side and a little behind the echelon, keeping pace with it.
                double mx = lanes[1].x[t];
                o[0] = mx - 0.6;
                o[1] = G + 1.3;
                o[2] = -4.6;
                o[3] = yawTo(o[0], o[2], mx, 1.8);
                o[4] = 9;
            }
        };
        s.time = time;
        s.settle = 50;
        s.frames = 120;
        s.fps = 24;
        return s;
    }

    /** Twenty players in ten duels, all swinging, hurting and comboing: the budget's worst case. */
    static Showcase.Scene brawl(String name, long time) {
        Showcase.Scene s = new Showcase.Scene(name) {
            @Override
            void stage(Showcase sh, MinecraftClient mc) {
                for (int pair = 0; pair < 10; pair++) {
                    final double ox = (pair % 5 - 2) * 3.2, oz = (pair / 5) * 3.4;
                    // Staggered so hits land all through the measuring window, combos looping.
                    final Track[] d = duel(400, 20 + pair * 7, 6, 10);
                    for (int side = 0; side < 2; side++) {
                        final Track src = d[side];
                        final int phase = pair * 5;
                        JsonObject outfit = Showcase.OUTFITS[(pair * 2 + side) % Showcase.OUTFITS.length];
                        sh.spawn(mc, pair * 2 + side, outfit, new ShowcasePlayer.Script() {
                            @Override
                            public void at(int tick, double[] o) {
                                // Loop the duel (minus the death) so the brawl never runs out.
                                int t = 20 + (tick + phase) % 120;
                                src.at(t, o);
                                o[0] = ox + (o[0] * 0.5);
                                o[2] = oz + o[2] * 0.5;
                                o[8] = src.swing[t] ? 1 : 0;
                                o[9] = src.hurt[t] ? 1 : 0;
                                o[10] = 0;
                            }
                        });
                    }
                }
            }

            @Override
            void camera(int tick, double[] o) {
                o[0] = 0;
                o[1] = G + 2.2;
                o[2] = -6.5;
                o[3] = 0;
                o[4] = 14;
            }
        };
        s.time = time;
        s.settle = 160;
        return s;
    }
}
