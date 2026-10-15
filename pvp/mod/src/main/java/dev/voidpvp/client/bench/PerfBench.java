package dev.voidpvp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.perf.OcclusionCuller;
import dev.voidpvp.client.perf.PerfConfig;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.SurvivalInventoryScreen;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

/**
 * The FPS-boost benchmark, and the visual mods' screenshot tour — a dev harness, inert unless the
 * JVM is started with {@code -Dvoid.bench=fps} or {@code -Dvoid.bench=shots}
 * ({@code ./gradlew runBench}, see {@code build.gradle}).
 *
 * <h2>The scene</h2>
 * <p>A fresh superflat creative world, the same every run: a 9×9 grid of hollow stone-brick
 * houses on 12-block centres with streets between them. Each house holds two chests, a sign, a
 * torch, an armour stand and two villagers (no AI, so nothing moves), has a glass window in each
 * wall and a three-line hologram (three invisible named armour stands) over the roof; each street
 * crossing has one more villager. That is 162 chests, 81 signs, 81 torches, 243 villagers and 324
 * armour stands — a lobby or a Bedwars town, which is the case the culling mod exists for.</p>
 *
 * <h2>The run ({@code fps})</h2>
 * <p>The camera flies a fixed loop through the streets at eye height, at 5 blocks a second with a
 * slow sideways sweep, positioned from wall-clock time inside each phase — so every phase sees the
 * same path segment. Phases alternate <b>boost off / on / off / on</b>, 15 s each after an 8 s
 * warm-up, so thermal or JIT drift cannot favour one side. Off is
 * {@code PerfConfig.benchForceOff}, which disables culling, the particle limiter and the frame
 * limiter together; vsync is off and the frame cap is the slider's unlimited end throughout.
 * Every frame's duration is recorded; the result is average FPS and 1% low (the FPS of the
 * 99th-percentile frame time) per side, written to the log and to
 * {@code run/void-bench.json}, and the game exits.</p>
 *
 * <h2>The tour ({@code shots})</h2>
 * <p>The same scene, then a fixed list of poses and settings, one screenshot each into
 * {@code run/screenshots/}, each visual mod shown off and on.</p>
 */
public final class PerfBench {

    private static final String MODE = System.getProperty("void.bench", "");
    private static final boolean ENABLED = !MODE.isEmpty();
    private static final boolean SHOTS = "shots".equals(MODE);
    private static final int PHASE_SECONDS = Integer.getInteger("void.bench.seconds", 15);
    private static final int WARMUP_SECONDS = 8;

    private static final int GRID = 4;
    private static final int SPACING = 12;
    private static final int FLOOR = 4;

    private static int state;
    private static int waitTicks;
    private static Future<?> pending;

    // -- fps run --
    private static int phase = -1;
    private static long phaseStart;
    private static long lastFrame;
    private static float[] frames = new float[60000];
    private static int frameCount;
    private static final List<double[]> results = new ArrayList<double[]>();
    private static final List<Integer> hiddenCounts = new ArrayList<Integer>();

    // -- shots --
    private static int shot;
    private static int shotWait;

    private PerfBench() {
    }

    /** Whether a bench is running — the frame limiter stands aside so the numbers are honest. */
    public static boolean active() {
        return ENABLED;
    }

    /** Once a client tick, from {@code VoidClient.tickWave7}. */
    public static void tick(MinecraftClient mc) {
        if (!ENABLED) {
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            VoidLog.warn("bench: failed, stopping: " + t);
            t.printStackTrace();
            mc.scheduleStop();
        }
    }

