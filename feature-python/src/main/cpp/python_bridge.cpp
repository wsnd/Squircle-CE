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
#include <string>
#include <android/log.h>
#include <sys/stat.h>

// Include Python.h - let it use default configuration
#include <Python.h>

#define LOG_TAG "PythonBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Global Python interpreter state
static PyThreadState* g_python_thread_state = nullptr;
static JavaVM* g_java_vm = nullptr;

extern "C" {

/**
 * Called when the native library is loaded
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_java_vm = vm;
    
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    LOGI("Python bridge library loaded");
    LOGI("Python will be initialized when first needed");
    
    return JNI_VERSION_1_6;
}

/**
 * Called when the native library is unloaded
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("Finalizing Python interpreter...");
    
    if (Py_IsInitialized()) {
        if (g_python_thread_state) {
            PyEval_RestoreThread(g_python_thread_state);
        }
        Py_Finalize();
    }
    
    LOGI("Python interpreter finalized");
}

/**
 * Initialize Python interpreter (called from Kotlin)
 */
JNIEXPORT void JNICALL
Java_com_blacksquircle_ui_feature_editor_data_interactor_PythonExecutor_initializePython(
    JNIEnv* env, jobject thiz, jstring pythonPath) {
    
    LOGI("Initializing Python 3.14 interpreter...");
    
    // Set Python path if provided using PyConfig (Python 3.8+)
    if (pythonPath) {
        const char* path_str = env->GetStringUTFChars(pythonPath, nullptr);
        if (path_str) {
            LOGI("Setting Python path to: %s", path_str);
            
            // Check if path exists
            struct stat st;
            if (stat(path_str, &st) != 0) {
                LOGE("Python path does not exist: %s", path_str);
                env->ReleaseStringUTFChars(pythonPath, path_str);
                // Fallback to no-path initialization
                goto init_without_path;
            }
            
            // Use PyConfig to set paths (compatible with Python 3.8+)
            PyConfig config;
            PyConfig_InitPythonConfig(&config);

            // On Android, we must set these for the interpreter to find its files
            config.site_import = 1;

            // Convert path to wchar_t
            size_t len = strlen(path_str) + 1;
            wchar_t* wide_path = new wchar_t[len];
            mbstowcs(wide_path, path_str, len);

            // Set home and prefixes so Python knows where to look for 'lib/python3.14'
            PyConfig_SetString(&config, &config.home, wide_path);
            PyConfig_SetString(&config, &config.prefix, wide_path);
            PyConfig_SetString(&config, &config.base_prefix, wide_path);
            PyConfig_SetString(&config, &config.exec_prefix, wide_path);
            PyConfig_SetString(&config, &config.base_exec_prefix, wide_path);

            // Important: Set standard library paths explicitly as well
            wchar_t path1[4096];
            wchar_t path2[4096];
            swprintf(path1, 4096, L"%ls/lib/python3.14", wide_path);
            swprintf(path2, 4096, L"%ls/lib/python3.14/lib-dynload", wide_path);

            config.module_search_paths_set = 1;
            PyWideStringList_Append(&config.module_search_paths, path1);
            PyWideStringList_Append(&config.module_search_paths, path2);
            
            LOGI("Home set to: %ls", wide_path);
            LOGI("Added search path: %ls", path1);
            LOGI("Added search path: %ls", path2);

            LOGI("Initializing Python with config...");
            
            // Initialize Python with config
            PyStatus status = Py_InitializeFromConfig(&config);
            if (PyStatus_Exception(status)) {
                LOGE("Python initialization failed: %s", status.err_msg ? status.err_msg : "Unknown error");
                PyConfig_Clear(&config);
                delete[] wide_path;
                env->ReleaseStringUTFChars(pythonPath, path_str);
                return;
            }
            
            PyConfig_Clear(&config);
            delete[] wide_path;
            env->ReleaseStringUTFChars(pythonPath, path_str);
            
            LOGI("Python initialized with custom path");
        } else {
            LOGW("Failed to get path string, using default initialization");
            goto init_without_path;
        }
    } else {
        LOGW("No Python path provided, using default initialization");
        goto init_without_path;
    }
    
    goto init_complete;
    
init_without_path:
    LOGW("Initializing Python without custom path");
    
    // Use PyConfig to disable site module (Python 3.8+)
    {
        PyConfig config;
        PyConfig_InitIsolatedConfig(&config);
        config.site_import = 0;  // Disable site module
        
        LOGI("Calling Py_InitializeFromConfig with isolated config...");
        
        PyStatus status = Py_InitializeFromConfig(&config);
        if (PyStatus_Exception(status)) {
            LOGE("Python initialization failed: %s", status.err_msg ? status.err_msg : "Unknown error");
            PyConfig_Clear(&config);
            // Mark as not initialized
            return;
        }
        
        PyConfig_Clear(&config);
    }
    
    if (!Py_IsInitialized()) {
        LOGE("Python initialization FAILED - interpreter not ready!");
        return;
    }
    
    LOGI("Python initialized without custom path");
    
init_complete:
    
    LOGI("Python 3.14 initialized successfully");
    LOGI("Python version: %s", Py_GetVersion());
    
    // Save thread state
    g_python_thread_state = PyEval_SaveThread();
}

/**
 * Execute Python code and return output
 */
JNIEXPORT jstring JNICALL
Java_com_blacksquircle_ui_feature_editor_data_interactor_PythonExecutor_executePythonCode(
    JNIEnv* env, jobject thiz, jstring code) {
    
    if (!code) {
        return env->NewStringUTF("");
    }
    
    // Get current environment
    JNIEnv* current_env = nullptr;
    if (g_java_vm->GetEnv(reinterpret_cast<void**>(&current_env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed to get JNI environment");
        return env->NewStringUTF("Error: Failed to get JNI environment");
    }
    
    // Acquire GIL
    PyGILState_STATE gstate = PyGILState_Ensure();
    
    const char* code_str = env->GetStringUTFChars(code, nullptr);
    if (!code_str) {
        PyGILState_Release(gstate);
        return env->NewStringUTF("Error: Failed to get code string");
    }
    
    LOGI("Executing Python code: %s", code_str);
    
    // Check if Python is initialized
    if (!Py_IsInitialized()) {
        LOGE("Python interpreter is not initialized!");
        env->ReleaseStringUTFChars(code, code_str);
        return env->NewStringUTF("Error: Python interpreter not initialized");
    }
    
    // Redirect stdout to capture output
    PyObject* sys_module = PyImport_ImportModule("sys");
    if (!sys_module) {
        LOGE("Failed to import sys module");
        PyErr_Print();
        PyGILState_Release(gstate);
        env->ReleaseStringUTFChars(code, code_str);
        return env->NewStringUTF("Error: Failed to import sys module");
    }
    
    PyObject* io_module = PyImport_ImportModule("io");
    if (!io_module) {
        LOGE("Failed to import io module");
        Py_DECREF(sys_module);
        PyGILState_Release(gstate);
        env->ReleaseStringUTFChars(code, code_str);
        return env->NewStringUTF("Error: Failed to import io module");
    }
    
    PyObject* string_io_class = PyObject_GetAttrString(io_module, "StringIO");
    if (!string_io_class) {
        LOGE("Failed to get StringIO class");
        Py_DECREF(io_module);
        Py_DECREF(sys_module);
        PyGILState_Release(gstate);
        env->ReleaseStringUTFChars(code, code_str);
        return env->NewStringUTF("Error: Failed to get StringIO class");
    }
    
    PyObject* string_io = PyObject_CallObject(string_io_class, nullptr);
    if (!string_io) {
        LOGE("Failed to create StringIO instance");
        Py_DECREF(string_io_class);
        Py_DECREF(io_module);
        Py_DECREF(sys_module);
        PyGILState_Release(gstate);
        env->ReleaseStringUTFChars(code, code_str);
        return env->NewStringUTF("Error: Failed to create StringIO instance");
    }
    
    // Save original stdout/stderr/stdin
    PyObject* original_stdout = PyObject_GetAttrString(sys_module, "stdout");
    PyObject* original_stderr = PyObject_GetAttrString(sys_module, "stderr");
    PyObject* original_stdin = PyObject_GetAttrString(sys_module, "stdin");
    
    // Redirect stdout and stderr
    PyObject_SetAttrString(sys_module, "stdout", string_io);
    PyObject_SetAttrString(sys_module, "stderr", string_io);
    
    // Execute the code
    int result = PyRun_SimpleString(code_str);
    
    // Check for errors
    if (result != 0) {
        LOGE("Python execution failed with code: %d", result);
        PyErr_Print();
    } else {
        LOGI("Python execution succeeded");
    }
    
    // Get captured output
    PyObject* output = PyObject_CallMethod(string_io, "getvalue", nullptr);
    
    // Restore original stdout/stderr/stdin
    PyObject_SetAttrString(sys_module, "stdout", original_stdout);
    PyObject_SetAttrString(sys_module, "stderr", original_stderr);
    PyObject_SetAttrString(sys_module, "stdin", original_stdin);
    
    // Convert result to jstring
    jstring java_result = nullptr;
    if (output && PyUnicode_Check(output)) {
        const char* output_str = PyUnicode_AsUTF8(output);
        if (output_str) {
            java_result = env->NewStringUTF(output_str);
        } else {
            java_result = env->NewStringUTF("");
        }
    } else {
        if (result == 0) {
            java_result = env->NewStringUTF("Code executed successfully");
        } else {
            java_result = env->NewStringUTF("Execution failed");
        }
    }
    
    // Cleanup
    Py_XDECREF(output);
    Py_XDECREF(string_io);
    Py_XDECREF(string_io_class);
    Py_XDECREF(io_module);
    Py_XDECREF(original_stdout);
    Py_XDECREF(original_stderr);
    Py_XDECREF(original_stdin);
    Py_XDECREF(sys_module);
    
    env->ReleaseStringUTFChars(code, code_str);
    
    // Release GIL
    PyGILState_Release(gstate);
    
    return java_result;
}

} // extern "C"
