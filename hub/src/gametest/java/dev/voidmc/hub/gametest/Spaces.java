package dev.voidmc.hub.gametest;

import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.ui.HubView;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Collapsed spaces at low GUI scales. Ultralight lays text out on whole device px per glyph advance: under the page's
 * own zoom (a layout unit under 1, GUI 1 and 2 at 1080p) a small font's 0.2 em space came out one device px, which
 * next to the glyphs' side bearings reads as no space at all ("fromHearth"). The probe measures every space the page
 * shows between two words (inside a text node, or a whitespace-only text node between two inline elements; not one a
 * line wraps at, nor one a flex container drops) as laid out, in device px, plus a row of synthetic samples at each
 * size the pages use, and names the ones narrower than {@link #MIN_DEV} device px.
 */
final class Spaces {

	/** A word space narrower than this many device px reads as none. */
	static final double MIN_DEV = 1.5;

	private static final String JS = "(function(){var Z=(window.HUBZ||1)*(window.devicePixelRatio||1),MIN=" + MIN_DEV + ",n=0,bad=[],least=99,r=document.createRange();"
		+ "function vis(el){for(var e=el;e&&e!==document.body;e=e.parentElement){var c=getComputedStyle(e);"
		+ "if(c.display==='none'||c.visibility==='hidden'||parseFloat(c.opacity)===0)return false;}return true;}"
		+ "function inl(e){return e&&e.nodeType===1&&/^inline/.test(getComputedStyle(e).display);}"
		+ "function word(c){return !!c&&!/\\s/.test(c);}"
		+ "function box(t,a,b){r.setStart(t,a);r.setEnd(t,b);var rs=r.getClientRects();return rs.length?rs[0]:null;}"
		// The gap a space leaves: from the end of the letter before it to the start of the letter after it (its own box
		// reads 0 wide once word-spacing is set). L/R: the last/first letter's box of a node (text or element).
		+ "function L(n){if(!n)return null;if(n.nodeType===3)return n.data.length?box(n,n.data.length-1,n.data.length):null;return n.getBoundingClientRect();}"
		+ "function R(n){if(!n)return null;if(n.nodeType===3)return n.data.length?box(n,0,1):null;return n.getBoundingClientRect();}"
		+ "function look(el,a0,a1,txt){if(!a0||!a1||a0.height===0||a1.height===0)return;"
		// A space a line wraps at is not drawn: the letters either side sit on different lines.
		+ "if(Math.abs(a0.top-a1.top)>Math.max(2,a0.height/2))return;"
		+ "var fs=parseFloat(getComputedStyle(el).fontSize)||10,dv=(a1.left-a0.right)*Z;n++;if(dv<least)least=dv;"
		+ "if(dv<MIN&&bad.length<16)bad.push(txt.replace(/\s+/g,' ').trim().slice(0,40)+' ['+(el.className||el.tagName)+' '+fs+'px: '+dv.toFixed(2)+' dev px]');}"
		+ "var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT),t;"
		+ "while((t=w.nextNode())){var s=t.data,el=t.parentElement;if(!el||!vis(el))continue;var cs=getComputedStyle(el),ws=cs.whiteSpace,flex=/flex|grid/.test(cs.display);"
		+ "var pre=ws==='pre'||ws==='pre-wrap'||ws==='break-spaces';"
		+ "if(/^\\s+$/.test(s)){if(flex)continue;"
		+ "var a=t.previousSibling,z=t.nextSibling;if(inl(a)&&inl(z)&&word((a.textContent||'').slice(-1))&&word((z.textContent||'').charAt(0)))"
		+ "look(el,a.getBoundingClientRect(),z.getBoundingClientRect(),(a.textContent||'')+' '+(z.textContent||''));continue;}"
		+ "for(var i=0;i<s.length;i++){var c=s.charAt(i);if(c!==' '&&c!=='\\u00a0')continue;"
		+ "var p=i>0?s.charAt(i-1):((t.previousSibling&&t.previousSibling.textContent)||'').slice(-1);"
		+ "var q=i<s.length-1?s.charAt(i+1):((t.nextSibling&&t.nextSibling.textContent)||'').charAt(0);"
		+ "if(!word(p)||!word(q))continue;if(flex&&(i===0||i===s.length-1))continue;if(!pre&&i>0&&/\\s/.test(s.charAt(i-1)))continue;look(el,i>0?box(t,i-1,i):L(t.previousSibling),i<s.length-1?box(t,i+1,i+2):R(t.nextSibling),s);}}"
		// Synthetic samples: each size and weight the pages use, zoomed as the page is.
		+ "var bx=document.createElement('div');bx.style.cssText='position:fixed;left:0;top:0;opacity:0.01;pointer-events:none;white-space:nowrap';"
		+ "document.body.appendChild(bx);var tab=[];[8,9,9.5,10,10.5,11,11.5,12,12.5,14,16].forEach(function(fs){[300,400,500].forEach(function(fw){"
		+ "var d=document.createElement('div');d.style.cssText='font-size:'+fs+'px;font-weight:'+fw;d.textContent='a b';bx.appendChild(d);"
		+ "var dv=(box(d.firstChild,2,3).left-box(d.firstChild,0,1).right)*Z;if(fw===500)tab.push(fs+':'+dv.toFixed(2));n++;if(dv<least)least=dv;"
		+ "if(dv<MIN&&bad.length<16)bad.push('sample '+fs+'px/'+fw+': '+dv.toFixed(2)+' dev px');});});"
		+ "bx.parentNode.removeChild(bx);"
		+ "return JSON.stringify({z:Z,n:n,bad:bad,least:least,tab:tab.join(' ')});})()";

	private Spaces() {
	}

	/** What the probe found on the open page: device px per CSS px, spaces measured, the narrow ones, the narrowest. */
	record Result(double scale, int spaces, List<Object> collapsed, double narrowest, String table) {
		@Override
		public String toString() {
			return String.format(java.util.Locale.ROOT, "%.3f device px per CSS px, %d spaces, narrowest %.2f device px, 500-weight space by size %s%s", this.scale,
				this.spaces, this.narrowest, this.table, this.collapsed.isEmpty() ? "" : ", collapsed: " + this.collapsed);
		}
	}

	@SuppressWarnings("unchecked")
	static Result run(ClientGameTestContext context) {
		Map<String, Object> m = Json.parseObject(context.computeOnClient(mc -> HubView.get().eval(JS)));
		return new Result(((Number) m.get("z")).doubleValue(), ((Number) m.get("n")).intValue(), (List<Object>) m.get("bad"), ((Number) m.get("least")).doubleValue(),
			String.valueOf(m.get("tab")));
	}
}
