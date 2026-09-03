// ULGPUDriver on OpenGL 2.1 — the half of this binding that cannot be tested off a real game.
//
// Design notes, all forced by the target (Minecraft 1.8.9 / LWJGL 2):
//
//   * GLSL 1.20 shaders (see shaders_glsl120.h); attribute locations are assigned with
//     glBindAttribLocation before linking, since `layout(location=)` needs 3.30.
//   * FBOs come from ARB_framebuffer_object where present, EXT_framebuffer_object otherwise
//     (that is what Apple's legacy 2.1 profile advertises).
//   * VAOs are used only if ARB/APPLE_vertex_array_object is there; the fallback rebinds the
//     vertex format on every draw, which a 2.1 driver is perfectly happy with.
//   * A8 coverage masks upload as GL_LUMINANCE8 unless ARB_texture_rg is present, so the glyph
//     shader's `.r` read still sees coverage.
//   * Everything we touch is saved and restored around the paint. MC's renderer is immediate-mode
//     and assumes its client-array state, texture bindings and blend func survive.

#include "gpu_driver_gl.h"

#include <cstring>
#include <map>
#include <string>
#include <vector>

#include "common.h"
#include "gl_loader.h"
#include "shaders_glsl120.h"

namespace voidul {
namespace gpu {
namespace {

using namespace voidul::gl;
#define G voidul::gl::api

// Ultralight renders views into offscreen FBOs. GL textures are bottom-left origin, Ultralight's
// coordinate space is top-left, and ulApplyProjection's flip_y flag is what reconciles the two:
// with flip_y = true the resulting texture reads correctly when sampled with v = 0 at the top,
// which is what View.glTextureId() promises its caller. (Verified against ulApplyProjection's
// output — see test/projection_probe.cpp.)
constexpr bool kFlipY = true;

constexpr int kMaxAttribs = 11;

struct TextureEntry {
  GLuint id = 0;
  uint32_t width = 0;
  uint32_t height = 0;
  bool alpha_mask = false;
};

struct RenderBufferEntry {
  GLuint fbo = 0;
  uint32_t texture_id = 0;
  uint32_t width = 0;
  uint32_t height = 0;
};

struct GeometryEntry {
  GLuint vbo = 0;
  GLuint ibo = 0;
  GLuint vao = 0;
  ULVertexBufferFormat format = kVertexBufferFormat_2f_4ub_2f;
  bool vao_configured = false;
};

struct Program {
  GLuint id = 0;
  GLint u_state = -1;
  GLint u_transform = -1;
  GLint u_scalar4 = -1;
  GLint u_vector = -1;
  GLint u_clip_size = -1;
  GLint u_clip = -1;
  GLint u_tex1 = -1;
  GLint u_tex2 = -1;
  GLint u_tex3 = -1;
};

struct SavedState {
  GLint program = 0;
  GLint active_texture = 0;
  GLint tex_binding[3] = {0, 0, 0};
  GLint array_buffer = 0;
  GLint element_buffer = 0;
  GLint framebuffer = 0;
  GLint vertex_array = 0;
  GLint viewport[4] = {0, 0, 0, 0};
  GLint scissor[4] = {0, 0, 0, 0};
  GLboolean blend = 0, scissor_test = 0, depth_test = 0, cull_face = 0, stencil_test = 0;
  GLboolean texture_2d[3] = {0, 0, 0};
  GLboolean alpha_test = 0;
  GLint blend_src_rgb = 0, blend_dst_rgb = 0, blend_src_a = 0, blend_dst_a = 0;
  GLint blend_eq_rgb = 0, blend_eq_a = 0;
  GLboolean color_mask[4] = {1, 1, 1, 1};
  GLboolean depth_mask = 1;
  GLint attrib_enabled[kMaxAttribs] = {0};
  GLboolean client_vertex = 0, client_color = 0, client_texcoord = 0, client_normal = 0;
  bool valid = false;
};

struct Driver {
  std::map<uint32_t, TextureEntry> textures;
  std::map<uint32_t, RenderBufferEntry> render_buffers;
  std::map<uint32_t, GeometryEntry> geometries;
  std::vector<ULCommand> commands;

  uint32_t next_texture_id = 1;
  uint32_t next_render_buffer_id = 1;
  uint32_t next_geometry_id = 1;

  Program fill;      // kShaderType_Fill
  Program fill_path; // kShaderType_FillPath