    private static void step(MinecraftClient mc) {
        switch (state) {
            case 0: // wait for the title screen, then create the world
                if (mc.world != null || mc.currentScreen == null || ++waitTicks < 60) {
                    return;
                }
                mc.options.vsync = false;
                mc.options.maxFramerate = 260;
                mc.options.pauseOnLostFocus = false;
                mc.options.viewDistance = 8;
                mc.options.perspective = 0;
                LevelInfo info = new LevelInfo(0x564F4944L, LevelInfo.GameMode.CREATIVE, false,
                        false, LevelGeneratorType.FLAT).setGeneratorOptions("2;7,2x3,2;1;")
                        .enableCommands();
                String name = "void-bench-" + System.currentTimeMillis();
                VoidLog.info("bench: creating world " + name);
                mc.startIntegratedServer(name, name, info);
                state = 1;
                waitTicks = 0;
                return;
            case 1: // wait for the player, then build the town on the server thread
                if (mc.world == null || mc.player == null || mc.getServer() == null) {
                    return;
                }
                if (++waitTicks < 40) {
                    return;
                }
                final IntegratedServer server = mc.getServer();
                pending = server.submit(new Runnable() {
                    @Override
                    public void run() {
                        buildTown(server.getWorld(0));
                    }
                });
                state = 2;
                waitTicks = 0;
                return;
            case 2: // wait for the build and for the chunks to arrive and mesh
                if (!pending.isDone()) {
                    return;
                }
                mc.player.abilities.flying = true;
                mc.player.noClip = true;
                if (++waitTicks < 200) {
                    return;
                }
                VoidLog.info("bench: scene ready (" + mc.world.entities.size() + " entities, "
                        + mc.world.blockEntities.size() + " block entities)");
                state = SHOTS ? 10 : 3;
                phase = -1;
                return;
            case 3: // the fps run is driven per frame; ticks only watch for the end
                return;
            case 10:
                shotTick(mc);
                return;
            default:
                return;
        }
    }

    /** Once a frame, from {@code BenchFrameMixin} at the head of the game loop. */
    public static void frame() {
        if (!ENABLED) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (state == 3) {
            fpsFrame(mc);
        } else if (state == 10) {
            shotFrame(mc);
        }
    }

    // ---------------------------------------------------------------------------------------
    // fps
    // ---------------------------------------------------------------------------------------

    private static void fpsFrame(MinecraftClient mc) {
        long now = System.nanoTime();
        if (mc.player == null) {
            return;
        }
        if (phase < 0) {
            beginPhase(mc, 0, now);
        }
        double t = (now - phaseStart) / 1e9;
        // Record the frame that just finished (the gap between two loop heads).
        if (phase > 0 && lastFrame != 0 && frameCount < frames.length) {
            frames[frameCount++] = (now - lastFrame) / 1e6f;
        }
        lastFrame = now;
        placeCamera(mc, t);
        double length = phase == 0 ? WARMUP_SECONDS : PHASE_SECONDS;
        if (t >= length) {
            if (phase > 0) {
                finishPhase();
            }
            if (phase == 4) {
                report(mc);
                state = 99;
                mc.scheduleStop();
                return;
            }
            beginPhase(mc, phase + 1, now);
        }
    }

    private static void beginPhase(MinecraftClient mc, int next, long now) {
        phase = next;
        // Phases 1 and 3 run with the boost forced off; 0 (warm-up), 2 and 4 with it on.
        PerfConfig.benchForceOff = next == 1 || next == 3;
        phaseStart = now;
        lastFrame = 0;
        frameCount = 0;
        VoidLog.info("bench: phase " + next + (PerfConfig.benchForceOff ? " (boost OFF)" : " (boost ON)"));
    }

    private static void finishPhase() {
        float[] copy = java.util.Arrays.copyOf(frames, frameCount);
        double[] stats = FrameStats.summarize(copy);
        results.add(new double[] {phase, PerfConfig.benchForceOff ? 0 : 1, stats[0], stats[1], stats[2], frameCount});
        hiddenCounts.add(OcclusionCuller.get().lastOccludedCount());
        VoidLog.info(String.format(Locale.ROOT,
                "bench: phase %d %s — avg %.1f fps, 1%% low %.1f fps, 0.1%% low %.1f fps over %d frames; culler %s",
                phase, PerfConfig.benchForceOff ? "OFF" : "ON", stats[0], stats[1], stats[2],
                frameCount, OcclusionCuller.get().stats()));
    }

