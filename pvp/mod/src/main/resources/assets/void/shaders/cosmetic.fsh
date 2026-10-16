// void — cosmetics material shader, fragment stage (cosmetic/CosmeticShader).
//
// Lighting starts from Minecraft's own entity model — the two fixed lights, the 0.4 ambient, the
// lightmap texel for where the player stands, the fog — so a cosmetic never looks pasted onto a
// skin that is lit differently. On top of that, per material:
//
//   0 matte     vanilla's diffuse, contact occlusion, a rim of sky light
//   1 cloth     wrapped diffuse (soft terminator), cool hue-shifted occlusion, a grazing sheen
//   2 metal     a stylised environment: dark ground, a bright horizon band, sky above, reflected
//               about the normal, so trims and crowns glint as they turn; a tight specular
//   3 glass     tinted, see-through (alpha by Fresnel), a sky reflection and a hard highlight
//   4 emissive  full bright, and into the bloom mask
//
// One pass draws all of an item's layers: the albedo, the tint mask over it (u_tintTex, coloured
// by u_tintRgb) and the emissive layer added on top (u_emi, at u_emiK) — the fixed-function path
// needs three draws for what this does in one.
//
// gl_FragData[1] is the bloom mask (cosmetic/Bloom): emissive light goes in, everything else
// writes black so a surface in front of a glow occludes it. When the mask is not attached the
// second output goes nowhere.
#version 120

uniform sampler2D u_tex;
uniform sampler2D u_light;
uniform sampler2D u_emi;      // unit 2
uniform sampler2D u_tintTex;  // unit 3
uniform float u_emiK;      // emissive layer strength; 0 = the item has none
uniform float u_tintOn;
uniform vec3 u_tintRgb;
uniform float u_alphaMat;  // 1: albedo alpha 250..254 carries a material code
uniform vec4 u_tint;
uniform float u_bloom;     // this player's bloom gain
uniform float u_fog;       // 0 off, 1 linear, 2 exp, 3 exp2
uniform float u_rim;

varying vec2 v_uv;
varying vec2 v_lm;
varying vec3 v_n;
varying vec3 v_pos;
varying vec4 v_col;
varying float v_mat;
varying float v_ao;

float fogFactor() {
    float z = abs(v_pos.z);
    if (u_fog < 0.5) return 1.0;
    if (u_fog < 1.5) return clamp((gl_Fog.end - z) * gl_Fog.scale, 0.0, 1.0);
    if (u_fog < 2.5) return clamp(exp(-gl_Fog.density * z), 0.0, 1.0);
    float dz = gl_Fog.density * z;
    return clamp(exp(-dz * dz), 0.0, 1.0);
}

void main() {
    vec4 t = texture2D(u_tex, v_uv);
    float f = fogFactor();
    float mat = floor(v_mat + 0.5);
    float alpha = t.a;
    if (u_alphaMat > 0.5) {
        float a8 = floor(t.a * 255.0 + 0.5);
        if (a8 > 249.5 && a8 < 254.5) {
            mat = 254.0 - a8;
            alpha = 1.0;
        }
    }
    if (alpha < 0.1) discard;
    vec3 base = t.rgb;
    if (u_tintOn > 0.5) {
        vec4 tm = texture2D(u_tintTex, v_uv);
        base = mix(base, tm.rgb * u_tintRgb, tm.a);
    }
    vec3 alb = base * u_tint.rgb * v_col.rgb;
    vec3 em = vec3(0.0);
    if (u_emiK > 0.0) {
        vec4 e = texture2D(u_emi, v_uv);
        em = e.rgb * e.a;
    }
    vec3 lm = texture2D(u_light, v_lm).rgb;

    if (mat > 3.5) {
        gl_FragData[0] = vec4(mix(gl_Fog.color.rgb, alb, f) + em * u_emiK * f, alpha * u_tint.a);
        gl_FragData[1] = vec4(max(alb, em) * (u_bloom * 1.5 * f), 1.0);
        return;
    }

    vec3 V = normalize(-v_pos);
    vec3 N = normalize(v_n);
    if (dot(N, V) < 0.0) N = -N;
    vec3 L0 = normalize(gl_LightSource[0].position.xyz);
    vec3 L1 = normalize(gl_LightSource[1].position.xyz);
    // Minecraft's two lights are mirror images about world up, so their sum is up in eye space.
    vec3 up = normalize(L0 + L1 + vec3(0.0, 1e-4, 0.0));
    float amb = gl_LightModel.ambient.r;
    float dif = gl_LightSource[0].diffuse.r;
    float d0 = dot(N, L0), d1 = dot(N, L1);
    float nv = clamp(dot(N, V), 0.0, 1.0);
    float lit = min(1.0, amb + dif * (max(d0, 0.0) + max(d1, 0.0)));
    // Occlusion darkens toward a cool violet rather than toward grey: hue-shifted shadow.
    float ao = clamp(v_ao, 0.0, 1.0);
    vec3 occ = mix(vec3(0.46, 0.43, 0.6), vec3(1.0), ao);
    float skyUp = clamp(0.55 + 0.45 * dot(N, up), 0.0, 1.0);
    float rim = u_rim * pow(1.0 - nv, 4.0) * skyUp;

    vec3 col;
    float outA = alpha;
    if (mat < 0.5) {
        col = alb * lit * occ * lm;
    } else if (mat < 1.5) {
        // Cloth: wrapped diffuse so folds roll off softly, a little less ambient and a little more
        // direct light than vanilla so the form reads in flat daylight, and a velvet sheen at
        // grazing angles.
        float w = 0.35;
        float k0 = max((d0 + w) / (1.0 + w), 0.0), k1 = max((d1 + w) / (1.0 + w), 0.0);
        float cl = min(1.05, (amb - 0.1) + dif * 1.15 * (k0 + k1));
        float sheen = pow(1.0 - nv, 3.0) * 0.22;
        col = alb * cl * occ * lm + (alb * 0.75 + 0.05) * sheen * lm * ao;
    } else {
        vec3 R = reflect(-V, N);
        float h = dot(R, up);
        // A stylised studio environment, not a real one: mostly bright, darkening only for steep
        // downward reflections, with a hot band just below the horizon — where a third-person
        // view reflects to — so metal on a moving cape or a turning head keeps catching it.
        float env = mix(0.5, 1.15, smoothstep(-0.65, 0.35, h)) + 0.45 * exp(-pow((h + 0.06) / 0.12, 2.0));
        float s0 = pow(max(dot(N, normalize(L0 + V)), 0.0), 48.0);
        float s1 = pow(max(dot(N, normalize(L1 + V)), 0.0), 48.0);
        float spec = (s0 + s1) * ao;
        if (mat < 2.5) {
            col = alb * (0.32 * lit + 0.6 * env) * occ * lm + spec * 0.5 * mix(alb, vec3(1.0), 0.5) * lm;
        } else {
            float fres = pow(1.0 - nv, 3.0);
            col = alb * lit * 0.8 * lm + vec3(0.7, 0.88, 1.0) * (0.08 + 0.5 * fres) * env * lm + spec * 1.2 * lm;
            outA = alpha * mix(0.6, 0.97, fres);
        }
    }
    col += mix(alb, vec3(1.0), 0.4) * rim * lm * ao;
    gl_FragData[0] = vec4(mix(gl_Fog.color.rgb, col, f) + em * u_emiK * f, outA * u_tint.a);
    gl_FragData[1] = vec4(em * (u_bloom * 1.5 * f), outA);
}
