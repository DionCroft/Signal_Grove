# Bind the optional ARM64 speech runtime and its C API to the reviewed release.
set(kokoro_stage "${CMAKE_CURRENT_BINARY_DIR}/kokoro")
file(MAKE_DIRECTORY "${kokoro_stage}")
function(kokoro_fetch url hash destination)
    if(EXISTS "${destination}")
        file(SHA256 "${destination}" existing)
        if(existing STREQUAL hash)
            return()
        endif()
    endif()
    file(DOWNLOAD "${url}" "${destination}.part" EXPECTED_HASH "SHA256=${hash}"
        TLS_VERIFY ON TIMEOUT 300 INACTIVITY_TIMEOUT 45)
    file(RENAME "${destination}.part" "${destination}")
endfunction()

kokoro_fetch(
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-v2.0.3-android.tar.bz2"
    "2a30b222abae6d5cf544df4a1c27944279449c25dbb9c755097189cb45db1df0"
    "${kokoro_stage}/runtime.tar.bz2")
kokoro_fetch(
    "https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v2.0.3/sherpa-onnx/c-api/c-api.h"
    "1f738de63a206328253ff471edda96b011fa1bd6f2a028f82967a7761abc2edd"
    "${kokoro_stage}/c-api.h")

foreach(runtime onnxruntime sherpa-onnx-c-api)
    set(member "./jniLibs/arm64-v8a/lib${runtime}.so")
    file(ARCHIVE_EXTRACT INPUT "${kokoro_stage}/runtime.tar.bz2" DESTINATION "${kokoro_stage}"
        PATTERNS "${member}")
    add_library(${runtime} SHARED IMPORTED)
    set_target_properties(${runtime} PROPERTIES IMPORTED_LOCATION "${kokoro_stage}/${member}")
endforeach()

add_library(signal_grove_kokoro SHARED signal_grove_kokoro.cpp)
target_include_directories(signal_grove_kokoro SYSTEM PRIVATE "${kokoro_stage}")
target_link_libraries(signal_grove_kokoro PRIVATE sherpa-onnx-c-api onnxruntime)
target_compile_options(signal_grove_kokoro PRIVATE -Wall -Wextra -Werror=return-type)
target_link_options(signal_grove_kokoro PRIVATE -Wl,-z,max-page-size=16384)