    private static void report(MinecraftClient mc) {
        double offAvg = 0;
        double offLow = 0;
        double onAvg = 0;
        double onLow = 0;
        int off = 0;
        int on = 0;
        JsonArray phases = new JsonArray();
        for (int i = 0; i < results.size(); i++) {
            double[] r = results.get(i);
            JsonObject p = new JsonObject();
            p.addProperty("phase", (int) r[0]);
            p.addProperty("boost", r[1] > 0);
            p.addProperty("avg_fps", round(r[2]));
            p.addProperty("low1_fps", round(r[3]));
            p.addProperty("low01_fps", round(r[4]));
            p.addProperty("frames", (int) r[5]);
            p.addProperty("culled_last_pass", hiddenCounts.get(i));
            phases.add(p);
            if (r[1] > 0) {
                onAvg += r[2];
                onLow += r[3];
                on++;
            } else {
                offAvg += r[2];
                offLow += r[3];
                off++;
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("scene", "superflat town: 81 houses, 162 chests, 81 signs, 81 torches, "
                + "243 villagers (no AI), 324 armour stands; street fly-through at 5 b/s, "
                + "render distance 8, vsync off, uncapped");
        o.addProperty("window", mc.width + "x" + mc.height);
        o.addProperty("phase_seconds", PHASE_SECONDS);
        o.add("phases", phases);
        o.addProperty("off_avg_fps", round(offAvg / Math.max(1, off)));
        o.addProperty("off_low1_fps", round(offLow / Math.max(1, off)));
        o.addProperty("on_avg_fps", round(onAvg / Math.max(1, on)));
        o.addProperty("on_low1_fps", round(onLow / Math.max(1, on)));
        String text = o.toString();
        VoidLog.info("bench: RESULT " + text);
        File out = new File(mc.runDirectory, "void-bench.json");
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(out), Charset.forName("UTF-8"));
            try {
                w.write(text);
            } finally {
                w.close();
            }
        } catch (java.io.IOException e) {
            VoidLog.warn("bench: could not write " + out + ": " + e);
        }
    }

    private static Double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    // ---------------------------------------------------------------------------------------
    // The camera
    // ---------------------------------------------------------------------------------------

    /** The street loop, as (x, z) corners. Street centres sit at {@code n * 12 - 3}. */
    private static final double[][] PATH = {
        {-51, -3}, {45, -3}, {45, 21}, {-27, 21}, {-27, -27}, {21, -27}, {21, 9}, {-51, 9},
    };
    private static final double SPEED = 5.0;

    private static void placeCamera(MinecraftClient mc, double t) {
        double[] pose = BenchPath.pose(PATH, SPEED, t);
        double sweep = Math.sin(t * 0.7) * 30.0;
        pose(mc, pose[0], FLOOR, pose[1], (float) (pose[2] + sweep), 0f);
    }

    private static void pose(MinecraftClient mc, double x, double y, double z, float yaw,
                             float pitch) {
        if (mc.player == null) {
            return;
        }
        mc.player.refreshPositionAndAngles(x, y, z, yaw, pitch);
        mc.player.prevTickX = x;
        mc.player.prevTickY = y;
        mc.player.prevTickZ = z;
        mc.player.prevX = x;
        mc.player.prevY = y;
        mc.player.prevZ = z;
        mc.player.prevYaw = yaw;
        mc.player.prevPitch = pitch;
        mc.player.velocityX = 0;
        mc.player.velocityY = 0;
        mc.player.velocityZ = 0;
    }

    // ---------------------------------------------------------------------------------------
    // The scene (server thread)
    // ---------------------------------------------------------------------------------------

    private static void buildTown(ServerWorld w) {
        w.setTimeOfDay(6000);
        w.getGameRules().setGameRule("doDaylightCycle", "false");
        w.getGameRules().setGameRule("doMobSpawning", "false");
        for (int gx = -GRID; gx <= GRID; gx++) {
            for (int gz = -GRID; gz <= GRID; gz++) {
                house(w, gx * SPACING, gz * SPACING, gx, gz);
                villager(w, gx * SPACING - 1.5, gz * SPACING - 1.5, null);
            }
        }
        // A named villager beside the first pose, for the nametag shots.
        villager(w, 3.5, 13.5, "Opponent");
    }

