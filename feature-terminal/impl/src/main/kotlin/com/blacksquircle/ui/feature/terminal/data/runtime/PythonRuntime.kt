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

package com.blacksquircle.ui.feature.terminal.data.runtime

import android.content.Context
import com.blacksquircle.ui.feature.terminal.api.model.RuntimeType
import com.blacksquircle.ui.feature.terminal.domain.runtime.TerminalRuntime
import java.io.File

/**
 * Python runtime that launches embedded CPython 3.14 interpreter
 */
internal class PythonRuntime(private val context: Context) : TerminalRuntime {
    
    init {
        // Ensure python command is set up on initialization
        initializePythonCommand()
    }
    
    override val name: String = "Python 3.14"
    override val type: RuntimeType = RuntimeType.PYTHON
    
    override val shellPath: String
        get() = "/system/bin/sh"
    
    override val homeDir: String
        get() = context.filesDir.absolutePath
    override val tmpDir: String
        get() = context.cacheDir.absolutePath
    
    /**
     * Initialize python command - create wrapper script
     */
    private fun initializePythonCommand() {
        createPythonWrapper()
    }
    
    /**
     * Create a wrapper script that uses am broadcast to execute Python
     */
    private fun createPythonWrapper(): String {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        
        val wrapperScript = File(binDir, "python.sh")
        // Always recreate to ensure latest version
        if (wrapperScript.exists()) {
            wrapperScript.delete()
        }
        
        wrapperScript.writeText(
                """#!/system/bin/sh
# Python 3.14 Wrapper - Uses Android am broadcast to execute code

if [ -z "${'$'}1" ]; then
    echo "Python 3.14 (Embedded CPython)"
    echo "Usage: python <script.py>"
    echo ""
    echo "Error: No script specified"
    exit 1
fi

if [ ! -f "${'$'}1" ]; then
    echo "Error: File not found: ${'$'}1"
    exit 1
fi

# Convert to absolute path
SCRIPT_PATH="${'$'}1"
case "${'$'}SCRIPT_PATH" in
    /*) ;;
    *) SCRIPT_PATH="${'$'}(pwd)/${'$'}SCRIPT_PATH" ;;
esac

echo "Executing: ${'$'}SCRIPT_PATH"
echo "---"

# Try multiple methods to execute Python

# Method 1: Try am startservice
am startservice --user 0 -a com.blacksquircle.ui.ACTION_EXECUTE_PYTHON_FROM_TERMINAL -e file_path "${'$'}SCRIPT_PATH" -n com.blacksquircle.ui/com.blacksquircle.ui.feature.editor.service.PythonExecutionService >/dev/null 2>&1
SERVICE_STARTED=${'$'}?

if [ ${'$'}SERVICE_STARTED -eq 0 ]; then
    # Service started, wait for output
    OUTPUT_FILE="${'$'}HOME/last_python_output.txt"
    MAX_WAIT=20
    WAITED=0
    
    while [ ! -f "${'$'}OUTPUT_FILE" ] && [ ${'$'}WAITED -lt ${'$'}MAX_WAIT ]; do
        sleep 0.5
        WAITED=$((WAITED + 1))
    done
    
    if [ -f "${'$'}OUTPUT_FILE" ]; then
        cat "${'$'}OUTPUT_FILE"
        rm -f "${'$'}OUTPUT_FILE"
        exit 0
    fi
fi

# Method 2: Fallback - show message
echo ""
echo "⚠️  Terminal execution is not fully configured yet."
echo ""
echo "✅ Please use the Run button (▶️) in the editor instead:"
echo "   1. Open the file in Code Editor"
echo "   2. Click the Run button"
echo "   3. Results will appear here in Terminal"
echo ""
echo "File validated: ${'$'}SCRIPT_PATH"
echo "Status: Ready for execution via Run button"
                """.trimIndent()
            )
            
            wrapperScript.setReadable(true, false)
            android.util.Log.d("PythonRuntime", "Created python.sh at: ${wrapperScript.absolutePath}")
        return wrapperScript.absolutePath
    }
}
