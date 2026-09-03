#include "gl_loader.h"

#include <cstdio>
#include <cstring>
#include <string>

#include "common.h"

#if defined(_WIN32)
#include <windows.h>
#else
#include <dlfcn.h>
#endif

namespace voidul {
namespace gl {

Api api;

namespace {

#if defined(_WIN32)
HMODULE opengl32() {
  static HMODULE h = LoadLibraryA("opengl32.dll");
  return h;
}
void* resolve(const char* name) {
  // wglGetProcAddress only answers for functions above GL 1.1; everything at or below 1.1 has to
  // come out of opengl32.dll's export table.
  typedef PROC(WINAPI * PFNWGLGETPROCADDR)(LPCSTR);
  static PFNWGLGETPROCADDR wgl_get =
      opengl32() ? reinterpret_cast<PFNWGLGETPROCADDR>(
                       GetProcAddress(opengl32(), "wglGetProcAddress"))
                 : nullptr;
  void* p = nullptr;
  if (wgl_get) p = reinterpret_cast<void*>(wgl_get(name));
  // wglGetProcAddress returns these sentinels for "not supported".
  if (p == reinterpret_cast<void*>(0) || p == reinterpret_cast<void*>(1) ||
      p == reinterpret_cast<void*>(2) || p == reinterpret_cast<void*>(3) ||
      p == reinterpret_cast<void*>(-1)) {
    p = nullptr;
  }
  if (!p && opengl32()) p = reinterpret_cast<void*>(GetProcAddress(opengl32(), name));
  return p;
}
#elif defined(__APPLE__)
void* framework() {
  static void* h = dlopen("/System/Library/Frameworks/OpenGL.framework/Versions/Current/OpenGL",
                          RTLD_LAZY | RTLD_LOCAL);
  return h;
}
void* resolve(const char* name) {
  // On macOS every GL function the legacy profile exposes is a plain symbol in the framework,
  // which is already loaded into the process by LWJGL. RTLD_DEFAULT finds it without us taking
  // any ownership of the context.
  void* p = dlsym(RTLD_DEFAULT, name);
  if (!p && framework()) p = dlsym(framework(), name);
  return p;
}
#else
void* libgl() {
  static void* h = dlopen("libGL.so.1", RTLD_LAZY | RTLD_LOCAL);
  if (!h) h = dlopen("libGL.so", RTLD_LAZY | RTLD_LOCAL);
  return h;
}
void* resolve(const char* name) {
  typedef void* (*PFNGLXGETPROC)(const unsigned char*);
  static PFNGLXGETPROC glx_get = nullptr;
  static bool tried = false;
  if (!tried) {
    tried = true;
    void* h = libgl();
    if (h) {
      glx_get = reinterpret_cast<PFNGLXGETPROC>(dlsym(h, "glXGetProcAddress"));
      if (!glx_get) glx_get = reinterpret_cast<PFNGLXGETPROC>(dlsym(h, "glXGetProcAddressARB"));
    }
  }
  void* p = dlsym(RTLD_DEFAULT, name);
  if (!p && glx_get) p = glx_get(reinterpret_cast<const unsigned char*>(name));
  if (!p && libgl()) p = dlsym(libgl(), name);
  return p;
}
#endif

template <typename T>
bool bind(T& slot, const char* name, bool required, bool* ok) {
  slot = reinterpret_cast<T>(resolve(name));
  if (!slot && required) {
    log_error("gl: missing required entry point %s", name);
    if (ok) *ok = false;
    return false;
  }
  return slot != nullptr;
}

// Tries `name`, then `nameARB`, then `nameEXT`.
template <typename T>
bool bind_suffixed(T& slot, const char* name, bool* ok, bool required) {
  std::string arb = std::string(name) + "ARB";
  std::string ext = std::string(name) + "EXT";
  slot = reinterpret_cast<T>(resolve(name));
  if (!slot) slot = reinterpret_cast<T>(resolve(arb.c_str()));
  if (!slot) slot = reinterpret_cast<T>(resolve(ext.c_str()));
  if (!slot && required) {
    log_error("gl: missing required entry point %s (also tried ARB/EXT)", name);
    if (ok) *ok = false;
  }
  return slot != nullptr;
}

} // namespace

bool has_extension(const char* name) {
  if (!api.GetString) return false;
  const unsigned char* s = api.GetString(GL_EXTENSIONS);
  if (!s) return false; // core-profile contexts need glGetStringi; MC 1.8.9 never gives us one
  const char* exts = reinterpret_cast<const char*>(s);
  size_t len = strlen(name);
  const char* p = exts;
  while ((p = strstr(p, name)) != nullptr) {
    const char* end = p + len;
    if ((p == exts || p[-1] == ' ') && (*end == ' ' || *end == '\0')) return true;
    p = end;
  }
  return false;
}

bool load() {
  if (api.loaded) return true;
  bool ok = true;

  bind(api.GetError, "glGetError", true, &ok);
  bind(api.GetString, "glGetString", true, &ok);
  bind(api.GetIntegerv, "glGetIntegerv", true, &ok);
  bind(api.GetBooleanv, "glGetBooleanv", true, &ok);
  bind(api.IsEnabled, "glIsEnabled", true, &ok);
  bind(api.Enable, "glEnable", true, &ok);
  bind(api.Disable, "glDisable", true, &ok);
  bind(api.BlendFunc, "glBlendFunc", true, &ok);
  bind(api.Viewport, "glViewport", true, &ok);
  bind(api.Scissor, "glScissor", true, &ok);
  bind(api.ClearColor, "glClearColor", true, &ok);
  bind(api.Clear, "glClear", true, &ok);
  bind(api.DrawElements, "glDrawElements", true, &ok);
  bind(api.GenTextures, "glGenTextures", true, &ok);
  bind(api.DeleteTextures, "glDeleteTextures", true, &ok);
  bind(api.BindTexture, "glBindTexture", true, &ok);
  bind(api.TexImage2D, "glTexImage2D", true, &ok);
  bind(api.TexSubImage2D, "glTexSubImage2D", true, &ok);
  bind(api.TexParameteri, "glTexParameteri", true, &ok);
  bind(api.PixelStorei, "glPixelStorei", true, &ok);
  bind(api.ColorMask, "glColorMask", true, &ok);
  bind(api.DepthMask, "glDepthMask", true, &ok);
  bind(api.EnableClientState, "glEnableClientState", false, &ok);
  bind(api.DisableClientState, "glDisableClientState", false, &ok);

  bind(api.ActiveTexture, "glActiveTexture", true, &ok);
  bind(api.ClientActiveTexture, "glClientActiveTexture", false, &ok);
  bind(api.BlendFuncSeparate, "glBlendFuncSeparate", false, &ok);
  bind(api.BlendEquation, "glBlendEquation", false, &ok);
  bind(api.BlendEquationSeparate, "glBlendEquationSeparate", false, &ok);
  bind(api.GenBuffers, "glGenBuffers", true, &ok);
  bind(api.DeleteBuffers, "glDeleteBuffers", true, &ok);
  bind(api.BindBuffer, "glBindBuffer", true, &ok);
  bind(api.BufferData, "glBufferData", true, &ok);
  bind(api.BufferSubData, "glBufferSubData", true, &ok);

  bind(api.CreateShader, "glCreateShader", true, &ok);
  bind(api.ShaderSource, "glShaderSource", true, &ok);
  bind(api.CompileShader, "glCompileShader", true, &ok);
  bind(api.GetShaderiv, "glGetShaderiv", true, &ok);
  bind(api.GetShaderInfoLog, "glGetShaderInfoLog", true, &ok);
  bind(api.DeleteShader, "glDeleteShader", true, &ok);
  bind(api.CreateProgram, "glCreateProgram", true, &ok);
  bind(api.AttachShader, "glAttachShader", true, &ok);
  bind(api.BindAttribLocation, "glBindAttribLocation", true, &ok);
  bind(api.LinkProgram, "glLinkProgram", true, &ok);
  bind(api.GetProgramiv, "glGetProgramiv", true, &ok);
  bind(api.GetProgramInfoLog, "glGetProgramInfoLog", true, &ok);
  bind(api.UseProgram, "glUseProgram", true, &ok);
  bind(api.DeleteProgram, "glDeleteProgram", true, &ok);
  bind(api.GetUniformLocation, "glGetUniformLocation", true, &ok);
  bind(api.Uniform1i, "glUniform1i", true, &ok);
  bind(api.Uniform1f, "glUniform1f", true, &ok);
  bind(api.Uniform4fv, "glUniform4fv", true, &ok);
  bind(api.UniformMatrix4fv, "glUniformMatrix4fv", true, &ok);
  bind(api.EnableVertexAttribArray, "glEnableVertexAttribArray", true, &ok);
  bind(api.DisableVertexAttribArray, "glDisableVertexAttribArray", true, &ok);
  bind(api.VertexAttribPointer, "glVertexAttribPointer", true, &ok);
  bind(api.GetVertexAttribiv, "glGetVertexAttribiv", true, &ok);

  // FBOs: prefer ARB (identical to core 3.0), fall back to EXT_framebuffer_object, which is what
  // Apple's legacy 2.1 profile actually advertises.
  bool fbo = true;
  bind_suffixed(api.GenFramebuffers, "glGenFramebuffers", &fbo, true);
  bind_suffixed(api.DeleteFramebuffers, "glDeleteFramebuffers", &fbo, true);
  bind_suffixed(api.BindFramebuffer, "glBindFramebuffer", &fbo, true);
  bind_suffixed(api.FramebufferTexture2D, "glFramebufferTexture2D", &fbo, true);
  bind_suffixed(api.CheckFramebufferStatus, "glCheckFramebufferStatus", &fbo, true);
  api.has_fbo = fbo;
  if (!fbo) ok = false;

  // VAOs are optional. Ultralight's geometry works fine with plain VBOs in a 2.1 context; when a
  // VAO is available we use one per geometry purely to keep attribute state off the global one.
  bool vao = true;
  bind_suffixed(api.GenVertexArrays, "glGenVertexArrays", &vao, false);
  bind_suffixed(api.DeleteVertexArrays, "glDeleteVertexArrays", &vao, false);
  bind_suffixed(api.BindVertexArray, "glBindVertexArray", &vao, false);
  if (!api.GenVertexArrays) {
    // APPLE_vertex_array_object spells them differently.
    bind(api.GenVertexArrays, "glGenVertexArraysAPPLE", false, nullptr);
    bind(api.DeleteVertexArrays, "glDeleteVertexArraysAPPLE", false, nullptr);
    bind(api.BindVertexArray, "glBindVertexArrayAPPLE", false, nullptr);
  }
  api.has_vao = api.GenVertexArrays && api.BindVertexArray && api.DeleteVertexArrays &&
                (has_extension("GL_ARB_vertex_array_object") ||
                 has_extension("GL_APPLE_vertex_array_object"));

  // GL_RED/GL_R8 is 3.0 / ARB_texture_rg. Without it, A8 glyph masks go in as GL_LUMINANCE8 so
  // that the shader's `.r` swizzle still reads the coverage value.
  api.has_texture_rg = has_extension("GL_ARB_texture_rg");

  if (api.GetString) {
    const unsigned char* ver = api.GetString(GL_VERSION);
    if (ver) {
      sscanf(reinterpret_cast<const char*>(ver), "%d.%d", &api.version_major, &api.version_minor);
      log_info("gl: %s | %s", reinterpret_cast<const char*>(ver),
               reinterpret_cast<const char*>(api.GetString(GL_RENDERER)));
    }
    if (api.version_major >= 3) api.has_texture_rg = true;
  }
  log_info("gl: fbo=%d vao=%d texture_rg=%d", int(api.has_fbo), int(api.has_vao),
           int(api.has_texture_rg));

  api.loaded = ok;
  return ok;
}

} // namespace gl
} // namespace voidul
