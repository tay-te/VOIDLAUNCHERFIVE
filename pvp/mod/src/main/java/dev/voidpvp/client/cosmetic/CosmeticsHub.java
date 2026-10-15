package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.cosmetic.dev.Showcase;
import dev.voidpvp.client.state.Json;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The cosmetics' beat: what runs once per client tick, once per frame and once per world change.
 * Driven from {@code CosmeticsHooksMixin} so the rest of the mod does not have to know cosmetics
 * exist.
 *
 * <ul>
 *   <li><b>Queries.</b> Every second, the uuids of players in the world that this client has not
 *       asked about recently go to the launcher in one {@code cosmetics_query} (at most
 *       {@link #QUERY_BATCH}); answers come back as {@code cosmetics}. Each uuid is asked about at
 *       most once per {@link #QUERY_TTL_MS}. Version-2 uuids (server-side NPCs) are never asked
 *       about.</li>
 *   <li><b>Emotes.</b> Sweeps finished clips, runs the local camera, and cancels a remote player's
 *       emote when they visibly move or swing — the same rule the local player is held to.</li>
 *   <li><b>Dev.</b> {@code -Dvoid.dev.cosmetics=<file.json>} applies a {@code cosmetics} message
 *       from disk (and owns everything) so cosmetics can be seen without a launcher;
 *       {@code -Dvoid.dev.showcase=<dir>} runs the {@link Showcase}.</li>
 * </ul>
 */
public final class CosmeticsHub {

    static final int QUERY_BATCH = 64;
    static final long QUERY_TTL_MS = 10 * 60_000L;

    private static final Map<String, Long> ASKED = new HashMap<String, Long>();
    private static final Map<String, double[]> LAST_POS = new HashMap<String, double[]>();
    private static int tickCount;
    private static boolean devLoaded;
    private static Showcase showcase;

    private CosmeticsHub() {
    }

    /** End of {@code MinecraftClient.tick}. */
    public static void tick(MinecraftClient mc) {
        tickCount++;
        if (!devLoaded) {
            devLoaded = true;
            loadDev();
            String dir = System.getProperty("void.dev.showcase");
            if (dir != null && !dir.isEmpty()) {
                showcase = new Showcase(new File(dir));
            }
        }
        if (mc.player == null && CosmeticState.get().self().uuid.isEmpty() && mc.getSession() != null) {
            // Before a world exists the session is the only identity there is; a dev session
            // whose id is not a uuid stays empty until the player entity gives one.
            String id = mc.getSession().getUuid();
            String u = EquippedSet.normalise(id);
            if (u.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                CosmeticState.get().setSelfUuid(u);
                Emotes.get().setSelfUuid(u);
            }
        }
        if (mc.player != null) {
            String self = EquippedSet.normalise(String.valueOf(mc.player.getUuid()));
            if (!self.equals(CosmeticState.get().self().uuid)) {
                CosmeticState.get().setSelfUuid(self);
                Emotes.get().setSelfUuid(self);
            }
        }
        Emotes.get().sweep();
        EmoteCamera.tick(mc);
        if (mc.world != null) {
            cancelMovingRemotes(mc);
            if (tickCount % 20 == 0) {
                query(mc);
                CosmeticRenderer.sweep();
            }
        }
        if (showcase != null) {
            showcase.tick(mc);
        }
        if (Boolean.getBoolean("void.dev.cosmetics.log") || showcase != null) {
            String r = Budget.report();
            if (r != null) {
                if (showcase != null) {
                    showcase.say(r);
                } else {
                    VoidLog.info(r);
                }
            }
        }
    }

    /** A world was joined or left. */
    public static void worldChanged() {
        Trails.clear();
        CosmeticRenderer.clear();
        LAST_POS.clear();
        // Ask again on the new server; the sets may have changed while we were away.
        ASKED.clear();
    }

    /** Start of {@code GameRenderer.render}. */
    public static void frameStart() {
        EmoteCamera.frame();
    }

    /** End of {@code GameRenderer.render}. */
    public static void frameEnd(MinecraftClient mc) {
        Budget.endFrame();
        if (showcase != null) {
            showcase.frame(mc);
        }
    }

    /** Right after vanilla's particles in the world pass. */
    public static void worldPass(MinecraftClient mc, float td) {
        net.minecraft.entity.Entity cam = mc.getCameraEntity();
        if (cam == null) {
            return;
        }
        long t0 = System.nanoTime();
        CosmeticRenderer.emitUnrendered(mc, td);
        double cx = cam.prevTickX + (cam.x - cam.prevTickX) * td;
        double cy = cam.prevTickY + (cam.y - cam.prevTickY) * td;
        double cz = cam.prevTickZ + (cam.z - cam.prevTickZ) * td;
        Trails.render(cx, cy, cz, dev.voidpvp.client.state.LiveState.get().cosmeticsGlow);
        Budget.add(System.nanoTime() - t0);
    }

    private static void query(MinecraftClient mc) {
        if (!CosmeticState.get().hasSink()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<String> ask = new ArrayList<String>();
        for (Object o : mc.world.playerEntities) {
            if (!(o instanceof PlayerEntity) || o == mc.player) {
                continue;
            }
            UUID id = ((PlayerEntity) o).getUuid();
            if (id == null || id.version() == 2) {
                continue;
            }
            String u = EquippedSet.normalise(id.toString());
            Long at = ASKED.get(u);
            if (at != null && now - at.longValue() < QUERY_TTL_MS) {
                continue;
            }
            ASKED.put(u, Long.valueOf(now));
            ask.add(u);
            if (ask.size() >= QUERY_BATCH) {
                break;
            }
        }
        CosmeticState.get().sendQuery(ask);
    }

    private static void cancelMovingRemotes(MinecraftClient mc) {
        for (Object o : mc.world.playerEntities) {
            if (!(o instanceof PlayerEntity) || o == mc.player) {
                continue;
            }
            PlayerEntity p = (PlayerEntity) o;
            String u = EquippedSet.normalise(String.valueOf(p.getUuid()));
            Emotes.Active a = Emotes.get().of(u);
            double[] last = LAST_POS.get(u);
            if (last == null) {
                last = new double[] {p.x, p.z};
                LAST_POS.put(u, last);
            }
            double dx = p.x - last[0], dz = p.z - last[1];
            last[0] = p.x;
            last[1] = p.z;
            if (a == null || a.cancelled() || showcase != null) {
                continue;
            }
            if (dx * dx + dz * dz > 0.0036 || p.handSwinging) {
                Emotes.get().cancel(u);
            }
        }
    }

    /** {@code -Dvoid.dev.cosmetics}: a {@code cosmetics} message from disk. */
    private static void loadDev() {
        String path = System.getProperty("void.dev.cosmetics");
        if (path == null || path.isEmpty()) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
            applyMessage(Json.parseObject(text));
            CosmeticState.get().setOwnsEverything(true);
            VoidLog.info("dev cosmetics applied from " + path);
        } catch (Exception e) {
            VoidLog.warn("dev cosmetics: could not read " + path + ": " + e);
        }
    }

    /** Applies a {@code cosmetics}-shaped object: {self?, players?, owned?}. */
    public static void applyMessage(JsonObject o) {
        if (o == null) {
            return;
        }
        EquippedSet self = o.has("self") && o.get("self").isJsonObject()
                ? EquippedSet.fromJson(o.getAsJsonObject("self")) : null;
        List<EquippedSet> players = null;
        if (o.has("players") && o.get("players").isJsonArray()) {
            players = new ArrayList<EquippedSet>();
            for (JsonElement e : o.getAsJsonArray("players")) {
                if (e.isJsonObject()) {
                    players.add(EquippedSet.fromJson(e.getAsJsonObject()));
                }
            }
        }
        List<String> owned = null;
        if (o.has("owned") && o.get("owned").isJsonArray()) {
            owned = new ArrayList<String>();
            JsonArray a = o.getAsJsonArray("owned");
            for (JsonElement e : a) {
                if (e.isJsonPrimitive()) {
                    owned.add(e.getAsString());
                }
            }
        }
        CosmeticState.get().applyLauncher(self, players, owned);
    }

    public static boolean showcaseRunning() {
        return showcase != null;
    }
}
