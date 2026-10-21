package dev.voidmc.hub.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.ui.HubView;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Proves the whole window paints (the GUI-scale cut-off: at 1920x1080 GUI scale 2 nothing past a 1280 x 720 CSS corner
 * was drawn). The page puts five opaque magenta squares on top of everything, at the viewport's four corners and its
 * centre; a screenshot of the real frame (the page, the dirty-rect upload and the blit) must show every one of them.
 */
final class PaintProbe {

	private static final int SIZE = 24;

	private static final String ADD = "(function(){var o=document.getElementById('__probe');if(o)o.parentNode.removeChild(o);"
		+ "o=document.createElement('div');o.id='__probe';"
		+ "o.style.cssText='position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:2147483647';"
		// The viewport in page px: under the page's own zoom (a layout scale under 1) innerWidth is view px, page px x HUBZ.
		+ "var Z=window.HUBZ||1,W=window.innerWidth/Z,H=window.innerHeight/Z,s=" + SIZE + ",p=[[4,4],[W-4-s,4],[4,H-4-s],[W-4-s,H-4-s],[Math.round(W/2-s/2),Math.round(H/2-s/2)]],c=[];"
		+ "for(var i=0;i<p.length;i++){var d=document.createElement('div');"
		+ "d.style.cssText='position:absolute;background:#FF00FF;width:'+s+'px;height:'+s+'px;left:'+p[i][0]+'px;top:'+p[i][1]+'px';"
		+ "o.appendChild(d);c.push([p[i][0]+s/2,p[i][1]+s/2]);}"
		+ "document.body.appendChild(o);return JSON.stringify({w:W,h:H,c:c});})()";
	private static final String REMOVE = "(function(){var o=document.getElementById('__probe');if(o)o.parentNode.removeChild(o);return ''})()";
	private static final String[] NAMES = {"top left", "top right", "bottom left", "bottom right", "centre"};

	private PaintProbe() {
	}

	/** The probes that did not show (empty: the whole window painted), and what the page measured, for the log. */
	record Result(List<String> missing, String page) {
	}

	/** Runs the probe on the open hub screen; {@code name} names the screenshot. */
	static Result run(ClientGameTestContext context, String name) {
		return run(context, name, true);
	}

	/**
	 * Runs the probe; {@code centre} false leaves the centre out, for a page whose middle Java draws over (Mettle's draft
	 * dims the cards that burn natively, over the page).
	 */
	@SuppressWarnings("unchecked")
	static Result run(ClientGameTestContext context, String name, boolean centre) {
		String json = context.computeOnClient(mc -> HubView.get().eval(ADD));
		Map<String, Object> m = Json.parseObject(json);
		double w = ((Number) m.get("w")).doubleValue();
		double h = ((Number) m.get("h")).doubleValue();
		List<String> missing = new ArrayList<>();
		String page = "";
		// The UI thread runs below the game's priority: on a loaded machine (another game) the probes may land a few
		// ticks later than on a quiet one. Up to four looks, 6 ticks apart; a probe that never lands still fails.
		for (int attempt = 0; attempt < 4; attempt++) {
			context.waitTicks(6);
			Path shot = context.takeScreenshot(attempt == 0 ? name : name + "_again" + attempt);
			missing.clear();
			try (InputStream in = Files.newInputStream(shot); NativeImage img = NativeImage.read(in)) {
				double sx = img.getWidth() / w;
				double sy = img.getHeight() / h;
				page = String.format(java.util.Locale.ROOT, "page %.0fx%.0f CSS, frame %dx%d", w, h, img.getWidth(), img.getHeight()) + (attempt > 0 ? ", look " + (attempt + 1) : "");
				List<Object> c = (List<Object>) m.get("c");
				for (int i = 0; i < (centre ? c.size() : 4); i++) {
					List<Object> p = (List<Object>) c.get(i);
					int x = (int) Math.floor(((Number) p.get(0)).doubleValue() * sx);
					int y = (int) Math.floor(((Number) p.get(1)).doubleValue() * sy);
					int argb = img.getPixel(Math.max(0, Math.min(img.getWidth() - 1, x)), Math.max(0, Math.min(img.getHeight() - 1, y)));
					int a = argb >>> 24 & 0xFF;
					int r = argb >> 16 & 0xFF;
					int g = argb >> 8 & 0xFF;
					int b = argb & 0xFF;
					// Magenta is red and blue alike, so ARGB and ABGR read the same.
					if (!(r > 220 && b > 220 && g < 40)) {
						missing.add(NAMES[i] + " at " + x + "," + y + String.format(java.util.Locale.ROOT, " is #%02X%02X%02X", r, g, b) + (a < 255 ? " a" + a : ""));
					}
				}
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
			if (missing.isEmpty()) {
				break;
			}
		}
		context.computeOnClient(mc -> HubView.get().eval(REMOVE));
		return new Result(missing, page);
	}
}
