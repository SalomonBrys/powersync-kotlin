cmake_minimum_required(VERSION 3.18.1)

project(powersync-sqlite)

if(NOT SQLITE3_SRC_DIR)
    set(SQLITE3_SRC_DIR "../../../build/interop/sqlite")
endif()

set(PACKAGE_NAME "powersync-sqlite")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY output)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY output)

find_package(JNI REQUIRED)
find_package(Threads REQUIRED)

include_directories(${SQLITE3_SRC_DIR})
include_directories(${JNI_INCLUDE_DIRS})

add_library(sqlite3 STATIC
        "${SQLITE3_SRC_DIR}/sqlite3.c"
)

add_library(
        ${PACKAGE_NAME}
        SHARED
        ../../jvmNative/cpp/sqlite_bindings.cpp
)

target_link_libraries(
        ${PACKAGE_NAME}
        PRIVATE
        sqlite3
        ${CMAKE_THREAD_LIBS_INIT}
)