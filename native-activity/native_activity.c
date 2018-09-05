#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "android_native_app_glue.h"
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>

#define LOG_TAG   "native-activity"
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

static EGLDisplay display;
static EGLConfig  config;
static EGLSurface surface;
static EGLContext context;

static GLuint vShader, fShader;
static GLuint glProgram;
static EGLBoolean initialized;

//vertex data
static GLfloat triangle[] = {
    0, 0.5f, 0,
    -0.5f, 0, 0,
    0, -0.5f, 0,
    0.5f, 0, 0,
};

static GLfloat color[] = {
    1.0f, 0, 0, 0,
    0, 1.0f, 0, 0,
    0, 0, 1.0f, 0,
    0, 0, 0, 0,
};

static void destroyGlEs (struct android_app *app) {
    if (initialized == GL_FALSE)
        return;

    glDeleteShader(vShader);
    glDeleteShader(fShader);
    glDeleteProgram(glProgram);

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
}

static void obtainGlEsParams () {
    EGLint width, height;

    eglQuerySurface(display, surface, EGL_WIDTH, &width);
    eglQuerySurface(display, surface, EGL_HEIGHT, &height);
    LOGE("egl dimension width %d, height %d", width, height);
}

static void getEglConfigs () {
    GLint num_configs = 0;
    GLint attrs[] = {
        EGL_BUFFER_SIZE, EGL_RED_SIZE, EGL_GREEN_SIZE, EGL_BLUE_SIZE,
        EGL_ALPHA_SIZE,  EGL_DEPTH_SIZE, EGL_SURFACE_TYPE,
    };
    GLchar* attr_names[] = {
        "EGL_BUFFER_SIZE", "EGL_RED_SIZE", "EGL_GREEN_SIZE", "EGL_BLUE_SIZE",
        "EGL_ALPHA_SIZE",  "EGL_DEPTH_SIZE", "EGL_SURFACE_TYPE",
    };

    if (eglGetConfigs(display, NULL, 0, &num_configs) == EGL_FALSE) {
        LOGE("eglGetConfigs failed. %s", eglGetError());
        return;
    }

    LOGI("eglGetConfigs num_configs : %d", num_configs);
    EGLConfig *pconfig = (EGLConfig*)malloc(sizeof(EGLConfig) * num_configs);
    if (pconfig == NULL) {
        LOGE("alloc EGLConfig buffer failed.");
        return;
    }

    GLint tmp;
    if (eglGetConfigs(display, pconfig, num_configs, &tmp) == EGL_FALSE) {
        free(pconfig);
        LOGE("eglGetConfig information failed. %d", eglGetError());
        return;
    }

    int i = 0, j = 0;
    GLint value;
    for (j = 0; j < num_configs; j++) {
        EGLConfig config = pconfig[j];
        for (i = 0; i < sizeof(attrs)/sizeof(GLint); i++) {
            eglGetConfigAttrib(display, config, attrs[i], &value);
            LOGI("eglGetConfigAttrib %s : %d", attr_names[i], value);
        }
    }

    free(pconfig);
}

