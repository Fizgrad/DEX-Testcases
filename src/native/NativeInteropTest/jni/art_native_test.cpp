#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <inttypes.h>
#include <jni.h>
#include <vector>

static void native_log(const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  printf("[native] : ");
  vprintf(fmt, args);
  printf("\n");
  va_end(args);
  fflush(stdout);
}

#define LOGI(...) native_log(__VA_ARGS__)
#define LOGE(...) native_log(__VA_ARGS__)

static inline uint64_t fnv64_basis() { return 0xcbf29ce484222325ULL; }

static inline uint64_t fnv64_mix(uint64_t h, uint64_t v) {
  h ^= v;
  h *= 0x100000001b3ULL;
  return h;
}

static inline uint32_t bit_cast_u32(float value) {
  uint32_t bits = 0;
  static_assert(sizeof(bits) == sizeof(value), "float size mismatch");
  memcpy(&bits, &value, sizeof(bits));
  return bits;
}

static inline uint64_t bit_cast_u64(double value) {
  uint64_t bits = 0;
  static_assert(sizeof(bits) == sizeof(value), "double size mismatch");
  memcpy(&bits, &value, sizeof(bits));
  return bits;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeProbe(
    JNIEnv *env, jclass /*clazz*/, jlong ptr, jlong theme_ptr,
    jint def_style_attr, jint def_style_resid, jlong xml_parser_ptr,
    jintArray java_attrs, jlong out_values_ptr, jlong out_indices_ptr) {

  // 打印标量参数（十六进制/十进制混合）
  LOGI("ptr=0x%" PRIx64 ", theme_ptr=0x%" PRIx64
       ", def_style_attr=%d, def_style_resid=%d, xml_parser_ptr=0x%" PRIx64
       ", out_values_ptr=0x%" PRIx64 ", out_indices_ptr=0x%" PRIx64,
       (uint64_t)ptr, (uint64_t)theme_ptr, (int)def_style_attr,
       (int)def_style_resid, (uint64_t)xml_parser_ptr, (uint64_t)out_values_ptr,
       (uint64_t)out_indices_ptr);

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
  uint64_t h = fnv64_basis();
  auto mix = [&](uint64_t v) { h = fnv64_mix(h, v); };

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

extern "C" JNIEXPORT jboolean JNICALL
Java_ArtNativeTest_nativeBooleanEcho(JNIEnv *, jclass,
                                                           jboolean value) {
  LOGI("nativeBooleanEcho received=%s", value == JNI_TRUE ? "true" : "false");
  return value;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_ArtNativeTest_nativeByteEcho(JNIEnv *, jclass,
                                                        jbyte value) {
  LOGI("nativeByteEcho received=%d", (int)value);
  return value;
}

extern "C" JNIEXPORT jchar JNICALL
Java_ArtNativeTest_nativeCharEcho(JNIEnv *, jclass,
                                                        jchar value) {
  LOGI("nativeCharEcho received=0x%04x ('%c')", (unsigned)value, (char)value);
  return value;
}

extern "C" JNIEXPORT jshort JNICALL
Java_ArtNativeTest_nativeShortEcho(JNIEnv *, jclass,
                                                         jshort value) {
  LOGI("nativeShortEcho received=%d", (int)value);
  return value;
}

extern "C" JNIEXPORT jint JNICALL
Java_ArtNativeTest_nativeIntEcho(JNIEnv *, jclass,
                                                       jint value) {
  LOGI("nativeIntEcho received=0x%08x", (uint32_t)value);
  return value;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeLongEcho(JNIEnv *, jclass,
                                                        jlong value) {
  LOGI("nativeLongEcho received=0x%016" PRIx64, (uint64_t)value);
  return value;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_ArtNativeTest_nativeFloatEcho(JNIEnv *, jclass,
                                                         jfloat value) {
  LOGI("nativeFloatEcho received=%f", (double)value);
  return value;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_ArtNativeTest_nativeDoubleEcho(JNIEnv *, jclass,
                                                          jdouble value) {
  LOGI("nativeDoubleEcho received=%lf", value);
  return value;
}

extern "C" JNIEXPORT jstring JNICALL
Java_ArtNativeTest_nativeStringEcho(JNIEnv *env, jclass,
                                                          jstring value) {
  if (!value) {
    LOGI("nativeStringEcho received=null");
    return nullptr;
  }
  const char *utf = env->GetStringUTFChars(value, nullptr);
  if (utf) {
    LOGI("nativeStringEcho received=\"%s\"", utf);
    env->ReleaseStringUTFChars(value, utf);
  } else {
    LOGE("nativeStringEcho could not read chars");
  }
  return value;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ArtNativeTest_nativeByteArrayEcho(JNIEnv *env,
                                                             jclass,
                                                             jbyteArray array) {
  if (!array) {
    LOGI("nativeByteArrayEcho received=null");
    return nullptr;
  }
  jsize len = env->GetArrayLength(array);
  std::vector<jbyte> buf(len);
  if (len > 0) {
    env->GetByteArrayRegion(array, 0, len, buf.data());
  }
  LOGI("nativeByteArrayEcho len=%d", (int)len);
  for (jsize i = 0; i < len; ++i) {
    LOGI("nativeByteArrayEcho[%d]=%d", (int)i, (int)buf[i]);
  }
  return array;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_ArtNativeTest_nativeIntArrayEcho(JNIEnv *env, jclass,
                                                            jintArray array) {
  if (!array) {
    LOGI("nativeIntArrayEcho received=null");
    return nullptr;
  }
  jsize len = env->GetArrayLength(array);
  std::vector<jint> buf(len);
  if (len > 0) {
    env->GetIntArrayRegion(array, 0, len, buf.data());
  }
  LOGI("nativeIntArrayEcho len=%d", (int)len);
  for (jsize i = 0; i < len; ++i) {
    LOGI("nativeIntArrayEcho[%d]=%d", (int)i, (int)buf[i]);
  }
  return array;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_ArtNativeTest_nativeLongArrayEcho(JNIEnv *env,
                                                             jclass,
                                                             jlongArray array) {
  if (!array) {
    LOGI("nativeLongArrayEcho received=null");
    return nullptr;
  }
  jsize len = env->GetArrayLength(array);
  std::vector<jlong> buf(len);
  if (len > 0) {
    env->GetLongArrayRegion(array, 0, len, buf.data());
  }
  LOGI("nativeLongArrayEcho len=%d", (int)len);
  for (jsize i = 0; i < len; ++i) {
    LOGI("nativeLongArrayEcho[%d]=0x%016" PRIx64, (int)i, (uint64_t)buf[i]);
  }
  return array;
}

extern "C" JNIEXPORT jbooleanArray JNICALL
Java_ArtNativeTest_nativeBooleanArrayEcho(
    JNIEnv *env, jclass, jbooleanArray array) {
  if (!array) {
    LOGI("nativeBooleanArrayEcho received=null");
    return nullptr;
  }
  jsize len = env->GetArrayLength(array);
  std::vector<jboolean> buf(len);
  if (len > 0) {
    env->GetBooleanArrayRegion(array, 0, len, buf.data());
  }
  LOGI("nativeBooleanArrayEcho len=%d", (int)len);
  for (jsize i = 0; i < len; ++i) {
    LOGI("nativeBooleanArrayEcho[%d]=%s", (int)i,
         buf[i] == JNI_TRUE ? "true" : "false");
  }
  return array;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeMultiPrimitiveChecksum(
    JNIEnv *, jclass, jboolean bool_value, jbyte byte_value, jchar char_value,
    jshort short_value, jint int_value, jlong long_value, jfloat float_value,
    jdouble double_value) {

  LOGI("nativeMultiPrimitiveChecksum bool=%s byte=%d char=0x%04x('%c') "
       "short=%d int=%d long=0x%016" PRIx64 " float=%f double=%lf",
       bool_value == JNI_TRUE ? "true" : "false", (int)byte_value,
       (uint32_t)char_value, (char)char_value, (int)short_value, (int)int_value,
       (uint64_t)long_value, (double)float_value, double_value);

  uint64_t h = 0xcbf29ce484222325ULL;
  auto mix = [&](uint64_t v) {
    h ^= v;
    h *= 0x100000001b3ULL;
  };
  mix(bool_value == JNI_TRUE ? 1 : 0);
  mix((uint64_t)(uint8_t)byte_value);
  mix((uint64_t)(uint32_t)char_value);
  mix((uint64_t)(uint32_t)(uint16_t)short_value);
  mix((uint64_t)(uint32_t)int_value);
  mix((uint64_t)long_value);
  mix((uint64_t)bit_cast_u32(float_value));
  mix(bit_cast_u64(double_value));
  return (jlong)h;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeMixedReferenceChecksum(
    JNIEnv *env, jclass, jboolean flag, jstring text, jintArray ints,
    jlongArray longs, jbooleanArray bools) {

  LOGI("nativeMixedReferenceChecksum flag=%s",
       flag == JNI_TRUE ? "true" : "false");
  if (text) {
    const char *utf = env->GetStringUTFChars(text, nullptr);
    LOGI("nativeMixedReferenceChecksum text=\"%s\"", utf ? utf : "<invalid>");
    if (utf) {
      env->ReleaseStringUTFChars(text, utf);
    }
  } else {
    LOGI("nativeMixedReferenceChecksum text=null");
  }

  auto load_int_array = [&](jintArray array, const char *tag) {
    if (!array) {
      LOGI("%s null", tag);
      return std::vector<jint>();
    }
    jsize len = env->GetArrayLength(array);
    std::vector<jint> data(len);
    if (len > 0) {
      env->GetIntArrayRegion(array, 0, len, data.data());
    }
    LOGI("%s len=%d", tag, (int)len);
    for (jsize i = 0; i < len; ++i) {
      LOGI("%s[%d]=%d", tag, (int)i, (int)data[i]);
    }
    return data;
  };

  auto load_long_array = [&](jlongArray array, const char *tag) {
    if (!array) {
      LOGI("%s null", tag);
      return std::vector<jlong>();
    }
    jsize len = env->GetArrayLength(array);
    std::vector<jlong> data(len);
    if (len > 0) {
      env->GetLongArrayRegion(array, 0, len, data.data());
    }
    LOGI("%s len=%d", tag, (int)len);
    for (jsize i = 0; i < len; ++i) {
      LOGI("%s[%d]=0x%016" PRIx64, tag, (int)i, (uint64_t)data[i]);
    }
    return data;
  };

  auto load_bool_array = [&](jbooleanArray array, const char *tag) {
    if (!array) {
      LOGI("%s null", tag);
      return std::vector<jboolean>();
    }
    jsize len = env->GetArrayLength(array);
    std::vector<jboolean> data(len);
    if (len > 0) {
      env->GetBooleanArrayRegion(array, 0, len, data.data());
    }
    LOGI("%s len=%d", tag, (int)len);
    for (jsize i = 0; i < len; ++i) {
      LOGI("%s[%d]=%s", tag, (int)i, data[i] == JNI_TRUE ? "true" : "false");
    }
    return data;
  };

  std::vector<jint> ints_vec = load_int_array(ints, "ints");
  std::vector<jlong> longs_vec = load_long_array(longs, "longs");
  std::vector<jboolean> bools_vec = load_bool_array(bools, "bools");

  uint64_t h = fnv64_basis();
  auto mix = [&](uint64_t v) { h = fnv64_mix(h, v); };

  mix(flag == JNI_TRUE ? 1 : 0);
  jsize text_len = text ? env->GetStringLength(text) : 0;
  mix((uint64_t)text_len);
  if (text_len > 0) {
    std::vector<jchar> chars(text_len);
    env->GetStringRegion(text, 0, text_len, chars.data());
    for (jchar ch : chars) {
      mix((uint64_t)ch);
    }
  }

  mix((uint64_t)ints_vec.size());
  for (jint v : ints_vec) {
    mix((uint64_t)(uint32_t)v);
  }

  mix((uint64_t)longs_vec.size());
  for (jlong v : longs_vec) {
    mix((uint64_t)v);
  }

  mix((uint64_t)bools_vec.size());
  for (jboolean v : bools_vec) {
    mix(v == JNI_TRUE ? 1 : 0);
  }

  return (jlong)h;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeComboChecksum3(JNIEnv *, jclass,
                                                              jint a, jlong b,
                                                              jdouble c) {
  LOGI("nativeComboChecksum3 a=0x%08x b=0x%016" PRIx64 " c=%lf", (uint32_t)a,
       (uint64_t)b, c);
  uint64_t h = fnv64_basis();
  auto mix = [&](uint64_t v) { h = fnv64_mix(h, v); };
  mix((uint64_t)(uint32_t)a);
  mix((uint64_t)b);
  mix(bit_cast_u64(c));
  return (jlong)h;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeComboChecksum5(JNIEnv *, jclass,
                                                              jboolean flag,
                                                              jbyte b, jshort s,
                                                              jint i, jlong l) {
  LOGI(
      "nativeComboChecksum5 flag=%s byte=%d short=%d int=%d long=0x%016" PRIx64,
      flag == JNI_TRUE ? "true" : "false", (int)b, (int)s, (int)i, (uint64_t)l);
  uint64_t h = fnv64_basis();
  auto mix = [&](uint64_t v) { h = fnv64_mix(h, v); };
  mix(flag == JNI_TRUE ? 1 : 0);
  mix((uint64_t)(uint8_t)b);
  mix((uint64_t)(uint32_t)(uint16_t)s);
  mix((uint64_t)(uint32_t)i);
  mix((uint64_t)l);
  return (jlong)h;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeComboChecksum7(
    JNIEnv *, jclass, jfloat f1, jdouble d1, jlong l1, jint i1, jshort s1,
    jbyte b1, jboolean flag) {
  LOGI("nativeComboChecksum7 f1=%f d1=%lf l1=0x%016" PRIx64
       " i1=%d s1=%d b1=%d flag=%s",
       (double)f1, d1, (uint64_t)l1, (int)i1, (int)s1, (int)b1,
       flag == JNI_TRUE ? "true" : "false");
  uint64_t h = fnv64_basis();
  auto mix = [&](uint64_t v) { h = fnv64_mix(h, v); };
  mix((uint64_t)bit_cast_u32(f1));
  mix(bit_cast_u64(d1));
  mix((uint64_t)l1);
  mix((uint64_t)(uint32_t)i1);
  mix((uint64_t)(uint32_t)(uint16_t)s1);
  mix((uint64_t)(uint8_t)b1);
  mix(flag == JNI_TRUE ? 1 : 0);
  return (jlong)h;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeComboChecksum9(
    JNIEnv *, jclass, jint a1, jint a2, jlong l1, jlong l2, jfloat f1,
    jfloat f2, jdouble d1, jdouble d2, jboolean flag) {
  LOGI("nativeComboChecksum9 ints=[%d,%d] longs=[0x%016" PRIx64 ",0x%016" PRIx64
       "] f=[%f,%f] d=[%lf,%lf] flag=%s",
       (int)a1, (int)a2, (uint64_t)l1, (uint64_t)l2, (double)f1, (double)f2, d1,
       d2, flag == JNI_TRUE ? "true" : "false");
  uint64_t h = fnv64_basis();
  auto mix = [&](uint64_t v) { h = fnv64_mix(h, v); };
  mix((uint64_t)(uint32_t)a1);
  mix((uint64_t)(uint32_t)a2);
  mix((uint64_t)l1);
  mix((uint64_t)l2);
  mix((uint64_t)bit_cast_u32(f1));
  mix((uint64_t)bit_cast_u32(f2));
  mix(bit_cast_u64(d1));
  mix(bit_cast_u64(d2));
  mix(flag == JNI_TRUE ? 1 : 0);
  return (jlong)h;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeComboChecksum13(
    JNIEnv *, jclass, jlong l1, jlong l2, jlong l3, jlong l4, jlong l5, jint i1,
    jint i2, jint i3, jshort s1, jshort s2, jbyte b1, jboolean flag,
    jdouble d1) {
  LOGI("nativeComboChecksum13 longs=[0x%016" PRIx64 ",0x%016" PRIx64
       ",0x%016" PRIx64 ",0x%016" PRIx64 ",0x%016" PRIx64 "]"
       " ints=[%d,%d,%d] shorts=[%d,%d] byte=%d flag=%s d=%lf",
       (uint64_t)l1, (uint64_t)l2, (uint64_t)l3, (uint64_t)l4, (uint64_t)l5,
       (int)i1, (int)i2, (int)i3, (int)s1, (int)s2, (int)b1,
       flag == JNI_TRUE ? "true" : "false", d1);
  uint64_t h = fnv64_basis();
  auto mix = [&](uint64_t v) { h = fnv64_mix(h, v); };
  mix((uint64_t)l1);
  mix((uint64_t)l2);
  mix((uint64_t)l3);
  mix((uint64_t)l4);
  mix((uint64_t)l5);
  mix((uint64_t)(uint32_t)i1);
  mix((uint64_t)(uint32_t)i2);
  mix((uint64_t)(uint32_t)i3);
  mix((uint64_t)(uint32_t)(uint16_t)s1);
  mix((uint64_t)(uint32_t)(uint16_t)s2);
  mix((uint64_t)(uint8_t)b1);
  mix(flag == JNI_TRUE ? 1 : 0);
  mix(bit_cast_u64(d1));
  return (jlong)h;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ArtNativeTest_nativeComboChecksum20(
    JNIEnv *, jclass, jlong p1, jlong p2, jlong p3, jlong p4, jlong p5,
    jlong p6, jlong p7, jlong p8, jlong p9, jlong p10, jint i1, jint i2,
    jint i3, jint i4, jint i5, jshort s1, jshort s2, jbyte b1, jbyte b2,
    jboolean flag) {
  LOGI("nativeComboChecksum20 longs=[0x%016" PRIx64 ",0x%016" PRIx64
       ",0x%016" PRIx64 ",0x%016" PRIx64 ",0x%016" PRIx64 ",0x%016" PRIx64
       ",0x%016" PRIx64 ",0x%016" PRIx64 ",0x%016" PRIx64 ",0x%016" PRIx64 "]",
       (uint64_t)p1, (uint64_t)p2, (uint64_t)p3, (uint64_t)p4, (uint64_t)p5,
       (uint64_t)p6, (uint64_t)p7, (uint64_t)p8, (uint64_t)p9, (uint64_t)p10);
  LOGI("nativeComboChecksum20 ints=[%d,%d,%d,%d,%d] shorts=[%d,%d] "
       "bytes=[%d,%d] flag=%s",
       (int)i1, (int)i2, (int)i3, (int)i4, (int)i5, (int)s1, (int)s2, (int)b1,
       (int)b2, flag == JNI_TRUE ? "true" : "false");
  uint64_t h = fnv64_basis();
  auto mix = [&](uint64_t v) { h = fnv64_mix(h, v); };
  mix((uint64_t)p1);
  mix((uint64_t)p2);
  mix((uint64_t)p3);
  mix((uint64_t)p4);
  mix((uint64_t)p5);
  mix((uint64_t)p6);
  mix((uint64_t)p7);
  mix((uint64_t)p8);
  mix((uint64_t)p9);
  mix((uint64_t)p10);
  mix((uint64_t)(uint32_t)i1);
  mix((uint64_t)(uint32_t)i2);
  mix((uint64_t)(uint32_t)i3);
  mix((uint64_t)(uint32_t)i4);
  mix((uint64_t)(uint32_t)i5);
  mix((uint64_t)(uint32_t)(uint16_t)s1);
  mix((uint64_t)(uint32_t)(uint16_t)s2);
  mix((uint64_t)(uint8_t)b1);
  mix((uint64_t)(uint8_t)b2);
  mix(flag == JNI_TRUE ? 1 : 0);
  return (jlong)h;
}

extern "C" jint JNI_OnLoad(JavaVM *vm, void *) {
  JNIEnv *env = nullptr;
  if (vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK || !env) {
    return JNI_ERR;
  }
  LOGI("JNI_OnLoad ok");
  return JNI_VERSION_1_6;
}
