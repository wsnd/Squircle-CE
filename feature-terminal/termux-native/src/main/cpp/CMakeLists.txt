cmake_minimum_required(VERSION 3.22.1)

project("termux-native")

# Build libtermux.so (C, no STL needed)
add_library("termux" SHARED termux.c)
target_link_libraries("termux" PRIVATE log)

# Build liblocal-socket.so (C++, static STL to avoid libstdc++ dependency)
add_library("local-socket" SHARED local-socket.cpp)
target_link_libraries("local-socket" PRIVATE log)
target_compile_options("local-socket" PRIVATE -fvisibility=hidden)

# 16KB page alignment for Android 15+ (same as -Wl,-z,max-page-size=16384)
set_target_properties(termux local-socket PROPERTIES
    LINK_FLAGS "-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
)
