// Runtime OpenGL entry-point loading, deliberately self-contained.
//
// Constraints that shape this file (PVP_ARCHITECTURE §13, and the brief):
//
//   * We run inside Minecraft 1.8.9's context: **GL 2.1** on macOS (LWJGL 2, legacy profile) and a
//     compatibility context on Windows. So: no core-3.x entry points, no VAO requirement.
//   * We must NOT link a loader that creates or owns a context (GLEW/GLAD/GLFW). Everything is
//     resolved from the *current* context at first use, via wglGetProcAddress (Windows),
//     dlsym on the OpenGL framework (macOS) or glXGetProcAddress (Linux).
//   * No GL headers are included: on Windows <GL/gl.h> needs windows.h first and stops at 1.1,
//     and on macOS OpenGL/gl.h is deprecation-warning noise. The handful of types and enums we
//     need are declared here instead.

#pragma once

#include <cstddef>
#include <cstdint>

namespace voidul {
namespace gl {

// ---- types -------------------------------------------------------------------------------------
typedef unsigned int GLenum;
typedef unsigned char GLboolean;
typedef unsigned int GLbitfield;
typedef void GLvoid;
typedef int GLint;
typedef unsigned int GLuint;
typedef int GLsizei;
typedef float GLfloat;
typedef float GLclampf;
typedef char GLchar;
typedef ptrdiff_t GLintptr;
typedef ptrdiff_t GLsizeiptr;

// ---- enums we use ------------------------------------------------------------------------------
enum : GLenum {
  GL_NO_ERROR = 0,
  GL_FALSE = 0,
  GL_TRUE = 1,
  GL_TRIANGLES = 0x0004,
  GL_SRC_ALPHA = 0x0302,
  GL_ONE_MINUS_SRC_ALPHA = 0x0303,
  GL_ONE = 1,
  GL_ZERO = 0,
  GL_FUNC_ADD = 0x8006,
  GL_BLEND = 0x0BE2,
  GL_DEPTH_TEST = 0x0B71,
  GL_CULL_FACE = 0x0B44,
  GL_STENCIL_TEST = 0x0B90,
  GL_SCISSOR_TEST = 0x0C11,
  GL_TEXTURE_2D = 0x0DE1,
  GL_ALPHA_TEST = 0x0BC0,
  GL_DITHER = 0x0BD0,
  GL_COLOR_BUFFER_BIT = 0x00004000,
  GL_UNSIGNED_BYTE = 0x1401,
  GL_UNSIGNED_INT = 0x1405,
  GL_FLOAT = 0x1406,
  GL_RGBA = 0x1908,
  GL_RGBA8 = 0x8058,
  GL_BGRA = 0x80E1,
  GL_RED = 0x1903,
  GL_R8 = 0x8229,
  GL_LUMINANCE = 0x1909,
  GL_LUMINANCE8 = 0x8040,
  GL_TEXTURE_MIN_FILTER = 0x2801,
  GL_TEXTURE_MAG_FILTER = 0x2800,
  GL_TEXTURE_WRAP_S = 0x2802,
  GL_TEXTURE_WRAP_T = 0x2803,
  GL_LINEAR = 0x2601,
  GL_NEAREST = 0x2600,
  GL_CLAMP_TO_EDGE = 0x812F,
  GL_UNPACK_ALIGNMENT = 0x0CF5,
  GL_UNPACK_ROW_LENGTH = 0x0CF2,
  GL_PACK_ALIGNMENT = 0x0D05,
  GL_TEXTURE0 = 0x84C0,
  GL_ARRAY_BUFFER = 0x8892,
  GL_ELEMENT_ARRAY_BUFFER = 0x8893,
  GL_STATIC_DRAW = 0x88E4,
  GL_DYNAMIC_DRAW = 0x88E8,
  GL_FRAGMENT_SHADER = 0x8B30,
  GL_VERTEX_SHADER = 0x8B31,
  GL_COMPILE_STATUS = 0x8B81,
  GL_LINK_STATUS = 0x8B82,
  GL_INFO_LOG_LENGTH = 0x8B84,
  GL_FRAMEBUFFER = 0x8D40,
  GL_COLOR_ATTACHMENT0 = 0x8CE0,
  GL_FRAMEBUFFER_COMPLETE = 0x8CD5,
  GL_FRAMEBUFFER_BINDING = 0x8CA6,
  GL_CURRENT_PROGRAM = 0x8B8D,
  GL_ACTIVE_TEXTURE = 0x84E0,
  GL_TEXTURE_BINDING_2D = 0x8069,
  GL_ARRAY_BUFFER_BINDING = 0x8894,
  GL_ELEMENT_ARRAY_BUFFER_BINDING = 0x8895,
  GL_VERTEX_ARRAY_BINDING = 0x85B5,
  GL_VIEWPORT = 0x0BA2,
  GL_SCISSOR_BOX = 0x0C10,
  GL_BLEND_SRC_RGB = 0x80C9,
  GL_BLEND_DST_RGB = 0x80C8,
  GL_BLEND_SRC_ALPHA = 0x80CB,
  GL_BLEND_DST_ALPHA = 0x80CA,
  GL_BLEND_EQUATION_RGB = 0x8009,
  GL_BLEND_EQUATION_ALPHA = 0x883D,
  GL_COLOR_WRITEMASK = 0x0C23,
  GL_DEPTH_WRITEMASK = 0x0B72,
  GL_VERTEX_ARRAY = 0x8074,
  GL_COLOR_ARRAY = 0x8076,
  GL_TEXTURE_COORD_ARRAY = 0x8078,
  GL_NORMAL_ARRAY = 0x8075,
  GL_EXTENSIONS = 0x1F03,
  GL_NUM_EXTENSIONS = 0x821D,
  GL_VERSION = 0x1F02,
  GL_RENDERER = 0x1F01,
  GL_VENDOR = 0x1F00,
  GL_MAX_TEXTURE_SIZE = 0x0D33,
  GL_VERTEX_ATTRIB_ARRAY_ENABLED = 0x8622,
  GL_TEXTURE_MAX_LEVEL = 0x813D,
  GL_MAX_VARYING_FLOATS = 0x8B4B,
  GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872,
};

// ---- the dispatch table ------------------------------------------------------------------------
struct Api {
  // 1.x
  GLenum (*GetError)() = nullptr;
  const unsigned char* (*GetString)(GLenum) = nullptr;
  void (*GetIntegerv)(GLenum, GLint*) = nullptr;
  void (*GetBooleanv)(GLenum, GLboolean*) = nullptr;
  GLboolean (*IsEnabled)(GLenum) = nullptr;
  void (*Enable)(GLenum) = nullptr;
  void (*Disable)(GLenum) = nullptr;
  void (*BlendFunc)(GLenum, GLenum) = nullptr;
  void (*Viewport)(GLint, GLint, GLsizei, GLsizei) = nullptr;
  void (*Scissor)(GLint, GLint, GLsizei, GLsizei) = nullptr;
  void (*ClearColor)(GLclampf, GLclampf, GLclampf, GLclampf) = nullptr;
  void (*Clear)(GLbitfield) = nullptr;
  void (*DrawElements)(GLenum, GLsizei, GLenum, const void*) = nullptr;
  void (*GenTextures)(GLsizei, GLuint*) = nullptr;
  void (*DeleteTextures)(GLsizei, const GLuint*) = nullptr;
  void (*BindTexture)(GLenum, GLuint) = nullptr;
  void (*TexImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum,
                     const void*) = nullptr;
  void (*TexSubImage2D)(GLenum, GLint, GLint, GLint, GLsizei, GLsizei, GLenum, GLenum,
                        const void*) = nullptr;
  void (*TexParameteri)(GLenum, GLenum, GLint) = nullptr;
  void (*PixelStorei)(GLenum, GLint) = nullptr;
  void (*ColorMask)(GLboolean, GLboolean, GLboolean, GLboolean) = nullptr;
  void (*DepthMask)(GLboolean) = nullptr;
  void (*EnableClientState)(GLenum) = nullptr;
  void (*DisableClientState)(GLenum) = nullptr;

