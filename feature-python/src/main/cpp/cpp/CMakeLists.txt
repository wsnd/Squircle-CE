cmake_minimum_required(VERSION 3.18.1)

project("python_repl_native")

# Add the native library
add_library(
    python_repl_native
    SHARED
    python_repl_native.cpp
)

# Find required libraries
find_library(log-lib log)

# Link libraries
target_link_libraries(
    python_repl_native
    ${log-lib}
)
