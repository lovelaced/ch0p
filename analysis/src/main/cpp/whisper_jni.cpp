// JNI bridge to whisper.cpp for on-device ASR with word-level timestamps. Android-only.
// Returns a tab/newline-delimited word list ("text\tstartMs\tendMs\n") that Kotlin parses
// (WhisperParser) — keeps the parsing host/JVM-testable and the JNI surface tiny.
#if defined(__ANDROID__)

#include <jni.h>

#include <cmath>
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

namespace {
// Forwards whisper's 0..100 progress to Whisper.progressCallback(int) on the calling thread.
struct ProgressCtx { JNIEnv* env; jobject obj; jmethodID mid; };
void progressCb(struct whisper_context*, struct whisper_state*, int progress, void* userData) {
    auto* pc = static_cast<ProgressCtx*>(userData);
    if (pc != nullptr && pc->mid != nullptr) pc->env->CallVoidMethod(pc->obj, pc->mid, static_cast<jint>(progress));
}
}  // namespace

JNIEXPORT jstring JNICALL
Java_io_ch0p_analysis_Whisper_nativeTranscribe(
        JNIEnv* env, jobject thiz, jlong handle, jfloatArray pcm, jstring lang, jint threads,
        jboolean translate) {
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
    wparams.no_context = true;                 // independent clips: don't bleed context across joins
    wparams.temperature = 0.0f;
    wparams.temperature_inc = 0.0f;            // disable temperature-fallback re-decodes (up to ~5x)
    // Shrink the encoder context to the actual audio length (~2x faster encoder on short clips),
    // capped at the full 30s window. audio_ctx default (1500) wastes work padding short audio.
    {
        const double seconds = static_cast<double>(n) / 16000.0;
        int ac = static_cast<int>(std::ceil(((seconds / 30.0) * 1500.0 + 128.0) / 64.0) * 64.0);
        if (ac < 768) ac = 768;
        if (ac > 1500) ac = 1500;
        wparams.audio_ctx = ac;
    }
    wparams.translate = translate == JNI_TRUE; // task=translate → English output
    const char* l = env->GetStringUTFChars(lang, nullptr);
    wparams.language = l;                      // "auto" (detect) or e.g. "en"

    jclass cls = env->GetObjectClass(thiz);
    ProgressCtx pc { env, thiz, env->GetMethodID(cls, "progressCallback", "(I)V") };
    wparams.progress_callback = progressCb;
    wparams.progress_callback_user_data = &pc;

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

// Language auto-detected by the most recent nativeTranscribe (e.g. "en", "es"); "" if unknown.
JNIEXPORT jstring JNICALL
Java_io_ch0p_analysis_Whisper_nativeLanguage(JNIEnv* env, jobject, jlong handle) {
    auto* ctx = reinterpret_cast<whisper_context*>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");
    const int id = whisper_full_lang_id(ctx);
    const char* code = id >= 0 ? whisper_lang_str(id) : "";
    return env->NewStringUTF(code != nullptr ? code : "");
}

JNIEXPORT void JNICALL
Java_io_ch0p_analysis_Whisper_nativeFree(JNIEnv*, jobject, jlong handle) {
    auto* ctx = reinterpret_cast<whisper_context*>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

}  // extern "C"

#endif  // __ANDROID__