  SavedState saved;
  GLuint default_fbo = 0; // whatever MC had bound when we entered
  bool ready = false;
  float time_seconds = 0.0f;
};

Driver& d() {
  static Driver instance;
  return instance;
}

// ---- shader compilation ------------------------------------------------------------------------
GLuint compile(GLenum type, const std::string& src, const char* label) {
  GLuint sh = G.CreateShader(type);
  const GLchar* ptr = src.c_str();
  GLint len = static_cast<GLint>(src.size());
  G.ShaderSource(sh, 1, &ptr, &len);
  G.CompileShader(sh);
  GLint ok = 0;
  G.GetShaderiv(sh, GL_COMPILE_STATUS, &ok);
  if (!ok) {
    GLint log_len = 0;
    G.GetShaderiv(sh, GL_INFO_LOG_LENGTH, &log_len);
    std::vector<char> log(log_len > 1 ? log_len : 1, 0);
    G.GetShaderInfoLog(sh, static_cast<GLsizei>(log.size()), nullptr, log.data());
    log_error("shader %s failed to compile:\n%s", label, log.data());
    G.DeleteShader(sh);
    return 0;
  }
  return sh;
}

bool link_program(Program* p, const std::string& vs_src, const std::string& fs_src,
                  bool with_data_attribs, const char* label) {
  GLuint vs = compile(GL_VERTEX_SHADER, vs_src, label);
  if (!vs) return false;
  GLuint fs = compile(GL_FRAGMENT_SHADER, fs_src, label);
  if (!fs) {
    G.DeleteShader(vs);
    return false;
  }

  p->id = G.CreateProgram();
  G.AttachShader(p->id, vs);
  G.AttachShader(p->id, fs);

  // GLSL 1.20 has no layout qualifiers: the locations are assigned here, and they must match the
  // order gpu::draw() sets the pointers in.
  G.BindAttribLocation(p->id, 0, "in_Position");
  G.BindAttribLocation(p->id, 1, "in_Color");
  G.BindAttribLocation(p->id, 2, "in_TexCoord");
  if (with_data_attribs) {
    G.BindAttribLocation(p->id, 3, "in_ObjCoord");
    G.BindAttribLocation(p->id, 4, "in_Data0");
    G.BindAttribLocation(p->id, 5, "in_Data1");
    G.BindAttribLocation(p->id, 6, "in_Data2");
    G.BindAttribLocation(p->id, 7, "in_Data3");
    G.BindAttribLocation(p->id, 8, "in_Data4");
    G.BindAttribLocation(p->id, 9, "in_Data5");
    G.BindAttribLocation(p->id, 10, "in_Data6");
  }

  G.LinkProgram(p->id);
  G.DeleteShader(vs);
  G.DeleteShader(fs);

  GLint ok = 0;
  G.GetProgramiv(p->id, GL_LINK_STATUS, &ok);
  if (!ok) {
    GLint log_len = 0;
    G.GetProgramiv(p->id, GL_INFO_LOG_LENGTH, &log_len);
    std::vector<char> log(log_len > 1 ? log_len : 1, 0);
    G.GetProgramInfoLog(p->id, static_cast<GLsizei>(log.size()), nullptr, log.data());
    log_error("program %s failed to link:\n%s", label, log.data());
    G.DeleteProgram(p->id);
    p->id = 0;
    return false;
  }

  p->u_state = G.GetUniformLocation(p->id, "State");
  p->u_transform = G.GetUniformLocation(p->id, "Transform");
  p->u_scalar4 = G.GetUniformLocation(p->id, "Scalar4");
  p->u_vector = G.GetUniformLocation(p->id, "Vector");
  p->u_clip_size = G.GetUniformLocation(p->id, "ClipSize");
  p->u_clip = G.GetUniformLocation(p->id, "Clip");
  p->u_tex1 = G.GetUniformLocation(p->id, "Texture1");
  p->u_tex2 = G.GetUniformLocation(p->id, "Texture2");
  p->u_tex3 = G.GetUniformLocation(p->id, "Texture3");
  return true;
}

// ---- texture helpers ---------------------------------------------------------------------------
void set_texture_params() {
  G.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  G.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  G.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  G.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  // No mipmaps: everything Ultralight draws is 1:1 or scaled by the device scale, and
  // glGenerateMipmap is an FBO-extension entry point we would rather not depend on.
  G.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
}

void upload_bitmap(const TextureEntry& tex, ULBitmap bitmap, bool allocate) {
  ULBitmapFormat fmt = ulBitmapGetFormat(bitmap);
  uint32_t w = ulBitmapGetWidth(bitmap);
  uint32_t h = ulBitmapGetHeight(bitmap);
  uint32_t bpp = ulBitmapGetBpp(bitmap);
  uint32_t row_bytes = ulBitmapGetRowBytes(bitmap);

  GLint internal_format;
  GLenum format;
  if (fmt == kBitmapFormat_A8_UNORM) {
    if (G.has_texture_rg) {
      internal_format = GL_R8;
      format = GL_RED;
    } else {
      // GL 2.1 without ARB_texture_rg: LUMINANCE puts the coverage value in .r, which is what
      // the glyph shader samples. GL_ALPHA would put it in .a and render nothing.
      internal_format = GL_LUMINANCE8;
      format = GL_LUMINANCE;
    }
  } else {
    internal_format = GL_RGBA8;
    format = GL_BGRA;
  }

  void* pixels = ulBitmapLockPixels(bitmap);
  G.PixelStorei(GL_UNPACK_ALIGNMENT, 1);
  G.PixelStorei(GL_UNPACK_ROW_LENGTH, static_cast<GLint>(row_bytes / bpp));
  if (allocate) {
    G.TexImage2D(GL_TEXTURE_2D, 0, internal_format, static_cast<GLsizei>(w),
                 static_cast<GLsizei>(h), 0, format, GL_UNSIGNED_BYTE, pixels);
  } else {
    G.TexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, static_cast<GLsizei>(w), static_cast<GLsizei>(h),
                    format, GL_UNSIGNED_BYTE, pixels);
  }
  G.PixelStorei(GL_UNPACK_ROW_LENGTH, 0);
  ulBitmapUnlockPixels(bitmap);
}

void bind_texture_unit(unsigned int unit, uint32_t texture_id) {
  G.ActiveTexture(GL_TEXTURE0 + unit);
  auto it = d().textures.find(texture_id);
  G.BindTexture(GL_TEXTURE_2D, it == d().textures.end() ? 0 : it->second.id);
}

// ---- driver callbacks --------------------------------------------------------------------------
void cb_begin_synchronize() {}
void cb_end_synchronize() {}

unsigned int cb_next_texture_id() { return d().next_texture_id++; }
unsigned int cb_next_render_buffer_id() { return d().next_render_buffer_id++; }
unsigned int cb_next_geometry_id() { return d().next_geometry_id++; }

void cb_create_texture(unsigned int texture_id, ULBitmap bitmap) {
  Driver& dr = d();
  if (!dr.ready) return;
  TextureEntry tex;
  tex.width = ulBitmapGetWidth(bitmap);
  tex.height = ulBitmapGetHeight(bitmap);
  tex.alpha_mask = ulBitmapGetFormat(bitmap) == kBitmapFormat_A8_UNORM;

  G.GenTextures(1, &tex.id);
  G.ActiveTexture(GL_TEXTURE0);
  G.BindTexture(GL_TEXTURE_2D, tex.id);
  set_texture_params();

  if (ulBitmapIsEmpty(bitmap)) {
    // An empty bitmap means "this texture backs a render buffer" — allocate storage only.
    G.TexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, static_cast<GLsizei>(tex.width),
                 static_cast<GLsizei>(tex.height), 0, GL_BGRA, GL_UNSIGNED_BYTE, nullptr);
  } else {
    upload_bitmap(tex, bitmap, /*allocate=*/true);
  }
  dr.textures[texture_id] = tex;
}

void cb_update_texture(unsigned int texture_id, ULBitmap bitmap) {
  Driver& dr = d();
  if (!dr.ready) return;
  auto it = dr.textures.find(texture_id);
  if (it == dr.textures.end()) {
    cb_create_texture(texture_id, bitmap);
    return;
  }
  G.ActiveTexture(GL_TEXTURE0);
  G.BindTexture(GL_TEXTURE_2D, it->second.id);
  uint32_t w = ulBitmapGetWidth(bitmap);
  uint32_t h = ulBitmapGetHeight(bitmap);
  bool resized = (w != it->second.width || h != it->second.height);
  if (resized) {
    it->second.width = w;
    it->second.height = h;
  }
  upload_bitmap(it->second, bitmap, /*allocate=*/resized);
}

void cb_destroy_texture(unsigned int texture_id) {
  Driver& dr = d();
  if (!dr.ready) return;
  auto it = dr.textures.find(texture_id);
  if (it == dr.textures.end()) return;
  G.DeleteTextures(1, &it->second.id);
  dr.textures.erase(it);
}

void cb_create_render_buffer(unsigned int render_buffer_id, ULRenderBuffer buffer) {
  Driver& dr = d();
  if (!dr.ready) return;
  RenderBufferEntry rb;
  rb.texture_id = buffer.texture_id;
  rb.width = buffer.width;
  rb.height = buffer.height;

  auto tex = dr.textures.find(buffer.texture_id);
  if (tex == dr.textures.end()) {
    log_error("create_render_buffer %u: backing texture %u does not exist", render_buffer_id,
              buffer.texture_id);
    return;
  }

  G.GenFramebuffers(1, &rb.fbo);
  G.BindFramebuffer(GL_FRAMEBUFFER, rb.fbo);
  G.FramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex->second.id, 0);
  GLenum status = G.CheckFramebufferStatus(GL_FRAMEBUFFER);
  if (status != GL_FRAMEBUFFER_COMPLETE)
    log_error("create_render_buffer %u: framebuffer incomplete (0x%04X)", render_buffer_id, status);
  G.BindFramebuffer(GL_FRAMEBUFFER, dr.default_fbo);
  dr.render_buffers[render_buffer_id] = rb;
}