static GLboolean initGlEs (struct android_app *app) {
    display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return GL_FALSE;
    }

    if (!eglInitialize(display, NULL, NULL)) {
        LOGE("eglInitialize failed");
        return GL_FALSE;
    }

    char *buf;
    buf = eglQueryString(display, EGL_VERSION);
    LOGI("eglQueryString EGL_VERSION : %s", buf);
    buf = eglQueryString(display, EGL_VENDOR);
    LOGI("eglQueryString EGL_VENDOR  : %s", buf);
    buf = eglQueryString(display, EGL_EXTENSIONS);
    LOGI("eglQueryString EGL_EXTENSIONS : %s", buf);
    buf = eglQueryString(display, EGL_CLIENT_APIS);
    LOGI("eglQueryString EGL_CLIENT_APIS : %s", buf);

    getEglConfigs();

    GLint attributes[] = {
        EGL_RED_SIZE,   8,
        EGL_BLUE_SIZE,  8,
        EGL_GREEN_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_NONE,
    };

    EGLint num_config;
    if(!eglChooseConfig(display, attributes, &config, 1, &num_config) || num_config <= 0) {
        LOGE("eglChooseConfig failed");
        return GL_FALSE;
    }
    
    EGLint format;
    eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format);
    ANativeWindow_setBuffersGeometry(app->window, 0, 0, format);

    surface = eglCreateWindowSurface(display, config, app->window, NULL);
    if (surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed");
        return GL_FALSE;
    }

    EGLint context_attr[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE,
    };
    context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attr);
    if (context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed. %d", eglGetError());
        eglDestroySurface(display, surface);
        return GL_FALSE;
    }

    if (eglMakeCurrent(display, surface, surface, context) == EGL_FALSE) {
        eglDestroySurface(display, surface);
        eglDestroyContext(display, context);
        LOGE("eglMakeCurent failed");
        return GL_FALSE;
    }

    GLint maxVertexUniformsVector, maxFragmentUniformsVector;
    glGetIntegerv(GL_MAX_VERTEX_UNIFORM_VECTORS, &maxVertexUniformsVector);
    glGetIntegerv(GL_MAX_FRAGMENT_UNIFORM_VECTORS, &maxFragmentUniformsVector);
    LOGI("maxVertexUniformVector : %d, maxFragmentUniformsVector : %d", maxVertexUniformsVector, maxFragmentUniformsVector);

    GLint maxInVertex, maxOutVertex;
    glGetIntegerv(GL_MAX_VERTEX_ATTRIBS, &maxInVertex);
    glGetIntegerv(GL_MAX_VERTEX_OUTPUT_COMPONENTS, &maxOutVertex);
    LOGI("maxVertexInput : %d, maxVertexOutput : %d", maxInVertex, maxOutVertex);

    GLint maxInFragment;
    glGetIntegerv(GL_MAX_FRAGMENT_INPUT_COMPONENTS, &maxInFragment);
    LOGI("maxFragmentInput : %d", maxInFragment);

    return GL_TRUE;
}

static GLuint loadShader (const GLenum type, const GLchar *src) {
    GLuint shader = 0;

    shader = glCreateShader(type);
    if (!shader) {
        LOGE("glCreateShader %d failed. %d", type, glGetError());
        return 0;
    }

    glShaderSource(shader, 1, &src, NULL);
    glCompileShader(shader);

    GLint status;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) {
        LOGE("glCompile %d:%d failed.", shader, type);

        GLint length;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
        if (length <= 0) {
            glDeleteShader(shader);
            return 0;
        }

        GLchar *buf_log = malloc(sizeof(GLchar) * length);
        if (buf_log != NULL) {
            glGetShaderInfoLog(shader, length, NULL, buf_log);
            LOGE("glCompileShader result %s", buf_log);
            free(buf_log);
        }

        glDeleteShader(shader);
        return 0;
    }

    return shader;
}

