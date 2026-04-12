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

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Python Standard Library Extractor
 * 
 * Copies Python standard library from assets to app's data directory
 * so that the embedded CPython interpreter can find and use it.
 */
object PythonStdlibExtractor {
    
    private const val TAG = "PythonStdlibExtractor"
    private const val ASSET_PATH = "python3.14"
    private const val PYTHON_DIR_NAME = "python"
    
    /**
     * Extract Python standard library if not already extracted
     * 
     * @param context Application context
     * @return Path to extracted Python directory, or null if extraction failed
     */
    fun extractIfNeeded(context: Context): String? {
        val pythonDir = File(context.filesDir, PYTHON_DIR_NAME) // This will be PYTHONHOME
        val stdlibDir = File(pythonDir, "lib/python3.14")
        
        // Check if correct architecture is already extracted
        val abi = android.os.Build.SUPPORTED_ABIS[0]
        val markerFile = File(pythonDir, "extracted_$abi")
        
        if (markerFile.exists()) {
            Log.i(TAG, "Python stdlib for $abi already extracted at: ${pythonDir.absolutePath}")
            return pythonDir.absolutePath
        }

        try {
            if (pythonDir.exists()) {
                pythonDir.deleteRecursively()
            }
            stdlibDir.mkdirs()
            
            Log.i(TAG, "Extracting Python stdlib for $abi...")
            
            // LOG ALL ASSETS IN THE ROOT
            val allRootAssets = context.assets.list(ASSET_PATH)
            Log.e(TAG, "ALL ASSETS IN $ASSET_PATH: ${allRootAssets?.joinToString(", ")}")

            // Recursively copy from assets/python3.14 to files/python/lib/python3.14
            copyAssets(context, ASSET_PATH, stdlibDir)
            
            // Create site-packages directory if it doesn't exist
            val sitePackages = File(stdlibDir, "site-packages")
            if (!sitePackages.exists()) {
                sitePackages.mkdirs()
            }
            
            // Log directory structure of stdlibDir for debugging
            Log.i(TAG, "Checking extracted stdlibDir: ${stdlibDir.absolutePath}")
            val stdlibFiles = stdlibDir.list()
            Log.i(TAG, "stdlibDir contains: ${stdlibFiles?.joinToString(", ")}")

            val pyReplDir = File(stdlibDir, "_pyrepl")
            if (pyReplDir.exists()) {
                Log.i(TAG, "_pyrepl directory exists in extracted path")
                val files = pyReplDir.list()
                Log.i(TAG, "_pyrepl contains: ${files?.joinToString(", ")}")
            } else {
                Log.e(TAG, "_pyrepl directory MISSING in extracted path!")
                val uPyReplDir = File(stdlibDir, "u_pyrepl")
                if (uPyReplDir.exists()) {
                    Log.e(TAG, "FOUND u_pyrepl INSTEAD OF _pyrepl!")
                }
            }

            // Marker for successful extraction
            markerFile.createNewFile()

            val resultPath = pythonDir.absolutePath
            Log.i(TAG, "Python stdlib copied successfully to: $resultPath")
            return resultPath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy Python stdlib: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Clean up extracted Python directory
     */
    fun cleanup(context: Context) {
        val pythonDir = File(context.filesDir, PYTHON_DIR_NAME)
        if (pythonDir.exists()) {
            pythonDir.deleteRecursively()
            Log.i(TAG, "Cleaned up Python stdlib directory")
        }
    }
    
    /**
     * Robust recursive asset copy
     */
    private fun copyAssets(context: Context, assetPath: String, destFile: File) {
        val assets = try {
            context.assets.list(assetPath)
        } catch (e: Exception) {
            null
        }
        
        // If list() returns entries, it's a directory
        if (!assets.isNullOrEmpty()) {
            if (!destFile.exists()) {
                destFile.mkdirs()
            }
            
            val abi = android.os.Build.SUPPORTED_ABIS[0]
            val currentArch = getArch(abi)
            
            for (asset in assets) {
                // Filter architecture-specific files
                if (isWrongArchitecture(asset, currentArch)) {
                    continue
                }
                
                // Skip __pycache__
                if (asset == "__pycache__") continue
                
                // Map the destination name
                val mappedName = mapAssetName(asset)
                
                // Recurse
                copyAssets(context, "$assetPath/$asset", File(destFile, mappedName))
            }
        } else {
            // It might be a file or an empty directory
            try {
                // Try to open as a file
                context.assets.open(assetPath).use { input ->
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    Log.v(TAG, "Copied file: $assetPath to $destFile")
                }
            } catch (e: Exception) {
                // If it's a directory that list() returned empty for (filtered or empty)
                if (assets != null && !destFile.exists()) {
                    destFile.mkdirs()
                }
            }
        }
    }

    private fun isWrongArchitecture(asset: String, currentArch: String): Boolean {
        // Only filter if it looks like an architecture-specific file/directory
        if (asset.contains("aarch64") || asset.contains("x86_64") || 
            asset.contains("arm64") || asset.contains("x86")) {
             return !asset.contains(currentArch)
        }
        return false
    }

    private fun mapAssetName(asset: String): String {
        return when {
            asset == "u_phello" -> "__phello__"
            asset == "u_pyrepl" -> "_pyrepl"
            asset.startsWith("u_") -> asset.replaceFirst("u_", "_")
            asset.endsWith("_dir") -> "_" + asset.removeSuffix("_dir")
            else -> asset
        }
    }

    private fun getArch(abi: String): String {
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("x86_64") -> "x86_64"
            else -> abi
        }
    }

    private fun copyFileOrEmptyDir(context: Context, assetPath: String, destPath: File) {
        try {
            context.assets.open(assetPath).use { input ->
                destPath.parentFile?.mkdirs()
                destPath.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            // If open fails, it's likely a directory (or a missing file, which shouldn't happen here)
            if (!destPath.exists()) {
                destPath.mkdirs()
            }
        }
    }
}