void cb_destroy_render_buffer(unsigned int render_buffer_id) {
  Driver& dr = d();
  if (!dr.ready) return;
  auto it = dr.render_buffers.find(render_buffer_id);
  if (it == dr.render_buffers.end()) return;
  G.DeleteFramebuffers(1, &it->second.fbo);
  dr.render_buffers.erase(it);
}

void upload_geometry(GeometryEntry* geo, ULVertexBuffer vertices, ULIndexBuffer indices) {
  G.BindBuffer(GL_ARRAY_BUFFER, geo->vbo);
  G.BufferData(GL_ARRAY_BUFFER, static_cast<GLsizeiptr>(vertices.size), vertices.data,
               GL_DYNAMIC_DRAW);
  G.BindBuffer(GL_ELEMENT_ARRAY_BUFFER, geo->ibo);
  G.BufferData(GL_ELEMENT_ARRAY_BUFFER, static_cast<GLsizeiptr>(indices.size), indices.data,
               GL_DYNAMIC_DRAW);
}

void cb_create_geometry(unsigned int geometry_id, ULVertexBuffer vertices, ULIndexBuffer indices) {
  Driver& dr = d();
  if (!dr.ready) return;
  GeometryEntry geo;
  geo.format = vertices.format;
  G.GenBuffers(1, &geo.vbo);
  G.GenBuffers(1, &geo.ibo);
  if (G.has_vao) G.GenVertexArrays(1, &geo.vao);
  upload_geometry(&geo, vertices, indices);
  dr.geometries[geometry_id] = geo;
}

