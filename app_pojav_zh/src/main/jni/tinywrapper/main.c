//#import <Foundation/Foundation.h>
#include <stdio.h>
#include <dlfcn.h>
#include <string.h>
#include <malloc.h>

#include "GL/gl.h"
#include "spirv_cross/include/spirv_cross_c.h"
#include "shaderc/include/shaderc.h"
#include "string_utils.h"

#define LOOKUP_FUNC(func) \
    if (!gles_##func) { \
        gles_##func = dlsym(RTLD_NEXT, #func); \
    } if (!gles_##func) { \
        gles_##func = dlsym(RTLD_DEFAULT, #func); \
    }

int proxy_width, proxy_height, proxy_intformat, maxTextureSize;

void(*gles_glGetTexLevelParameteriv)(GLenum target, GLint level, GLenum pname, GLint *params);
void(*gles_glShaderSource)(GLuint shader, GLsizei count, const GLchar * const *string, const GLint *length);
GLuint (*gles_glCreateShader) (GLenum shaderType);
void(*gles_glTexImage2D)(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const GLvoid *data);
void(*gles_glDrawElementsBaseVertex)(GLenum mode,
                                  GLsizei count,
                                  GLenum type,
                                  void *indices,
                                  GLint basevertex);
void (*gles_glGetBufferParameteriv) (GLenum target, GLenum pname, GLint *params);
void * (*gles_glMapBufferRange) (GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access);
const GLubyte * (*gles_glGetString) (GLenum name);
void (*gles_glTexParameterf) (GLenum target, GLenum pname, GLfloat param);

void *glMapBuffer(GLenum target, GLenum access) {
    // Use: GL_EXT_map_buffer_range
    LOOKUP_FUNC(glGetBufferParameteriv);
    LOOKUP_FUNC(glMapBufferRange);

    GLenum access_range;
    GLint length;

    switch (target) {
        // GL 4.2
        case GL_ATOMIC_COUNTER_BUFFER:

        // GL 4.3
        case GL_DISPATCH_INDIRECT_BUFFER:
        case GL_SHADER_STORAGE_BUFFER	:

        // GL 4.4
        case GL_QUERY_BUFFER:
            printf("ERROR: glMapBuffer unsupported target=0x%x", target);
            break; // not supported for now

	     case GL_DRAW_INDIRECT_BUFFER:
        case GL_TEXTURE_BUFFER:
            printf("ERROR: glMapBuffer unimplemented target=0x%x", target);
            break;
    }

    switch (access) {
        case GL_READ_ONLY:
            access_range = GL_MAP_READ_BIT;
            break;

        case GL_WRITE_ONLY:
            access_range = GL_MAP_WRITE_BIT;
            break;

        case GL_READ_WRITE:
            access_range = GL_MAP_READ_BIT | GL_MAP_WRITE_BIT;
            break;
    }

    gles_glGetBufferParameteriv(target, GL_BUFFER_SIZE, &length);
    return gles_glMapBufferRange(target, 0, length, access_range);
}

static GLenum currShaderType = GL_VERTEX_SHADER;

GLuint glCreateShader(GLenum shaderType) {
    LOOKUP_FUNC(glCreateShader);

    currShaderType = shaderType;

    return gles_glCreateShader(shaderType);
}

void glShaderSource(GLuint shader, GLsizei count, const GLchar * const *string, const GLint *length) {
    LOOKUP_FUNC(glShaderSource)

    // DBG(printf("glShaderSource(%d, %d, %p, %p)\n", shader, count, string, length);)
    char *source = NULL;
    char *converted;

    // get the size of the shader sources and than concatenate in a single string
    int l = 0;
    for (int i=0; i<count; i++) l+=(length && length[i] >= 0)?length[i]:strlen(string[i]);
    if (source) free(source);
    source = calloc(1, l+1);
    if(length) {
        for (int i=0; i<count; i++) {
            if(length[i] >= 0)
                strncat(source, string[i], length[i]);
            else
                strcat(source, string[i]);
        }
    } else {
        for (int i=0; i<count; i++)
            strcat(source, string[i]);
    }

    char *source2 = strchr(source, '#');
    if (!source2) {
        source2 = source;
    }
    // are there #version?
    if (!strncmp(source2, "#version ", 9)) {
        converted = strdup(source2);
        if (converted[9] == '1') {
            if (converted[10] - '0' < 2) {
                // 100, 110 -> 120
                converted[10] = '2';
            } else if (converted[10] - '0' < 6) {
                // 130, 140, 150 -> 330
                converted[9] = converted[10] = '3';
            }
        }
        // remove "core", is it safe?
        if (!strncmp(&converted[13], "core", 4)) {
            strncpy(&converted[13], "\n//c", 4);
        }
    } else {
        converted = calloc(1, strlen(source) + 13);
        strcpy(converted, "#version 120\n");
        strcpy(&converted[13], strdup(source));
    }

    int convertedLen = strlen(converted);

#ifdef __APPLE__
    // patch OptiFine 1.17.x
    if (gl4es_find_string(converted, "\nuniform mat4 textureMatrix = mat4(1.0);")) {
        gl4es_inplace_replace(converted, &convertedLen, "\nuniform mat4 textureMatrix = mat4(1.0);", "\n#define textureMatrix mat4(1.0)");
    }
#endif

    // some needed exts
    const char* extensions =
        "#extension GL_EXT_blend_func_extended : enable\n"
        // For OptiFine (see patch above)
        "#extension GL_EXT_shader_non_constant_global_initializers : enable\n";
    converted = gl4es_inplace_insert(gl4es_getline(converted, 1), extensions, converted, &convertedLen);

    gles_glShaderSource(shader, 1, (const GLchar * const*)((converted)?(&converted):(&source)), NULL);

    free(source);
    free(converted);
}

