cmake_minimum_required(VERSION 3.20)
project(voidultralight VERSION 0.1.0 LANGUAGES CXX)

# --------------------------------------------------------------------------------------------------
# void-pvp — JNI binding to Ultralight 1.4's C API + an OpenGL 2.1 GPUDriver.
#
#   Owner:  the `native` agent (pvp/CONTRACTS.md).  Consumed by `mod/` as a loadable library.
#   Output: libvoidultralight.{so,dylib} / voidultralight.dll
#
# Configure/build with scripts/build.sh (POSIX) or scripts/build.ps1 (Windows).
# --------------------------------------------------------------------------------------------------

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

option(VOIDUL_BUILD_JAVA  "Compile the Java API (and the Linux CPU test harness) with --release 8" ON)
option(VOIDUL_BUILD_TESTS "Build the CPU-renderer smoke test (needs VOIDUL_BUILD_JAVA)" ON)

include(cmake/UltralightSDK.cmake)

# --------------------------------------------------------------------------------------------------
# JNI headers.  We compile against whatever JDK is around (21 here) — jni.h is ABI-stable and we
# only use JNI 1.6 features, so the resulting library loads fine in the Java 8 JVM that runs 1.8.9.
# --------------------------------------------------------------------------------------------------
find_package(JNI QUIET)
if(NOT JNI_INCLUDE_DIRS)
  if(DEFINED ENV{JAVA_HOME})
    set(_jh "$ENV{JAVA_HOME}")
    if(WIN32)
      set(JNI_INCLUDE_DIRS "${_jh}/include" "${_jh}/include/win32")
    elseif(APPLE)
      set(JNI_INCLUDE_DIRS "${_jh}/include" "${_jh}/include/darwin")
    else()
      set(JNI_INCLUDE_DIRS "${_jh}/include" "${_jh}/include/linux")
    endif()
  else()
    message(FATAL_ERROR "No JNI headers found. Set JAVA_HOME to a JDK.")
  endif()
endif()
message(STATUS "JNI headers: ${JNI_INCLUDE_DIRS}")

# --------------------------------------------------------------------------------------------------
# The library
# --------------------------------------------------------------------------------------------------
add_library(voidultralight SHARED
  src/common.cpp
  src/platform.cpp
  src/font_loader.cpp
  src/jni_api.cpp
  src/js_bridge.cpp
  src/gl_loader.cpp
  src/gpu_driver_gl.cpp
)

target_include_directories(voidultralight PRIVATE ${JNI_INCLUDE_DIRS} src)
target_link_libraries(voidultralight PRIVATE ultralight::sdk)
target_compile_definitions(voidultralight PRIVATE
  VOIDUL_VERSION="${PROJECT_VERSION}"
  VOIDUL_ULTRALIGHT_SDK_VERSION="${ULTRALIGHT_SDK_VERSION}")

# JNI_OnLoad and every Java_* entry point is marked JNIEXPORT; everything else stays hidden.
set_target_properties(voidultralight PROPERTIES
  OUTPUT_NAME voidultralight
  PREFIX ""            # no "lib" prefix: System.load() is given an explicit path anyway, and a
                       # single name per platform keeps NativeLoader's file list trivial
  MACOSX_RPATH ON
  BUILD_WITH_INSTALL_RPATH ON)

if(APPLE)
  # Ultralight's dylibs have @rpath install names. Our library sits in the same extracted temp
  # directory as them, so @loader_path resolves them without DYLD_LIBRARY_PATH.
  set_target_properties(voidultralight PROPERTIES INSTALL_RPATH "@loader_path")
  target_link_options(voidultralight PRIVATE
    "LINKER:-rpath,@loader_path"
    "LINKER:-undefined,dynamic_lookup")   # GL entry points are resolved at runtime, never linked
elseif(UNIX)
  set_target_properties(voidultralight PROPERTIES INSTALL_RPATH "$ORIGIN")
  target_link_options(voidultralight PRIVATE "LINKER:-z,origin")
endif()

