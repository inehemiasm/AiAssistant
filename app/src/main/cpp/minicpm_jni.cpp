// Chevere's bounded, serialized JNI adapter for the pinned MiniCPM llama.cpp fork.
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <stdexcept>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

namespace {
constexpr int CONTEXT = 4096, OUTPUT = 1024, BATCH = 256;
struct Session {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    mtmd_context *vision = nullptr;
    llama_sampler *sampler = nullptr;
    std::atomic<bool> cancelled{false};
    int input = 0, output = 0;
    bool ended = false;
    ~Session() {
        if (sampler) llama_sampler_free(sampler);
        if (vision) mtmd_free(vision);
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};
bool abort_eval(void *p) { return static_cast<Session *>(p)->cancelled.load(); }
// Backend logs are suppressed in release. Debug errors diagnose model compatibility.
void quiet_log(ggml_log_level level, const char *text, void *) {
#ifndef NDEBUG
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, "MiniCpmNative", "%s", text);
#endif
}
void stage(const char *text) {
#ifndef NDEBUG
    __android_log_print(ANDROID_LOG_INFO, "MiniCpmNative", "%s", text);
#endif
}
void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
std::string bytes(JNIEnv *env, jbyteArray a) {
    if (!a) return {};
    std::string s(env->GetArrayLength(a), '\0');
    env->GetByteArrayRegion(a, 0, s.size(), reinterpret_cast<jbyte *>(s.data()));
    return s;
}
Session &session(jlong handle) {
    if (!handle) throw std::runtime_error("No session");
    return *reinterpret_cast<Session *>(handle);
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_neo_chevere_data_inference_minicpm_MiniCpmNative_open(
        JNIEnv *env, jobject, jbyteArray rootBytes) {
    try {
        static std::once_flag initialized;
        std::call_once(initialized, [] { llama_log_set(quiet_log, nullptr); llama_backend_init(); });
        stage("Opening model");
        auto s = std::make_unique<Session>();
        const std::string root = bytes(env, rootBytes);
        const std::string model = root + "/MiniCPM-V-4_7-35B-A3B-mix_q3gud_im-les.gguf";
        const std::string experts = root + "/experts";
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.moe_experts_path = experts.c_str();
        mp.moe_slot_bank = 256;
        s->model = llama_model_load_from_file(model.c_str(), mp);
        if (!s->model) throw std::runtime_error("Model load failed");
        stage("Model loaded; creating context");
        auto cp = llama_context_default_params();
        cp.n_ctx = CONTEXT; cp.n_batch = BATCH; cp.n_ubatch = BATCH;
        cp.n_threads = 4; cp.n_threads_batch = 4;
        cp.abort_callback = abort_eval; cp.abort_callback_data = s.get();
        s->context = llama_init_from_model(s->model, cp);
        if (!s->context) throw std::runtime_error("Context load failed");
        stage("Context created; configuring lookahead");
        llama_set_moe_lookahead_prefetch_depth(s->context, 1);
        stage("Loading vision projector");
        auto vp = mtmd_context_params_default();
        vp.use_gpu = false; vp.n_threads = 4; vp.warmup = false;
        vp.image_max_slice_nums = 1; vp.image_max_tokens = 512;
        s->vision = mtmd_init_from_file((root + "/mmproj-model-f16.gguf").c_str(), s->model, vp);
        if (!s->vision) throw std::runtime_error("Vision load failed");
        stage("Vision projector ready");
        auto sp = llama_sampler_chain_default_params();
        s->sampler = llama_sampler_chain_init(sp);
        llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        return reinterpret_cast<jlong>(s.release());
    } catch (...) { fail(env, "MiniCPM initialization failed"); return 0; }
}
extern "C" JNIEXPORT jint JNICALL
Java_com_neo_chevere_data_inference_minicpm_MiniCpmNative_begin(
        JNIEnv *env, jobject, jlong handle, jbyteArray promptBytes, jbyteArray imageBytes) {
    try {
        auto &s = session(handle);
        s.ended = false; s.input = 0; s.output = 0;
        llama_memory_clear(llama_get_memory(s.context), true);
        llama_sampler_reset(s.sampler);
        const auto image = bytes(env, imageBytes);
        const auto prompt = bytes(env, promptBytes);
        // Exact MiniCPM/Qwen ChatML, thinking disabled. History is already bounded by the app.
        const std::string formatted = "<|im_start|>user\n" +
            (image.empty() ? "" : std::string(mtmd_default_marker()) + "\n") + prompt +
            "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n";
        using Bitmap = std::unique_ptr<mtmd_bitmap, decltype(&mtmd_bitmap_free)>;
        Bitmap bitmap(nullptr, mtmd_bitmap_free);
        if (!image.empty()) {
            bitmap.reset(mtmd_helper_bitmap_init_from_buf(s.vision,
                reinterpret_cast<const unsigned char *>(image.data()), image.size()));
            if (!bitmap) throw std::runtime_error("Invalid image");
        }
        auto chunks = std::unique_ptr<mtmd_input_chunks, decltype(&mtmd_input_chunks_free)>(
            mtmd_input_chunks_init(), mtmd_input_chunks_free);
        const mtmd_bitmap *images[] = {bitmap.get()};
        mtmd_input_text text{formatted.c_str(), true, true};
        if (mtmd_tokenize(s.vision, chunks.get(), &text, images, bitmap ? 1 : 0))
            throw std::runtime_error("Tokenization failed");
        size_t count = 0;
        for (size_t i=0; i<mtmd_input_chunks_size(chunks.get()); ++i)
            count += mtmd_input_chunk_get_n_tokens(mtmd_input_chunks_get(chunks.get(), i));
        // Reject before decode; never truncate a current request or shift memory silently.
        stage("Prompt tokenized");
        if (count + OUTPUT + 8 > CONTEXT) return -2;
        llama_pos end = 0;
        if (mtmd_helper_eval_chunks(s.vision, s.context, chunks.get(), 0, 0, BATCH, true, &end))
            throw std::runtime_error("Prefill failed");
        stage("Prompt prefilled");
        s.input = static_cast<int>(count);
        return s.input;
    } catch (...) { fail(env, "MiniCPM prompt processing failed"); return -1; }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_neo_chevere_data_inference_minicpm_MiniCpmNative_next(JNIEnv *env, jobject, jlong handle) {
    try {
        auto &s = session(handle);
        if (s.ended || s.cancelled || s.output >= OUTPUT || s.input+s.output+1 >= CONTEXT) return nullptr;
        const auto token = llama_sampler_sample(s.sampler, s.context, -1);
        if (llama_vocab_is_eog(llama_model_get_vocab(s.model), token)) {s.ended = true; return nullptr;}
        llama_sampler_accept(s.sampler, token);
        auto batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(s.context, batch)) throw std::runtime_error("Decode failed");
        ++s.output;
        const auto vocab = llama_model_get_vocab(s.model);
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (n < 0) {piece.resize(-n); n=llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);}
        if (n < 0) throw std::runtime_error("Invalid token");
        auto result = env->NewByteArray(n);
        env->SetByteArrayRegion(result, 0, n, reinterpret_cast<const jbyte *>(piece.data()));
        return result;
    } catch (...) { fail(env, "MiniCPM decoding failed"); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_neo_chevere_data_inference_minicpm_MiniCpmNative_close(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Session *>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_neo_chevere_data_inference_minicpm_MiniCpmNative_cancel(JNIEnv *, jobject, jlong handle) {
    if (handle) reinterpret_cast<Session *>(handle)->cancelled = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neo_chevere_data_inference_minicpm_MiniCpmNative_arm(JNIEnv *, jobject, jlong handle) {
    if (handle) reinterpret_cast<Session *>(handle)->cancelled = false;
}
