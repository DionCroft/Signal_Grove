#include <jni.h>
extern "C" JNIEXPORT jboolean JNICALL Java_org_hermesmobile_app_local_NativeLlmBindings_available(JNIEnv *, jobject) { return JNI_FALSE; }
extern "C" JNIEXPORT jlong JNICALL Java_org_hermesmobile_app_local_NativeLlmBindings_create(JNIEnv *, jobject, jstring) { return 0; }
extern "C" JNIEXPORT jint JNICALL Java_org_hermesmobile_app_local_NativeLlmBindings_load(JNIEnv *, jobject, jlong, jstring, jint, jint, jlong) { return 6; }
extern "C" JNIEXPORT jint JNICALL Java_org_hermesmobile_app_local_NativeLlmBindings_generate(JNIEnv *, jobject, jlong, jobjectArray, jobjectArray, jobjectArray, jint, jlong, jobject) { return 6; }
extern "C" JNIEXPORT void JNICALL Java_org_hermesmobile_app_local_NativeLlmBindings_cancel(JNIEnv *, jobject, jlong, jlong) {}
extern "C" JNIEXPORT void JNICALL Java_org_hermesmobile_app_local_NativeLlmBindings_dispose(JNIEnv *, jobject, jlong) {}
