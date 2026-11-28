#include "c-api.h"
#include <jni.h>
#include <cstdint>
#include <string>

namespace {
std::string text(JNIEnv *env, jbyteArray value) {
    if (!value) return {};
    std::string result(env->GetArrayLength(value), '\0');
    env->GetByteArrayRegion(value, 0, static_cast<jsize>(result.size()),
        reinterpret_cast<jbyte *>(result.data()));
    return result;
}

struct Playback {
    JNIEnv *env;
    jobject sink;
    jmethodID write;
    bool active = true;
    bool wrote = false;
};

int32_t deliver(const float *samples, int32_t count, float, void *opaque) {
    auto &play = *static_cast<Playback *>(opaque);
    if (!play.active) return 0;
    if (count <= 0 || !samples) return 1;
    // Keep each JNI allocation small, even for dense Mandarin or slow speeds.
    for (int32_t start = 0; start < count;) {
        const int32_t size = count - start > 4096 ? 4096 : count - start;
        jfloatArray pcm = play.env->NewFloatArray(size);
        if (!pcm) {
            play.active = false;
            return 0;
        }
        play.env->SetFloatArrayRegion(pcm, 0, size, samples + start);
        play.active = !play.env->ExceptionCheck() &&
            play.env->CallBooleanMethod(play.sink, play.write, pcm);
        play.env->DeleteLocalRef(pcm);
        if (!play.active || play.env->ExceptionCheck()) {
            play.active = false;
            return 0;
        }
        play.wrote = true;
        start += size;
    }
    return 1;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_hermesmobile_app_voice_KokoroNative_open(JNIEnv *env, jobject, jbyteArray path) {
    const auto root = text(env, path);
    if (root.empty() || env->ExceptionCheck()) return 0;
    const auto model = root + "/model.int8.onnx";
    const auto tokens = root + "/tokens.txt";
    const auto voices = root + "/voices.bin";
    const auto dict = root + "/dict";
    const auto lexicon = root + "/lexicon-us-en.txt," + root + "/lexicon-zh.txt";
    const auto rules = root + "/date-zh.fst," + root + "/number-zh.fst," + root + "/phone-zh.fst";
    SherpaOnnxOfflineTtsConfig config{};
    config.model.provider = "cpu";
    config.model.num_threads = 2;
    config.model.kokoro.model = model.c_str();
    config.model.kokoro.tokens = tokens.c_str();
    config.model.kokoro.voices = voices.c_str();
    config.model.kokoro.dict_dir = dict.c_str();
    config.model.kokoro.lexicon = lexicon.c_str();
    config.model.kokoro.length_scale = 1.0f;
    config.rule_fsts = rules.c_str();
    config.max_num_sentences = 1;
    config.silence_scale = 1.0f;
    const auto *tts = SherpaOnnxCreateOfflineTts(&config);
    if (tts && SherpaOnnxOfflineTtsSampleRate(tts) == 24000 &&
        SherpaOnnxOfflineTtsNumSpeakers(tts) == 103) return reinterpret_cast<jlong>(tts);
    if (tts) SherpaOnnxDestroyOfflineTts(tts);
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_hermesmobile_app_voice_KokoroNative_generate(
    JNIEnv *env, jobject, jlong handle, jbyteArray input, jint speaker, jfloat speed, jobject sink) {
    if (!handle || !sink || speaker < 0 || speaker >= 103 || !(speed >= .75f && speed <= 1.25f)) return false;
    const auto value = text(env, input);
    if (value.empty() || value.size() > 1024 || env->ExceptionCheck()) return false;
    jclass type = env->GetObjectClass(sink);
    const auto method = type ? env->GetMethodID(type, "write", "([F)Z") : nullptr;
    if (type) env->DeleteLocalRef(type);
    if (!method) return false;
    Playback play{env, sink, method};
    SherpaOnnxGenerationConfig config{};
    config.speed = speed;
    config.sid = speaker;
    config.silence_scale = 1.0f;
    const auto *audio = SherpaOnnxOfflineTtsGenerateWithConfig(
        reinterpret_cast<const SherpaOnnxOfflineTts *>(handle), value.c_str(), &config, deliver, &play);
    if (!audio) return false;
    SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
    return play.active && play.wrote;
}

extern "C" JNIEXPORT void JNICALL
Java_org_hermesmobile_app_voice_KokoroNative_close(JNIEnv *, jobject, jlong handle) {
    if (handle) SherpaOnnxDestroyOfflineTts(reinterpret_cast<const SherpaOnnxOfflineTts *>(handle));
}
