// Ultralight's reference fill / fill_path shaders, ported from GLSL 1.50 to GLSL 1.20.
//
// Why: the SDK ships `shaders/glsl/*.h` at `#version 150`, which needs a GL 3.2 core context.
// Minecraft 1.8.9 hands us GL 2.1 on macOS (LWJGL 2, legacy profile) and a compatibility context
// on Windows, so 1.50 will not compile. The port is mechanical but has five real traps:
//
//   1. No `uint` and no unsigned literals    -> everything is `int`; `bool(uint(x + 0.5))` becomes
//                                               a float comparison.
//   2. No `switch`                            -> if/else chains.
//   3. No `in`/`out` at global scope          -> `attribute`/`varying`; the fragment shader writes
//                                               `gl_FragColor`. The originals *read back* their
//                                               out param (blend, applyClip), and gl_FragColor is
//                                               not reliably readable, so we keep a global
//                                               `out_Color` and copy it out at the end of main().
//   4. Only "constant-index-expressions" may index a uniform array. `Clip[i]`, `Vector[i]` and
//                                               `Scalar4[..][i]` are all dynamic in the original,
//                                               so each becomes an explicit if/else fan-out.
//                                               Dynamic *vector component* indexing (ex_Data2[i])
//                                               gets the same treatment — GL 2.1 drivers differ on
//                                               whether they accept it.
//   5. `texture()` -> `texture2D()`.
//
// Also dropped: the `ex_ScreenCoord` varying. Neither fragment shader uses it, and Apple's GLSL
// compiler rejects a varying that is read but never written.
//
// Everything else is byte-for-byte the reference logic, including Inigo Quilez's MIT-licensed
// sdEllipse (notice preserved below).

#pragma once

namespace voidul {
namespace shaders {

// ------------------------------------------------------------------------------------------------
// Vertex: 2f_4ub_2f_2f_28f  (quad geometry -> the "fill" program)
// ------------------------------------------------------------------------------------------------
static const char* kFillVertex = R"GLSL(#version 120

uniform vec4 State;
uniform mat4 Transform;
uniform vec4 Scalar4[2];
uniform vec4 Vector[8];
uniform int  ClipSize;
uniform mat4 Clip[8];

attribute vec2 in_Position;
attribute vec4 in_Color;
attribute vec2 in_TexCoord;
attribute vec2 in_ObjCoord;
attribute vec4 in_Data0;
attribute vec4 in_Data1;
attribute vec4 in_Data2;
attribute vec4 in_Data3;
attribute vec4 in_Data4;
attribute vec4 in_Data5;
attribute vec4 in_Data6;

varying vec4 ex_Color;
varying vec2 ex_TexCoord;
varying vec4 ex_Data0;
varying vec4 ex_Data1;
varying vec4 ex_Data2;
varying vec4 ex_Data3;
varying vec4 ex_Data4;
varying vec4 ex_Data5;
varying vec4 ex_Data6;
varying vec2 ex_ObjectCoord;

void main(void) {
  ex_ObjectCoord = in_ObjCoord;
  gl_Position = Transform * vec4(in_Position, 0.0, 1.0);
  ex_Color = in_Color;
  ex_TexCoord = in_TexCoord;
  ex_Data0 = in_Data0;
  ex_Data1 = in_Data1;
  ex_Data2 = in_Data2;
  ex_Data3 = in_Data3;
  ex_Data4 = in_Data4;
  ex_Data5 = in_Data5;
  ex_Data6 = in_Data6;
}
)GLSL";

// ------------------------------------------------------------------------------------------------
// Vertex: 2f_4ub_2f  (tessellated path geometry -> the "fill_path" program)
// ------------------------------------------------------------------------------------------------
static const char* kPathVertex = R"GLSL(#version 120

uniform vec4 State;
uniform mat4 Transform;
uniform vec4 Scalar4[2];
uniform vec4 Vector[8];
uniform int  ClipSize;
uniform mat4 Clip[8];

attribute vec2 in_Position;
attribute vec4 in_Color;
attribute vec2 in_TexCoord;

varying vec4 ex_Color;
varying vec2 ex_ObjectCoord;

void main(void) {
  ex_ObjectCoord = in_TexCoord;
  gl_Position = Transform * vec4(in_Position, 0.0, 1.0);
  ex_Color = in_Color;
}
)GLSL";

