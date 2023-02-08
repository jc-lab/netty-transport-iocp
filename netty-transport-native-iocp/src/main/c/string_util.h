#ifndef STRING_UTIL_H_
#define STRING_UTIL_H_

#include <windows.h>
#include <tchar.h>
#include <jni.h>

TCHAR* netty_iocp_util_jstring_to_tstr(JNIEnv* env, jstring obj);

#endif // STRING_UTIL_H_