if(MSVC)
  target_compile_options(voidultralight PRIVATE /W3 /permissive- /EHsc /utf-8)
  target_compile_definitions(voidultralight PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN _CRT_SECURE_NO_WARNINGS)
  # /MD to match the SDK's CRT.
  set_property(TARGET voidultralight PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreadedDLL")
else()
  target_compile_options(voidultralight PRIVATE -Wall -Wextra -Wno-unused-parameter)
endif()

# We never link an OpenGL loader — the driver resolves entry points from the *current* context
# (Minecraft's), because creating or binding a context here would be fatal.  On Windows the only
# thing we need at link time is opengl32's wglGetProcAddress, and even that is looked up via
# GetProcAddress, so no GL library appears in any target_link_libraries.
if(WIN32)
  target_link_libraries(voidultralight PRIVATE) # (intentionally empty; see above)
elseif(NOT APPLE)
  target_link_libraries(voidultralight PRIVATE ${CMAKE_DL_LIBS})
endif()

# --------------------------------------------------------------------------------------------------
# Staging: the exact `natives/<os>-<arch>/` payload the mod JAR ships.
# --------------------------------------------------------------------------------------------------
if(WIN32)
  set(VOIDUL_NATIVES_KEY "windows-x64")
elseif(APPLE)
  if(ULTRALIGHT_PLATFORM_KEY STREQUAL "mac-arm64")
    set(VOIDUL_NATIVES_KEY "macos-arm64")
  else()
    set(VOIDUL_NATIVES_KEY "macos-x64")
  endif()
else()
  set(VOIDUL_NATIVES_KEY "linux-x64")
endif()

set(VOIDUL_STAGE_DIR "${CMAKE_BINARY_DIR}/natives/${VOIDUL_NATIVES_KEY}")

add_custom_command(TARGET voidultralight POST_BUILD
  COMMAND ${CMAKE_COMMAND} -E make_directory "${VOIDUL_STAGE_DIR}/resources/fonts"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different $<TARGET_FILE:voidultralight> "${VOIDUL_STAGE_DIR}/"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different ${ULTRALIGHT_RUNTIME_LIBS} "${VOIDUL_STAGE_DIR}/"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different
          "${ULTRALIGHT_RESOURCES_DIR}/cacert.pem" "${ULTRALIGHT_RESOURCES_DIR}/icudt67l.dat"
          "${VOIDUL_STAGE_DIR}/resources/"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different
          "${CMAKE_CURRENT_SOURCE_DIR}/assets/fonts/Inter-Variable.ttf"
          "${CMAKE_CURRENT_SOURCE_DIR}/assets/fonts/OFL.txt"
          "${VOIDUL_STAGE_DIR}/resources/fonts/"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different
          "${ULTRALIGHT_SDK_DIR}/license/NOTICES.md" "${VOIDUL_STAGE_DIR}/resources/"
  COMMAND ${CMAKE_COMMAND}
          -DSTAGE_DIR=${VOIDUL_STAGE_DIR}
          -DSTAMP=${PROJECT_VERSION}-${ULTRALIGHT_SDK_VERSION}
          -P "${CMAKE_CURRENT_SOURCE_DIR}/cmake/WriteNativesManifest.cmake"
  COMMENT "Staging natives/${VOIDUL_NATIVES_KEY}/")

# --------------------------------------------------------------------------------------------------
# Java: dev.voidclient.ultralight, compiled with --release 8 (Minecraft 1.8.9 runs on a Java 8 JVM).
# --------------------------------------------------------------------------------------------------
if(VOIDUL_BUILD_JAVA)
  find_package(Java 1.8 REQUIRED COMPONENTS Development)
  file(GLOB_RECURSE VOIDUL_JAVA_SOURCES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/java/*.java")
  set(VOIDUL_JAVA_CLASSES "${CMAKE_BINARY_DIR}/java-classes")

  add_custom_command(OUTPUT "${VOIDUL_JAVA_CLASSES}/.stamp"
    COMMAND ${CMAKE_COMMAND} -E make_directory "${VOIDUL_JAVA_CLASSES}"
    COMMAND ${Java_JAVAC_EXECUTABLE} --release 8 -Xlint:-options
            -d "${VOIDUL_JAVA_CLASSES}" ${VOIDUL_JAVA_SOURCES}
    COMMAND ${CMAKE_COMMAND} -E touch "${VOIDUL_JAVA_CLASSES}/.stamp"
    DEPENDS ${VOIDUL_JAVA_SOURCES}
    COMMENT "javac --release 8 dev.voidclient.ultralight")
  add_custom_target(voidultralight_java ALL DEPENDS "${VOIDUL_JAVA_CLASSES}/.stamp")

  # A jar so the mod build (or the test) can put the API + the natives on a classpath.
  add_custom_command(OUTPUT "${CMAKE_BINARY_DIR}/voidultralight-api.jar"
    COMMAND ${Java_JAR_EXECUTABLE} cf "${CMAKE_BINARY_DIR}/voidultralight-api.jar" -C "${VOIDUL_JAVA_CLASSES}" .
    DEPENDS "${VOIDUL_JAVA_CLASSES}/.stamp"
    COMMENT "Packaging voidultralight-api.jar")
  add_custom_target(voidultralight_api_jar ALL DEPENDS "${CMAKE_BINARY_DIR}/voidultralight-api.jar")

  if(VOIDUL_BUILD_TESTS)
    file(GLOB_RECURSE VOIDUL_TEST_SOURCES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/test/java/*.java")
    set(VOIDUL_TEST_CLASSES "${CMAKE_BINARY_DIR}/test-classes")
    add_custom_command(OUTPUT "${VOIDUL_TEST_CLASSES}/.stamp"
      COMMAND ${CMAKE_COMMAND} -E make_directory "${VOIDUL_TEST_CLASSES}"
      COMMAND ${Java_JAVAC_EXECUTABLE} --release 8 -Xlint:-options
              -cp "${VOIDUL_JAVA_CLASSES}" -d "${VOIDUL_TEST_CLASSES}" ${VOIDUL_TEST_SOURCES}
      COMMAND ${CMAKE_COMMAND} -E touch "${VOIDUL_TEST_CLASSES}/.stamp"
      DEPENDS ${VOIDUL_TEST_SOURCES} "${VOIDUL_JAVA_CLASSES}/.stamp"
      COMMENT "javac --release 8 test harness")
    add_custom_target(voidultralight_test ALL DEPENDS "${VOIDUL_TEST_CLASSES}/.stamp")

    # The classpath here is the shipping shape on purpose: ${CMAKE_BINARY_DIR} contributes
    # natives/<key>/… exactly as the mod JAR will, so the run also covers files.txt, the temp-dir
    # extraction and System.load() ordering.
    enable_testing()
    add_test(NAME cpu_render
      COMMAND ${Java_JAVA_EXECUTABLE}
              -cp "${VOIDUL_TEST_CLASSES}:${VOIDUL_JAVA_CLASSES}:${CMAKE_BINARY_DIR}:${CMAKE_CURRENT_SOURCE_DIR}/test/resources"
              dev.voidclient.ultralight.test.CpuRenderTest
              "${CMAKE_CURRENT_SOURCE_DIR}/test/expected/out.png")
  endif()
endif()

message(STATUS "voidultralight: staging to ${VOIDUL_STAGE_DIR}")
