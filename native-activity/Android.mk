LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := libnative-activity
LOCAL_SRC_FILES := native_activity.c
LOCAL_C_INCLUES := android_native_app_glue.h

LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv3
LOCAL_STATIC_LIBRARIES := android_native_app_glue

include $(BUILD_SHARED_LIBRARY)
$(call import-module,android/native_app_glue)
