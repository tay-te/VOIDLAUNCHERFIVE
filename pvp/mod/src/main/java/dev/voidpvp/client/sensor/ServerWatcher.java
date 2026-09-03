package dev.voidpvp.client.sensor;

import com.google.gson.JsonObject;

/**
 * The {@code server} sensor (§6.6). Deduplicates connect/disconnect so the
 * bridge event and the {@code server} protocol message are raised once per
 * actual change, whichever hook noticed it.
 */
public final class ServerWatcher {

    private boolean connected;
    private String host = "";
    private int port = 25565;

    /**
     * @return true when this changed the connection state, i.e. when the
     *         {@code server} event and message are due
     */
    public boolean update(boolean nowConnected, String nowHost, int nowPort) {
        String h = nowConnected ? (nowHost == null ? "" : stripPort(nowHost)) : "";
        int p = nowConnected ? nowPort : 25565;
        if (nowConnected == connected && h.equals(host) && p == port) {
            return false;
        }
        connected = nowConnected;
        host = h;
        port = p;
        return true;
    }

    public boolean connected() {
        return connected;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    /** {@code bridge.json#/definitions/server_payload}. */
    public JsonObject payload() {
        JsonObject o = new JsonObject();
        o.addProperty("host", host);
        o.addProperty("connected", Boolean.valueOf(connected));
        return o;
    }

    /** Minecraft stores {@code host:port}; both contracts want the host alone. */
    public static String stripPort(String address) {
        if (address == null) {
            return "";
        }
        int colon = address.lastIndexOf(':');
        if (colon > 0 && address.indexOf(':') == colon) {
            return address.substring(0, colon);
        }
        return address;
    }

    /** The port in {@code host:port}, or 25565 when absent or unparseable. */
    public static int portOf(String address) {
        if (address == null) {
            return 25565;
        }
        int colon = address.lastIndexOf(':');
        if (colon > 0 && address.indexOf(':') == colon) {
            try {
                int p = Integer.parseInt(address.substring(colon + 1));
                if (p >= 1 && p <= 65535) {
                    return p;
                }
            } catch (NumberFormatException e) {
                return 25565;
            }
        }
        return 25565;
    }
}
