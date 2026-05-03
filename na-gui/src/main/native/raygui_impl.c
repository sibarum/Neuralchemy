/*
 * Single translation unit that pulls raygui's implementation out of the header.
 * raygui is header-only by default; #defining RAYGUI_IMPLEMENTATION before include
 * activates the function bodies. Compiled into raygui.dll, which links against
 * raylib.lib (the import library produced by na-raylib's CMake build) so its
 * raylib calls resolve at runtime against the already-loaded raylib.dll.
 */
#define RAYGUI_IMPLEMENTATION
#include "raygui.h"
