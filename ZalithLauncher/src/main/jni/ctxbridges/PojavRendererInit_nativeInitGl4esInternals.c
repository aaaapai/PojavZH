#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <string.h>
#include <malloc.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <stdbool.h>
#include <environ/environ.h>
#include "gl_bridge.h"
#include "egl_loader.h"

static const char* g_LogTag = "GLBridge";
static __thread gl_render_window_t* currentBundle;
static EGLDisplay g_EglDisplay;

static void gl4esi_get_display_dimensions(int* width, int* height) {
    if(currentBundle == NULL) goto zero;
    EGLSurface surface = currentBundle->surface;
    // Fetch dimensions from the EGL surface - the most reliable way
    EGLBoolean result_width = eglQuerySurface_p(g_EglDisplay, surface, EGL_WIDTH, width);
    EGLBoolean result_height = eglQuerySurface_p(g_EglDisplay, surface, EGL_HEIGHT, height);
    if(!result_width || !result_height) goto zero;
    return;

    zero:
    // No idea what to do, but feeding gl4es incorrect or non-initialized dimensions may be
    // a bad idea. Set to zero in case of errors.
    *width = 0;
    *height = 0;
}

static bool already_initialized = false;
void gl_init_gl4es_internals(void) {
    if(already_initialized) return;
    already_initialized = true;
    void* gl4es = dlopen("libgl4es_114.so", RTLD_NOLOAD);
    if(gl4es == NULL) return;
    void (*set_getmainfbsize)(void (*new_getMainFBSize)(int* width, int* height));
    set_getmainfbsize = dlsym(gl4es, "set_getmainfbsize");
    if(set_getmainfbsize == NULL) goto warn;
    set_getmainfbsize(gl4esi_get_display_dimensions);
    goto cleanup;

    warn:
    printf("gl4esinternals warning: gl4es was found but internals not initialized. expect rendering issues.\n");
    cleanup:
    // dlclose just decreases a ref counter, so this is fine
    dlclose(gl4es);
}
