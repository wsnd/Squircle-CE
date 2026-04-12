/*
 * Copyright Squircle CE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

#define LOG_TAG "PythonReplJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global state
static int g_pty_master_fd = -1;
static pid_t g_python_pid = -1;

extern "C" {

/**
 * Create PTY and spawn Python interpreter
 * Returns PTY master file descriptor
 */
JNIEXPORT jint JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_createPythonRepl(
    JNIEnv* env, jobject thiz, jstring pythonPath) {
    
    LOGI("Creating Python REPL with PTY");
    
    // Open PTY master
    int ptm = open("/dev/ptmx", O_RDWR | O_NOCTTY);
    if (ptm < 0) {
        LOGE("Failed to open /dev/ptmx: %s", strerror(errno));
        return -1;
    }
    
    // Grant access and unlock PTY slave
    if (grantpt(ptm) != 0) {
        LOGE("grantpt failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }
    
    if (unlockpt(ptm) != 0) {
        LOGE("unlockpt failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }
    
    // Get PTY slave name
    char pts_name[128];
    if (ptsname_r(ptm, pts_name, sizeof(pts_name)) != 0) {
        LOGE("ptsname_r failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }
    
    LOGI("PTY master fd: %d, slave: %s", ptm, pts_name);
    
    // Configure terminal settings
    struct termios tios;
    tcgetattr(ptm, &tios);
    
    // Enable UTF-8 mode
    tios.c_iflag |= IUTF8;
    
    // Disable flow control (prevent Ctrl+S from locking)
    tios.c_iflag &= ~(IXON | IXOFF);
    
    // Set raw mode for proper interactive behavior
    cfmakeraw(&tios);
    
    // Echo input characters
    tios.c_lflag |= ECHO;
    
    tcsetattr(ptm, TCSANOW, &tios);
    
    // Set initial window size
    struct winsize ws;
    ws.ws_row = 24;
    ws.ws_col = 80;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(ptm, TIOCSWINSZ, &ws);
    
    // Fork process
    pid_t pid = fork();
    
    if (pid < 0) {
        LOGE("Fork failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }
    
    if (pid == 0) {
        // Child process - become session leader
        close(ptm);
        
        // Open PTY slave
        int pts = open(pts_name, O_RDWR);
        if (pts < 0) {
            LOGE("Failed to open PTY slave: %s", strerror(errno));
            _exit(1);
        }
        
        // Redirect stdin/stdout/stderr to PTY slave
        dup2(pts, STDIN_FILENO);
        dup2(pts, STDOUT_FILENO);
        dup2(pts, STDERR_FILENO);
        
        // Close all other file descriptors
        for (int fd = 3; fd < 1024; fd++) {
            close(fd);
        }
        
        // Create new session
        setsid();
        
        // Make PTY slave the controlling terminal
        ioctl(pts, TIOCSCTTY, 0);
        
        // Get Python path from argument
        const char* py_path = env->GetStringUTFChars(pythonPath, NULL);
        
        // Write debug info to stderr before redirecting
        FILE* debug_log = fopen("/data/local/tmp/python_repl_debug.log", "a");
        if (debug_log) {
            fprintf(debug_log, "=== Python REPL Debug ===\n");
            fprintf(debug_log, "Python path: %s\n", py_path);
            fflush(debug_log);
        }
        
        // Calculate lib directory (python3.14/bin/python3 -> python3.14/lib)
        char lib_dir[512];
        strncpy(lib_dir, py_path, sizeof(lib_dir) - 1);
        lib_dir[sizeof(lib_dir) - 1] = '\0';
        
        // Find last '/' and replace 'bin' with 'lib'
        char* last_slash = strrchr(lib_dir, '/');
        if (last_slash) {
            strcpy(last_slash + 1, "lib");
        }
        
        if (debug_log) {
            fprintf(debug_log, "Library directory: %s\n", lib_dir);
            fflush(debug_log);
        }
        
        // Set LD_LIBRARY_PATH
        char ld_library_path[1024];
        const char* existing_ld = getenv("LD_LIBRARY_PATH");
        if (existing_ld && strlen(existing_ld) > 0) {
            snprintf(ld_library_path, sizeof(ld_library_path), "%s:%s", lib_dir, existing_ld);
        } else {
            strncpy(ld_library_path, lib_dir, sizeof(ld_library_path) - 1);
            ld_library_path[sizeof(ld_library_path) - 1] = '\0';
        }
        
        setenv("LD_LIBRARY_PATH", ld_library_path, 1);
        
        if (debug_log) {
            fprintf(debug_log, "LD_LIBRARY_PATH: %s\n", ld_library_path);
            fflush(debug_log);
        }
        
        // Set PYTHONHOME
        char python_home[512];
        strncpy(python_home, py_path, sizeof(python_home) - 1);
        python_home[sizeof(python_home) - 1] = '\0';
        
        // Remove /bin/python3 to get base dir
        char* bin_pos = strstr(python_home, "/bin/python3");
        if (bin_pos) {
            *bin_pos = '\0';
        }
        
        setenv("PYTHONHOME", python_home, 1);
        
        if (debug_log) {
            fprintf(debug_log, "PYTHONHOME: %s\n", python_home);
            fprintf(debug_log, "Executing: %s -i\n", py_path);
            fclose(debug_log);
        }
        
        // Execute Python in interactive mode
        const char* python_args[] = {
            py_path,
            "-i",  // Interactive mode
            NULL
        };
        
        execvp(python_args[0], (char* const*)python_args);
        
        // If exec fails
        LOGE("execvp failed: %s (errno: %d)", strerror(errno), errno);
        _exit(127);
    }
    
    // Parent process
    g_pty_master_fd = ptm;
    g_python_pid = pid;
    
    LOGI("Python REPL started, PID: %d, PTY fd: %d", pid, ptm);
    
    return ptm;
}

/**
 * Write data to Python REPL (user input)
 */
JNIEXPORT jint JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_writeToRepl(
    JNIEnv* env, jobject thiz, jint fd, jbyteArray data) {
    
    if (fd < 0) {
        LOGE("Invalid PTY fd");
        return -1;
    }
    
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, NULL);
    
    ssize_t written = write(fd, bytes, len);
    
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    
    if (written < 0) {
        LOGE("Write failed: %s", strerror(errno));
        return -1;
    }
    
    return (jint)written;
}

/**
 * Read output from Python REPL
 */
JNIEXPORT jbyteArray JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_readFromRepl(
    JNIEnv* env, jobject thiz, jint fd, jint maxBytes) {
    
    if (fd < 0 || maxBytes <= 0) {
        return NULL;
    }
    
    // Allocate buffer
    char* buffer = (char*)malloc(maxBytes);
    if (!buffer) {
        LOGE("Failed to allocate read buffer");
        return NULL;
    }
    
    // Try to read with timeout using select
    fd_set readfds;
    FD_ZERO(&readfds);
    FD_SET(fd, &readfds);
    
    struct timeval timeout;
    timeout.tv_sec = 0;
    timeout.tv_usec = 100000; // 100ms timeout
    
    int ret = select(fd + 1, &readfds, NULL, NULL, &timeout);
    
    if (ret <= 0) {
        // No data available or error
        free(buffer);
        return NULL;
    }
    
    // Read data
    ssize_t bytesRead = read(fd, buffer, maxBytes);
    
    if (bytesRead <= 0) {
        free(buffer);
        return NULL;
    }
    
    // Convert to Java byte array
    jbyteArray result = env->NewByteArray(bytesRead);
    env->SetByteArrayRegion(result, 0, bytesRead, (jbyte*)buffer);
    
    free(buffer);
    
    return result;
}

/**
 * Resize PTY window
 */
JNIEXPORT void JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_resizePty(
    JNIEnv* env, jobject thiz, jint fd, jint rows, jint cols) {
    
    if (fd < 0) return;
    
    struct winsize ws;
    ws.ws_row = rows;
    ws.ws_col = cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    
    if (ioctl(fd, TIOCSWINSZ, &ws) < 0) {
        LOGE("Failed to resize PTY: %s", strerror(errno));
    }
}

/**
 * Check if Python process is still running
 */
JNIEXPORT jboolean JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_isReplRunning(
    JNIEnv* env, jobject thiz) {
    
    if (g_python_pid <= 0) {
        return JNI_FALSE;
    }
    
    // Check if process exists
    int status;
    pid_t result = waitpid(g_python_pid, &status, WNOHANG);
    
    if (result == 0) {
        // Process still running
        return JNI_TRUE;
    }
    
    // Process exited
    g_python_pid = -1;
    return JNI_FALSE;
}

/**
 * Terminate Python REPL
 */
JNIEXPORT void JNICALL
Java_com_blacksquircle_ui_feature_python_PythonReplNative_terminateRepl(
    JNIEnv* env, jobject thiz, jint fd) {
    
    LOGI("Terminating Python REPL");
    
    // Send EOF (Ctrl+D) to Python
    char eof_char = 4; // Ctrl+D
    if (fd >= 0) {
        write(fd, &eof_char, 1);
    }
    
    // Wait for process to exit
    if (g_python_pid > 0) {
        int status;
        waitpid(g_python_pid, &status, 0);
        LOGI("Python process exited with status: %d", WEXITSTATUS(status));
        g_python_pid = -1;
    }
    
    // Close PTY master
    if (fd >= 0) {
        close(fd);
        g_pty_master_fd = -1;
    }
}

} // extern "C"