static GLboolean initShader () {
    //vertex shader
    const GLchar *vertexShader =
        "#version 300 es               \n"
        "uniform vec4 test;            \n"
        "uniform block {               \n"
        "    vec4 status;              \n"
        "};                            \n"
        "in highp vec4 vPosition;            \n"
        "layout(location=15) in vec4 vColor;               \n"
        "flat out mediump vec4 color;        \n"
        "void main () {                \n"
        "    //vec initialize          \n"
        "    vec4 tmp = vec4(1.0, 1.0, 1.0, 1.0); \n"
        "    ivec2 tmp1 = ivec2(1);    \n"
        "    ivec3 tmp2 = ivec3(tmp1, 2); \n"
        "    ivec4 tmp3 = ivec4(tmp1, tmp1); \n"
        "    ivec2 t = ivec2(tmp3);    \n"
        "                              \n"
        "    //param translate         \n"
        "    float f1 = 2.3;           \n"
        "    bool  b1 = false;         \n"
        "    uint  u1 = uint(23);      \n"
        "    float f2 = float(b1);     \n"
        "    bool  b2 = bool(u1);      \n"
        "                              \n"
        "    //mat                     \n"
        "    mat2 m2 = mat2(1,2,3,4);  \n"
        "    mat2 m21 = mat2(tmp1, tmp1); \n"
        "    gl_Position = vPosition + test;  \n"
        "    color    = vColor;        \n"
        "}                             \n";

    //fragment shader
    const GLchar *fragmentShader = 
        "#version 300 es                      \n"
        "precision mediump float;             \n"
        "precision lowp int;                  \n"
        "struct testt {                       \n"
        "   uvec4 pos;                        \n"
        "   vec4  color;                      \n"
        "   float scale;                      \n"
        "};                                   \n"
        "                                     \n"
        "uniform vec4 test;                  \n"
        "flat in vec4 color;                       \n"
        "out vec4 frag_Color;                 \n"
        "                                     \n"
        "ivec4 add_ivec4 (in ivec4 p1, in ivec4 p2) { \n"
        "   vec4 color = vec4(0.3, 0.6, 0.2, 0.1); \n"
        "   if (color.a > 0.0 && color.a < 1.0) \n"
        "       color *= 2.0;                 \n"
        "   else                              \n"
        "       color = vec4(0.5, 0.5, 0.5, 0.5); \n"
        "                                     \n"
        "   vec3 v1 = vec3(0.1, 0.3, 0.5);    \n"
        "   vec3 v2 = vec3(0.8, 0.1, 0.12);   \n"
        "   float mul = dot(v1, v2);          \n"
        "   return p1 + p2;                   \n"
        "}                                    \n"
        "void main () {                       \n"
        "   testt t1 = testt(uvec4(1,1,1,1), vec4(1.0, 0.5, 0.8, 0.2), 0.5); \n"
        "   frag_Color = color;               \n"
        "                                     \n"
        "   bvec4 bv1 = bvec4(true, true, false, false); \n"
        "   bool  b1  = bv1.x;                \n"
        "   bool  b2  = bv1.w;                \n"
        "   bool  b3  = bv1[2];               \n"
        "   bvec4 bv2 = bv1.wzyx;             \n"
        "                                     \n"
        "   //mat                             \n"
        "   mat3 mt3 = mat3(1,2,3,4,5,6,7,8,9); \n"
        "   vec3 iv3 = mt3[0];               \n"
        "   float f   = mt3[1].x;            \n"
        "   vec3 v3  = mt3[2].zyx;           \n"
        "                                    \n"
        "   //const                          \n"
        "   const float pi = 3.1415926;      \n"
        "   //array                          \n"
        "   bvec2 bva[2] = bvec2[](bvec2(true, true), bvec2(false, false)); \n"
        "   ivec3 a = ivec3(1,2,3);          \n"
        "   int   a1 = 3;                    \n"
        "   ivec3 b = a * a1;                \n"
        "                                    \n"
        "   vec4 v4 = vec4(1.0, 0.5, 0.3, 0.6); \n"
        "   mat4 m4 = mat4(v4, v4, v4, v4);  \n"
        "   mat4 mm4 = m4 * m4;              \n"
        "   vec4 vv4 = m4 * v4;              \n"
        "   //matrix add                     \n"
        "   mat4 mm = m4 / mm4;              \n"
        "   ivec2 temp  = ivec2(2,4);        \n"
        "   ivec2 temp1 = temp % 2;          \n"
        "   //compare                        \n"
        "   bool equal = temp == temp1;      \n"
        "   bool equal1= m4 != mm4;          \n"
        "   ivec4 params = ivec4(1,2,3,4);   \n"
        "   ivec4 pp = add_ivec4(params, params); \n"
        "}                                   \n";

    if(!(vShader = loadShader(GL_VERTEX_SHADER, vertexShader)))
        return GL_FALSE;

    if(!(fShader = loadShader(GL_FRAGMENT_SHADER, fragmentShader))) {
        glDeleteShader(vShader);
        return GL_FALSE;
    }

    glProgram = glCreateProgram();
    if (!glProgram) {
        LOGE("glCreateProgram failed. %d", glGetError());
        return GL_FALSE;
    }

    glAttachShader(glProgram, vShader);
    glAttachShader(glProgram, fShader);
    glLinkProgram(glProgram);

    GLint status;
    glGetProgramiv(glProgram, GL_LINK_STATUS, &status);
    if (!status) {
        LOGE("glLinkProgram failed. %d", glGetError());

        GLint length;
        glGetProgramiv(glProgram, GL_INFO_LOG_LENGTH, &length);
        if (length <= 0) {
            glDeleteProgram(glProgram);
            return GL_FALSE;
        }

        GLchar *buf = malloc(sizeof(GLchar) * length);
        if (buf != NULL) {
            glGetProgramInfoLog(glProgram, length, NULL, buf);
            LOGE("glLinkProgram : %s", buf);
            free(buf);
        }

        glDeleteProgram(glProgram);
        return GL_FALSE;
    }

    GLint  size, name_length;
    GLuint index = glGetUniformBlockIndex(glProgram, "block");
    glGetActiveUniformBlockiv(glProgram, index, GL_UNIFORM_BLOCK_DATA_SIZE, &size);
    glGetActiveUniformBlockiv(glProgram, index, GL_UNIFORM_BLOCK_NAME_LENGTH, &name_length);
    LOGI("uniformBlockIndex : %ud, uniformBlockSize : %d, uniformBlockNameLength : %d", index, size, name_length);

    GLchar *name = (GLchar*)malloc(sizeof(GLchar) *name_length);
    glGetActiveUniformBlockName(glProgram, index, name_length, NULL, name);
    LOGI("glGetActiveUniformBlockName : %s", name);
    free(name);

    GLchar *uniformNames = "status";
    GLuint indice;
    GLint  offset;
    glGetUniformIndices(glProgram, 1, &uniformNames, &indice);
    glGetActiveUniformsiv(glProgram, 1, &indice, GL_UNIFORM_OFFSET, &offset);

    GLubyte *uniform_buffer_data = (GLubyte*)malloc(sizeof(GLubyte) * size);
    const GLfloat  translate[] = {0.5, 0, 0, 0};
    memcpy(uniform_buffer_data + offset, translate, 4 * sizeof(GLfloat));

    GLuint buffer_index;
    glGenBuffers(1, &buffer_index); 
    glBindBuffer(GL_UNIFORM_BUFFER, buffer_index);
    glBufferData(GL_UNIFORM_BUFFER, size, uniform_buffer_data, GL_DYNAMIC_DRAW);
    glBindBufferBase(GL_UNIFORM_BUFFER, index, buffer_index);
    free(uniform_buffer_data);

    GLint active_uniforms, active_uniform_max_length;
    glGetProgramiv(glProgram, GL_ACTIVE_UNIFORMS, &active_uniforms);
    glGetProgramiv(glProgram, GL_ACTIVE_UNIFORM_MAX_LENGTH, &active_uniform_max_length);
    LOGI("ACTIVE_UNIFORMS : %d, ACTIVE_UNIFORM_MAX_LENGTH : %d", active_uniforms, active_uniform_max_length);

    GLint active_vertex, active_vertex_length;
    glGetProgramiv(glProgram, GL_ACTIVE_ATTRIBUTES, &active_vertex);
    glGetProgramiv(glProgram, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, &active_vertex_length);
    LOGI("ACTIVE_ATTRIBUTES : %d, ACTIVE_ATTRIBUTE_MAX_LENGTH : %d", active_vertex, active_vertex_length);

    GLchar *attrib_name = (GLchar*)malloc(active_vertex_length * sizeof(GLchar));
    GLint attrib_size;
    GLenum attrib_type;

    GLint i = 0;
    for (i = 0; i < active_vertex; i++) {
        glGetActiveAttrib(glProgram, i, active_vertex_length, NULL, &attrib_size, &attrib_type, attrib_name);
        LOGI("glGetActiveAttrib %d : %d, %d, %s", i, attrib_size, attrib_type, attrib_name);
    }

    GLchar *uniform_name = (GLchar*)malloc(active_uniform_max_length * sizeof(GLchar));
    GLint uniform_size;
    GLenum uniform_type;

    for (i = 0; i < active_uniforms; i++) {
        glGetActiveUniform(glProgram, i, active_uniform_max_length, NULL, &uniform_size, &uniform_type, uniform_name);
        LOGI("glGetActiveUniform %d : %d, %d, %s", i, uniform_size, uniform_type, uniform_name);
    }
    free(uniform_name);

    return GL_TRUE;
}

