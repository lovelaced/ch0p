// JNI bridge to whisper.cpp for on-device ASR with word-level timestamps. Android-only.
// Returns a tab/newline-delimited word list ("text\tstartMs\tendMs\n") that Kotlin parses
// (WhisperParser) — keeps the parsing host/JVM-testable and the JNI surface tiny.
#if defined(__ANDROID__)

#include <jni.h>

#include <string>

#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_ch0p_analysis_Whisper_nativeInit(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // CPU is the reliable path on Android
    whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_io_ch0p_analysis_Whisper_nativeTranscribe(
        JNIEnv* env, jobject, jlong handle, jfloatArray pcm, jstring lang, jint threads) {
    auto* ctx = reinterpret_cast<whisper_context*>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");

    const jsize n = env->GetArrayLength(pcm);
    jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.token_timestamps = true;          // per-token times -> word timing
    wparams.n_threads = threads > 0 ? threads : 4;
    const char* l = env->GetStringUTFChars(lang, nullptr);
    wparams.language = l;                      // "auto" or e.g. "en"

    const int rc = whisper_full(ctx, wparams, samples, n);

    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(lang, l);
    if (rc != 0) return env->NewStringUTF("");

    // Group sub-word tokens into words (a leading space marks a new word).
    std::string out;
    std::string word;
    int64_t wStart = -1, wEnd = 0;
    auto flush = [&]() {
        if (!word.empty() && wStart >= 0) {
            out += word; out += '\t';
            out += std::to_string(wStart); out += '\t';
            out += std::to_string(wEnd); out += '\n';
        }
        word.clear(); wStart = -1;
    };

    const int nseg = whisper_full_n_segments(ctx);
    for (int s = 0; s < nseg; ++s) {
        const int ntok = whisper_full_n_tokens(ctx, s);
        for (int t = 0; t < ntok; ++t) {
            if (whisper_full_get_token_id(ctx, s, t) >= whisper_token_eot(ctx)) continue;
            const char* txt = whisper_full_get_token_text(ctx, s, t);
            if (txt == nullptr) continue;
            whisper_token_data td = whisper_full_get_token_data(ctx, s, t);
            const int64_t t0 = td.t0 * 10;  // centiseconds -> ms
            const int64_t t1 = td.t1 * 10;

            const bool startsWord = txt[0] == ' ';
            if (startsWord) flush();
            word += startsWord ? (txt + 1) : txt;
            if (wStart < 0) wStart = t0;
            wEnd = t1;
        }
        flush();  // segment boundary ends a word
    }
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_io_ch0p_analysis_Whisper_nativeFree(JNIEnv*, jobject, jlong handle) {
    auto* ctx = reinterpret_cast<whisper_context*>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

}  // extern "C"

#endif  // __ANDROID__