// ------------------------------------------------------------------------------------------------
// Shared fragment prelude: signed-distance helpers + the clip stack.
// Textually spliced in front of both fragment shaders.
// ------------------------------------------------------------------------------------------------
static const char* kFragCommon = R"GLSL(
#define AA_WIDTH 0.354

vec4 out_Color;

float antialias(in float d, in float width, in float median) {
  return smoothstep(median - width, median + width, d);
}

float sdRect(vec2 p, vec2 size) {
  vec2 d = abs(p) - size;
  return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

// The below function "sdEllipse" is MIT licensed with following text:
//
// The MIT License
// Copyright 2013 Inigo Quilez
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software
// is furnished to do so, subject to the following conditions: The above copyright
// notice and this permission notice shall be included in all copies or substantial
// portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF
// ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
// EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
// OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
float sdEllipse(vec2 p, in vec2 ab) {
  if (abs(ab.x - ab.y) < 0.1)
    return length(p) - ab.x;

  p = abs(p); if (p.x > p.y) { p = p.yx; ab = ab.yx; }

  float l = ab.y * ab.y - ab.x * ab.x;

  float m = ab.x * p.x / l;
  float n = ab.y * p.y / l;
  float m2 = m * m;
  float n2 = n * n;

  float c = (m2 + n2 - 1.0) / 3.0;
  float c3 = c * c * c;

  float q = c3 + m2 * n2 * 2.0;
  float d = c3 + m2 * n2;
  float g = m + m * n2;

  float co;

  if (d < 0.0) {
    float h = acos(q / c3) / 3.0;
    float s = cos(h);
    float t = sin(h) * sqrt(3.0);
    float rx = sqrt(-c * (s + t + 2.0) + m2);
    float ry = sqrt(-c * (s - t + 2.0) + m2);
    co = (ry + sign(l) * rx + abs(g) / (rx * ry) - m) / 2.0;
  } else {
    float h = 2.0 * m * n * sqrt(d);
    float s = sign(q + h) * pow(abs(q + h), 1.0 / 3.0);
    float u = sign(q - h) * pow(abs(q - h), 1.0 / 3.0);
    float rx = -s - u - c * 4.0 + 2.0 * m2;
    float ry = (s - u) * sqrt(3.0);
    float rm = sqrt(rx * rx + ry * ry);
    float k = ry / sqrt(rm - rx);
    co = (k + 2.0 * g / rm - m) / 2.0;
  }

  float si = sqrt(1.0 - co * co);
  vec2 r = vec2(ab.x * co, ab.y * si);
  return length(r - p) * sign(p.y - r.y);
}

float sdRoundRect(vec2 p, vec2 size, vec4 rx, vec4 ry) {
  size *= 0.5;
  vec2 corner;

  corner = vec2(-size.x + rx.x, -size.y + ry.x);   // Top-Left
  vec2 local = p - corner;
  if (dot(rx.x, ry.x) > 0.0 && p.x < corner.x && p.y <= corner.y)
    return sdEllipse(local, vec2(rx.x, ry.x));

  corner = vec2(size.x - rx.y, -size.y + ry.y);    // Top-Right
  local = p - corner;
  if (dot(rx.y, ry.y) > 0.0 && p.x >= corner.x && p.y <= corner.y)
    return sdEllipse(local, vec2(rx.y, ry.y));

  corner = vec2(size.x - rx.z, size.y - ry.z);     // Bottom-Right
  local = p - corner;
  if (dot(rx.z, ry.z) > 0.0 && p.x >= corner.x && p.y >= corner.y)
    return sdEllipse(local, vec2(rx.z, ry.z));

  corner = vec2(-size.x + rx.w, size.y - ry.w);    // Bottom-Left
  local = p - corner;
  if (dot(rx.w, ry.w) > 0.0 && p.x < corner.x && p.y > corner.y)
    return sdEllipse(local, vec2(rx.w, ry.w));

  return sdRect(p, size);
}

vec2 transformAffine(vec2 val, vec2 a, vec2 b, vec2 c) {
  return val.x * a + val.y * b + c;
}

void Unpack(vec4 x, out vec4 a, out vec4 b) {
  const float s = 65536.0;
  a = floor(x / s);
  b = floor(x - a * s);
}

// GLSL 1.20: a uniform array index must be a constant-index-expression, so fan the clip stack out
// by hand rather than writing Clip[i].
mat4 ClipAt(int i) {
  if (i == 0) return Clip[0];
  if (i == 1) return Clip[1];
  if (i == 2) return Clip[2];
  if (i == 3) return Clip[3];
  if (i == 4) return Clip[4];
  if (i == 5) return Clip[5];
  if (i == 6) return Clip[6];
  return Clip[7];
}

void applyClip() {
  for (int i = 0; i < 8; i++) {
    if (i >= ClipSize) break;
    mat4 data = ClipAt(i);
    vec2 origin = data[0].xy;
    vec2 size = data[0].zw;
    vec4 radii_x, radii_y;
    Unpack(data[1], radii_x, radii_y);
    bool inverse = data[3].z != 0.0;

    vec2 p = ex_ObjectCoord;
    p = transformAffine(p, data[2].xy, data[2].zw, data[3].xy);
    p -= origin;

    float d_clip = sdRoundRect(p, size, radii_x, radii_y) * (inverse ? -1.0 : 1.0);
    float alpha = antialias(-d_clip, AA_WIDTH, 0.0);
    out_Color = vec4(out_Color.rgb * alpha, out_Color.a * alpha);
  }
}
)GLSL";