static void glDraw () {
    GLint position = 0;
    GLint pColor = 0;
    GLint uniform_pos = 0;
    GLint uniform_status = 0;

    if (initialized != GL_TRUE)
        return;

    uniform_pos = glGetUniformLocation(glProgram, "test");
    //LOGI("test uniform location : %d", uniform_pos);
    glUniform4f(uniform_pos, 0.5, 0, 0, 0);

    //clear 
    glViewport(360, 1000, 360, 540);
    glClearColor(1.0f, 0, 0, 0);
    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(glProgram); 

    //shader
    position = glGetAttribLocation(glProgram, "vPosition");
    pColor = glGetAttribLocation(glProgram, "vColor");
    //LOGI("vPostion location : %d, vColor location : %d", position, pColor);

    glEnableVertexAttribArray(position);
    glVertexAttribPointer(position, 3, GL_FLOAT, GL_FALSE, 0, triangle);

    glEnableVertexAttribArray(pColor);
    glVertexAttribPointer(pColor, 4, GL_FLOAT, GL_FALSE, 0, color);
    glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
    eglSwapBuffers(display, surface);
}

static void handle_app_command (struct android_app *app, int32_t cmd) {
    switch(cmd) {
        case APP_CMD_START:
            LOGI("%s", "APP_CMD_START");
            break;
        case APP_CMD_RESUME:
            LOGI("%s", "APP_CMD_RESUME");
            break;
        case APP_CMD_PAUSE:
            LOGI("%s", "APP_CMD_PAUSE");
            break;
        case APP_CMD_STOP:
            LOGI("%s", "APP_CMD_STOP");
            break;
        case APP_CMD_DESTROY:
            LOGI("%s", "APP_CMD_DESTROY");
            break;
        case APP_CMD_CONFIG_CHANGED:
            LOGI("%s", "APP_CMD_CONFIG_CHANGED");
            break;
        case APP_CMD_SAVE_STATE:
            LOGI("%s", "APP_CMD_SAVE_STATE");
            break;
        case APP_CMD_INIT_WINDOW:
            LOGI("%s", "APP_CMD_INIT_WINDOW");
            if(initGlEs(app) == GL_TRUE && initShader() != GL_FALSE) {
                obtainGlEsParams();
                initialized = GL_TRUE;
            }
            break;
        case APP_CMD_TERM_WINDOW:
            LOGI("%s", "APP_CMD_TERM_WINDOW");
            destroyGlEs(app);
            break;
        case APP_CMD_LOST_FOCUS:
            LOGI("%s", "APP_CMD_LOST_FOCUS");
            break;
        case APP_CMD_GAINED_FOCUS:
            LOGI("%s", "APP_CMD_GAINED_FOCUS");
            break;
    }
}

static int32_t handle_app_event (struct android_app *app, AInputEvent *event) {
    return 0;
}

void android_main (struct android_app *app) {
    app_dummy();
    
    app->onAppCmd     = handle_app_command;
    app->onInputEvent = handle_app_event;

    while (1) {
        int ident;
        int events;
        struct android_poll_source *source;
        
        while ((ident = ALooper_pollAll(0, NULL, &events, (void**)&source)) >= 0) {
            if (source != NULL)
                source->process(app, source);

            if (app->destroyRequested != 0)
                return;
        }
        
        glDraw();
    }
}
