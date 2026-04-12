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

package com.blacksquircle.ui.feature.python

import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * Native Python REPL using PTY (Pseudo Terminal)
 * Provides full interactive support like Termux
 */
class PythonReplNative {
    
    companion object {
        private const val TAG = "PythonReplNative"
        
        init {
            try {
                System.loadLibrary("python_repl_native")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }
    
    private var ptyFd: Int = -1
    private var isRunning = false
    @Suppress("PropertyName") // Accessed from JNI
    private var g_python_pid: Int = -1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Start Python REPL with PTY
     */
    fun startRepl(
        pythonPath: String,
        nativeLibraryDir: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit = {}
    ): Boolean {
        if (isRunning) {
            Log.w(TAG, "REPL already running")
            return false
        }
        
        Log.d(TAG, "Starting Python REPL with PTY: $pythonPath")
        
        // Find Python executable
        val pythonExe = findPythonExecutable(pythonPath, nativeLibraryDir)
        if (pythonExe == null) {
            val error = "Python executable not found in: $pythonPath"
            Log.e(TAG, error)
            onError(error)
            return false
        }
        
        // Create PTY and spawn Python
        ptyFd = createPythonRepl(
            pythonPath = pythonExe.absolutePath,
            pythonHome = pythonPath,
            nativeLibDir = nativeLibraryDir
        )
        
        if (ptyFd < 0) {
            val error = "Failed to create Python REPL (PTY creation failed)"
            Log.e(TAG, error)
            onError(error)
            return false
        }
        
        isRunning = true
        
        // Start reading output in background
        scope.launch {
            try {
                readLoop(onOutput, onError)
            } catch (e: Exception) {
                Log.e(TAG, "Read loop crashed: ${e.message}", e)
                onError("REPL read error: ${e.message}")
                isRunning = false
            }
        }
        
        // Monitor process health
        scope.launch {
            monitorProcessHealth(onError)
        }
        
        Log.i(TAG, "Python REPL started successfully, PTY fd: $ptyFd, PID: $g_python_pid")
        return true
    }
    
    /**
     * Write input to Python REPL
     */
    fun writeInput(input: String): Boolean {
        if (!isRunning || ptyFd < 0) {
            Log.w(TAG, "REPL not running")
            return false
        }
        
        val bytes = input.toByteArray(Charsets.UTF_8)
        val written = writeToRepl(ptyFd, bytes)
        
        return written > 0
    }
    
    /**
     * Resize PTY window
     */
    fun resizeWindow(rows: Int, cols: Int) {
        if (ptyFd >= 0) {
            resizePty(ptyFd, rows, cols)
        }
    }
    
    /**
     * Stop Python REPL
     */
    fun stopRepl() {
        if (!isRunning) return
        Log.d(TAG, "Stopping Python REPL")
        isRunning = false
        terminateRepl(ptyFd)
        ptyFd = -1
        scope.cancel()
    }
    
    /**
     * Check if REPL is still running
     */
    fun isReplActive(): Boolean {
        if (!isRunning) return false
        return isReplRunning()
    }
    
    /**
     * Find Python executable path
     */
    private fun findPythonExecutable(basePath: String, nativeLibDir: String): File? {
        // SELinux on Android 10+ restricts execution to the app's native library directory.
        // We package the python binary as "libpython_exe.so" to bypass this.
        val candidates = mutableListOf<File>()
        
        // 1. Primary candidate: The shared library workaround for SELinux
        candidates.add(File(nativeLibDir, "libpython_exe.so"))
        
        // 2. Secondary candidates: Extracted binaries (may fail on Android 10+)
        candidates.add(File(basePath, "bin/python3"))
        candidates.add(File(basePath, "python3"))
        
        // 3. Fallback: check nested path if basePath is just the root
        val versionedPath = File(basePath, "python3.14")
        candidates.add(File(versionedPath, "bin/python3"))
        candidates.add(File(versionedPath, "python3"))
        
        for (candidate in candidates) {
            if (candidate.exists()) {
                Log.d(TAG, "Checking Python candidate: ${candidate.absolutePath}")
                if (candidate.canExecute()) {
                    Log.i(TAG, "✓ Found executable Python: ${candidate.absolutePath}")
                    return candidate
                } else {
                    Log.w(TAG, "Found but not executable: ${candidate.absolutePath}. Attempting to set permissions...")
                    candidate.setExecutable(true)
                    if (candidate.canExecute()) {
                        Log.i(TAG, "✓ Successfully set execution permission for: ${candidate.absolutePath}")
                        return candidate
                    }
                }
            }
        }
        
        // Log all checked paths to help debugging
        Log.e(TAG, "No Python executable found. Checked paths:")
        candidates.forEach { Log.e(TAG, "  - ${it.absolutePath} (exists=${it.exists()})") }

        return null
    }
    
    private suspend fun readLoop(onOutput: (String) -> Unit, onError: (String) -> Unit) {
        val buffer = ByteArray(4096)
        while (isRunning && ptyFd >= 0) {
            try {
                val data = readFromRepl(ptyFd, buffer.size)
                if (data != null && data.isNotEmpty()) {
                    onOutput(String(data, Charsets.UTF_8))
                } else {
                    delay(50)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read error: ${e.message}")
                delay(100)
            }
        }
    }
    
    private suspend fun monitorProcessHealth(onError: (String) -> Unit) {
        while (isRunning) {
            delay(1000)
            if (!isReplRunning()) {
                Log.w(TAG, "Python process terminated unexpectedly")
                onError("Python process exited unexpectedly")
                isRunning = false
                break
            }
        }
    }
    
    private external fun createPythonRepl(pythonPath: String, pythonHome: String, nativeLibDir: String): Int
    private external fun writeToRepl(fd: Int, data: ByteArray): Int
    private external fun readFromRepl(fd: Int, maxBytes: Int): ByteArray?
    private external fun resizePty(fd: Int, rows: Int, cols: Int)
    private external fun isReplRunning(): Boolean
    private external fun terminateRepl(fd: Int)
}
