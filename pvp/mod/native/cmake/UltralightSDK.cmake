# ------------------------------------------------------------------------------------------------
# UltralightSDK.cmake — fetch + cache the Ultralight SDK, keyed by platform.
#
# The SDK is a ~20 MB .7z per platform. We do NOT vendor it; it is downloaded once into
# ${VOIDUL_SDK_DIR} (default: <native>/sdk, gitignored) and extracted to
# ${VOIDUL_SDK_DIR}/<platform-key>/.
#
# Exposes:
#   ULTRALIGHT_SDK_DIR        the extracted SDK root for the host platform
#   ULTRALIGHT_PLATFORM_KEY   win-x64 | mac-x64 | mac-arm64 | linux-x64 | linux-arm64
#   target  ultralight::sdk   INTERFACE target carrying include dirs + link libs
#   ULTRALIGHT_RUNTIME_LIBS   list of absolute paths to the runtime shared libraries
#   ULTRALIGHT_RESOURCES_DIR  absolute path to the SDK's resources/ (cacert.pem, icudt67l.dat)
#
# NOTE ON THE DOWNLOAD HOST (important, read before "fixing" a 404):
#   The "release" bucket
#     https://ultralight-sdk.sfo2.cdn.digitaloceanspaces.com/ultralight-sdk-latest-<plat>.7z
#   still serves **1.3.0** (rev 208d653, 2023-07-24) and has **no mac-arm64 build at all**.
#   The 1.4 line lives in the *dev* bucket, which is where the official docs point for 1.4:
#     https://ultralight-sdk-dev.sfo2.cdn.digitaloceanspaces.com/ultralight-sdk-<rev>-<plat>.7z
#   We pin an exact revision rather than "latest" so builds are reproducible, and verify SHA-256.
# ------------------------------------------------------------------------------------------------

set(ULTRALIGHT_REV "081c48b" CACHE STRING "Ultralight SDK revision to fetch")
set(ULTRALIGHT_VERSION_EXPECTED "1.4.0b.081c48b")
set(ULTRALIGHT_BASE_URL "https://ultralight-sdk-dev.sfo2.cdn.digitaloceanspaces.com"
    CACHE STRING "Base URL of the Ultralight SDK bucket")
set(VOIDUL_SDK_DIR "${CMAKE_CURRENT_LIST_DIR}/../sdk" CACHE PATH "Where SDK archives are cached")
get_filename_component(VOIDUL_SDK_DIR "${VOIDUL_SDK_DIR}" ABSOLUTE)

# ---- platform key --------------------------------------------------------------------------------
if(WIN32)
  set(ULTRALIGHT_PLATFORM_KEY "win-x64")
elseif(APPLE)
  # Honour an explicit CMAKE_OSX_ARCHITECTURES (single-arch only; Ultralight ships no fat binaries).
  if(CMAKE_OSX_ARCHITECTURES)
    list(LENGTH CMAKE_OSX_ARCHITECTURES _n_arch)
    if(_n_arch GREATER 1)
      message(FATAL_ERROR
        "Ultralight ships separate mac-x64 and mac-arm64 SDKs; universal builds are not possible. "
        "Configure once per arch with -DCMAKE_OSX_ARCHITECTURES=x86_64 and again with arm64.")
    endif()
    set(_arch "${CMAKE_OSX_ARCHITECTURES}")
  else()
    set(_arch "${CMAKE_HOST_SYSTEM_PROCESSOR}")
  endif()
  if(_arch MATCHES "arm64|aarch64")
    set(ULTRALIGHT_PLATFORM_KEY "mac-arm64")
  else()
    set(ULTRALIGHT_PLATFORM_KEY "mac-x64")
  endif()
elseif(UNIX)
  if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
    set(ULTRALIGHT_PLATFORM_KEY "linux-arm64")
  else()
    set(ULTRALIGHT_PLATFORM_KEY "linux-x64")
  endif()
else()
  message(FATAL_ERROR "Unsupported platform for the Ultralight SDK")
endif()

# ---- pinned hashes -------------------------------------------------------------------------------
set(_ul_sha_win-x64     "3899e728293bb12bbd5d828bf2a2d622647c35c438fd81160baf01ea6c5a8cc4")
set(_ul_sha_mac-x64     "1ee67a0b8484ca2dec8d43a2710e87ef94256f06383514869491336aee170001")
set(_ul_sha_mac-arm64   "d9b459fcab7116df6d24b355a2c521657f2c058ca9a1ec18cec38e4bb424807f")
set(_ul_sha_linux-x64   "41a2b5034112d764acef1ecf63e19c0fafff3616c3ac3347a0473f120a535ec8")
set(_ul_sha_linux-arm64 "")  # not pinned; not a shipping target

set(_ul_archive "${VOIDUL_SDK_DIR}/ultralight-sdk-${ULTRALIGHT_REV}-${ULTRALIGHT_PLATFORM_KEY}.7z")
set(ULTRALIGHT_SDK_DIR "${VOIDUL_SDK_DIR}/${ULTRALIGHT_PLATFORM_KEY}")