  // 1.3 / 1.4 / 1.5
  void (*ActiveTexture)(GLenum) = nullptr;
  void (*ClientActiveTexture)(GLenum) = nullptr;
  void (*BlendFuncSeparate)(GLenum, GLenum, GLenum, GLenum) = nullptr;
  void (*BlendEquation)(GLenum) = nullptr;
  void (*BlendEquationSeparate)(GLenum, GLenum) = nullptr;
  void (*GenBuffers)(GLsizei, GLuint*) = nullptr;
  void (*DeleteBuffers)(GLsizei, const GLuint*) = nullptr;
  void (*BindBuffer)(GLenum, GLuint) = nullptr;
  void (*BufferData)(GLenum, GLsizeiptr, const void*, GLenum) = nullptr;
  void (*BufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*) = nullptr;

  // 2.0 (shaders)
  GLuint (*CreateShader)(GLenum) = nullptr;
  void (*ShaderSource)(GLuint, GLsizei, const GLchar* const*, const GLint*) = nullptr;
  void (*CompileShader)(GLuint) = nullptr;
  void (*GetShaderiv)(GLuint, GLenum, GLint*) = nullptr;
  void (*GetShaderInfoLog)(GLuint, GLsizei, GLsizei*, GLchar*) = nullptr;
  void (*DeleteShader)(GLuint) = nullptr;
  GLuint (*CreateProgram)() = nullptr;
  void (*AttachShader)(GLuint, GLuint) = nullptr;
  void (*BindAttribLocation)(GLuint, GLuint, const GLchar*) = nullptr;
  void (*LinkProgram)(GLuint) = nullptr;
  void (*GetProgramiv)(GLuint, GLenum, GLint*) = nullptr;
  void (*GetProgramInfoLog)(GLuint, GLsizei, GLsizei*, GLchar*) = nullptr;
  void (*UseProgram)(GLuint) = nullptr;
  void (*DeleteProgram)(GLuint) = nullptr;
  GLint (*GetUniformLocation)(GLuint, const GLchar*) = nullptr;
  void (*Uniform1i)(GLint, GLint) = nullptr;
  void (*Uniform1f)(GLint, GLfloat) = nullptr;
  void (*Uniform4fv)(GLint, GLsizei, const GLfloat*) = nullptr;
  void (*UniformMatrix4fv)(GLint, GLsizei, GLboolean, const GLfloat*) = nullptr;
  void (*EnableVertexAttribArray)(GLuint) = nullptr;
  void (*DisableVertexAttribArray)(GLuint) = nullptr;
  void (*VertexAttribPointer)(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*) = nullptr;
  void (*GetVertexAttribiv)(GLuint, GLenum, GLint*) = nullptr;

