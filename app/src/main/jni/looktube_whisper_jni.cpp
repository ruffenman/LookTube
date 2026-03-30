#include <jni.h>

#include <string>

#include "whisper.h"

namespace {

JavaVM * g_java_vm = nullptr;
jclass g_whisper_bridge_class = nullptr;
jmethodID g_dispatch_progress_method = nullptr;

jclass find_illegal_state_exception(JNIEnv * env) {
    return env->FindClass("java/lang/IllegalStateException");
}

void throw_illegal_state(JNIEnv * env, const char * message) {
    jclass exception_class = find_illegal_state_exception(env);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
    }
}

bool ensure_progress_bridge(JNIEnv * env) {
    if (g_whisper_bridge_class != nullptr && g_dispatch_progress_method != nullptr) {
        return true;
    }
    jclass local_bridge_class = env->FindClass("com/looktube/app/WhisperNativeBridge");
    if (local_bridge_class == nullptr) {
        env->ExceptionClear();
        return false;
    }
    g_whisper_bridge_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_bridge_class));
    env->DeleteLocalRef(local_bridge_class);
    if (g_whisper_bridge_class == nullptr) {
        return false;
    }
    g_dispatch_progress_method = env->GetStaticMethodID(g_whisper_bridge_class, "dispatchNativeProgress", "(I)V");
    if (g_dispatch_progress_method == nullptr) {
        env->ExceptionClear();
        return false;
    }
    return true;
}

void whisper_progress_bridge(
    whisper_context * /* context */,
    whisper_state * /* state */,
    int progress,
    void * /* user_data */
) {
    if (g_java_vm == nullptr || g_whisper_bridge_class == nullptr || g_dispatch_progress_method == nullptr) {
        return;
    }
    JNIEnv * env = nullptr;
    bool attached_current_thread = false;
    if (g_java_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_java_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return;
        }
        attached_current_thread = true;
    }
    env->CallStaticVoidMethod(g_whisper_bridge_class, g_dispatch_progress_method, progress);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (attached_current_thread) {
        g_java_vm->DetachCurrentThread();
    }
}

}  // namespace

extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void * /* reserved */) {
    g_java_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_looktube_app_WhisperNativeBridge_nativeInitContext(
    JNIEnv * env,
    jobject /* thiz */,
    jstring model_path
) {
    const char * model_path_chars = env->GetStringUTFChars(model_path, nullptr);
    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;
    context_params.flash_attn = false;
    whisper_context * context = whisper_init_from_file_with_params(model_path_chars, context_params);
    env->ReleaseStringUTFChars(model_path, model_path_chars);
    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_looktube_app_WhisperNativeBridge_nativeFreeContext(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong context_pointer
) {
    if (context_pointer == 0L) {
        return;
    }
    whisper_free(reinterpret_cast<whisper_context *>(context_pointer));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_looktube_app_WhisperNativeBridge_nativeFullTranscribe(
    JNIEnv * env,
    jobject /* thiz */,
    jlong context_pointer,
    jint num_threads,
    jfloatArray audio_data
) {
    whisper_context * context = reinterpret_cast<whisper_context *>(context_pointer);
    if (context == nullptr) {
        throw_illegal_state(env, "Offline caption model context was not initialized.");
        return;
    }

    jfloat * audio_data_pointer = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize audio_data_length = env->GetArrayLength(audio_data);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;
    if (ensure_progress_bridge(env)) {
        params.progress_callback = whisper_progress_bridge;
        params.progress_callback_user_data = nullptr;
    }

    const int result = whisper_full(context, params, audio_data_pointer, audio_data_length);
    env->ReleaseFloatArrayElements(audio_data, audio_data_pointer, JNI_ABORT);

    if (result != 0) {
        throw_illegal_state(env, "Offline caption transcription failed.");
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_looktube_app_WhisperNativeBridge_nativeGetSegmentCount(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong context_pointer
) {
    whisper_context * context = reinterpret_cast<whisper_context *>(context_pointer);
    return context == nullptr ? 0 : whisper_full_n_segments(context);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_looktube_app_WhisperNativeBridge_nativeGetSegmentText(
    JNIEnv * env,
    jobject /* thiz */,
    jlong context_pointer,
    jint index
) {
    whisper_context * context = reinterpret_cast<whisper_context *>(context_pointer);
    if (context == nullptr) {
        return env->NewStringUTF("");
    }
    const char * text = whisper_full_get_segment_text(context, index);
    return env->NewStringUTF(text == nullptr ? "" : text);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_looktube_app_WhisperNativeBridge_nativeGetSegmentStartTicks(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong context_pointer,
    jint index
) {
    whisper_context * context = reinterpret_cast<whisper_context *>(context_pointer);
    return context == nullptr ? 0L : static_cast<jlong>(whisper_full_get_segment_t0(context, index));
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_looktube_app_WhisperNativeBridge_nativeGetSegmentEndTicks(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong context_pointer,
    jint index
) {
    whisper_context * context = reinterpret_cast<whisper_context *>(context_pointer);
    return context == nullptr ? 0L : static_cast<jlong>(whisper_full_get_segment_t1(context, index));
}
