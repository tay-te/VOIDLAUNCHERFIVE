// VOID Welkin, look pass 6: the light shafts' colour and strength for the hour (lib/rays.glsl), once per frame in a
// vertex shader. Needs lib/sky/luminous.glsl. Written from scratch for Welkin; no other pack's code.

// The beams' light at full strength (display): the sun's own light led toward the warm gold through the golden hours,
// as bright as the sun-side horizon (never dimmer than RAY_LEVEL), strongest at a low sun and gentle at noon.
// composite3 gates the night, the overcast and the eye in water.
vec3 rayLightFor(SkyLum f) {
	vec3 hue = pow(mix(f.hue, f.gold, 0.6) * 0.4, vec3(1.0 / 2.2));
	hue /= max(dot(hue, vec3(0.2126, 0.7152, 0.0722)), 1e-3);
	float level = max(skyLumLuma(skyHorizon(true)), 0.6);
	float low = mix(1.0, 0.35, smoothstep(0.3, 0.85, f.sunW.y));
	return hue * level * low * float(SHAFT_STRENGTH) * 0.01;
}
