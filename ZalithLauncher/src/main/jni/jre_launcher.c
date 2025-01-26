/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
// Boardwalk: missing include
#include <string.h>

#include "log.h"
#include "utils.h"
#include "environ/environ.h"

// Uncomment to try redirect signal handling to JVM
// #define TRY_SIG2JVM

// PojavLancher: fixme: are these wrong?
#define FULL_VERSION "1.8.0-internal"
#define DOT_VERSION "1.8"

void ZL_check_java8(void) {
    if (getenv("ZL_isJava8") != NULL) {
    } else {
        #define TRY_SIG2JVM
    }
}

__attribute__((unused)) static const char* const_progname = "java";
__attribute__((unused)) static const char* const_launcher = "openjdk";
static const char** const_jargs = NULL;
__attribute__((unused)) static const char** const_appclasspath = NULL;
static const jboolean const_javaw = JNI_FALSE;
static const jboolean const_cpwildcard = JNI_TRUE;
static const jint const_ergo_class = 0; // DEFAULT_POLICY
static struct sigaction old_sa[NSIG];

static void (*__old_sa)(int signal, siginfo_t *info, void *reserved);
static int (*sigaction_p) (int signum,
              const struct sigaction *_Nullable restrict act,
              struct sigaction *_Nullable restrict oldact);
static int (*JVM_handle_linux_signal)(int signo, siginfo_t* siginfo, void* ucontext, int abort_if_unrecognized);

static void android_sigaction(int signal, siginfo_t *info, void *reserved) {
  if (JVM_handle_linux_signal == NULL) { // should not happen, but still
      __old_sa = old_sa[signal].sa_sigaction;
      __old_sa(signal,info,reserved);
      exit(1);
  } else {
      // Based on https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u/blob/aarch64-shenandoah-jdk8u272-b10/hotspot/src/os/linux/vm/os_linux.cpp#L4688-4693
      int orig_errno = errno;  // Preserve errno value over signal handler.
      JVM_handle_linux_signal(signal, info, reserved, true);
      errno = orig_errno;
  }
}
typedef jint JNI_CreateJavaVM_func(JavaVM **pvm, void **penv, void *args);

typedef jint JLI_Launch_func(int argc, char ** argv, /* main argc, argc */
        int jargc, const char** jargv,          /* java args */
        int appclassc, const char** appclassv,  /* app classpath */
        const char* fullversion,                /* full version defined */
        const char* dotversion,                 /* dot version defined */
        const char* pname,                      /* program name */
        const char* lname,                      /* launcher name */
        jboolean javaargs,                      /* JAVA_ARGS */
        jboolean cpwildcard,                    /* classpath wildcard*/
        jboolean javaw,                         /* windows-only javaw */
        jint ergo                               /* ergonomics class policy */
);

struct {
    sigset_t tracked_sigset;
    int pipe[2];
} abort_waiter_data;

_Noreturn extern void nominal_exit(int code, bool is_signal);

_Noreturn static void* abort_waiter_thread(void* extraArg) {
    pthread_sigmask(SIG_BLOCK, &abort_waiter_data.tracked_sigset, NULL);
    int signal;
    read(abort_waiter_data.pipe[0], &signal, sizeof(int));
    nominal_exit(signal, true);
}

_Noreturn static void abort_waiter_handler(int signal) {
    write(abort_waiter_data.pipe[1], &signal, sizeof(int));
    while(1) {}
}

static void abort_waiter_setup(void) {
    const static int tracked_signals[] = {SIGABRT};
    const static int ntracked = (sizeof(tracked_signals) / sizeof(tracked_signals[0]));
    struct sigaction sigactions[ntracked];
    sigemptyset(&abort_waiter_data.tracked_sigset);

    for (size_t i = 0; i < ntracked; i++)
    {
        sigaddset(&abort_waiter_data.tracked_sigset, tracked_signals[i]);
        sigactions[i].sa_handler = abort_waiter_handler;
    }

    if (pipe(abort_waiter_data.pipe) != 0)
    {
        printf("Failed to set up aborter pipe: %s\n", strerror(errno));
        return;
    }

    pthread_t waiter_thread; int result;

    if ((result = pthread_create(&waiter_thread, NULL, abort_waiter_thread, NULL)) != 0)
    {
        printf("Failed to start up waiter thread: %s", strerror(result));
        for(int i = 0; i < 2; i++) close(abort_waiter_data.pipe[i]);
        return;
    }

    for (size_t i = 0; i < ntracked; i++)
    {
        if (sigaction(tracked_signals[i], &sigactions[i], NULL) != 0)
        {
            printf("Failed to set signal hander for signal %i: %s", i, strerror(errno));
        }
    }
}

