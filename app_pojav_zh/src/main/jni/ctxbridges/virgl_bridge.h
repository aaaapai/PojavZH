//
// Created by Vera-Firefly on 20.08.2024.
//

#ifndef VIRGL_BRIDGE_H
#define VIRGL_BRIDGE_H

void* virglGetCurrentContext(void*);
void loadSymbolsVirGL(void);
int virglInit(int);
void virglSwapBuffers(void*);
void virglMakeCurrent(void* window);
void* virglCreateContext(void* contextSrc);
void virglSwapInterval(int interval);

#endif //VIRGL_BRIDGE_H
