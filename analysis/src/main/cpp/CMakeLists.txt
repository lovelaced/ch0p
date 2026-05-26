cmake_minimum_required(VERSION 3.22.1)
project(ch0panalysis LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# One shared lib for the whole native surface. Algorithm + JNI bridge.
add_library(ch0panalysis SHARED
    scene_motion.cpp
    jni_bridge.cpp)

target_compile_options(ch0panalysis PRIVATE -O3 -fvisibility=hidden)

find_library(log-lib log)
target_link_libraries(ch0panalysis ${log-lib})

# --- Future native deps go here via FetchContent (built static, linked in) ---
# include(FetchContent)
#   OpenCV  -> Lucas-Kanade optical flow + RANSAC global motion
#   aubio   -> beat/tempo detection
#   libebur128 -> LUFS loudness
#   whisper.cpp -> on-device ASR with DTW word timestamps