static jint launchJVM(int margc, char** margv) {
   void* libjli = dlopen("libjli.so", RTLD_LAZY | RTLD_GLOBAL);
   struct sigaction clean_sa;
   memset(&clean_sa, 0, sizeof (struct sigaction));

   for (int sigid = SIGHUP; sigid < NSIG; sigid++)
   {
       if (sigid == SIGSEGV)
           clean_sa.sa_handler = SIG_IGN;
       else clean_sa.sa_handler = SIG_DFL;

       sigaction(sigid, &clean_sa, NULL);
   }

   abort_waiter_setup();
   // Boardwalk: silence
   // LOGD("JLI lib = %x", (int)libjli);
   if (NULL == libjli)
   {
       LOGE("JLI lib = NULL: %s", dlerror());
       return -1;
   }
   LOGD("Found JLI lib");

   JLI_Launch_func *pJLI_Launch = (JLI_Launch_func *)dlsym(libjli, "JLI_Launch");
    // Boardwalk: silence
    // LOGD("JLI_Launch = 0x%x", *(int*)&pJLI_Launch);

   if (NULL == pJLI_Launch)
   {
       LOGE("JLI_Launch = NULL");
       return -1;
   }

   LOGD("Calling JLI_Launch");

   return pJLI_Launch(margc, margv,
                   0, NULL, // sizeof(const_jargs) / sizeof(char *), const_jargs,
                   0, NULL, // sizeof(const_appclasspath) / sizeof(char *), const_appclasspath,
                   FULL_VERSION,
                   DOT_VERSION,
                   *margv, // (const_progname != NULL) ? const_progname : *margv,
                   *margv, // (const_launcher != NULL) ? const_launcher : *margv,
                   (const_jargs != NULL) ? JNI_TRUE : JNI_FALSE,
                   const_cpwildcard, const_javaw, const_ergo_class);

}

JNIEXPORT jint JNICALL Java_com_oracle_dalvik_VMLauncher_launchJVM(JNIEnv *env, jclass clazz, jobjectArray argsArray) {
#ifdef TRY_SIG2JVM
  void* libjsig = dlopen("libjsig.so", RTLD_LAZY | RTLD_GLOBAL);
  if (NULL == libjsig) {
      LOGE("JSig lib = NULL: %s", dlerror());
      return -1;
  }
  sigaction_p = (void*) dlsym(libjsig, "sigaction");

  void* libjvm = dlopen("libjvm.so", RTLD_LAZY | RTLD_GLOBAL);
  if (NULL == libjvm) {
      LOGE("JVM lib = NULL: %s", dlerror());
      return -1;
  }
  JVM_handle_linux_signal = dlsym(libjvm, "JVM_handle_linux_signal");
#endif
    jint res = 0;
    // int i;
    //Prepare the signal trapper
    struct sigaction catcher;
    memset(&catcher,0,sizeof(sigaction));
    catcher.sa_sigaction = android_sigaction;
    catcher.sa_flags = SA_SIGINFO|SA_RESTART;
    // SA_RESETHAND;
#define CATCHSIG(X) sigaction_p(X, &catcher, &old_sa[X])
     CATCHSIG(SIGILL);
     CATCHSIG(SIGABRT);
     CATCHSIG(SIGBUS);
     CATCHSIG(SIGFPE);
#ifdef TRY_SIG2JVM
     CATCHSIG(SIGSEGV);
#endif
     CATCHSIG(SIGSTKFLT);
     CATCHSIG(SIGPIPE);
     CATCHSIG(SIGXFSZ);
    //Signal trapper ready

    // Save dalvik JNIEnv pointer for JVM launch thread
    pojav_environ->dalvikJNIEnvPtr_ANDROID = env;

    if (argsArray == NULL)
    {
        LOGE("Args array null, returning");
        return 0;
    }

    int argc = (*env)->GetArrayLength(env, argsArray);
    char **argv = convert_to_char_array(env, argsArray);
    LOGD("Done processing args");

    res = launchJVM(argc, argv);

    LOGD("Going to free args");
    free_char_array(env, argsArray, argv);

    LOGD("Free done");
    return res;
}
