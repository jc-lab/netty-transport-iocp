#include "string_util.h"

#if defined(_UNICODE)
TCHAR* netty_iocp_util_jstring_to_tstr(JNIEnv* env, jstring obj) {
    int text_length = (*env)->GetStringLength(env, obj);
    const jchar* jchars = (*env)->GetStringChars(env, obj, NULL);
    TCHAR* buffer = (TCHAR*)malloc(sizeof(TCHAR) * (text_length + 1));
    memcpy(buffer, jchars, sizeof(TCHAR) * text_length);
    buffer[text_length] = 0;
    (*env).ReleaseStringChars(env, obj, jchars);
    return buffer;
}
#else
TCHAR* netty_iocp_util_jstring_to_tstr(JNIEnv* env, jstring obj) {
    int text_length = (*env)->GetStringLength(env, obj);
    const jchar* jchars = (*env)->GetStringChars(env, obj, NULL);
    int ansi_length = WideCharToMultiByte(CP_ACP, 0, jchars, text_length, NULL, 0, NULL, NULL);
    char* buffer = (char*) malloc(ansi_length + 1);
    WideCharToMultiByte(CP_ACP, 0, jchars, text_length, buffer, ansi_length + 1, NULL, NULL);
    buffer[ansi_length] = 0;
    (*env)->ReleaseStringChars(env, obj, jchars);
    return buffer;
}
#endif
