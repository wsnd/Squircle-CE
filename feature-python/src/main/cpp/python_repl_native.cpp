#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <signal.h>
#include <android/log.h>
#include <errno.h>

#define LOG_TAG "PythonReplJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int g_pty_master_fd = -1;
static pid_t g_python_pid = -1;

extern "C" {

JNIEXPORT jint JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_createPythonRepl(
    JNIEnv* env, jobject thiz, jstring pythonPath, jstring pythonHome, jstring nativeLibDir) {
    
    LOGI("Creating Python REPL with PTY");
    
    int ptm = open("/dev/ptmx", O_RDWR | O_NOCTTY);
    if (ptm < 0) {
        LOGE("Failed to open /dev/ptmx: %s", strerror(errno));
        return -1;
    }
    
    if (grantpt(ptm) != 0 || unlockpt(ptm) != 0) {
        LOGE("PTY setup failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }
    
    char pts_name[128];
    if (ptsname_r(ptm, pts_name, sizeof(pts_name)) != 0) {
        LOGE("ptsname_r failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }

    struct termios tios;
    tcgetattr(ptm, &tios);
    tios.c_iflag |= IUTF8;
    tios.c_iflag &= ~(IXON | IXOFF);
    cfmakeraw(&tios);
    tios.c_lflag |= ECHO;
    tcsetattr(ptm, TCSANOW, &tios);

    const char* py_path = env->GetStringUTFChars(pythonPath, NULL);
    const char* py_home = env->GetStringUTFChars(pythonHome, NULL);
    const char* lib_path = env->GetStringUTFChars(nativeLibDir, NULL);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("Fork failed: %s", strerror(errno));
        env->ReleaseStringUTFChars(pythonPath, py_path);
        env->ReleaseStringUTFChars(pythonHome, py_home);
        env->ReleaseStringUTFChars(nativeLibDir, lib_path);
        close(ptm);
        return -1;
    }
    
    if (pid == 0) {
        // Child Process
        close(ptm);
        int pts = open(pts_name, O_RDWR);
        if (pts < 0) _exit(1);
        
        dup2(pts, STDIN_FILENO);
        dup2(pts, STDOUT_FILENO);
        dup2(pts, STDERR_FILENO);
        
        for (int fd = 3; fd < 1024; fd++) {
            if (fd != pts) close(fd);
        }
        
        setsid();
        ioctl(pts, TIOCSCTTY, 0);
        
        setenv("PYTHONHOME", py_home, 1);
        setenv("LD_LIBRARY_PATH", lib_path, 1);

        char python_path_env[4096];
        // The standard library is now at $PYTHONHOME/lib/python3.14/
        snprintf(python_path_env, sizeof(python_path_env),
                 "%s/lib/python3.14:%s/lib/python3.14/lib-dynload:%s/lib/python3.14/site-packages",
                 py_home, py_home, py_home);
        setenv("PYTHONPATH", python_path_env, 1);
        setenv("TERM", "xterm-256color", 1);

        LOGI("Child Process: Executing %s", py_path);
        LOGI("PYTHONHOME=%s", py_home);
        LOGI("PYTHONPATH=%s", python_path_env);
        LOGI("LD_LIBRARY_PATH=%s", lib_path);

        const char* python_args[] = { py_path, "-i", NULL };
        execv(py_path, (char* const*)python_args);
        LOGE("execv failed: %s", strerror(errno));
        _exit(127);
    }
    
    env->ReleaseStringUTFChars(pythonPath, py_path);
    env->ReleaseStringUTFChars(pythonHome, py_home);
    env->ReleaseStringUTFChars(nativeLibDir, lib_path);

    g_pty_master_fd = ptm;
    g_python_pid = pid;

    jclass clazz = env->GetObjectClass(thiz);
    jfieldID fid = env->GetFieldID(clazz, "g_python_pid", "I");
    if (fid != NULL) {
        env->SetIntField(thiz, fid, (jint)pid);
    }

    LOGI("Python REPL started, PID: %d, PTY fd: %d", pid, ptm);
    return ptm;
}

JNIEXPORT jint JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_writeToRepl(
    JNIEnv* env, jobject thiz, jint fd, jbyteArray data) {
    if (fd < 0) return -1;
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, NULL);
    ssize_t written = write(fd, bytes, len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return (jint)written;
}

JNIEXPORT jbyteArray JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_readFromRepl(
    JNIEnv* env, jobject thiz, jint fd, jint maxBytes) {
    if (fd < 0 || maxBytes <= 0) return NULL;
    char* buffer = (char*)malloc(maxBytes);
    if (!buffer) return NULL;
    
    fd_set readfds;
    FD_ZERO(&readfds);
    FD_SET(fd, &readfds);
    struct timeval timeout = {0, 100000};
    
    if (select(fd + 1, &readfds, NULL, NULL, &timeout) <= 0) {
        free(buffer);
        return NULL;
    }
    
    ssize_t bytesRead = read(fd, buffer, maxBytes);
    if (bytesRead <= 0) {
        free(buffer);
        return NULL;
    }
    
    jbyteArray result = env->NewByteArray(bytesRead);
    env->SetByteArrayRegion(result, 0, bytesRead, (jbyte*)buffer);
    free(buffer);
    return result;
}

JNIEXPORT void JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_resizePty(
    JNIEnv* env, jobject thiz, jint fd, jint rows, jint cols) {
    if (fd < 0) return;
    struct winsize ws = { (unsigned short)rows, (unsigned short)cols, 0, 0 };
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jboolean JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_isReplRunning(
    JNIEnv* env, jobject thiz) {
    if (g_python_pid <= 0) return JNI_FALSE;
    int status;
    pid_t result = waitpid(g_python_pid, &status, WNOHANG);
    if (result == 0) return JNI_TRUE;
    g_python_pid = -1;
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_terminateRepl(
    JNIEnv* env, jobject thiz, jint fd) {
    if (fd >= 0) {
        char eof_char = 4;
        write(fd, &eof_char, 1);
        close(fd);
    }
    if (g_python_pid > 0) {
        kill(g_python_pid, SIGTERM);
        int status;
        waitpid(g_python_pid, &status, 0);
        g_python_pid = -1;
    }
}

} // extern "C"
