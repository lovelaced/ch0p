#ifndef CH0P_LOG_H
#define CH0P_LOG_H

#if defined(__ANDROID__)
#  include <android/log.h>
#  define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ch0p", __VA_ARGS__)
#  define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ch0p", __VA_ARGS__)
#else
#  include <cstdio>
#  define LOGI(...) do { std::fprintf(stderr, __VA_ARGS__); std::fputc('\n', stderr); } while (0)
#  define LOGE(...) do { std::fprintf(stderr, __VA_ARGS__); std::fputc('\n', stderr); } while (0)
#endif

#endif  // CH0P_LOG_H
