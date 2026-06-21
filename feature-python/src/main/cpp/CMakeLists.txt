cmake_minimum_required(VERSION 3.18.1)

project("python_repl_native")

# PYTHON_PREFIX_DIR and PYTHON_VERSION are passed from build.gradle.kts
# as cmake arguments. The prefix dir uses {{triplet}} placeholder which AGP
# resolves to the actual ABI-specific triplet at build time.
#
# PYTHON_PROJECT_DIR is also passed from build.gradle.kts and points to the
# CPython submodule root so that the fallback path is explicit and not
# dependent on brittle relative-path guessing.

# Resolve the triplet placeholder using CMAKE_LIBRARY_ARCHITECTURE (e.g., "aarch64-linux-android")
if(DEFINED PYTHON_PREFIX_DIR)
    string(REPLACE "{{triplet}}" ${CMAKE_LIBRARY_ARCHITECTURE}
        PYTHON_PREFIX_DIR ${PYTHON_PREFIX_DIR})
endif()

# Fallback: use project-level path passed from Gradle
if(NOT DEFINED PYTHON_PREFIX_DIR OR NOT EXISTS "${PYTHON_PREFIX_DIR}")
    if(DEFINED PYTHON_PROJECT_DIR)
        set(PYTHON_PREFIX_DIR "${PYTHON_PROJECT_DIR}/cross-build/${CMAKE_LIBRARY_ARCHITECTURE}/prefix")
    endif()
endif()

# Final safety check
if(NOT DEFINED PYTHON_PREFIX_DIR OR NOT EXISTS "${PYTHON_PREFIX_DIR}")
    message(FATAL_ERROR
        "PYTHON_PREFIX_DIR is not set or does not exist: ${PYTHON_PREFIX_DIR}\n"
        "Make sure the cross-build output is available or set CROSS_BUILD_DIR environment variable.")
endif()

message(STATUS "Python prefix dir: ${PYTHON_PREFIX_DIR}")
message(STATUS "Python version: ${PYTHON_VERSION}")

# Add the prebuilt libpython
add_library(python3.14 SHARED IMPORTED)
set_target_properties(python3.14 PROPERTIES IMPORTED_LOCATION
    ${PYTHON_PREFIX_DIR}/lib/libpython${PYTHON_VERSION}.so)

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
    ${PYTHON_PREFIX_DIR}/include/python${PYTHON_VERSION}
)
target_include_directories(python_repl_native PRIVATE
    ${PYTHON_PREFIX_DIR}/include/python${PYTHON_VERSION}
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

# 16KB page size alignment (required for Android 15+)
target_link_options(python_bridge PRIVATE -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
target_link_options(python_repl_native PRIVATE -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
