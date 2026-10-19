// VOID Welkin: deferred_cloudmarch, run as Iris's deferred6 (tools/wrappers.sh maps it), after the light (D7, the clouds'
// cost). The voxel clouds' march (lib/clouds/march.glsl) on its own, per pixel: its hit into colortex10
// (lib/clouds/hitbuf.glsl), which the clouds pass (program/parts/clouds.glsl, composite) lights. The march is a loop of
// dependent cloud map reads; compiled in one program with the cloud light it ran at that whole program's occupancy.
//
// The translucents are not drawn yet, so the land's distance here is the opaque land's (lib/depth.glsl), never nearer
// than what the clouds pass sees: the march runs as far as it would there or farther, takes the same steps, and the
// clouds pass keeps a hit only nearer than its own land (and the far bands' cut). Overworld only, with CLOUDS.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"
#include "/lib/clouds/buffers.glsl"

#ifdef VERTEX_SHADER

#include "/lib/clouds/envflat.glsl"

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
	wcEnvWrite(welkinCloudEnv());
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/depth.glsl"
#include "/lib/clouds/envflat.glsl"
#include "/lib/clouds/hitbuf.glsl"
#include "/lib/clouds/bandcut.glsl"

in vec2 texcoord;

#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

/* RENDERTARGETS: 10 */
layout(location = 0) out vec4 outHit;

void main() {
	outHit = vec4(0.0);
#ifdef CLOUDS
	ivec2 p = ivec2(gl_FragCoord.xy);
	Surface s = surfaceAt(p, texcoord);
	float tScene = s.sky ? 1e30 : length(s.view);
	vec3 rd = normalize(mat3(gbufferModelViewInverse) * viewFromDepth(texcoord, 1.0, gbufferProjectionInverse));
	vec3 ro = vec3(0.0);
	float pixAngle = 2.0 / (gbufferProjection[1][1] * viewHeight);
	// (as the clouds pass) land nearer than the lowest a cloud can stand, seen by an eye well under every deck
	float eyeY = float(cameraPositionInt.y) + cameraPositionFract.y;
	float lowest = cloudHeight - 1.0;
	if (eyeY < lowest - 40.0 && (rd.y <= 0.0 || tScene * rd.y < lowest - eyeY)) {
		return;
	}
	CloudEnv e = wcEnvRead();
	float tCut = min(tScene, min(wcBandCut(0, ro, rd, pixAngle, e), min(wcBandCut(1, ro, rd, pixAngle, e),
		wcBandCut(2, ro, rd, pixAngle, e))));
	outHit = wcHitEncode(welkinCloudMarch(ro, rd, tCut, pixAngle, e));
#endif
}

#endif
