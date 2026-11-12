#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cerrno>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"
#include "ggml-backend.h"
#include "chat.h"
#include "local_chat_output.h"
#include "common.h"
#include "log.h"
#include "sampling.h"

namespace {
// Result codes are intentionally fixed, without filenames, prompts or upstream exception text.
constexpr int ok = 0, cancelled = 1, context_full = 2, failed = 3, invalid = 4, token_limit = 5;
struct Engine {
    std::mutex operation_mutex;
    std::atomic<jlong> cancelled_through{0};
    std::atomic<jlong> active{0};
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    int context_size = 2048;
    int threads = 4;
    common_chat_templates_ptr templates;
    common_params_sampling sampling;
    bool stopped() const { return cancelled_through.load() >= active.load(); }
    void clear() {
        templates.reset();
        if (context) { llama_free(context); context = nullptr; }
        if (model) { llama_model_free(model); model = nullptr; }
    }
    ~Engine() { clear(); }
};
std::mutex registry_mutex;
std::map<jlong, std::shared_ptr<Engine>> engines;
std::atomic<jlong> next_handle{1};
std::once_flag initialized;
std::shared_ptr<Engine> get(jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto found = engines.find(handle);
    return found == engines.end() ? nullptr : found->second;
}
void stop(const std::shared_ptr<Engine> & engine, jlong id) {
    auto previous = engine->cancelled_through.load();
    while (previous < id && !engine->cancelled_through.compare_exchange_weak(previous, id)) {}
}
bool aborted(void * value) { return static_cast<Engine *>(value)->stopped(); }
bool progress(float, void * value) { return !aborted(value); }
float model_float(const llama_model * model, llama_model_meta_key key, float fallback, float minimum, float maximum) {
    char buffer[128] = {};
    const auto size = llama_model_meta_val_str(model, llama_model_meta_key_str(key), buffer, sizeof(buffer));
    if (size < 0) return fallback;
    if (size == 0 || size >= static_cast<int>(sizeof(buffer))) throw invalid;
    errno = 0; char * end = nullptr; const float value = std::strtof(buffer, &end);
    if (errno || end == buffer || *end != '\0' || !std::isfinite(value) || value < minimum || value > maximum) throw invalid;
    return value;
}
int model_integer(const llama_model * model, llama_model_meta_key key, int fallback, int minimum, int maximum) {
    char buffer[64] = {};
    const auto size = llama_model_meta_val_str(model, llama_model_meta_key_str(key), buffer, sizeof(buffer));
    if (size < 0) return fallback;
    if (size == 0 || size >= static_cast<int>(sizeof(buffer))) throw invalid;
    errno = 0; char * end = nullptr; const long value = std::strtol(buffer, &end, 10);
    if (errno || end == buffer || *end != '\0' || value < minimum || value > maximum) throw invalid;
    return static_cast<int>(value);
}
common_params_sampling model_sampling(const llama_model * model, int context_size) {
    common_params_sampling params;
    // Deterministic sampling still needs model-declared repetition controls.
    // Accept only bounded scalar metadata, never a model-supplied sampler program.
    params.temp = 0; params.seed = 42;
    params.top_k = model_integer(model, LLAMA_MODEL_META_KEY_SAMPLING_TOP_K, params.top_k, 1, 200);
    params.top_p = model_float(model, LLAMA_MODEL_META_KEY_SAMPLING_TOP_P, params.top_p, 0, 1);
    params.min_p = model_float(model, LLAMA_MODEL_META_KEY_SAMPLING_MIN_P, params.min_p, 0, 1);
    params.penalty_repeat = model_float(model, LLAMA_MODEL_META_KEY_SAMPLING_PENALTY_REPEAT, params.penalty_repeat, 0.5f, 2);
    params.penalty_last_n = model_integer(model, LLAMA_MODEL_META_KEY_SAMPLING_PENALTY_LAST_N, params.penalty_last_n, -1, 4096);
    if (params.penalty_last_n < 0) params.penalty_last_n = context_size;
    params.penalty_last_n = std::min(params.penalty_last_n, context_size);
    return params;
}
llama_context * new_context(Engine & engine) {
    auto params = llama_context_default_params();
    params.n_ctx = engine.context_size;
    params.n_batch = 128;
    params.n_ubatch = 128;
    params.n_seq_max = 1;
    params.n_threads = engine.threads;
    params.n_threads_batch = engine.threads;
    params.abort_callback = aborted;
    params.abort_callback_data = &engine;
    return llama_init_from_model(engine.model, params);
}
std::string utf(JNIEnv * env, jstring value) {
    if (!value) throw invalid;
    const char * data = env->GetStringUTFChars(value, nullptr);
    if (!data) throw failed;
    std::string result(data);
    env->ReleaseStringUTFChars(value, data);
    return result;
}
std::string bytes(JNIEnv * env, jbyteArray array) {
    if (!array) throw invalid;
    auto size = env->GetArrayLength(array);
    if (size > 65536) throw invalid;
    std::string result(size, '\0');
    env->GetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte *>(result.data()));
    if (env->ExceptionCheck()) throw failed;
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_hermesmobile_app_local_NativeLlmBindings_create(JNIEnv * env, jobject, jstring directory) {
    try {
        auto path = utf(env, directory);
        std::call_once(initialized, [&path] {
            // Upstream logs can contain model paths/template input. Never send them to logcat.
            common_log_set_verbosity_thold(-1);
            llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
            ggml_backend_load_all_from_path(path.c_str());
            llama_backend_init();
        });
        auto handle = next_handle.fetch_add(1);
        std::lock_guard<std::mutex> lock(registry_mutex);
        engines.emplace(handle, std::make_shared<Engine>());
        return handle;
    } catch (...) { return 0; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_hermesmobile_app_local_NativeLlmBindings_available(JNIEnv *, jobject) { return JNI_TRUE; }

extern "C" JNIEXPORT jint JNICALL
Java_org_hermesmobile_app_local_NativeLlmBindings_load(JNIEnv * env, jobject, jlong handle, jstring file, jint context_size, jint threads, jlong operation) {
    auto engine = get(handle);
    if (!engine || operation <= 0 || context_size < 512 || context_size > 4096 || threads < 1 || threads > 4) return invalid;
    std::unique_lock<std::mutex> lock(engine->operation_mutex, std::try_to_lock);
    if (!lock.owns_lock()) return invalid;
    engine->active.store(operation);
    if (engine->stopped()) return cancelled;
    engine->clear();
    try {
        auto path = utf(env, file);
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        params.load_mode = LLAMA_LOAD_MODE_MMAP;
        params.check_tensors = true;
        params.progress_callback = progress;
        params.progress_callback_user_data = engine.get();
        engine->model = llama_model_load_from_file(path.c_str(), params);
        if (!engine->model) return engine->stopped() ? cancelled : failed;
        const auto * tmpl = llama_model_chat_template(engine->model, nullptr);
        if (!tmpl || std::strlen(tmpl) > 65536 || llama_model_n_ctx_train(engine->model) < context_size) { engine->clear(); return invalid; }
        engine->templates = common_chat_templates_init(engine->model, "");
        if (!engine->templates || !common_chat_templates_was_explicit(engine->templates.get())) { engine->clear(); return invalid; }
        engine->context_size = context_size;
        engine->threads = threads;
        engine->sampling = model_sampling(engine->model, context_size);
        engine->context = new_context(*engine);
        if (engine->stopped()) { engine->clear(); return cancelled; }
        if (!engine->context) { engine->clear(); return failed; }
        return ok;
    } catch (...) { engine->clear(); return failed; }
}

extern "C" JNIEXPORT jint JNICALL
Java_org_hermesmobile_app_local_NativeLlmBindings_generate(JNIEnv * env, jobject, jlong handle, jobjectArray roles, jobjectArray contents, jint limit, jlong operation, jobject sink) {
    auto engine = get(handle);
    if (!engine || !roles || !contents || !sink || operation <= 0 || limit < 1 || limit > 512) return invalid;
    std::unique_lock<std::mutex> lock(engine->operation_mutex, std::try_to_lock);
    if (!lock.owns_lock() || !engine->model || !engine->templates) return invalid;
    engine->active.store(operation);
    if (engine->stopped()) return cancelled;
    try {
        const int count = env->GetArrayLength(roles);
        if (count < 1 || count > 64 || count != env->GetArrayLength(contents)) return invalid;
        auto input = signal_grove::local_chat_inputs();
        size_t input_bytes = 0;
        for (int i = 0; i < count; ++i) {
            auto role_ref = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
            auto content_ref = static_cast<jbyteArray>(env->GetObjectArrayElement(contents, i));
            auto role = utf(env, role_ref);
            auto content = bytes(env, content_ref);
            env->DeleteLocalRef(role_ref); env->DeleteLocalRef(content_ref);
            if (role != "system" && role != "user" && role != "assistant") return invalid;
            if (i == count - 1 && role != "user") return invalid;
            input_bytes += content.size();
            if (input_bytes > 65536) return invalid;
            common_chat_msg message; message.role = role; message.content = std::move(content);
            input.messages.push_back(std::move(message));
        }
        const auto * vocab = llama_model_get_vocab(engine->model);
        common_chat_session session(engine->templates.get(), vocab, input, {});
        if (session.prompt().str().size() > 131072) return context_full;
        std::vector<llama_token> tokens;
        // The upstream formatter removes automatic BOS/EOS from template text.
        // Restore them exactly once, without parsing user-provided control tokens.
        if (llama_vocab_get_add_bos(vocab)) tokens.push_back(llama_vocab_bos(vocab));
        for (const auto & part : session.prompt().parts) {
            // Template syntax may contain control tokens; user text is always plain text.
            auto more = common_tokenize(vocab, part.text, false, !part.is_input);
            tokens.insert(tokens.end(), more.begin(), more.end());
            if (tokens.size() + limit > static_cast<size_t>(engine->context_size)) return context_full;
        }
        if (llama_vocab_get_add_eos(vocab)) tokens.push_back(llama_vocab_eos(vocab));
        if (tokens.size() + limit > static_cast<size_t>(engine->context_size)) return context_full;
        if (tokens.empty()) return invalid;
        // Each request supplies its complete, explicit history. Rebuild all transient
        // state while retaining resident weights; an interrupted recurrent decode
        // must not influence the next request through a reused scheduler/context.
        if (engine->context) { llama_free(engine->context); engine->context = nullptr; }
        if (engine->stopped()) return cancelled;
        engine->context = new_context(*engine);
        if (!engine->context) return engine->stopped() ? cancelled : failed;
        for (size_t start = 0; start < tokens.size(); start += 128) {
            if (engine->stopped()) return cancelled;
            auto batch = llama_batch_get_one(tokens.data() + start, std::min<size_t>(128, tokens.size() - start));
            if (llama_decode(engine->context, batch) != 0) return engine->stopped() ? cancelled : failed;
        }
        auto sampling = engine->sampling;
        session.apply_sampling(sampling);
        common_sampler_ptr sampler(common_sampler_init(engine->model, sampling));
        if (!sampler) return failed;
        for (const auto token : tokens) common_sampler_accept(sampler.get(), token, false);
        auto sink_class = env->GetObjectClass(sink);
        auto method = env->GetMethodID(sink_class, "onBytes", "([B)Z");
        env->DeleteLocalRef(sink_class);
        if (!method) return invalid;
        signal_grove::FinalAnswerProjection answer;
        auto emit_answer = [&](const common_chat_msg & message) {
            if (engine->stopped()) return cancelled;
            auto delta = answer.append(message);
            if (delta.empty()) return ok;
            if (delta.size() > 65536) return failed;
            auto buffer = env->NewByteArray(delta.size());
            if (!buffer) return failed;
            env->SetByteArrayRegion(buffer, 0, delta.size(), reinterpret_cast<const jbyte *>(delta.data()));
            auto accepted = env->CallBooleanMethod(sink, method, buffer);
            env->DeleteLocalRef(buffer);
            return env->ExceptionCheck() || !accepted || engine->stopped() ? cancelled : ok;
        };
        size_t generated_bytes = 0;
        for (int i = 0; i < limit; ++i) {
            if (engine->stopped()) return cancelled;
            auto token = common_sampler_sample(sampler.get(), engine->context, -1);
            common_sampler_accept(sampler.get(), token, true);
            if (llama_vocab_is_eog(vocab, token)) {
                auto status = emit_answer(session.finish());
                return status != ok ? status : answer.empty() ? failed : ok;
            }
            auto piece = common_token_to_piece(vocab, token, true);
            generated_bytes += piece.size();
            if (piece.size() > 65536 || generated_bytes > 131072) return failed;
            common_chat_input chunk; chunk.append(piece, token);
            auto status = emit_answer(session.feed(chunk));
            if (status != ok) return status;
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(engine->context, batch) != 0) return engine->stopped() ? cancelled : failed;
        }
        auto status = emit_answer(session.finish());
        return status != ok ? status : token_limit;
    } catch (...) { return failed; }
}

extern "C" JNIEXPORT void JNICALL
Java_org_hermesmobile_app_local_NativeLlmBindings_cancel(JNIEnv *, jobject, jlong handle, jlong operation) {
    auto engine = get(handle); if (engine && operation > 0) stop(engine, operation);
}
extern "C" JNIEXPORT void JNICALL
Java_org_hermesmobile_app_local_NativeLlmBindings_dispose(JNIEnv *, jobject, jlong handle) {
    // A concurrent call retains its shared_ptr until it unwinds; no use-after-free.
    auto engine = get(handle); if (!engine) return;
    stop(engine, INT64_MAX);
    std::lock_guard<std::mutex> lock(registry_mutex); engines.erase(handle);
}