void cb_update_geometry(unsigned int geometry_id, ULVertexBuffer vertices, ULIndexBuffer indices) {
  Driver& dr = d();
  if (!dr.ready) return;
  auto it = dr.geometries.find(geometry_id);
  if (it == dr.geometries.end()) {
    cb_create_geometry(geometry_id, vertices, indices);
    return;
  }
  it->second.format = vertices.format;
  upload_geometry(&it->second, vertices, indices);
}

void cb_destroy_geometry(unsigned int geometry_id) {
  Driver& dr = d();
  if (!dr.ready) return;
  auto it = dr.geometries.find(geometry_id);
  if (it == dr.geometries.end()) return;
  G.DeleteBuffers(1, &it->second.vbo);
  G.DeleteBuffers(1, &it->second.ibo);
  if (it->second.vao) G.DeleteVertexArrays(1, &it->second.vao);
  dr.geometries.erase(it);
}

void cb_update_command_list(ULCommandList list) {
  Driver& dr = d();
  dr.commands.assign(list.commands, list.commands + list.size);
}

// ---- drawing ----------------------------------------------------------------------------------
void set_vertex_format(ULVertexBufferFormat format) {
  if (format == kVertexBufferFormat_2f_4ub_2f) {
    const GLsizei stride = 20;
    G.EnableVertexAttribArray(0);
    G.EnableVertexAttribArray(1);
    G.EnableVertexAttribArray(2);
    for (int i = 3; i < kMaxAttribs; ++i) G.DisableVertexAttribArray(i);
    G.VertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, stride, reinterpret_cast<const void*>(0));
    G.VertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, GL_TRUE, stride,
                          reinterpret_cast<const void*>(8));
    G.VertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, stride, reinterpret_cast<const void*>(12));
  } else {
    const GLsizei stride = 140;
    for (int i = 0; i < kMaxAttribs; ++i) G.EnableVertexAttribArray(i);
    G.VertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, stride, reinterpret_cast<const void*>(0));
    G.VertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, GL_TRUE, stride,
                          reinterpret_cast<const void*>(8));
    G.VertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, stride, reinterpret_cast<const void*>(12));
    G.VertexAttribPointer(3, 2, GL_FLOAT, GL_FALSE, stride, reinterpret_cast<const void*>(20));
    for (int i = 0; i < 7; ++i) {
      G.VertexAttribPointer(static_cast<GLuint>(4 + i), 4, GL_FLOAT, GL_FALSE, stride,
                            reinterpret_cast<const void*>(28 + i * 16));
    }
  }
}