int isProxyTexture(GLenum target) {
    switch (target) {
        case GL_PROXY_TEXTURE_1D:
        case GL_PROXY_TEXTURE_2D:
        case GL_PROXY_TEXTURE_3D:
        case GL_PROXY_TEXTURE_RECTANGLE_ARB:
            return 1;
    }
    return 0;
}

static int inline nlevel(int size, int level) {
    if(size) {
        size>>=level;
        if(!size) size=1;
    }
    return size;
}

void glGetTexLevelParameteriv(GLenum target, GLint level, GLenum pname, GLint *params) {
    LOOKUP_FUNC(glGetTexLevelParameteriv)
    // NSLog("glGetTexLevelParameteriv(%x, %d, %x, %p)", target, level, pname, params);
    if (isProxyTexture(target)) {
        switch (pname) {
            case GL_TEXTURE_WIDTH:
                (*params) = nlevel(proxy_width,level);
                break;
            case GL_TEXTURE_HEIGHT:
                (*params) = nlevel(proxy_height,level);
                break;
            case GL_TEXTURE_INTERNAL_FORMAT:
                (*params) = proxy_intformat;
                break;
        }
    } else {
        gles_glGetTexLevelParameteriv(target, level, pname, params);
    }
}

void glTexParameterf(GLenum target, GLenum pname, GLfloat param) {
    LOOKUP_FUNC(glTexParameterf);

    // Not supported, crashes some mods that check
    // for OpenGL errors
    if(pname == GL_TEXTURE_LOD_BIAS_EXT) {
        return;
    }

    gles_glTexParameterf(target, pname, param);
}

void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const GLvoid *data) {
    LOOKUP_FUNC(glTexImage2D)

    // Regal doesn't handle depth formats well
    // Convert it to sized GLES formats instead
    if(internalformat == GL_DEPTH_COMPONENT) {
        switch (type) {
            case GL_UNSIGNED_SHORT:
                internalformat = GL_DEPTH_COMPONENT16;
                break;
            case GL_UNSIGNED_INT:
                internalformat = GL_DEPTH_COMPONENT24;
                break;
            case GL_FLOAT:
                internalformat = GL_DEPTH_COMPONENT32F;
                break;
            default:
                printf("Depth texture type %d failed for depth component!\n", type);
                break;
        }
    } else if(internalformat == GL_DEPTH_STENCIL) {
        switch (type) {
            case GL_UNSIGNED_INT:
                internalformat = GL_DEPTH24_STENCIL8;
                break;
            case GL_FLOAT:
                internalformat = GL_DEPTH32F_STENCIL8;
                break;
            default:
                printf("Depth texture type %d failed for depth stencil!\n", type);
                break;
        }
    }

    if (isProxyTexture(target)) {
        if (!maxTextureSize) {
            glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTextureSize);
            // maxTextureSize = 16384;
            // NSLog(@"Maximum texture size: %d", maxTextureSize);
        }
        proxy_width = ((width<<level)>maxTextureSize)?0:width;
        proxy_height = ((height<<level)>maxTextureSize)?0:height;
        proxy_intformat = internalformat;
        // swizzle_internalformat((GLenum *) &internalformat, format, type);
    } else {
        gles_glTexImage2D(target, level, internalformat, width, height, border, format, type, data);
    }
}

// Sodium
const void *const glMultiDrawElementsBaseVertex(	GLenum mode,
                                       const GLsizei *count,
                                       GLenum type,
                                       const void * const *indices,
                                       GLsizei drawcount,
                                       const GLint *basevertex) {
    LOOKUP_FUNC(glDrawElementsBaseVertex);
    for (int i = 0; i < drawcount; i++) {
        if (count[i] > 0)
            gles_glDrawElementsBaseVertex(mode,
                                     count[i],
                                     type,
                                     indices[i],
                                     basevertex[i]);
    }
}

const GLubyte * glGetString(GLenum name) {
    LOOKUP_FUNC(glGetString);

    switch (name) {
        case GL_VERSION:
            return "4.6";
        case GL_SHADING_LANGUAGE_VERSION:
            return "4.5";
        default:
            return gles_glGetString(name);
    }
}
