cmake_minimum_required(VERSION 3.4)

# enable MSVC_RUNTIME_LIBRARY target property
# see https://cmake.org/cmake/help/latest/policy/CMP0091.html
if(POLICY CMP0091)
  cmake_policy(SET CMP0091 NEW)
endif()

project("netty-transport-native-iocp" LANGUAGES C)

# Find Java if JNI_INCLUDE_DIR is not defined
if(NOT DEFINED JNI_INCLUDE_DIR)
  find_package(JNI REQUIRED)
  set(JNI_INCLUDE_DIR ${JAVA_INCLUDE_PATH})
endif()

add_library(
        netty_transport_native_iocp
        SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/netty_iocp_native.c
        ${CMAKE_CURRENT_SOURCE_DIR}/netty_iocp_native.h
        ${CMAKE_CURRENT_SOURCE_DIR}/netty_jni_util.c
        ${CMAKE_CURRENT_SOURCE_DIR}/netty_jni_util.h
        ${CMAKE_CURRENT_SOURCE_DIR}/string_util.c
        ${CMAKE_CURRENT_SOURCE_DIR}/string_util.h
)

set_property(TARGET netty_transport_native_iocp PROPERTY
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
)

target_include_directories(
        netty_transport_native_iocp
        PRIVATE
        ${JNI_INCLUDE_DIR}
        ${JNI_INCLUDE_DIR}/win32
)

target_link_libraries(
        netty_transport_native_iocp
        PRIVATE
        ws2_32.lib
)

message("CMAKE_GENERATOR_PLATFORM : ${CMAKE_GENERATOR_PLATFORM}")
if ("${CMAKE_GENERATOR_PLATFORM}" MATCHES "(Win64|IA64|x64)")
    set_target_properties(
            netty_transport_native_iocp
            PROPERTIES
            PREFIX ""
            OUTPUT_NAME "netty_transport_native_iocp_x86_64"
    )
else()
    set_target_properties(
            netty_transport_native_iocp
            PROPERTIES
            PREFIX ""
            OUTPUT_NAME "netty_transport_native_iocp_x86_32"
    )
endif()