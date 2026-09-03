/*
 * window.void — the in-game bridge shim.
 *
 * This is the JS half of schema/bridge.json (PVP_ARCHITECTURE.md §6.5). The
 * in-game bundle loads it before anything else and then only ever talks to
 * `window.void`; it never calls `window.__void_native` itself.
 *
 * Shape of the seam:
 *   Java -> JS   window.void.__emit([{e, payload}, ...])   once per frame
 *   JS -> Java   window.__void_native('{"c":..,"params":[..]}') -> '{"c":..,"returns":..}'
 *
 * The envelopes exist on this seam only. `void.on(e, handler)` hands the
 * handler the bare `payload`, and the call methods take positional arguments,
 * exactly as bridge.json describes. Because Ultralight runs inside the JVM the
 * calls are synchronous and return the state actually applied: no ack, no
 * request id, no optimistic UI. openKeybindCapture is the one exception and
 * returns a Promise that Java resolves later through __emitKeybind.
 *
 * `window.void` is a legal property name — `void` is a reserved word, but ES5
 * onwards allows reserved words as property names, so `window.void.on(...)`
 * parses. `void.on(...)` on its own does not; always go through `window`.
 *
 * Owned by mod/. The browser ?debug harness of §9 replaces this file with a
 * fake that replays recorded envelopes.
 */
(function (global) {
  'use strict';

  if (global.void && global.void.__isVoidBridge) {
    return;
  }

  var EVENTS = ['keys', 'tick', 'server', 'loadout', 'menu'];
  var handlers = {};
  var pendingKeybind = null;

  for (var i = 0; i < EVENTS.length; i++) {
    handlers[EVENTS[i]] = [];
  }

  function call(name, params) {
    if (typeof global.__void_native !== 'function') {
      // No Java on the other side: the ?debug harness, or a view that came up
      // before the message handler was attached. Fail quiet, never throw into
      // a render.
      return null;
    }
    var answer = global.__void_native(JSON.stringify({ c: name, params: params }));
    if (!answer) {
      return null;
    }
    try {
      var parsed = JSON.parse(answer);
      return parsed && 'returns' in parsed ? parsed.returns : null;
    } catch (e) {
      return null;
    }
  }

  var bridge = {
    __isVoidBridge: true,

    /** Subscribe to one of the five Java -> JS channels. */
    on: function (event, handler) {
      var list = handlers[event];
      if (list && typeof handler === 'function' && list.indexOf(handler) === -1) {
        list.push(handler);
      }
      return this;
    },

    /** Unsubscribe. Passing no handler drops every handler on the channel. */
    off: function (event, handler) {
      var list = handlers[event];
      if (!list) {
        return this;
      }
      if (!handler) {
        handlers[event] = [];
        return this;
      }
      var at = list.indexOf(handler);
      if (at !== -1) {
        list.splice(at, 1);
      }
      return this;
    },

    /**
     * Called by Java once per frame with this frame's envelopes, batched so a
     * frame costs one JS call however many sensors fired. Accepts a single
     * envelope too, which is what the ?debug harness replays.
     */
    __emit: function (batch) {
      var list = Array.isArray(batch) ? batch : [batch];
      for (var i = 0; i < list.length; i++) {
        var envelope = list[i];
        if (!envelope || !envelope.e) {
          continue;
        }
        var subscribers = handlers[envelope.e];
        if (!subscribers) {
          continue;
        }
        for (var j = 0; j < subscribers.length; j++) {
          try {
            subscribers[j](envelope.payload);
          } catch (e) {
            // One bad handler must not cost the rest of the frame's events.
            if (global.console && global.console.error) {
              global.console.error('void.on(' + envelope.e + ') handler threw', e);
            }
          }
        }
      }
    },

    /**
     * Whether a text input has focus. Java asks before letting Escape close
     * the menu, so typing Escape inside a field does not throw the player back
     * into the game (§6.3).
     */
    __hasFocus: function () {
      var el = global.document && global.document.activeElement;
      if (!el) {
        return false;
      }
      if (el.isContentEditable) {
        return true;
      }
      var tag = (el.tagName || '').toUpperCase();
      if (tag === 'TEXTAREA' || tag === 'SELECT') {
        return true;
      }
      if (tag !== 'INPUT') {
        return false;
      }
      var type = (el.getAttribute('type') || 'text').toLowerCase();
      return type !== 'checkbox' && type !== 'radio' && type !== 'button'
        && type !== 'submit' && type !== 'range' && type !== 'color';
    },

    /** Java resolves the pending openKeybindCapture promise through this. */
    __emitKeybind: function (key) {
      var resolve = pendingKeybind;
      pendingKeybind = null;
      if (resolve) {
        resolve(key === undefined ? null : key);
      }
    },

    // -- the six calls of bridge.json --------------------------------------

    /** Toggle a gameplay mod; returns the state actually applied. */
    setGameplay: function (id, on) {
      return call('setGameplay', [id, !!on]);
    },

    /** Move or scale a HUD item; returns the item as stored, after snapping. */
    setHud: function (id, placement) {
      return call('setHud', [id, placement]);
    },

    /** Change one setting of one mod; returns the value stored, after clamping. */
    setModSetting: function (id, key, value) {
      return call('setModSetting', [id, key, value]);
    },

    /** Switch the active loadout; returns whether the switch happened. */
    switchLoadout: function (id) {
      return call('switchLoadout', [id]);
    },

    /** Close the menu screen and give the mouse back to the game. */
    closeMenu: function () {
      return call('closeMenu', []);
    },

    /**
     * Capture the next key press. Resolves with an LWJGL key name, or null if
     * the player cancelled with Escape. Never rejects. Only one capture can be
     * open at a time; a second call cancels the first.
     */
    openKeybindCapture: function (modId) {
      if (pendingKeybind) {
        var stale = pendingKeybind;
        pendingKeybind = null;
        stale(null);
      }
      return new Promise(function (resolve) {
        pendingKeybind = resolve;
        call('openKeybindCapture', [modId]);
      });
    }
  };

  global.void = bridge;
})(typeof window !== 'undefined' ? window : this);
