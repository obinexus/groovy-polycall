#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "groovy_polycall.h"

JNIEXPORT jint JNICALL
Java_org_obinexus_polycall_Polycall_nativeRunConfig(
    JNIEnv *env,
    jclass polycall_class,
    jstring config_path
) {
    const char *jni_config_path;
    char *native_config_path;
    jsize utf_length;
    int status;

    (void)polycall_class;

    if (config_path == NULL) {
        jclass null_pointer_exception =
            (*env)->FindClass(env, "java/lang/NullPointerException");

        if (null_pointer_exception != NULL) {
            (*env)->ThrowNew(env, null_pointer_exception, "configPath");
        }
        return 0;
    }

    utf_length = (*env)->GetStringUTFLength(env, config_path);
    jni_config_path = (*env)->GetStringUTFChars(env, config_path, NULL);
    if (jni_config_path == NULL) {
        return 0;
    }

    native_config_path = malloc((size_t)utf_length + 1U);
    if (native_config_path == NULL) {
        jclass out_of_memory_error;

        (*env)->ReleaseStringUTFChars(env, config_path, jni_config_path);
        out_of_memory_error = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (out_of_memory_error != NULL) {
            (*env)->ThrowNew(env, out_of_memory_error, "config path allocation failed");
        }
        return 0;
    }

    memcpy(native_config_path, jni_config_path, (size_t)utf_length);
    native_config_path[utf_length] = '\0';
    (*env)->ReleaseStringUTFChars(env, config_path, jni_config_path);

    status = groovy_polycall_run_config(native_config_path);
    free(native_config_path);
    return (jint)status;
}