// ------------------------------------------------------------------------------------------------
// Fragment: fill_path
// ------------------------------------------------------------------------------------------------
static const char* kPathFragmentHead = R"GLSL(#version 120

uniform vec4 State;
uniform mat4 Transform;
uniform vec4 Scalar4[2];
uniform vec4 Vector[8];
uniform int  ClipSize;
uniform mat4 Clip[8];

varying vec4 ex_Color;
varying vec2 ex_ObjectCoord;
)GLSL";

static const char* kPathFragmentBody = R"GLSL(
void main(void) {
  out_Color = ex_Color;
  applyClip();
  gl_FragColor = out_Color;
}
)GLSL";

// ------------------------------------------------------------------------------------------------
// Fragment: fill
// ------------------------------------------------------------------------------------------------
static const char* kFillFragmentHead = R"GLSL(#version 120

uniform vec4 State;
uniform mat4 Transform;
uniform vec4 Scalar4[2];
uniform vec4 Vector[8];
uniform int  ClipSize;
uniform mat4 Clip[8];

uniform sampler2D Texture1;
uniform sampler2D Texture2;
uniform sampler2D Texture3;

varying vec4 ex_Color;
varying vec2 ex_TexCoord;
varying vec2 ex_ObjectCoord;
varying vec4 ex_Data0;
varying vec4 ex_Data1;
varying vec4 ex_Data2;
varying vec4 ex_Data3;
varying vec4 ex_Data4;
varying vec4 ex_Data5;
varying vec4 ex_Data6;

float Scalar(int i) {
  if (i == 0) return Scalar4[0].x;
  if (i == 1) return Scalar4[0].y;
  if (i == 2) return Scalar4[0].z;
  if (i == 3) return Scalar4[0].w;
  if (i == 4) return Scalar4[1].x;
  if (i == 5) return Scalar4[1].y;
  if (i == 6) return Scalar4[1].z;
  return Scalar4[1].w;
}

vec4 VectorAt(int i) {
  if (i == 0) return Vector[0];
  if (i == 1) return Vector[1];
  if (i == 2) return Vector[2];
  if (i == 3) return Vector[3];
  if (i == 4) return Vector[4];
  if (i == 5) return Vector[5];
  if (i == 6) return Vector[6];
  return Vector[7];
}

int  FillType()            { return int(ex_Data0.x + 0.5); }
vec4 TileRectUV()          { return Vector[0]; }
vec2 TileSize()            { return Vector[1].zw; }
vec2 PatternTransformA()   { return Vector[2].xy; }
vec2 PatternTransformB()   { return Vector[2].zw; }
vec2 PatternTransformC()   { return Vector[3].xy; }
int  Gradient_NumStops()   { return int(ex_Data0.y + 0.5); }
bool Gradient_IsRadial()   { return (ex_Data0.z + 0.5) >= 1.0; }
vec2 Gradient_P0()         { return ex_Data1.xy; }
vec2 Gradient_P1()         { return ex_Data1.zw; }
)GLSL";

static const char* kFillFragmentBody = R"GLSL(
struct GradientStop { float percent; vec4 color; };

GradientStop GetGradientStop(int offset) {
  GradientStop result;
  result.percent = 0.0;
  result.color = vec4(0.0);
  if (offset < 4) {
    if (offset == 0)      { result.percent = ex_Data2.x; result.color = ex_Data3; }
    else if (offset == 1) { result.percent = ex_Data2.y; result.color = ex_Data4; }
    else if (offset == 2) { result.percent = ex_Data2.z; result.color = ex_Data5; }
    else                  { result.percent = ex_Data2.w; result.color = ex_Data6; }
  } else {
    result.percent = Scalar(offset - 4);
    result.color = VectorAt(offset - 4);
  }
  return result;
}

