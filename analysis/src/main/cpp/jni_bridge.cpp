// JNI surface for SceneMotionAnalyzer. Android-only; the whole file compiles to nothing
// on the host so the algorithm in scene_motion.cpp stays host-testable.
#if defined(__ANDROID__)

#include <jni.h>

#include <vector>

#include "scene_motion.h"

using ch0p::SceneMotionAnalyzer;
using ch0p::AnalyzerConfig;

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_ch0p_analysis_NativeAnalyzer_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new SceneMotionAnalyzer(AnalyzerConfig{}));
}

JNIEXPORT void JNICALL
Java_io_ch0p_analysis_NativeAnalyzer_nativePushFrame(
        JNIEnv* env, jobject, jlong handle, jbyteArray rgb, jint width, jint height, jdouble tSec) {
    auto* a = reinterpret_cast<SceneMotionAnalyzer*>(handle);
    if (a == nullptr) return;
    jbyte* buf = env->GetByteArrayElements(rgb, nullptr);
    a->pushFrame(reinterpret_cast<const uint8_t*>(buf), width, height, tSec);
    env->ReleaseByteArrayElements(rgb, buf, JNI_ABORT);  // read-only, no copy-back
}

JNIEXPORT jdoubleArray JNICALL
Java_io_ch0p_analysis_NativeAnalyzer_nativeCutTimes(JNIEnv* env, jobject, jlong handle) {
    auto* a = reinterpret_cast<SceneMotionAnalyzer*>(handle);
    std::vector<double> cuts = a ? a->cutTimes() : std::vector<double>{};
    jdoubleArray out = env->NewDoubleArray(static_cast<jsize>(cuts.size()));
    if (!cuts.empty()) env->SetDoubleArrayRegion(out, 0, static_cast<jsize>(cuts.size()), cuts.data());
    return out;
}

JNIEXPORT jfloatArray JNICALL
Java_io_ch0p_analysis_NativeAnalyzer_nativeMotion(JNIEnv* env, jobject, jlong handle) {
    auto* a = reinterpret_cast<SceneMotionAnalyzer*>(handle);
    std::vector<float> v;
    if (a) for (const auto& s : a->samples()) v.push_back(s.motion);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(v.size()));
    if (!v.empty()) env->SetFloatArrayRegion(out, 0, static_cast<jsize>(v.size()), v.data());
    return out;
}

JNIEXPORT jfloatArray JNICALL
Java_io_ch0p_analysis_NativeAnalyzer_nativeSharpness(JNIEnv* env, jobject, jlong handle) {
    auto* a = reinterpret_cast<SceneMotionAnalyzer*>(handle);
    std::vector<float> v;
    if (a) for (const auto& s : a->samples()) v.push_back(s.sharpness);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(v.size()));
    if (!v.empty()) env->SetFloatArrayRegion(out, 0, static_cast<jsize>(v.size()), v.data());
    return out;
}

JNIEXPORT jfloatArray JNICALL
Java_io_ch0p_analysis_NativeAnalyzer_nativeColorfulness(JNIEnv* env, jobject, jlong handle) {
    auto* a = reinterpret_cast<SceneMotionAnalyzer*>(handle);
    std::vector<float> v;
    if (a) for (const auto& s : a->samples()) v.push_back(s.colorfulness);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(v.size()));
    if (!v.empty()) env->SetFloatArrayRegion(out, 0, static_cast<jsize>(v.size()), v.data());
    return out;
}

JNIEXPORT jdoubleArray JNICALL
Java_io_ch0p_analysis_NativeAnalyzer_nativeSampleTimes(JNIEnv* env, jobject, jlong handle) {
    auto* a = reinterpret_cast<SceneMotionAnalyzer*>(handle);
    std::vector<double> v;
    if (a) for (const auto& s : a->samples()) v.push_back(s.tSec);
    jdoubleArray out = env->NewDoubleArray(static_cast<jsize>(v.size()));
    if (!v.empty()) env->SetDoubleArrayRegion(out, 0, static_cast<jsize>(v.size()), v.data());
    return out;
}

JNIEXPORT void JNICALL
Java_io_ch0p_analysis_NativeAnalyzer_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<SceneMotionAnalyzer*>(handle);
}

}  // extern "C"

#endif  // __ANDROID__