void bind_render_buffer(unsigned int render_buffer_id) {
  Driver& dr = d();
  if (render_buffer_id == 0) {
    G.BindFramebuffer(GL_FRAMEBUFFER, dr.default_fbo);
    return;
  }
  auto it = dr.render_buffers.find(render_buffer_id);
  G.BindFramebuffer(GL_FRAMEBUFFER, it == dr.render_buffers.end() ? dr.default_fbo : it->second.fbo);
}

void apply_state(const ULGPUState& state, Program* program) {
  G.UseProgram(program->id);

  ULMatrix4x4 mvp = ulApplyProjection(state.transform, static_cast<float>(state.viewport_width),
                                      static_cast<float>(state.viewport_height), kFlipY);
  if (program->u_transform >= 0)
    G.UniformMatrix4fv(program->u_transform, 1, GL_FALSE, mvp.data);

  if (program->u_state >= 0) {
    GLfloat st[4] = {d().time_seconds, static_cast<GLfloat>(state.viewport_width),
                     static_cast<GLfloat>(state.viewport_height), 1.0f};
    G.Uniform4fv(program->u_state, 1, st);
  }
  if (program->u_scalar4 >= 0) {
    GLfloat scalars[8];
    memcpy(scalars, state.uniform_scalar, sizeof(scalars));
    G.Uniform4fv(program->u_scalar4, 2, scalars);
  }
  if (program->u_vector >= 0)
    G.Uniform4fv(program->u_vector, 8, reinterpret_cast<const GLfloat*>(state.uniform_vector));
  if (program->u_clip_size >= 0) G.Uniform1i(program->u_clip_size, state.clip_size);
  if (program->u_clip >= 0)
    G.UniformMatrix4fv(program->u_clip, 8, GL_FALSE, reinterpret_cast<const GLfloat*>(state.clip));

  if (program->u_tex1 >= 0) G.Uniform1i(program->u_tex1, 0);
  if (program->u_tex2 >= 0) G.Uniform1i(program->u_tex2, 1);
  if (program->u_tex3 >= 0) G.Uniform1i(program->u_tex3, 2);

  if (state.enable_texturing) {
    bind_texture_unit(0, state.texture_1_id);
    bind_texture_unit(1, state.texture_2_id);
    bind_texture_unit(2, state.texture_3_id);
  }

  G.Viewport(0, 0, static_cast<GLsizei>(state.viewport_width),
             static_cast<GLsizei>(state.viewport_height));

  if (state.enable_blend) {
    G.Enable(GL_BLEND);
    // Ultralight's fragment output is premultiplied, so ONE / (1 - src.a) for colour, and the
    // same for alpha so the render target composites correctly when drawn over the game.
    if (G.BlendFuncSeparate)
      G.BlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    else
      G.BlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    if (G.BlendEquation) G.BlendEquation(GL_FUNC_ADD);
  } else {
    G.Disable(GL_BLEND);
  }

  if (state.enable_scissor) {
    G.Enable(GL_SCISSOR_TEST);
    const ULIntRect& r = state.scissor_rect;
    G.Scissor(r.left, r.top, r.right - r.left, r.bottom - r.top);
  } else {
    G.Disable(GL_SCISSOR_TEST);
  }
}

} // namespace

// ------------------------------------------------------------------------------------------------
bool initialized() { return d().ready; }

