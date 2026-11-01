cmake_minimum_required(VERSION 3.22)
project(giljabi_native_regression LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Release)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(ENGINE "${CMAKE_CURRENT_LIST_DIR}/../third_party/llama.cpp-omni")
set(BRIDGE "${CMAKE_CURRENT_LIST_DIR}/../app/src/main/cpp")
add_subdirectory("${ENGINE}" engine)
add_executable(template_regression template_regression.cpp)
target_include_directories(template_regression PRIVATE "${BRIDGE}")
target_link_libraries(template_regression PRIVATE llama llama-common)
set_target_properties(template_regression PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin")
if(NOT EXISTS "$ENV{JAVA_HOME}/include/jni.h")
    message(FATAL_ERROR "JAVA_HOME must point to a full JDK")
endif()
add_library(giljabi_guard_probe SHARED guard_probe.cpp)
target_include_directories(giljabi_guard_probe PRIVATE "$ENV{JAVA_HOME}/include" "$ENV{JAVA_HOME}/include/linux" "${BRIDGE}")
set_target_properties(giljabi_guard_probe PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin")

# Host smoke test of the exact production JNI bridge with the real model pair.
# Built only when explicitly selected; template/guard checks retain their small build.
if(NOT TARGET mtmd)
    set(LLAMA_INSTALL_VERSION "0.0.0") # Test-only ABI label for mtmd added outside the engine subdir.
    add_subdirectory("${ENGINE}/tools/mtmd" mtmd)
endif()
add_library(giljabi_native SHARED "${BRIDGE}/llama_jni.cpp")
target_include_directories(giljabi_native PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/host-android"
    "$ENV{JAVA_HOME}/include" "$ENV{JAVA_HOME}/include/linux"
    "${BRIDGE}" "${ENGINE}/common" "${ENGINE}/include"
    "${ENGINE}/ggml/include" "${ENGINE}/ggml/src" "${ENGINE}/tools/mtmd" "${ENGINE}/vendor")
target_link_libraries(giljabi_native PRIVATE llama llama-common mtmd)
set_target_properties(giljabi_native PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin")
