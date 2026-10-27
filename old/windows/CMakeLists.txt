cmake_minimum_required(VERSION 3.21)
project(IslandWindows LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT WIN32)
  message(FATAL_ERROR "The Island desktop app targets Windows. The command/layout tests can also be built with any C++17 compiler.")
endif()

enable_language(RC)
add_executable(Island WIN32 main.cpp speech.cpp commands.cpp layout.cpp app.rc)
target_include_directories(Island PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_definitions(Island PRIVATE UNICODE _UNICODE)
target_link_libraries(Island PRIVATE gdiplus user32 gdi32 shell32 ole32 oleaut32 uuid)
if(MSVC)
  target_link_libraries(Island PRIVATE sapi)
  target_compile_options(Island PRIVATE $<$<COMPILE_LANGUAGE:CXX>:/utf-8> $<$<COMPILE_LANGUAGE:CXX>:/W4>)
  # The manifest is supplied in app.rc, and the MSVC C++ runtime is static so
  # the resulting program does not require a separate VC++ redistributable.
  target_link_options(Island PRIVATE /MANIFEST:NO)
  set_property(TARGET Island PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
elseif(MINGW)
  # Use the Unicode GUI entry point with MinGW-compatible compilers.
  target_link_options(Island PRIVATE -municode -Wl,--subsystem,windows)
endif()

enable_testing()
add_executable(IslandLogicTests ../tests/native/logic_test.cpp commands.cpp layout.cpp)
if(MSVC)
  target_compile_options(IslandLogicTests PRIVATE /utf-8 /W4)
  set_property(TARGET IslandLogicTests PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()
add_test(NAME native_logic COMMAND IslandLogicTests)
