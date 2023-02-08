/*
 * Copyright 2023 JC-Lab
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
#include <winsock2.h>
#include <windows.h>

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#pragma comment(lib, "ws2_32.lib")

//#include "netty_iocp_native.h"
#include "netty_jni_util.h"
#include "string_util.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define BUFFER_CLASSNAME "io/netty/channel/unix/Buffer"
#define STATICALLY_CLASSNAME "kr/jclab/netty/channel/iocp/NativeStaticallyReferencedJniMethods"
#define NATIVE_CLASSNAME "kr/jclab/netty/channel/iocp/Native"

static const char* staticPackagePrefix = NULL;

// Those are initialized in the init(...) method and cached for performance reasons
static jfieldID overlappedEntryCompletionKeyFieldId = NULL;
static jfieldID overlappedEntryOverlappedPointerFieldId = NULL;
static jfieldID overlappedEntryNumberOfBytesTransferredFieldId = NULL;
static jfieldID overlappedEntryOverlappedValidFieldId = NULL;
static jfieldID overlappedEntryFileHandleFieldId = NULL;
static jfieldID overlappedEntryEventHandleFieldId = NULL;
static jfieldID overlappedEntryBufferSizeFieldId = NULL;
static jfieldID nativeOverlappedMemoryAddressFieldId = NULL;
static jfieldID nativeOverlappedNumberOfBytesTransferredFieldId = NULL;

#define OVERLAPPED_MAGIC 0x0caffe00

typedef struct _netty_iocp_native_overlapped {
    OVERLAPPED overlapped;
    DWORD      magic;
    HANDLE     fileHandle;
    DWORD      bufferSize;
} netty_iocp_native_overlapped_t;

#define OVERLAPPED_BUFFER(pov) (((char*)pov) + sizeof(netty_iocp_native_overlapped_t))

// JNI Registered Methods Begin

static jint netty_iocp_native_nop(JNIEnv* env, jclass clazz) {
    return 0;
}

static jlong netty_iocp_native_buffer_memoryAddress0(JNIEnv* env, jclass clazz, jobject buffer) {
    return (jlong) (*env)->GetDirectBufferAddress(env, buffer);
}

static jint netty_iocp_native_buffer_addressSize0(JNIEnv* env, jclass clazz) {
    return (jint) sizeof(int*);
}

static jint netty_iocp_native_sizeOfUlongPtr(JNIEnv* env, jclass clazz) {
    return (jint) sizeof(ULONG_PTR);
}

static jint netty_iocp_native_sizeOfPtr(JNIEnv* env, jclass clazz) {
    return (jint) sizeof(void*);
}

static jint netty_iocp_native_sizeOfOverlappedEntry(JNIEnv* env, jclass clazz) {
    return (jint) sizeof(OVERLAPPED_ENTRY);
}

static jint netty_iocp_native_sizeOfNativeOverlappedStruct(JNIEnv* env, jclass clazz) {
    return (jint) sizeof(netty_iocp_native_overlapped_t);
}

static jint pipeAccessDuplex() {
    return (jint) PIPE_ACCESS_DUPLEX;
}

static jint pipeAccessInbound() {
    return (jint) PIPE_ACCESS_INBOUND;
}

static jint pipeAccessOutbound() {
    return (jint) PIPE_ACCESS_OUTBOUND;
}

static jint fileFlagFirstPipeInstance() {
    return (jint) FILE_FLAG_FIRST_PIPE_INSTANCE;
}

static jint fileFlagWriteThrough() {
    return (jint) FILE_FLAG_WRITE_THROUGH;
}

static jint fileFlagOverlapped() {
    return (jint) FILE_FLAG_OVERLAPPED;
}

static jlong netty_iocp_native_createIoCompletionPort(
    JNIEnv* env, jclass clazz,
    jlong file_handle, jlong existing_completion_port, jlong completion_key, jint number_of_concurrent_threads
) {
    return (jlong) CreateIoCompletionPort((HANDLE) file_handle, (HANDLE) existing_completion_port, (ULONG_PTR) completion_key, (DWORD) number_of_concurrent_threads);
}

static jint netty_iocp_native_winCloseHandle(
    JNIEnv* env, jclass clazz,
    jlong handle
) {
    if (CloseHandle((HANDLE) handle)) {
        return 0;
    } else {
        return GetLastError();
    }
}

static jint netty_iocp_native_wsaCloseEvent(
    JNIEnv* env, jclass clazz,
    jlong handle
) {
    if (WSACloseEvent((HANDLE) handle)) {
        return 0;
    } else {
        return GetLastError();
    }
}

// This needs to be consistent with Native.java
#define IOCP_WAIT_RESULT(V, ARM_TIMER)  ((jlong) ((uint64_t) ((uint32_t) V) << 32 | ARM_TIMER))

static jint netty_iocp_native_getQueuedCompletionStatusExWait(
    JNIEnv* env, jclass clazz,
    jlong handle,
    jlong entries,
    jint count,
    jint timeout
) {
    DWORD dw_err;
    ULONG numberOfEntriesRemoved = 0;
    if (GetQueuedCompletionStatusEx(
        (HANDLE) handle,
        (LPOVERLAPPED_ENTRY) entries,
        (ULONG) count,
        &numberOfEntriesRemoved,
        (DWORD) timeout,
        FALSE
    )) {
        return (jint) numberOfEntriesRemoved;
    }
    dw_err = GetLastError();
    if (dw_err == WAIT_TIMEOUT) {
        return 0;
    }
    return -((int)dw_err);
}

static jint netty_iocp_native_getQueuedCompletionStatusExBusyWait(
    JNIEnv* env, jclass clazz,
    jlong handle,
    jlong entries,
    jint count
) {
    ULONG numberOfEntriesRemoved = 0;
    DWORD dw_err;
    int err = 0;
    do {
        if (GetQueuedCompletionStatusEx(
            (HANDLE) handle,
            (LPOVERLAPPED_ENTRY) entries,
            (ULONG) count,
            &numberOfEntriesRemoved,
            (DWORD) 0,
            FALSE
        )) {
            if (numberOfEntriesRemoved == 0) {
                YieldProcessor();
            }
            return (jint) numberOfEntriesRemoved;
        }
        dw_err = GetLastError();
        if (dw_err == WAIT_TIMEOUT) {
            err = 0;
        } else {
            err = -((int)dw_err);
        }
    } while (err); // (err == EINTR)

    return -err;
}

static jint netty_iocp_native_postQueuedCompletionStatus(
    JNIEnv* env, jclass clazz,
    jlong handle,
    jint numberOfBytesTransferred,
    jlong completionKey,
    jlong overlapped_pointer
) {
    if (PostQueuedCompletionStatus((HANDLE) handle, (DWORD) numberOfBytesTransferred, (ULONG_PTR) completionKey, (LPOVERLAPPED) overlapped_pointer)) {
        return 0;
    }
    return -((int) GetLastError());
}

static jint netty_iocp_native_readOverlappedEntry(
    JNIEnv* env, jclass clazz,
    jlong pointer,
    jobject target
) {
    OVERLAPPED_ENTRY *entry = (OVERLAPPED_ENTRY*) pointer;
    netty_iocp_native_overlapped_t *nativeOverlapped = (netty_iocp_native_overlapped_t*) entry->lpOverlapped;
    (*env)->SetLongField(env, target, overlappedEntryCompletionKeyFieldId, (jlong) entry->lpCompletionKey);
    (*env)->SetLongField(env, target, overlappedEntryOverlappedPointerFieldId, (jlong) entry->lpOverlapped);
    (*env)->SetIntField(env, target, overlappedEntryNumberOfBytesTransferredFieldId, (jint) entry->dwNumberOfBytesTransferred);
    if (nativeOverlapped && nativeOverlapped->magic == OVERLAPPED_MAGIC) {
        (*env)->SetBooleanField(env, target, overlappedEntryOverlappedValidFieldId, 1);
        (*env)->SetLongField(env, target, overlappedEntryFileHandleFieldId, (jlong) nativeOverlapped->fileHandle);
        (*env)->SetLongField(env, target, overlappedEntryEventHandleFieldId,
                             (jlong) nativeOverlapped->overlapped.hEvent);
        (*env)->SetIntField(env, target, overlappedEntryBufferSizeFieldId, (jint) nativeOverlapped->bufferSize);
    } else {
        (*env)->SetBooleanField(env, target, overlappedEntryOverlappedValidFieldId, 0);
    }
    return 0;
}

//static jint netty_iocp_native_readNativeOverlapped(
//    JNIEnv* env, jclass clazz,
//    jobject target
//) {
//    jlong pointer = (*env)->GetLongField(env, target, nativeOverlappedMemoryAddressFieldId);
//    if (!pointer) {
//        return 1;
//    }
//
//    netty_iocp_native_overlapped_t *nativeOverlapped = (netty_iocp_native_overlapped_t*) pointer;
//    (*env)->SetLongField(env, target, nativeOverlappedNumberOfBytesTransferredFieldId, nativeOverlapped->numberOfBytesTransferred);
//
//    return 0;
//}

static jlong netty_iocp_native_createNamedPipe(
    JNIEnv* env, jclass clazz,
    jstring name,
    jint openMode,
    jint maxInstances,
    jint outBufferSize,
    jint inBufferSize,
    jint defaultTimeout,
    jlong securityAttributesPointer
) {
    TCHAR* tstr_name = netty_iocp_util_jstring_to_tstr(env, name);
    HANDLE handle = CreateNamedPipe(
        tstr_name,
        PIPE_ACCESS_DUPLEX |
        FILE_FLAG_OVERLAPPED | openMode,
        PIPE_TYPE_MESSAGE |
        PIPE_READMODE_MESSAGE |
        PIPE_WAIT,
        maxInstances,
        outBufferSize,
        inBufferSize,
        defaultTimeout,
        (LPSECURITY_ATTRIBUTES) securityAttributesPointer
    );
    DWORD err = GetLastError();
    free(tstr_name);

    if (handle == INVALID_HANDLE_VALUE) {
        return -((int) err);
    }

    return (jlong) handle;
}

static jlong netty_iocp_native_createEvent(
    JNIEnv* env, jclass clazz,
    jlong securityAttributePointer,
    jboolean manualReset,
    jboolean initialState,
    jstring name
) {
    HANDLE event = CreateEvent((LPSECURITY_ATTRIBUTES) securityAttributePointer, manualReset, initialState, NULL);
    if (event == INVALID_HANDLE_VALUE) {
        return -((int) GetLastError());
    }
    return (jlong) event;
}


static jlong netty_iocp_native_overlappedInitialize(
    JNIEnv* env, jclass clazz,
    jlong memory,
    jlong eventHandle,
    jlong fileHandle,
    jint  bufferSize
) {
    netty_iocp_native_overlapped_t* entry = (netty_iocp_native_overlapped_t*) memory;
    memset(&entry->overlapped, 0, sizeof(entry->overlapped));
    entry->magic = OVERLAPPED_MAGIC;
    entry->overlapped.hEvent = (HANDLE) eventHandle;
    entry->fileHandle = (HANDLE) fileHandle;
    entry->bufferSize = bufferSize;
}

static jint netty_iocp_native_connectNamedPipe0(
    JNIEnv* env, jclass clazz,
    jlong handle,
    jlong overlappedPointer
) {
    DWORD last_error;
    LPOVERLAPPED lpoverlapped = (LPOVERLAPPED) overlappedPointer;
    ConnectNamedPipe((HANDLE) handle, lpoverlapped);
    last_error = GetLastError();
    switch (last_error) {
        case ERROR_IO_PENDING:
            return 0;
        case ERROR_PIPE_CONNECTED:
            SetEvent(lpoverlapped->hEvent);
            return 0;
    }
    return -((int) last_error);
}

static jint netty_iocp_native_startOverlappedRead(
    JNIEnv* env, jclass clazz,
    jlong overlappedPointer
) {
    netty_iocp_native_overlapped_t *overlapped = (netty_iocp_native_overlapped_t *) overlappedPointer;
    DWORD dw_err;
    DWORD dwReadBytes = 0;

    overlapped->overlapped.Internal = 0;
    overlapped->overlapped.InternalHigh = 0;
    overlapped->overlapped.Offset = 0;
    overlapped->overlapped.OffsetHigh = 0;

    if (ReadFile(
        overlapped->fileHandle,
        OVERLAPPED_BUFFER(overlapped),
        overlapped->bufferSize,
        &dwReadBytes,
        &overlapped->overlapped
    )) {
        return (jint) dwReadBytes;
    }

    dw_err = GetLastError();
    if (dw_err == ERROR_IO_PENDING) {
        return 0;
    }
    return -((int)dw_err);
}

static jint netty_iocp_native_startOverlappedWrite(
    JNIEnv* env, jclass clazz,
    jlong overlappedPointer,
    jint dataSize
) {
    netty_iocp_native_overlapped_t *overlapped = (netty_iocp_native_overlapped_t *) overlappedPointer;
    DWORD dw_err;
    DWORD dwReadBytes = 0;

    overlapped->overlapped.Internal = 0;
    overlapped->overlapped.InternalHigh = 0;
    overlapped->overlapped.Offset = 0;
    overlapped->overlapped.OffsetHigh = 0;

    if (WriteFile(
        overlapped->fileHandle,
        OVERLAPPED_BUFFER(overlapped),
        dataSize,
        &dwReadBytes,
        &overlapped->overlapped
    )) {
        return (jint) dwReadBytes;
    }

    dw_err = GetLastError();
    if (dw_err == ERROR_IO_PENDING) {
        return 0;
    }
    return -((int)dw_err);
}

static jlong netty_iocp_native_getNamedPipeClientProcessId(
    JNIEnv* env, jclass clazz,
    jlong handle
) {
    ULONG pid = 0;
    DWORD dw_err;

    if (GetNamedPipeClientProcessId((HANDLE) handle, &pid)) {
        return (jlong) pid;
    }
    dw_err = GetLastError();
    return -((int) dw_err);
}

// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "nop", "()I", (void *) netty_iocp_native_nop },
  { "bufferMemoryAddress", "(Ljava/nio/ByteBuffer;)J", (void *) netty_iocp_native_buffer_memoryAddress0 },
  { "bufferAddressSize", "()I", (void *) netty_iocp_native_buffer_addressSize0 },
  { "winCloseHandle", "(J)I", (void *) netty_iocp_native_winCloseHandle },
  { "wsaCloseEvent", "(J)I", (void *) netty_iocp_native_wsaCloseEvent },
  { "sizeOfUlongPtr", "()I", (void *) netty_iocp_native_sizeOfUlongPtr },
  { "sizeOfPtr", "()I", (void *) netty_iocp_native_sizeOfPtr },
  { "sizeOfOverlappedEntry", "()I", (void *) netty_iocp_native_sizeOfOverlappedEntry },
  { "sizeOfNativeOverlappedStruct", "()I", (void *) netty_iocp_native_sizeOfNativeOverlappedStruct },
  { "pipeAccessDuplex", "()I", (void *) pipeAccessDuplex },
  { "pipeAccessDuplex", "()I", (void *) pipeAccessDuplex },
  { "pipeAccessInbound", "()I", (void *) pipeAccessInbound },
  { "pipeAccessOutbound", "()I", (void *) pipeAccessOutbound },
  { "fileFlagFirstPipeInstance", "()I", (void *) fileFlagFirstPipeInstance },
  { "fileFlagWriteThrough", "()I", (void *) fileFlagWriteThrough },
  { "fileFlagOverlapped", "()I", (void *) fileFlagOverlapped },
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "createIoCompletionPort0", "(JJJI)J", (void *) netty_iocp_native_createIoCompletionPort },
  { "getQueuedCompletionStatusExWait", "(JJII)I", netty_iocp_native_getQueuedCompletionStatusExWait },
  { "getQueuedCompletionStatusExBusyWait", "(JJI)I", netty_iocp_native_getQueuedCompletionStatusExBusyWait },
  { "postQueuedCompletionStatus0", "(JIJJ)I", netty_iocp_native_postQueuedCompletionStatus },
  { "createNamedPipe0", "(Ljava/lang/String;IIIIIJ)J", netty_iocp_native_createNamedPipe },
  { "createEvent0", "(JZZLjava/lang/String;)J", netty_iocp_native_createEvent },
  { "overlappedInitialize0", "(JJJI)I", netty_iocp_native_overlappedInitialize },
  { "connectNamedPipe0", "(JJ)I", netty_iocp_native_connectNamedPipe0 },
  { "readOverlappedEntry0", "(JLkr/jclab/netty/channel/iocp/OverlappedEntry;)I", netty_iocp_native_readOverlappedEntry },
  { "startOverlappedRead0", "(J)I", netty_iocp_native_startOverlappedRead },
  { "startOverlappedWrite0", "(JI)I", netty_iocp_native_startOverlappedWrite },
  { "getNamedPipeClientProcessId0", "(J)J", netty_iocp_native_getNamedPipeClientProcessId }
  // static native long createEvent0(long defaultSecurityAttributePointer, boolean manualReset, boolean initialState, String name);

//  { "eventFd", "()I", (void *) netty_iocp_native_eventFd },
//  { "timerFd", "()I", (void *) netty_iocp_native_timerFd },
//  { "eventFdWrite", "(IJ)V", (void *) netty_iocp_native_eventFdWrite },
//  { "eventFdRead", "(I)V", (void *) netty_iocp_native_eventFdRead },
//  { "epollCreate", "()I", (void *) netty_iocp_native_epollCreate },
//  { "epollWait0", "(IJIIIIJ)J", (void *) netty_iocp_native_epollWait0 },
//  { "epollWait", "(IJII)I", (void *) netty_iocp_native_epollWait },
//  { "epollBusyWait0", "(IJI)I", (void *) netty_iocp_native_epollBusyWait0 },
//  { "epollCtlAdd0", "(III)I", (void *) netty_iocp_native_epollCtlAdd0 },
//  { "epollCtlMod0", "(III)I", (void *) netty_iocp_native_epollCtlMod0 },
//  { "epollCtlDel0", "(II)I", (void *) netty_iocp_native_epollCtlDel0 },
//  // "sendmmsg0" has a dynamic signature
//  { "sizeofEpollEvent", "()I", (void *) netty_iocp_native_sizeofEpollEvent },
//  { "offsetofEpollData", "()I", (void *) netty_iocp_native_offsetofEpollData },
//  { "splice0", "(IJIJJ)I", (void *) netty_iocp_native_splice0 },
//  { "isSupportingUdpSegment", "()Z", (void *) netty_iocp_native_isSupportingUdpSegment },
//  { "registerUnix", "()I", (void *) netty_iocp_native_registerUnix },
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 0; // 3 is for the dynamic method signatures.
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    char* dynamicTypeName = NULL;
    size_t size = sizeof(JNINativeMethod) * dynamicMethodsTableSize();
    JNINativeMethod* dynamicMethods = (JNINativeMethod*) malloc(size);
    if (dynamicMethods == NULL) {
        return NULL;
    }
    memset(dynamicMethods, 0, size);
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));

    return dynamicMethods;
error:
    free(dynamicTypeName);
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    return NULL;
}

// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Native to reflect that.
static jint netty_iocp_native_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    int staticallyRegistered = 0;
    int nativeRegistered = 0;
    char* nettyClassName = NULL;
    jclass javaNativeOverlappedCls = NULL;
    jclass javaOverlappedEntryCls = NULL;
    JNINativeMethod* dynamicMethods = NULL;

    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STATICALLY_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto done;
    }
    staticallyRegistered = 1;

    // Register the methods which are not referenced by static member variables
    dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (dynamicMethods == NULL) {
        goto done;
    }

    if (netty_jni_util_register_natives(env,
            packagePrefix,
            NATIVE_CLASSNAME,
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        goto done;
    }
    nativeRegistered = 1;
//
//    // Initialize this module
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "kr/jclab/netty/channel/iocp/OverlappedEntry", nettyClassName, done);
    NETTY_JNI_UTIL_FIND_CLASS(env, javaOverlappedEntryCls, nettyClassName, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "kr/jclab/netty/channel/iocp/NativeOverlapped", nettyClassName, done);
    NETTY_JNI_UTIL_FIND_CLASS(env, javaNativeOverlappedCls, nettyClassName, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_GET_FIELD(env, javaOverlappedEntryCls, overlappedEntryCompletionKeyFieldId, "completionKey", "J", done);
    NETTY_JNI_UTIL_GET_FIELD(env, javaOverlappedEntryCls, overlappedEntryOverlappedPointerFieldId, "overlappedPointer", "J", done);
    NETTY_JNI_UTIL_GET_FIELD(env, javaOverlappedEntryCls, overlappedEntryNumberOfBytesTransferredFieldId, "numberOfBytesTransferred", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, javaOverlappedEntryCls, overlappedEntryOverlappedValidFieldId, "overlappedValid", "Z", done);
    NETTY_JNI_UTIL_GET_FIELD(env, javaOverlappedEntryCls, overlappedEntryFileHandleFieldId, "fileHandle", "J", done);
    NETTY_JNI_UTIL_GET_FIELD(env, javaOverlappedEntryCls, overlappedEntryEventHandleFieldId, "eventHandle", "J", done);
    NETTY_JNI_UTIL_GET_FIELD(env, javaOverlappedEntryCls, overlappedEntryBufferSizeFieldId, "bufferSize", "I", done);

    NETTY_JNI_UTIL_GET_FIELD(env, javaNativeOverlappedCls, nativeOverlappedMemoryAddressFieldId, "memoryAddress", "J", done);
//    NETTY_JNI_UTIL_GET_FIELD(env, javaNativeOverlappedCls, nativeOverlappedNumberOfBytesTransferredFieldId, "numberOfBytesTransferred", "I", done);

    ret = NETTY_JNI_UTIL_JNI_VERSION;

    staticPackagePrefix = packagePrefix;

done:

    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    free(nettyClassName);

    if (ret == JNI_ERR) {
        if (staticallyRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
        }
        if (nativeRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, NATIVE_CLASSNAME);
        }
    }
    return ret;
}

static void netty_iocp_native_JNI_OnUnload(JNIEnv* env) {
    netty_jni_util_unregister_natives(env, staticPackagePrefix, STATICALLY_CLASSNAME);
    netty_jni_util_unregister_natives(env, staticPackagePrefix, NATIVE_CLASSNAME);

    if (staticPackagePrefix != NULL) {
        free((void *) staticPackagePrefix);
        staticPackagePrefix = NULL;
    }

    overlappedEntryCompletionKeyFieldId = NULL;
    overlappedEntryOverlappedPointerFieldId = NULL;
    overlappedEntryNumberOfBytesTransferredFieldId = NULL;
    overlappedEntryOverlappedValidFieldId = NULL;
    overlappedEntryFileHandleFieldId = NULL;
    overlappedEntryEventHandleFieldId = NULL;
    overlappedEntryBufferSizeFieldId = NULL;
    nativeOverlappedMemoryAddressFieldId = NULL;
//    nativeOverlappedNumberOfBytesTransferredFieldId = NULL;
}

// Invoked by the JVM when statically linked

// We build with -fvisibility=hidden so ensure we mark everything that needs to be visible with JNIEXPORT
// https://mail.openjdk.java.net/pipermail/core-libs-dev/2013-February/014549.html

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_transport_native_iocp(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_iocp", netty_iocp_native_JNI_OnLoad);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_transport_native_iocp(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_iocp_native_JNI_OnUnload);
}

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_iocp", netty_iocp_native_JNI_OnLoad);
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_iocp_native_JNI_OnUnload);
}
#endif /* NETTY_BUILD_STATIC */