bool initialize() {
  Driver& dr = d();
  if (dr.ready) return true;
  if (!gl::load()) {
    log_error("gpu: OpenGL entry points unavailable — is a context current on this thread?");
    return false;
  }

  GLint max_varying = 0;
  G.GetIntegerv(GL_MAX_VARYING_FLOATS, &max_varying);
  if (max_varying > 0 && max_varying < 36) {
    // The fill shader needs 9 vec4 of varyings. GL 2.0's floor is 32 floats; every GPU that can
    // run 1.8.9 in practice reports 64, but say so loudly rather than fail at link time.
    log_error("gpu: GL_MAX_VARYING_FLOATS is %d, the fill shader needs 36 — expect a link failure",
              max_varying);
  }

  G.GetIntegerv(GL_FRAMEBUFFER_BINDING, reinterpret_cast<GLint*>(&dr.default_fbo));

  std::string frag_common(shaders::kFragCommon);
  if (!link_program(&dr.fill, shaders::kFillVertex,
                    std::string(shaders::kFillFragmentHead) + frag_common +
                        shaders::kFillFragmentBody,
                    /*with_data_attribs=*/true, "fill")) {
    return false;
  }
  if (!link_program(&dr.fill_path, shaders::kPathVertex,
                    std::string(shaders::kPathFragmentHead) + frag_common +
                        shaders::kPathFragmentBody,
                    /*with_data_attribs=*/false, "fill_path")) {
    G.DeleteProgram(dr.fill.id);
    dr.fill.id = 0;
    return false;
  }

  dr.ready = true;
  log_info("gpu: GLSL 1.20 driver ready (vao=%d, texture_rg=%d)", int(G.has_vao),
           int(G.has_texture_rg));
  return true;
}

ULGPUDriver make_driver() {
  ULGPUDriver drv;
  drv.begin_synchronize = cb_begin_synchronize;
  drv.end_synchronize = cb_end_synchronize;
  drv.next_texture_id = cb_next_texture_id;
  drv.create_texture = cb_create_texture;
  drv.update_texture = cb_update_texture;
  drv.destroy_texture = cb_destroy_texture;
  drv.next_render_buffer_id = cb_next_render_buffer_id;
  drv.create_render_buffer = cb_create_render_buffer;
  drv.destroy_render_buffer = cb_destroy_render_buffer;
  drv.next_geometry_id = cb_next_geometry_id;
  drv.create_geometry = cb_create_geometry;
  drv.update_geometry = cb_update_geometry;
  drv.destroy_geometry = cb_destroy_geometry;
  drv.update_command_list = cb_update_command_list;
  return drv;
}

void draw_command_list() {
  Driver& dr = d();
  if (!dr.ready || dr.commands.empty()) return;

  for (const ULCommand& cmd : dr.commands) {
    if (cmd.command_type == kCommandType_ClearRenderBuffer) {
      bind_render_buffer(cmd.gpu_state.render_buffer_id);
      G.Disable(GL_SCISSOR_TEST);
      G.ColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
      G.ClearColor(0.0f, 0.0f, 0.0f, 0.0f);
      G.Clear(GL_COLOR_BUFFER_BIT);
      continue;
    }

    auto geo = dr.geometries.find(cmd.geometry_id);
    if (geo == dr.geometries.end()) continue;

    bind_render_buffer(cmd.gpu_state.render_buffer_id);
    Program* program =
        cmd.gpu_state.shader_type == kShaderType_FillPath ? &dr.fill_path : &dr.fill;
    apply_state(cmd.gpu_state, program);

    if (geo->second.vao) {
      G.BindVertexArray(geo->second.vao);
      if (!geo->second.vao_configured) {
        G.BindBuffer(GL_ARRAY_BUFFER, geo->second.vbo);
        G.BindBuffer(GL_ELEMENT_ARRAY_BUFFER, geo->second.ibo);
        set_vertex_format(geo->second.format);
        geo->second.vao_configured = true;
      }
    } else {
      G.BindBuffer(GL_ARRAY_BUFFER, geo->second.vbo);
      G.BindBuffer(GL_ELEMENT_ARRAY_BUFFER, geo->second.ibo);
      set_vertex_format(geo->second.format);
    }

    G.DrawElements(GL_TRIANGLES, static_cast<GLsizei>(cmd.indices_count), GL_UNSIGNED_INT,
                   reinterpret_cast<const void*>(
                       static_cast<uintptr_t>(cmd.indices_offset) * sizeof(unsigned int)));

    if (geo->second.vao) G.BindVertexArray(0);
  }
  dr.commands.clear();
  dr.time_seconds += 1.0f / 60.0f;
}

