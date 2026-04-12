cmake_minimum_required(VERSION 3.18.1)

project("python_repl_native")

# Set the path to jniLibs
set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs)

# Add the prebuilt libpython3.14
add_library(python3.14 SHARED IMPORTED)
set_target_properties(python3.14 PROPERTIES IMPORTED_LOCATION
    ${JNI_LIBS_DIR}/${ANDROID_ABI}/libpython3.14.so)

# Add the native libraries
add_library(
    python_bridge
    SHARED
    python_bridge.cpp
)

add_library(
    python_repl_native
    SHARED
    python_repl_native.cpp
)

# Include Python headers
target_include_directories(python_bridge PRIVATE
    ${JNI_LIBS_DIR}/arm64-v8a/prefix/include/python3.14
)
target_include_directories(python_repl_native PRIVATE
    ${JNI_LIBS_DIR}/arm64-v8a/prefix/include/python3.14
)

# Find required libraries
find_library(log-lib log)

# Link libraries
target_link_libraries(
    python_bridge
    python3.14
    ${log-lib}
)
target_link_libraries(
    python_repl_native
    python3.14
    ${log-lib}
)