# ---- download ------------------------------------------------------------------------------------
if(NOT EXISTS "${ULTRALIGHT_SDK_DIR}/VERSION.txt")
  if(NOT EXISTS "${_ul_archive}")
    file(MAKE_DIRECTORY "${VOIDUL_SDK_DIR}")
    set(_url "${ULTRALIGHT_BASE_URL}/ultralight-sdk-${ULTRALIGHT_REV}-${ULTRALIGHT_PLATFORM_KEY}.7z")
    message(STATUS "Downloading Ultralight SDK: ${_url}")
    set(_hash_args)
    if(_ul_sha_${ULTRALIGHT_PLATFORM_KEY})
      set(_hash_args EXPECTED_HASH "SHA256=${_ul_sha_${ULTRALIGHT_PLATFORM_KEY}}")
    endif()
    file(DOWNLOAD "${_url}" "${_ul_archive}.part" ${_hash_args} SHOW_PROGRESS STATUS _dl TLS_VERIFY ON)
    list(GET _dl 0 _dl_code)
    if(NOT _dl_code EQUAL 0)
      list(GET _dl 1 _dl_msg)
      file(REMOVE "${_ul_archive}.part")
      message(FATAL_ERROR "Ultralight SDK download failed (${_dl_code}): ${_dl_msg}\n  URL: ${_url}")
    endif()
    file(RENAME "${_ul_archive}.part" "${_ul_archive}")
  endif()
  message(STATUS "Extracting ${_ul_archive}")
  file(MAKE_DIRECTORY "${ULTRALIGHT_SDK_DIR}")
  # CMake's bundled libarchive reads 7-Zip/LZMA, so no p7zip dependency.
  file(ARCHIVE_EXTRACT INPUT "${_ul_archive}" DESTINATION "${ULTRALIGHT_SDK_DIR}")
  # Drop the parts we never build against (~90 MB of GLFW sources + Web Inspector assets).
  file(REMOVE_RECURSE "${ULTRALIGHT_SDK_DIR}/samples" "${ULTRALIGHT_SDK_DIR}/tools")
endif()

file(READ "${ULTRALIGHT_SDK_DIR}/VERSION.txt" _ul_version)
string(STRIP "${_ul_version}" ULTRALIGHT_SDK_VERSION)
if(NOT ULTRALIGHT_SDK_VERSION STREQUAL ULTRALIGHT_VERSION_EXPECTED)
  message(WARNING "Ultralight SDK version is '${ULTRALIGHT_SDK_VERSION}', expected "
                  "'${ULTRALIGHT_VERSION_EXPECTED}'. Delete ${ULTRALIGHT_SDK_DIR} to re-fetch.")
endif()

# Cross-check the header macro too — VERSION.txt is metadata, Defines.h is what we compile against.
file(STRINGS "${ULTRALIGHT_SDK_DIR}/include/Ultralight/Defines.h" _ul_ver_line
     REGEX "^#define ULTRALIGHT_VERSION \"")
string(REGEX REPLACE ".*\"(.*)\".*" "\\1" ULTRALIGHT_HEADER_VERSION "${_ul_ver_line}")
if(NOT ULTRALIGHT_HEADER_VERSION MATCHES "^1\\.4\\.")
  message(FATAL_ERROR "Ultralight headers report ${ULTRALIGHT_HEADER_VERSION}; this binding targets 1.4.x")
endif()
message(STATUS "Ultralight SDK ${ULTRALIGHT_SDK_VERSION} (headers ${ULTRALIGHT_HEADER_VERSION}) "
               "for ${ULTRALIGHT_PLATFORM_KEY} at ${ULTRALIGHT_SDK_DIR}")

# ---- link + runtime file lists -------------------------------------------------------------------
set(ULTRALIGHT_RESOURCES_DIR "${ULTRALIGHT_SDK_DIR}/resources")

if(WIN32)
  set(_ul_link
    "${ULTRALIGHT_SDK_DIR}/lib/UltralightCore.lib"
    "${ULTRALIGHT_SDK_DIR}/lib/Ultralight.lib"
    "${ULTRALIGHT_SDK_DIR}/lib/WebCore.lib")
  set(ULTRALIGHT_RUNTIME_LIBS
    "${ULTRALIGHT_SDK_DIR}/bin/UltralightCore.dll"
    "${ULTRALIGHT_SDK_DIR}/bin/Ultralight.dll"
    "${ULTRALIGHT_SDK_DIR}/bin/WebCore.dll")
elseif(APPLE)
  set(_ul_link
    "${ULTRALIGHT_SDK_DIR}/bin/libUltralightCore.dylib"
    "${ULTRALIGHT_SDK_DIR}/bin/libUltralight.dylib"
    "${ULTRALIGHT_SDK_DIR}/bin/libWebCore.dylib")
  set(ULTRALIGHT_RUNTIME_LIBS ${_ul_link})
else()
  set(_ul_link
    "${ULTRALIGHT_SDK_DIR}/bin/libUltralightCore.so"
    "${ULTRALIGHT_SDK_DIR}/bin/libUltralight.so"
    "${ULTRALIGHT_SDK_DIR}/bin/libWebCore.so")
  set(ULTRALIGHT_RUNTIME_LIBS ${_ul_link})
endif()

# We deliberately do NOT link AppCore: on Linux it drags in GTK3, and on every platform it owns
# window/run-loop creation, which is exactly what Minecraft already provides.
add_library(ultralight_sdk INTERFACE)
add_library(ultralight::sdk ALIAS ultralight_sdk)
target_include_directories(ultralight_sdk INTERFACE "${ULTRALIGHT_SDK_DIR}/include")
target_link_libraries(ultralight_sdk INTERFACE ${_ul_link})