void save_gl_state() {
  if (!gl::load()) return;
  SavedState& s = d().saved;

  G.GetIntegerv(GL_CURRENT_PROGRAM, &s.program);
  G.GetIntegerv(GL_ACTIVE_TEXTURE, &s.active_texture);
  for (int i = 0; i < 3; ++i) {
    G.ActiveTexture(GL_TEXTURE0 + i);
    G.GetIntegerv(GL_TEXTURE_BINDING_2D, &s.tex_binding[i]);
    s.texture_2d[i] = G.IsEnabled(GL_TEXTURE_2D);
  }
  G.ActiveTexture(static_cast<GLenum>(s.active_texture));

  G.GetIntegerv(GL_ARRAY_BUFFER_BINDING, &s.array_buffer);
  G.GetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING, &s.element_buffer);
  G.GetIntegerv(GL_FRAMEBUFFER_BINDING, &s.framebuffer);
  d().default_fbo = static_cast<GLuint>(s.framebuffer);
  if (G.has_vao) G.GetIntegerv(GL_VERTEX_ARRAY_BINDING, &s.vertex_array);

  G.GetIntegerv(GL_VIEWPORT, s.viewport);
  G.GetIntegerv(GL_SCISSOR_BOX, s.scissor);

  s.blend = G.IsEnabled(GL_BLEND);
  s.scissor_test = G.IsEnabled(GL_SCISSOR_TEST);
  s.depth_test = G.IsEnabled(GL_DEPTH_TEST);
  s.cull_face = G.IsEnabled(GL_CULL_FACE);
  s.stencil_test = G.IsEnabled(GL_STENCIL_TEST);
  s.alpha_test = G.IsEnabled(GL_ALPHA_TEST);

  G.GetIntegerv(GL_BLEND_SRC_RGB, &s.blend_src_rgb);
  G.GetIntegerv(GL_BLEND_DST_RGB, &s.blend_dst_rgb);
  G.GetIntegerv(GL_BLEND_SRC_ALPHA, &s.blend_src_a);
  G.GetIntegerv(GL_BLEND_DST_ALPHA, &s.blend_dst_a);
  G.GetIntegerv(GL_BLEND_EQUATION_RGB, &s.blend_eq_rgb);
  G.GetIntegerv(GL_BLEND_EQUATION_ALPHA, &s.blend_eq_a);

  G.GetBooleanv(GL_COLOR_WRITEMASK, s.color_mask);
  G.GetBooleanv(GL_DEPTH_WRITEMASK, &s.depth_mask);

  for (int i = 0; i < kMaxAttribs; ++i)
    G.GetVertexAttribiv(i, GL_VERTEX_ATTRIB_ARRAY_ENABLED, &s.attrib_enabled[i]);

  if (G.IsEnabled) {
    s.client_vertex = G.IsEnabled(GL_VERTEX_ARRAY);
    s.client_color = G.IsEnabled(GL_COLOR_ARRAY);
    s.client_texcoord = G.IsEnabled(GL_TEXTURE_COORD_ARRAY);
    s.client_normal = G.IsEnabled(GL_NORMAL_ARRAY);
  }

  s.valid = true;

  // Minecraft leaves depth/cull/alpha-test on; the UI is a 2D overlay and must not be depth-tested.
  G.Disable(GL_DEPTH_TEST);
  G.Disable(GL_CULL_FACE);
  G.Disable(GL_STENCIL_TEST);
  G.Disable(GL_ALPHA_TEST);
  G.DepthMask(GL_FALSE);
  G.ColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
  // Generic attribute arrays and fixed-function client arrays must not be enabled at the same
  // time on some drivers; MC only ever uses the latter, so turn them off for the duration.
  if (G.DisableClientState) {
    G.DisableClientState(GL_VERTEX_ARRAY);
    G.DisableClientState(GL_COLOR_ARRAY);
    G.DisableClientState(GL_TEXTURE_COORD_ARRAY);
    G.DisableClientState(GL_NORMAL_ARRAY);
  }
}

