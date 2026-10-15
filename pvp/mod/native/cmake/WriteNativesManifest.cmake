# Writes natives/<key>/files.txt and version.txt into the staging directory.
#
# files.txt is the extraction manifest: a JAR offers no directory listing, so NativeLoader cannot
# discover what to unpack. Each line is "<relative path>\t<size in bytes>\t<sha256>": the loader
# names its cache directory after a hash of this file (so the directory name is the content), checks
# every extracted file against its SHA-256, and on a warm start compares sizes only.
#
# version.txt names the temp directory. It carries a hash of the JNI library so that a rebuilt
# binding never runs against a previously extracted copy of itself — the failure mode that costs
# an hour when it happens.
file(GLOB_RECURSE _entries RELATIVE "${STAGE_DIR}" "${STAGE_DIR}/*")
list(REMOVE_ITEM _entries "files.txt" "version.txt")
list(SORT _entries)

set(_text "")
foreach(_e IN LISTS _entries)
  file(SIZE "${STAGE_DIR}/${_e}" _size)
  file(SHA256 "${STAGE_DIR}/${_e}" _sha)
  string(APPEND _text "${_e}\t${_size}\t${_sha}\n")
endforeach()
file(WRITE "${STAGE_DIR}/files.txt" "${_text}")

set(_hash "nolib")
foreach(_candidate voidultralight.so voidultralight.dylib voidultralight.dll)
  if(EXISTS "${STAGE_DIR}/${_candidate}")
    file(SHA256 "${STAGE_DIR}/${_candidate}" _full)
    string(SUBSTRING "${_full}" 0 12 _hash)
  endif()
endforeach()
file(WRITE "${STAGE_DIR}/version.txt" "${STAMP}-${_hash}\n")
