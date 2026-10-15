package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Who wears what, as far as this client knows. One instance, read by the renderer every frame and
 * written from three threads: the WS thread ({@code cosmetics} from the launcher), the UI thread
 * ({@code window.void.equipCosmetic}) and the game thread (the dev loader, the showcase).
 *
 * <p><b>The mod never talks HTTP.</b> Everything here arrives from the launcher over the bridge
 * socket (the contract's data flow): the local player's own set and what they own, and the sets of
 * other VOID players this client asked about with {@code cosmetics_query}. Being in
 * {@link #players} at all is what makes someone a VOID player, which is what the nametag marker
 * shows.</p>
 *
 * <p><b>Equip is local first.</b> {@link #equip} applies at once â€” the next frame draws it â€” and
 * then tells the launcher through the {@link Sink}, which persists it. A refused equip (an item not
 * owned, or for the wrong slot) changes nothing and returns what is still worn, which is the
 * bridge's "return the state actually applied" rule.</p>
 */
public final class CosmeticState {

    /** Messages to the launcher. Implemented by the WS client; a no-op when there is none. */
    public interface Sink {
        void query(List<String> uuids);

        void emote(String id);

        void equip(String slot, String id, String tint);

        void emotes(List<String> ids);

        /**
         * Show the launcher's Store on one item ({@code open_store}). Returns whether the launcher
         * was told. Default false, so a sink without a launcher behind it (tests) says so.
         */
        default boolean openStore(String id) {
            return false;
        }
    }

    /** Told whenever the local set or ownership changed, so the page can be re-pushed. */
    public interface Listener {
        void onCosmeticsChanged();
    }

    private static final CosmeticState INSTANCE = new CosmeticState();

    public static CosmeticState get() {
        return INSTANCE;
    }

    private volatile EquippedSet self = EquippedSet.empty("");
    private final Map<String, EquippedSet> players = new ConcurrentHashMap<String, EquippedSet>();
    /** Null until the launcher says what is owned; then only free items and these. */
    private volatile Set<String> owned;
    /** Dev and showcase: every catalog item counts as owned. */
    private volatile boolean ownsEverything;
    private volatile Sink sink;
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    CosmeticState() {
    }

    public void setSink(Sink sink) {
        this.sink = sink;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void setOwnsEverything(boolean all) {
        ownsEverything = all;
        changed();
    }

    /** Called once the session is known, so the local set answers to the local uuid. */
    public void setSelfUuid(String uuid) {
        String u = EquippedSet.normalise(uuid);
        if (!u.equals(self.uuid)) {
            self = self.withUuid(u);
        }
    }

    public EquippedSet self() {
        return self;
    }

    /**
     * Applies a launcher {@code cosmetics} message. {@code self} null leaves the local set alone;
     * {@code players} is a delta (an entry replaces that player's set); {@code owned} null leaves
     * ownership alone.
     */
    public void applyLauncher(EquippedSet newSelf, Collection<EquippedSet> others, Collection<String> newOwned) {
        boolean local = false;
        if (newSelf != null) {
            String keep = self.uuid;
            self = newSelf.uuid.isEmpty() ? newSelf.withUuid(keep) : newSelf;
            local = true;
        }
        if (others != null) {
            for (EquippedSet s : others) {
                if (s.uuid.isEmpty()) {
                    continue;
                }
                if (s.uuid.equals(self.uuid) && newSelf == null) {
                    // The launcher answered a query about ourselves; it is the same set.
                    self = s;
                    local = true;
                    continue;
                }
                players.put(s.uuid, s);
            }
        }
        if (newOwned != null) {
            Set<String> o = new LinkedHashSet<String>();
            for (String id : newOwned) {
                if (CosmeticCatalog.get(id) != null) {
                    o.add(id);
                }
            }
            owned = Collections.unmodifiableSet(o);
            local = true;
        }
        if (local) {
            changed();
        }
    }

    /** The set worn by this uuid, or null when it is not a VOID player we know of. */
    public EquippedSet of(String uuid) {
        if (uuid == null) {
            return null;
        }
        if (uuid.equals(self.uuid)) {
            return self;
        }
        return players.get(uuid);
    }

    public boolean isVoidPlayer(String uuid) {
        return uuid != null && (uuid.equals(self.uuid) || players.containsKey(uuid));
    }

    public boolean knows(String uuid) {
        return players.containsKey(uuid);
    }

    /** Forgets other players (a world change); the local set and ownership stay. */
    public void clearPlayers() {
        players.clear();
    }

    /** Dev/showcase: sets another player's set directly. */
    public void putPlayer(EquippedSet set) {
        if (set != null && !set.uuid.isEmpty()) {
            players.put(set.uuid, set);
        }
    }

    public boolean owns(String id) {
        CosmeticCatalog.Item item = CosmeticCatalog.get(id);
        if (item == null) {
            return false;
        }
        if (ownsEverything || item.free()) {
            return true;
        }
        Set<String> o = owned;
        if (o == null) {
            // The wire does not carry ownership (protocol.json `cosmetics` is self + players): with
            // a launcher attached, an equip is applied optimistically and the backend, which does
            // know, answers a refusal with a `cosmetics` whose `self` reverts it. Without one
            // there is nobody to ask, so only free items.
            return sink != null && !"grant".equals(item.availability);
        }
        return o.contains(id);
    }

    /** Owned ids in catalog order (free items included). */
    public List<String> ownedIds() {
        List<String> out = new ArrayList<String>();
        boolean known = owned != null || ownsEverything;
        EquippedSet cur = self;
        for (CosmeticCatalog.Item i : CosmeticCatalog.all()) {
            // Unknown ownership: report what is certainly owned — free items and whatever the
            // backend has us wearing — rather than the whole catalog.
            boolean mine = known ? owns(i.id)
                    : i.free() || i.id.equals(cur.get(i.kind.wire())) || cur.emotes().contains(i.id);
            if (mine) {
                out.add(i.id);
            }
        }
        return out;
    }

    /**
     * Wears {@code id} in {@code slot} (null takes it off). Applied locally at once and forwarded
     * to the launcher.
     *
     * @return the id now in the slot â€” the requested one, or what was already there if refused
     */
    public String equip(String slot, String id) {
        if (!CosmeticCatalog.isSlot(slot)) {
            return null;
        }
        EquippedSet cur = self;
        if (id != null && (!CosmeticCatalog.fits(slot, id) || !owns(id))) {
            return cur.get(slot);
        }
        String before = cur.get(slot);
        if (id == null ? before == null : id.equals(before)) {
            return before;
        }
        self = cur.with(slot, id);
        Sink s = sink;
        if (s != null) {
            int t = self.tint(slot);
            s.equip(slot, id, t < 0 ? null : EquippedSet.hex(t));
        }
        changed();
        return id;
    }

    /**
     * Recolours a tintable slot. Refused (returns the current tint) if the worn item is not
     * tintable. {@code hex} null clears the tint.
     */
    public String tint(String slot, String hex) {
        EquippedSet cur = self;
        String id = cur.get(slot);
        CosmeticCatalog.Item item = CosmeticCatalog.get(id);
        int curTint = cur.tint(slot);
        String curHex = curTint < 0 ? null : EquippedSet.hex(curTint);
        if (item == null || !item.tintable) {
            return curHex;
        }
        int rgb = hex == null ? -1 : EquippedSet.parseHex(hex);
        if (hex != null && rgb < 0) {
            return curHex;
        }
        self = cur.withTint(slot, rgb);
        Sink s = sink;
        if (s != null) {
            s.equip(slot, id, rgb < 0 ? null : EquippedSet.hex(rgb));
        }
        changed();
        return rgb < 0 ? null : EquippedSet.hex(rgb);
    }

    /** Sets the emote wheel; unknown and unowned ids are dropped. Returns what was stored. */
    public List<String> setEmotes(List<String> ids) {
        List<String> keep = new ArrayList<String>();
        if (ids != null) {
            for (String id : ids) {
                if (owns(id)) {
                    keep.add(id);
                }
            }
        }
        self = self.withEmotes(keep);
        Sink s = sink;
        if (s != null) {
            s.emotes(self.emotes());
        }
        changed();
        return self.emotes();
    }

    /** Tells the launcher the local player emoted. */
    public void sendEmote(String id) {
        Sink s = sink;
        if (s != null) {
            s.emote(id);
        }
    }

    public void sendQuery(List<String> uuids) {
        Sink s = sink;
        if (s != null && !uuids.isEmpty()) {
            s.query(uuids);
        }
    }

    /**
     * Get in launcher Store: asks the launcher to show this item's Store sheet. Neither ownership
     * nor this jar's catalog is checked: the launcher's catalog may be newer, the Store shows an
     * owned item as owned, and an id it does not know opens the gallery. Returns whether the
     * launcher was told.
     */
    public boolean openStore(String id) {
        Sink s = sink;
        return s != null && id != null && !id.isEmpty() && s.openStore(id);
    }

    public boolean hasSink() {
        return sink != null;
    }

    /** {@code bridge.json#/definitions/cosmetics_payload}: what the wardrobe UI needs. */
    public JsonObject uiPayload() {
        JsonObject o = new JsonObject();
        o.add("self", self.toJson());
        JsonArray own = new JsonArray();
        for (String id : ownedIds()) {
            own.add(new JsonPrimitive(id));
        }
        o.add("owned", own);
        o.addProperty("catalog_version", Integer.valueOf(CosmeticCatalog.VERSION));
        return o;
    }

    private void changed() {
        for (Listener l : listeners) {
            try {
                l.onCosmeticsChanged();
            } catch (RuntimeException ignored) {
                // A listener failing must not stop the state from applying.
            }
        }
    }
}
