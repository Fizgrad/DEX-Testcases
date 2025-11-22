#include <jni.h>
#include <android/log.h>
#include <inttypes.h>
#include <vector>

#define LOG_TAG "ArtNativeTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_artnative_ArtNativeTest_nativeProbe(
        JNIEnv* env,
        jclass /*clazz*/,
        jlong ptr,
        jlong theme_ptr,
        jint def_style_attr,
        jint def_style_resid,
        jlong xml_parser_ptr,
        jintArray java_attrs,
        jlong out_values_ptr,
        jlong out_indices_ptr) {

    // 打印标量参数（十六进制/十进制混合）
    LOGI("ptr=0x%" PRIx64 ", theme_ptr=0x%" PRIx64
         ", def_style_attr=%d, def_style_resid=%d, xml_parser_ptr=0x%" PRIx64
         ", out_values_ptr=0x%" PRIx64 ", out_indices_ptr=0x%" PRIx64,
         (uint64_t)ptr, (uint64_t)theme_ptr,
         (int)def_style_attr, (int)def_style_resid,
         (uint64_t)xml_parser_ptr,
         (uint64_t)out_values_ptr, (uint64_t)out_indices_ptr);

    // 读取 jintArray
    jsize len = (java_attrs ? env->GetArrayLength(java_attrs) : 0);
    std::vector<jint> buf;
    buf.resize(len);
    if (len > 0) {
        env->GetIntArrayRegion(java_attrs, 0, len, buf.data());
    }
    LOGI("java_attrs length=%d", (int)len);
    for (jsize i = 0; i < len; ++i) {
        LOGI("java_attrs[%d]=%d", (int)i, (int)buf[i]);
    }

    // 与 Java 侧一致的 checksum（FNV-like）
    uint64_t h = 0xcbf29ce484222325ULL;
    auto mix = [&](uint64_t v) {
        h ^= v;
        h *= 0x100000001b3ULL;
    };

    mix((uint64_t)ptr);
    mix((uint64_t)theme_ptr);
    mix((uint64_t)def_style_attr);
    mix((uint64_t)def_style_resid);
    mix((uint64_t)xml_parser_ptr);
    mix((uint64_t)out_values_ptr);
    mix((uint64_t)out_indices_ptr);
    mix((uint64_t)len);
    for (jsize i = 0; i < len; ++i) {
        mix((uint64_t)(uint32_t)buf[i]);
    }

    LOGI("checksum(native)=0x%016" PRIx64, h);
    return (jlong)h;
}

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK || !env) {
        return JNI_ERR;
    }
    LOGI("JNI_OnLoad ok");
    return JNI_VERSION_1_6;
}
