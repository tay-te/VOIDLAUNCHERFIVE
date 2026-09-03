package dev.voidpvp.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * One logger for the mod. Minecraft 1.8.9 already has log4j on the classpath,
 * so nothing is bundled for this.
 */
public final class VoidLog {

    private static final Logger LOGGER = LogManager.getLogger("void");

    private VoidLog() {
    }

    public static void info(String message) {
        LOGGER.info("[void] {}", message);
    }

    public static void warn(String message) {
        LOGGER.warn("[void] {}", message);
    }

    public static void error(String message) {
        LOGGER.error("[void] {}", message);
    }

    public static void error(String message, Throwable t) {
        LOGGER.error("[void] " + message, t);
    }
}