const float epsilon = AA_WIDTH;

float antialias2(float d) {
  return smoothstep(-epsilon, +epsilon, d);
}

vec4 blend(vec4 src, vec4 dest) {
  vec4 result;
  result.rgb = src.rgb + dest.rgb * (1.0 - src.a);
  result.a = src.a + dest.a * (1.0 - src.a);
  return result;
}

void fillSolid() {
  out_Color = ex_Color;
}

void fillImage(vec2 uv) {
  out_Color = texture2D(Texture1, uv) * ex_Color;
}

void fillPatternImage() {
  vec4 tile_rect_uv = TileRectUV();
  vec2 tile_size = TileSize();
  vec2 p = ex_ObjectCoord;
  vec2 transformed_coords =
      transformAffine(p, PatternTransformA(), PatternTransformB(), PatternTransformC());
  transformed_coords /= tile_size;
  vec2 uv = fract(transformed_coords);
  uv *= tile_rect_uv.zw - tile_rect_uv.xy;
  uv += tile_rect_uv.xy;
  fillImage(uv);
}

float ramp(in float inMin, in float inMax, in float val) {
  return clamp((val - inMin) / (inMax - inMin), 0.0, 1.0);
}

void fillPatternGradient() {
  int num_stops = Gradient_NumStops();
  bool is_radial = Gradient_IsRadial();
  vec2 p0 = Gradient_P0();
  vec2 p1 = Gradient_P1();

  float t = 0.0;
  if (is_radial) {
    float r0 = p1.x;
    float r1 = p1.y;
    t = distance(ex_TexCoord, p0);
    float rDelta = r1 - r0;
    t = clamp((t / rDelta) - (r0 / rDelta), 0.0, 1.0);
  } else {
    vec2 V = p1 - p0;
    t = clamp(dot(ex_TexCoord - p0, V) / dot(V, V), 0.0, 1.0);
  }

  GradientStop stop0 = GetGradientStop(0);
  GradientStop stop1 = GetGradientStop(1);

  out_Color = mix(stop0.color, stop1.color, ramp(stop0.percent, stop1.percent, t));
  if (num_stops > 2) {
    GradientStop stop2 = GetGradientStop(2);
    out_Color = mix(out_Color, stop2.color, ramp(stop1.percent, stop2.percent, t));
    if (num_stops > 3) {
      GradientStop stop3 = GetGradientStop(3);
      out_Color = mix(out_Color, stop3.color, ramp(stop2.percent, stop3.percent, t));
      if (num_stops > 4) {
        GradientStop stop4 = GetGradientStop(4);
        out_Color = mix(out_Color, stop4.color, ramp(stop3.percent, stop4.percent, t));
        if (num_stops > 5) {
          GradientStop stop5 = GetGradientStop(5);
          out_Color = mix(out_Color, stop5.color, ramp(stop4.percent, stop5.percent, t));
          if (num_stops > 6) {
            GradientStop stop6 = GetGradientStop(6);
            out_Color = mix(out_Color, stop6.color, ramp(stop5.percent, stop6.percent, t));
          }
        }
      }
    }
  }
}

float innerStroke(float stroke_width, float d) {
  return min(antialias(-d, AA_WIDTH, 0.0), 1.0 - antialias(-d, AA_WIDTH, stroke_width));
}

void fillRoundedRect() {
  vec2 p = ex_TexCoord;
  vec2 size = ex_Data0.zw;
  p = (p - 0.5) * size;
  float d = sdRoundRect(p, size, ex_Data1, ex_Data2);

  float alpha = antialias(-d, AA_WIDTH, 0.0);
  out_Color = ex_Color * alpha;

  float stroke_width = ex_Data3.x;
  vec4 stroke_color = ex_Data4;

  if (stroke_width > 0.0) {
    alpha = innerStroke(stroke_width, d);
    vec4 stroke = stroke_color * alpha;
    out_Color = blend(stroke, out_Color);
  }
}

