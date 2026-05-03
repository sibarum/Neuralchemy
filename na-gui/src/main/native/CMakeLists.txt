cmake_minimum_required(VERSION 3.20)
project(raygui_dll C)

# Inputs (set from the parent pom via -D):
#   RAYGUI_SRC   path containing raygui.h
#   RAYLIB_SRC   path containing raylib.h
#   RAYLIB_LIB   path to raylib.lib (the import library for raylib.dll)
foreach(var RAYGUI_SRC RAYLIB_SRC RAYLIB_LIB)
    if(NOT DEFINED ${var})
        message(FATAL_ERROR "${var} must be defined (pass -D${var}=...)")
    endif()
endforeach()

# Mark every raygui function exported. raygui's header uses RAYGUIAPI with the
# expected dllexport/dllimport guards, so we just need __declspec(dllexport) on
# the implementation side and the bindings will see exported symbols.
add_library(raygui SHARED ${CMAKE_CURRENT_SOURCE_DIR}/raygui_impl.c)
target_compile_definitions(raygui PRIVATE BUILD_LIBTYPE_SHARED)
target_include_directories(raygui PRIVATE ${RAYGUI_SRC} ${RAYLIB_SRC})
target_link_libraries(raygui PRIVATE ${RAYLIB_LIB})

set_target_properties(raygui PROPERTIES
    OUTPUT_NAME raygui
    RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}/Release
)