void restore_gl_state() {
  SavedState& s = d().saved;
  if (!s.valid || !G.loaded) return;

  for (int i = 0; i < kMaxAttribs; ++i) {
    if (s.attrib_enabled[i])
      G.EnableVertexAttribArray(i);
    else
      G.DisableVertexAttribArray(i);
  }
  if (G.has_vao) G.BindVertexArray(static_cast<GLuint>(s.vertex_array));

  G.BindBuffer(GL_ARRAY_BUFFER, static_cast<GLuint>(s.array_buffer));
  G.BindBuffer(GL_ELEMENT_ARRAY_BUFFER, static_cast<GLuint>(s.element_buffer));
  G.BindFramebuffer(GL_FRAMEBUFFER, static_cast<GLuint>(s.framebuffer));
  G.UseProgram(static_cast<GLuint>(s.program));

  for (int i = 0; i < 3; ++i) {
    G.ActiveTexture(GL_TEXTURE0 + i);
    G.BindTexture(GL_TEXTURE_2D, static_cast<GLuint>(s.tex_binding[i]));
    if (s.texture_2d[i])
      G.Enable(GL_TEXTURE_2D);
    else
      G.Disable(GL_TEXTURE_2D);
  }
  G.ActiveTexture(static_cast<GLenum>(s.active_texture));

  G.Viewport(s.viewport[0], s.viewport[1], s.viewport[2], s.viewport[3]);
  G.Scissor(s.scissor[0], s.scissor[1], s.scissor[2], s.scissor[3]);

  if (G.BlendFuncSeparate)
    G.BlendFuncSeparate(static_cast<GLenum>(s.blend_src_rgb), static_cast<GLenum>(s.blend_dst_rgb),
                        static_cast<GLenum>(s.blend_src_a), static_cast<GLenum>(s.blend_dst_a));
  else
    G.BlendFunc(static_cast<GLenum>(s.blend_src_rgb), static_cast<GLenum>(s.blend_dst_rgb));
  if (G.BlendEquationSeparate)
    G.BlendEquationSeparate(static_cast<GLenum>(s.blend_eq_rgb),
                            static_cast<GLenum>(s.blend_eq_a));

  s.blend ? G.Enable(GL_BLEND) : G.Disable(GL_BLEND);
  s.scissor_test ? G.Enable(GL_SCISSOR_TEST) : G.Disable(GL_SCISSOR_TEST);
  s.depth_test ? G.Enable(GL_DEPTH_TEST) : G.Disable(GL_DEPTH_TEST);
  s.cull_face ? G.Enable(GL_CULL_FACE) : G.Disable(GL_CULL_FACE);
  s.stencil_test ? G.Enable(GL_STENCIL_TEST) : G.Disable(GL_STENCIL_TEST);
  s.alpha_test ? G.Enable(GL_ALPHA_TEST) : G.Disable(GL_ALPHA_TEST);

  G.ColorMask(s.color_mask[0], s.color_mask[1], s.color_mask[2], s.color_mask[3]);
  G.DepthMask(s.depth_mask);

  if (G.EnableClientState) {
    s.client_vertex ? G.EnableClientState(GL_VERTEX_ARRAY) : G.DisableClientState(GL_VERTEX_ARRAY);
    s.client_color ? G.EnableClientState(GL_COLOR_ARRAY) : G.DisableClientState(GL_COLOR_ARRAY);
    s.client_texcoord ? G.EnableClientState(GL_TEXTURE_COORD_ARRAY)
                      : G.DisableClientState(GL_TEXTURE_COORD_ARRAY);
    s.client_normal ? G.EnableClientState(GL_NORMAL_ARRAY) : G.DisableClientState(GL_NORMAL_ARRAY);
  }

  s.valid = false;
}

void shutdown() {
  Driver& dr = d();
  if (!G.loaded) return;
  for (auto& kv : dr.textures) G.DeleteTextures(1, &kv.second.id);
  for (auto& kv : dr.render_buffers) G.DeleteFramebuffers(1, &kv.second.fbo);
  for (auto& kv : dr.geometries) {
    G.DeleteBuffers(1, &kv.second.vbo);
    G.DeleteBuffers(1, &kv.second.ibo);
    if (kv.second.vao) G.DeleteVertexArrays(1, &kv.second.vao);
  }
  dr.textures.clear();
  dr.render_buffers.clear();
  dr.geometries.clear();
  if (dr.fill.id) G.DeleteProgram(dr.fill.id);
  if (dr.fill_path.id) G.DeleteProgram(dr.fill_path.id);
  dr.fill = Program();
  dr.fill_path = Program();
  dr.ready = false;
}

} // namespace gpu
} // namespace voidul
