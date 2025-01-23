LOCAL_PATH := $(call my-dir)
HERE_PATH := $(LOCAL_PATH)

# include $(HERE_PATH)/crash_dump/libbase/Android.mk
# include $(HERE_PATH)/crash_dump/libbacktrace/Android.mk
# include $(HERE_PATH)/crash_dump/debuggerd/Android.mk


LOCAL_PATH := $(HERE_PATH)

$(call import-module,prefab/bytehook)
LOCAL_PATH := $(HERE_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE := angle_gles2
LOCAL_SRC_FILES := angle/angle-gles/$(TARGET_ARCH_ABI)/libGLESv2_angle.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := Angle
LOCAL_SRC_FILES := angle/main.c angle/string_utils.c
LOCAL_SHARED_LIBRARIES := angle_gles2
LOCAL_C_INCLUDES := $(LOCAL_PATH)/angle
LOCAL_CFLAGS += -rdynamic
LOCAL_CFLAGS += -O3 -fPIC -DPIC -flto=thin -fwhole-program-vtables -mllvm -polly -pthread -Wall -std=c2x -femulated-tls -march=armv8-a+simd -mcpu=cortex-a53 -fdata-sections -ffunction-sections -fmerge-all-constants
LOCAL_LDLAGS += -flto=thin -Wl,-plugin-opt=-emulated-tls -fuse-ld=lld
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
# Link GLESv2 for test
LOCAL_LDLIBS := -ldl -llog -landroid
# -lGLESv2
LOCAL_MODULE := pojavexec
LOCAL_SHARED_LIBRARIES := bytehook
LOCAL_CFLAGS += -rdynamic
# LOCAL_CFLAGS += -DDEBUG
# -DGLES_TEST
LOCAL_SRC_FILES := \
    bigcoreaffinity.c \
    egl_bridge.c \
    ctxbridges/gl_bridge.c \
    ctxbridges/osm_bridge.c \
    ctxbridges/egl_loader.c \
    ctxbridges/osmesa_loader.c \
    ctxbridges/swap_interval_no_egl.c \
    ctxbridges/virgl_bridge.c \
    environ/environ.c \
    input_bridge_v3.c \
    utils.c \
    stdio_is.c \
    java_exec_hooks.c \
    lwjgl_dlopen_hook.c \
    ctxbridges/PojavRendererInit_nativeInitGl4esInternals.c \
    driver_helper/nsbypass.c

ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS += -DADRENO_POSSIBLE
LOCAL_CFLAGS += -O3 -fPIC -DPIC -flto=thin -fwhole-program-vtables -mllvm -polly -pthread -Wall -std=c2x -femulated-tls -march=armv8-a+simd -mcpu=cortex-a53 -fdata-sections -ffunction-sections -fmerge-all-constants
LOCAL_LDLAGS += -flto=thin -Wl,-plugin-opt=-emulated-tls -fuse-ld=lld
LOCAL_LDLIBS += -lEGL -lGLESv3
endif
include $(BUILD_SHARED_LIBRARY)

#ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
include $(CLEAR_VARS)
LOCAL_MODULE := linkerhook
LOCAL_SRC_FILES := driver_helper/hook.c
LOCAL_LDFLAGS := -z global
LOCAL_CFLAGS += -O2 -fPIC -DPIC -flto=thin -fwhole-program-vtables -mllvm -polly -pthread -Wall -std=c2x -femulated-tls -march=armv8-a+simd -mcpu=cortex-a53 -fdata-sections -ffunction-sections -fmerge-all-constants
LOCAL_LDLAGS += -flto=thin -Wl,-plugin-opt=-emulated-tls -fuse-ld=lld
include $(BUILD_SHARED_LIBRARY)
#endif

include $(CLEAR_VARS)
LOCAL_MODULE := pojavexec_awt
LOCAL_SRC_FILES := \
    awt_bridge.c
LOCAL_CFLAGS += -O2 -fPIC -DPIC -flto=thin -fwhole-program-vtables -mllvm -polly -pthread -Wall -std=c2x -femulated-tls -march=armv8-a+simd -mcpu=cortex-a53 -fdata-sections -ffunction-sections -fmerge-all-constants
LOCAL_LDLAGS += -flto=thin -Wl,-plugin-opt=-emulated-tls -fuse-ld=lld
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := jrelauncher
LOCAL_SHARED_LIBRARIES := pojavexec
LOCAL_LDLIBS := -llog -landroid
LOCAL_SRC_FILES := \
    jre_launcher.c
LOCAL_CFLAGS += -O2 -fPIC -DPIC -flto=thin -fwhole-program-vtables -mllvm -polly -pthread -Wall -std=c2x -femulated-tls -march=armv8-a+simd -mcpu=cortex-a53 -fdata-sections -ffunction-sections -fmerge-all-constants
LOCAL_LDLAGS += -flto=thin -Wl,-plugin-opt=-emulated-tls -fuse-ld=lld
include $(BUILD_SHARED_LIBRARY)

# Helper to get current thread
# include $(CLEAR_VARS)
# LOCAL_MODULE := thread64helper
# LOCAL_SRC_FILES := thread_helper.cpp
# include $(BUILD_SHARED_LIBRARY)

# fake lib for linker
include $(CLEAR_VARS)
LOCAL_MODULE := awt_headless
include $(BUILD_SHARED_LIBRARY)

# libawt_xawt without X11, used to get Caciocavallo working
LOCAL_PATH := $(HERE_PATH)/awt_xawt
include $(CLEAR_VARS)
LOCAL_MODULE := awt_xawt
# LOCAL_CFLAGS += -DHEADLESS
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_SHARED_LIBRARIES := awt_headless
LOCAL_SRC_FILES := xawt_fake.c
LOCAL_CFLAGS += -O2 -fPIC -DPIC -flto=thin -fwhole-program-vtables -mllvm -polly -pthread -Wall -std=c2x -femulated-tls -march=armv8-a+simd -mcpu=cortex-a53 -fdata-sections -ffunction-sections -fmerge-all-constants
LOCAL_LDLAGS += -flto=thin -Wl,-plugin-opt=-emulated-tls -fuse-ld=lld
include $(BUILD_SHARED_LIBRARY)