  // framebuffer objects: ARB_framebuffer_object, else EXT_framebuffer_object
  void (*GenFramebuffers)(GLsizei, GLuint*) = nullptr;
  void (*DeleteFramebuffers)(GLsizei, const GLuint*) = nullptr;
  void (*BindFramebuffer)(GLenum, GLuint) = nullptr;
  void (*FramebufferTexture2D)(GLenum, GLenum, GLenum, GLuint, GLint) = nullptr;
  GLenum (*CheckFramebufferStatus)(GLenum) = nullptr;

  // vertex array objects: ARB_vertex_array_object or APPLE_vertex_array_object (optional)
  void (*GenVertexArrays)(GLsizei, GLuint*) = nullptr;
  void (*DeleteVertexArrays)(GLsizei, const GLuint*) = nullptr;
  void (*BindVertexArray)(GLuint) = nullptr;

  // capability flags, filled by load()
  bool has_vao = false;
  bool has_texture_rg = false; // GL_R8/GL_RED available (else GL_LUMINANCE8 for A8 masks)
  bool has_fbo = false;
  bool loaded = false;
  int version_major = 0;
  int version_minor = 0;
};

extern Api api;

// Resolves every entry point from the context that is current on the calling thread.
// Returns false (and logs) if anything mandatory is missing. Idempotent.
bool load();

// True if `name` appears in GL_EXTENSIONS of the current context.
bool has_extension(const char* name);

} // namespace gl
} // namespace voidul
