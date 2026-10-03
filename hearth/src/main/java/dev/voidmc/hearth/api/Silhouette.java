package dev.voidmc.hearth.api;

/**
 * How a building looks from very far away, for VOID LOD's horizon: a block of wall under a block of roof.
 *
 * @param wallColor 0xRRGGBB average of the walls seen from outside
 * @param roofColor 0xRRGGBB average of the roof seen from above
 * @param eaveY     the y where the walls meet the roof
 * @param ridgeY    the highest y of the roof (towers and spires included)
 */
public record Silhouette(int wallColor, int roofColor, int eaveY, int ridgeY) {
}
