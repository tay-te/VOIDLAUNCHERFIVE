/**
 * Cartridge HUD components — the in-game equivalents of `@void/ui`'s HUD chips, which stay as
 * they are because the desktop launcher shares them. Styled by `cart/hud.css` (`ch-` prefix).
 */

export { HudModule, HudLed, Meter, WindowMeter, inkStyle, litFor, type LedTone } from './parts';
export {
  CART_KEY_COLORS,
  CART_PRESSED_COLORS,
  CartKeystrokes,
  cartKeysStyle,
  type CartKeysState,
} from './Keys';
export { CartArmor, armorSprite, type CartArmorRow } from './Armor';
export { CartEffects, type CartEffectRow } from './Effects';