    private static void house(ServerWorld w, int x0, int z0, int gx, int gz) {
        for (int x = x0; x <= x0 + 6; x++) {
            for (int z = z0; z <= z0 + 6; z++) {
                for (int y = FLOOR; y <= FLOOR + 5; y++) {
                    boolean wall = x == x0 || x == x0 + 6 || z == z0 || z == z0 + 6;
                    boolean roof = y == FLOOR + 5;
                    if (!wall && !roof) {
                        continue;
                    }
                    boolean door = z == z0 + 6 && x == x0 + 3 && y <= FLOOR + 1;
                    if (door) {
                        continue;
                    }
                    boolean window = y == FLOOR + 2 && !roof
                            && ((x == x0 + 3 && (z == z0 || z == z0 + 6))
                            || (z == z0 + 3 && (x == x0 || x == x0 + 6)));
                    w.setBlockState(new BlockPos(x, y, z), window
                            ? Blocks.GLASS.getDefaultState()
                            : Blocks.STONE_BRICKS.getDefaultState(), 2);
                }
            }
        }
        w.setBlockState(new BlockPos(x0 + 1, FLOOR, z0 + 1), Blocks.CHEST.getDefaultState(), 2);
        w.setBlockState(new BlockPos(x0 + 5, FLOOR, z0 + 1), Blocks.CHEST.getDefaultState(), 2);
        BlockPos signPos = new BlockPos(x0 + 3, FLOOR, z0 + 1);
        w.setBlockState(signPos, Blocks.STANDING_SIGN.getDefaultState(), 2);
        BlockEntity be = w.getBlockEntity(signPos);
        if (be instanceof SignBlockEntity) {
            SignBlockEntity sign = (SignBlockEntity) be;
            sign.text[0] = new LiteralText("VOID");
            sign.text[1] = new LiteralText("House " + gx + "," + gz);
            sign.text[2] = new LiteralText("bench");
            sign.text[3] = new LiteralText("scene");
        }
        w.setBlockState(new BlockPos(x0 + 3, FLOOR, z0 + 5), Blocks.TORCH.getDefaultState(), 2);
        villager(w, x0 + 2.5, z0 + 3.5, null);
        villager(w, x0 + 4.5, z0 + 3.5, null);
        ArmorStandEntity stand = new ArmorStandEntity(w, x0 + 1.5, FLOOR, z0 + 4.5);
        w.spawnEntity(stand);
        String[] lines = {"§d§lVOID", "§fHouse " + gx + "," + gz, "§7click to join"};
        for (int i = 0; i < lines.length; i++) {
            ArmorStandEntity holo = new ArmorStandEntity(w, x0 + 3.5, FLOOR + 6.4 + (2 - i) * 0.3,
                    z0 + 3.5);
            holo.setInvisible(true);
            holo.setCustomName(lines[i]);
            holo.setCustomNameVisible(true);
            NbtCompound tag = new NbtCompound();
            holo.saveToNbt(tag);
            tag.putBoolean("NoGravity", true);
            holo.fromNbt(tag);
            w.spawnEntity(holo);
        }
    }

    private static void villager(ServerWorld w, double x, double z, String name) {
        VillagerEntity v = new VillagerEntity(w);
        v.refreshPositionAndAngles(x, FLOOR, z, 0f, 0f);
        NbtCompound tag = new NbtCompound();
        v.saveToNbt(tag);
        tag.putBoolean("NoAI", true);
        tag.putBoolean("Silent", true);
        v.fromNbt(tag);
        v.refreshPositionAndAngles(x, FLOOR, z, 0f, 0f);
        if (name != null) {
            v.setCustomName(name);
            v.setCustomNameVisible(true);
        }
        w.spawnEntity(v);
    }

    // ---------------------------------------------------------------------------------------
    // shots
    // ---------------------------------------------------------------------------------------

    /** One screenshot: its file name, the pose, and the setup it needs. */
    private abstract static class Shot {
        final String name;
        final double x;
        final double z;
        final float yaw;
        final float pitch;
        final int settle;

        Shot(String name, double x, double z, float yaw, float pitch, int settle) {
            this.name = name;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.settle = settle;
        }

        abstract void setup(MinecraftClient mc);
    }

    private static void mod(String id, boolean on) {
        LiveState.get().setGameplay(id, on);
    }

    private static void set(String id, String key, Object value) {
        com.google.gson.JsonElement v;
        if (value instanceof Boolean) {
            v = new JsonPrimitive((Boolean) value);
        } else if (value instanceof Number) {
            v = new JsonPrimitive((Number) value);
        } else {
            v = new JsonPrimitive(String.valueOf(value));
        }
        LiveState.get().setModSetting(id, key, v);
    }

