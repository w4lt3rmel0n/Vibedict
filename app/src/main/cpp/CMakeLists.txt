cmake_minimum_required(VERSION 3.22.1)

project("waltermelon-native")

# --- CRITICAL FIX 1: Force Scalar Mode Globally ---
# This tells the preprocessor: "Whenever you see tb64dec, replace it with tb64sdec"
# We use add_compile_definitions so it applies to EVERYTHING.
add_compile_definitions(TBASE64_NO_SIMD)
# -------------------------------------------------

add_library(
        waltermelon-native
        SHARED
        native-lib.cpp
        
        # Core MDict Source
        mdict-cpp/mdict.cc
        mdict-cpp/mdict_extern.cc
        mdict-cpp/adler32.cc
        mdict-cpp/binutils.cc
        mdict-cpp/ripemd128.c
        
        # Dependencies - Miniz
        mdict-cpp/deps/miniz/miniz.c
        mdict-cpp/deps/miniz/miniz_tdef.c
        mdict-cpp/deps/miniz/miniz_tinfl.c
        mdict-cpp/deps/miniz/miniz_zip.c
        
        # Dependencies - MiniLZO
        mdict-cpp/deps/minilzo/minilzo.c
        
        # Dependencies - TurboBase64
        mdict-cpp/deps/turbobase64/turbob64c.c
        mdict-cpp/deps/turbobase64/turbob64d.c
)

# Include directories
target_include_directories(
        waltermelon-native PRIVATE
        mdict-cpp/include
        mdict-cpp
        mdict-cpp/deps
        mdict-cpp/deps/miniz
        mdict-cpp/deps/minilzo
)

find_library(log-lib log)

target_link_libraries(
        waltermelon-native
        ${log-lib}
)