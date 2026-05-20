# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

project("seamlessloopmobile")

# 引入 Oboe 库
find_package(oboe REQUIRED CONFIG)

# 定义我们的动态库
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
        AudioEngine.cpp
        AudioDecoder.cpp)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
        # 链接 Oboe
        oboe::oboe

        # 链接系统库
        android
        log
        mediandk)

# ============ loopfinder 子项目 ============
set(BUILD_FOR_ANDROID ON CACHE BOOL "Build for Android NDK")
set(BUILD_TEST OFF CACHE BOOL "Build test executable")
add_subdirectory(loopfinder)