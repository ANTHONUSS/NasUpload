cmake_minimum_required(VERSION 3.31)
project(NasUpload)

set(CMAKE_CXX_STANDARD 20)

add_executable(NasUpload src/main.cpp lib/tinyFileDialogs/tinyfiledialogs.c lib/tinyFileDialogs/tinyfiledialogs.h resources/icon.rc)
target_link_options(NasUpload PRIVATE -static -static-libgcc -static-libstdc++)
