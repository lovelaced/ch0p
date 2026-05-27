cmake_minimum_required(VERSION 3.22.1)
project(ch0panalysis LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp (on-device ASR) via FetchContent — built static, linked into our one .so.
include(FetchContent)
set(BUILD_SHARED_LIBS   OFF CACHE BOOL "" FORCE)  # static whisper+ggml into ch0panalysis.so
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)  # avoid OpenMP runtime issues on Android
# Cross-compiling sets GGML_NATIVE OFF, which otherwise leaves -march empty → no dotprod/fp16
# SIMD (2-4x slower on Tensor). Pin the ARM arch so ggml emits the fast quantized matmul kernels.
# armv8.2-a+dotprod+fp16 is safe across all Tensor/Pixel cores (A76+); i8mm omitted for portability.
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH      "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG        v1.8.4
)
FetchContent_MakeAvailable(whisper)

# One shared lib for the whole native surface. Algorithm + JNI bridges.
add_library(ch0panalysis SHARED
    scene_motion.cpp
    jni_bridge.cpp
    whisper_jni.cpp)

target_compile_options(ch0panalysis PRIVATE -O3 -fvisibility=hidden)

find_library(log-lib log)
target_link_libraries(ch0panalysis whisper ${log-lib})

# --- Future native deps (FetchContent, built static, linked in) ---
#   OpenCV -> Lucas-Kanade optical flow + RANSAC global motion
#   aubio  -> beat/tempo detection ; libebur128 -> LUFS loudness