void fillBoxShadow() {
  vec2 p = ex_ObjectCoord;
  bool inset = (ex_Data0.y + 0.5) >= 1.0;
  float radius = ex_Data0.z;
  vec2 origin = ex_Data1.xy;
  vec2 size = ex_Data1.zw;
  vec2 clip_origin = ex_Data4.xy;
  vec2 clip_size = ex_Data4.zw;

  float sdClip = sdRoundRect(p - clip_origin, clip_size, ex_Data5, ex_Data6);
  float sdRectV = sdRoundRect(p - origin, size, ex_Data2, ex_Data3);

  float clip = inset ? -sdRectV : sdClip;
  float d = inset ? -sdClip : sdRectV;

  if (clip < 0.0) {
    discard;
  }

  float alpha = radius >= 1.0
      ? pow(antialias(-d, radius * 2.0 + 0.2, 0.0), 1.9) * 3.3 / pow(radius * 1.2, 0.15)
      : antialias(-d, AA_WIDTH, inset ? -1.0 : 1.0);
  alpha = clamp(alpha, 0.0, 1.0) * ex_Color.a;
  out_Color = vec4(ex_Color.rgb * alpha, alpha);
}

// The per-component ?: chains of the 1.50 original become branchless select()s: GLSL 1.20 has no
// dynamic vec3 component indexing, and `for (i) col[i] = ...` was the only reason for the loop.
vec3 blendOverlay(vec3 src, vec3 dest) {
  vec3 a = 2.0 * dest * src;
  vec3 b = 1.0 - 2.0 * (1.0 - dest) * (1.0 - src);
  return mix(b, a, step(dest, vec3(0.5)));
}

vec3 blendColorDodge(vec3 src, vec3 dest) {
  vec3 inv = max(1.0 - src, vec3(1e-6));
  vec3 r = min(dest / inv, vec3(1.0));
  return mix(r, src, step(vec3(1.0), src));
}

vec3 blendColorBurn(vec3 src, vec3 dest) {
  vec3 s = max(src, vec3(1e-6));
  vec3 r = max(1.0 - ((1.0 - dest) / s), vec3(0.0));
  return mix(r, src, step(src, vec3(0.0)));
}

vec3 blendSoftLight(vec3 src, vec3 dest) {
  vec3 a = 2.0 * dest * src + dest * dest * (1.0 - 2.0 * src);
  vec3 b = sqrt(dest) * (2.0 * src - 1.0) + 2.0 * dest * (1.0 - src);
  return mix(b, a, step(src, vec3(0.5)));
}

vec3 rgb2hsl(vec3 col) {
  const float eps = 0.0000001;
  float minc = min(col.r, min(col.g, col.b));
  float maxc = max(col.r, max(col.g, col.b));
  vec3 mask = step(col.grr, col.rgb) * step(col.bbg, col.rgb);
  vec3 h = mask * (vec3(0.0, 2.0, 4.0) + (col.gbr - col.brg) / (maxc - minc + eps)) / 6.0;
  return vec3(fract(1.0 + h.x + h.y + h.z),
              (maxc - minc) / (1.0 - abs(minc + maxc - 1.0) + eps),
              (minc + maxc) * 0.5);
}

vec3 hsl2rgb(vec3 c) {
  vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
  return c.z + c.y * (rgb - 0.5) * (1.0 - abs(2.0 * c.z - 1.0));
}

vec3 blendHue(vec3 src, vec3 dest) {
  vec3 baseHSL = rgb2hsl(dest);
  return hsl2rgb(vec3(rgb2hsl(src).r, baseHSL.g, baseHSL.b));
}

vec3 blendSaturation(vec3 src, vec3 dest) {
  vec3 baseHSL = rgb2hsl(dest);
  return hsl2rgb(vec3(baseHSL.r, rgb2hsl(src).g, baseHSL.b));
}

vec3 blendColorFn(vec3 src, vec3 dest) {
  vec3 blendHSL = rgb2hsl(src);
  return hsl2rgb(vec3(blendHSL.r, blendHSL.g, rgb2hsl(dest).b));
}

vec3 blendLuminosity(vec3 src, vec3 dest) {
  vec3 baseHSL = rgb2hsl(dest);
  return hsl2rgb(vec3(baseHSL.r, baseHSL.g, rgb2hsl(src).b));
}

vec4 saturateV(vec4 val) { return clamp(val, 0.0, 1.0); }