    private static void reset(MinecraftClient mc) {
        for (String id : new String[] {"block_outline", "shiny_pots", "clear_glass", "nametags",
                "scoreboard", "gui_scale", "fog", "menu_blur", "time_weather"}) {
            mod(id, false);
        }
        mc.options.perspective = 0;
        mc.options.debugEnabled = false;
        mc.options.hudHidden = false;
        mc.options.viewDistance = 8;
        if (mc.currentScreen != null) {
            mc.closeScreen();
        }
    }

    private static final List<Shot> TOUR = new ArrayList<Shot>();

    static {
        // Pose A: in the street south of house (0,0), facing its door and window.
        final double ax = 3.5;
        final double az = 10.5;
        TOUR.add(new Shot("00-scene", ax, az, 180f, 0f, 40) {
            void setup(MinecraftClient mc) {
                reset(mc);
            }
        });
        TOUR.add(new Shot("clear-glass-off", ax, az, 180f, 0f, 60) {
            void setup(MinecraftClient mc) {
                reset(mc);
            }
        });
        TOUR.add(new Shot("clear-glass-on", ax, az, 180f, 0f, 80) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mod("clear_glass", true);
            }
        });
        TOUR.add(new Shot("block-outline-vanilla", 1.5, 9.0, 180f, 8f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
            }
        });
        TOUR.add(new Shot("block-outline-on", 1.5, 9.0, 180f, 8f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mod("block_outline", true);
                set("block_outline", "outline_style", "both");
                set("block_outline", "outline_width", 3.5);
                set("block_outline", "outline_color", "#7ADFFFFF");
                set("block_outline", "fill_color", "#7ADFFF33");
            }
        });
        TOUR.add(new Shot("nametags-vanilla", 3.5, 9.0, 0f, 10f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mc.options.perspective = 2;
            }
        });
        TOUR.add(new Shot("nametags-on", 3.5, 9.0, 0f, 10f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mc.options.perspective = 2;
                mod("nametags", true);
                set("nametags", "tag_background", 0.55);
                set("nametags", "tag_scale", 1.3);
            }
        });
        TOUR.add(new Shot("scoreboard-vanilla", ax, az, 180f, 0f, 40) {
            void setup(MinecraftClient mc) {
                reset(mc);
                sidebar(mc);
            }
        });
        TOUR.add(new Shot("scoreboard-void", ax, az, 180f, 0f, 60) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mod("scoreboard", true);
            }
        });
        TOUR.add(new Shot("shiny-pots-off", ax, az, 180f, 0f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                potions(mc);
            }
        });
        TOUR.add(new Shot("shiny-pots-on", ax, az, 180f, 0f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mod("shiny_pots", true);
                set("shiny_pots", "glint_passes", 3);
                set("shiny_pots", "tint_glint", true);
            }
        });
        TOUR.add(new Shot("gui-scale-off", ax, az, 180f, 0f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
            }
        });
        TOUR.add(new Shot("gui-scale-on", ax, az, 180f, 0f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mod("gui_scale", true);
                set("gui_scale", "hotbar_scale", 1);
                set("gui_scale", "inventory_scale", 1);
            }
        });
        TOUR.add(new Shot("inventory-scale-off", ax, az, 180f, 0f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mc.setScreen(new SurvivalInventoryScreen(mc.player));
            }
        });
        TOUR.add(new Shot("inventory-scale-on", ax, az, 180f, 0f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mod("gui_scale", true);
                set("gui_scale", "hotbar_scale", 1);
                set("gui_scale", "inventory_scale", 1);
                mc.setScreen(new SurvivalInventoryScreen(mc.player));
            }
        });
        TOUR.add(new Shot("menu-blur-off", ax, az, 180f, 0f, 30) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mc.setScreen(new SurvivalInventoryScreen(mc.player));
            }
        });
        TOUR.add(new Shot("menu-blur-on", ax, az, 180f, 0f, 40) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mod("menu_blur", true);
                set("menu_blur", "blur_radius", 6);
                mc.setScreen(new SurvivalInventoryScreen(mc.player));
            }
        });
        TOUR.add(new Shot("fog-vanilla", -3, -50, 0f, 0f, 60) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mc.options.viewDistance = 4;
            }
        });
        TOUR.add(new Shot("fog-off", -3, -50, 0f, 0f, 60) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mc.options.viewDistance = 4;
                mod("fog", true);
                set("fog", "distance_fog", "off");
            }
        });
        TOUR.add(new Shot("time-weather-off", ax, az, 180f, -25f, 60) {
            void setup(MinecraftClient mc) {
                reset(mc);
                weather(mc, true);
            }
        });
        TOUR.add(new Shot("time-weather-on", ax, az, 180f, -25f, 60) {
            void setup(MinecraftClient mc) {
                reset(mc);
                mod("time_weather", true);
                set("time_weather", "time_ticks", 12600);
            }
        });
        TOUR.add(new Shot("culling-off-f3", -3, -27, 90f, 0f, 60) {
            void setup(MinecraftClient mc) {
                reset(mc);
                weather(mc, false);
                PerfConfig.benchForceOff = true;
                mc.options.debugEnabled = true;
            }
        });
        TOUR.add(new Shot("culling-on-f3", -3, -27, 90f, 0f, 60) {
            void setup(MinecraftClient mc) {
                reset(mc);
                PerfConfig.benchForceOff = false;
                mc.options.debugEnabled = true;
            }
        });
    }

    private static void shotTick(MinecraftClient mc) {
        if (shot >= TOUR.size()) {
            VoidLog.info("bench: tour done, " + TOUR.size() + " screenshots in "
                    + new File(mc.runDirectory, "screenshots"));
            state = 99;
            mc.scheduleStop();
            return;
        }
        Shot s = TOUR.get(shot);
        if (shotWait == 0) {
            VoidLog.info("bench: shot " + s.name);
            s.setup(mc);
        }
        shotWait++;
    }

    private static void shotFrame(MinecraftClient mc) {
        if (shot >= TOUR.size()) {
            return;
        }
        Shot s = TOUR.get(shot);
        pose(mc, s.x, FLOOR, s.z, s.yaw, s.pitch);
        if (shotWait > s.settle) {
            ScreenshotUtils.saveScreenshot(mc.runDirectory, "void-" + s.name + ".png", mc.width,
                    mc.height, mc.getFramebuffer());
            shot++;
            shotWait = 0;
        }
    }

    private static void sidebar(final MinecraftClient mc) {
        final IntegratedServer server = mc.getServer();
        server.submit(new Runnable() {
            @Override
            public void run() {
                Scoreboard sb = server.getWorld(0).getScoreboard();
                ScoreboardObjective o = sb.getNullableObjective("void");
                if (o == null) {
                    o = sb.method_4884("void", ScoreboardCriterion.DUMMY);
                }
                o.setDisplayName("§e§lBED WARS");
                sb.setObjectiveSlot(1, o);
                String[] lines = {"§710/15/26 §8m512A", " ", "§fDiamond II in §a0:42",
                    "§c§lR §fRed: §a✔", "§9§lB §fBlue: §a✔ §7YOU", "§a§lG §fGreen: §c✘",
                    "§e§lY §fYellow: §e2", "  ", "§ewww.void.gg"};
                for (int i = 0; i < lines.length; i++) {
                    sb.getPlayerScore(lines[i], o).setScore(lines.length - i);
                }
            }
        });
    }

    private static void potions(MinecraftClient mc) {
        if (mc.interactionManager == null) {
            return;
        }
        mc.interactionManager.clickCreativeStack(new ItemStack(Items.DIAMOND_SWORD), 36);
        int[] damages = {16421, 16421, 16418, 16389, 0, 8226};
        for (int i = 0; i < damages.length; i++) {
            mc.interactionManager.clickCreativeStack(new ItemStack(Items.POTION, 1, damages[i]),
                    37 + i);
        }
    }

    private static void weather(final MinecraftClient mc, final boolean rainyNight) {
        final IntegratedServer server = mc.getServer();
        server.submit(new Runnable() {
            @Override
            public void run() {
                ServerWorld w = server.getWorld(0);
                w.setTimeOfDay(rainyNight ? 17000 : 6000);
                w.getLevelProperties().setRaining(rainyNight);
                w.getLevelProperties().setRainTime(rainyNight ? 100000 : 0);
                w.getLevelProperties().setClearWeatherTime(rainyNight ? 0 : 100000);
            }
        });
    }
}
