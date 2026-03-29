cmake_minimum_required(VERSION 3.22.1)

project(looktube_whisper_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_ROOT ${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_LTO OFF CACHE BOOL "" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_HIP OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_ROOT}/ggml ${CMAKE_BINARY_DIR}/ggml)

find_library(log-lib log)
find_library(android-lib android)
find_package(Threads REQUIRED)

add_library(
    looktube_whisper
    SHARED
    looktube_whisper_jni.cpp
    ${WHISPER_ROOT}/src/whisper.cpp
)

target_include_directories(
    looktube_whisper
    PRIVATE
    ${WHISPER_ROOT}/include
    ${WHISPER_ROOT}/src
    ${WHISPER_ROOT}/ggml/include
    ${WHISPER_ROOT}/ggml/src
)

target_link_libraries(
    looktube_whisper
    PRIVATE
    ggml
    ${android-lib}
    ${log-lib}
    Threads::Threads
)

target_compile_definitions(
    looktube_whisper
    PRIVATE
    GGML_USE_CPU
    WHISPER_VERSION="looktube-vendored"
)

target_compile_options(
    looktube_whisper
    PRIVATE
    -O3
)