vec4 calcBlend() {
  fillImage(ex_TexCoord);
  vec4 src = out_Color;
  vec4 dest = texture2D(Texture2, ex_ObjectCoord);
  int op = int(ex_Data0.y + 0.5);

  if (op == 0)  return vec4(0.0, 0.0, 0.0, 0.0);                       // Clear
  if (op == 1)  return src;                                            // Source
  if (op == 2)  return src + dest * (1.0 - src.a);                     // Over
  if (op == 3)  return src * dest.a;                                   // In
  if (op == 4)  return src * (1.0 - dest.a);                           // Out
  if (op == 5)  return src * dest.a + dest * (1.0 - src.a);            // Atop
  if (op == 6)  return src * (1.0 - dest.a) + dest;                    // DestOver
  if (op == 7)  return dest * src.a;                                   // DestIn
  if (op == 8)  return dest * (1.0 - src.a);                           // DestOut
  if (op == 9)  return src * (1.0 - dest.a) + dest * src.a;            // DestAtop
  if (op == 10) return saturateV(src * (1.0 - dest.a) + dest * (1.0 - src.a));  // XOR
  if (op == 11) return vec4(min(src.rgb, dest.rgb) * src.a, dest.a * src.a);    // Darken
  if (op == 12) return saturateV(src + dest);                                   // Add
  if (op == 13) return vec4(abs(dest.rgb - src.rgb) * src.a, dest.a * src.a);   // Difference
  if (op == 14) return vec4(src.rgb * dest.rgb * src.a, dest.a * src.a);        // Multiply
  if (op == 15) return vec4((1.0 - ((1.0 - dest.rgb) * (1.0 - src.rgb))) * src.a, dest.a * src.a);
  if (op == 16) return vec4(blendOverlay(src.rgb, dest.rgb) * src.a, dest.a * src.a);
  if (op == 17) return vec4(max(src.rgb, dest.rgb) * src.a, dest.a * src.a);    // Lighten
  if (op == 18) return vec4(blendColorDodge(src.rgb, dest.rgb) * src.a, dest.a * src.a);
  if (op == 19) return vec4(blendColorBurn(src.rgb, dest.rgb) * src.a, dest.a * src.a);
  if (op == 20) return vec4(blendOverlay(dest.rgb, src.rgb) * src.a, dest.a * src.a);  // HardLight
  if (op == 21) return vec4(blendSoftLight(src.rgb, dest.rgb) * src.a, dest.a * src.a);
  if (op == 22) return vec4((dest.rgb + src.rgb - 2.0 * dest.rgb * src.rgb) * src.a, dest.a * src.a);
  if (op == 23) return vec4(blendHue(src.rgb, dest.rgb) * src.a, dest.a * src.a);
  if (op == 24) return vec4(blendSaturation(src.rgb, dest.rgb) * src.a, dest.a * src.a);
  if (op == 25) return vec4(blendColorFn(src.rgb, dest.rgb) * src.a, dest.a * src.a);
  if (op == 26) return vec4(blendLuminosity(src.rgb, dest.rgb) * src.a, dest.a * src.a);
  return src;
}

void fillBlend() {
  out_Color = calcBlend();
}

void fillMask() {
  fillImage(ex_TexCoord);
  float alpha = texture2D(Texture2, ex_ObjectCoord).a;
  out_Color *= alpha;
}

// Texture1 is an A8 coverage mask. On a GL 2.1 context without ARB_texture_rg it is uploaded as
// GL_LUMINANCE8, which still lands the coverage in .r — see gpu_driver_gl.cpp.
void fillGlyph(vec2 uv) {
  float alpha = texture2D(Texture1, uv).r * ex_Color.a;
  alpha = clamp(alpha, 0.0, 1.0);
  float fill_color_luma = ex_Data0.y;
  float corrected_alpha = texture2D(Texture2, vec2(alpha, fill_color_luma)).r;
  out_Color = vec4(ex_Color.rgb * corrected_alpha, corrected_alpha);
}

void main(void) {
  int fill_type = FillType();
  if (fill_type == 0)       fillSolid();
  else if (fill_type == 1)  fillImage(ex_TexCoord);
  else if (fill_type == 2)  fillPatternImage();
  else if (fill_type == 3)  fillPatternGradient();
  else if (fill_type == 7)  fillRoundedRect();
  else if (fill_type == 8)  fillBoxShadow();
  else if (fill_type == 9)  fillBlend();
  else if (fill_type == 10) fillMask();
  else if (fill_type == 11) fillGlyph(ex_TexCoord);
  else                      out_Color = vec4(0.0);

  applyClip();
  gl_FragColor = out_Color;
}
)GLSL";

} // namespace shaders
} // namespace voidul
