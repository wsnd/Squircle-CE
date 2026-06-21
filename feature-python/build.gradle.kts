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

import com.android.build.api.variant.*

plugins {
    id("com.blacksquircle.feature")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

// ---------------------------------------------------------------------------
// Python cross-compilation paths
// ---------------------------------------------------------------------------

// Path to the CPython submodule root
val PYTHON_DIR = file("../cpython")

// Cross-build output directory (where android.py build puts its results)
val PYTHON_CROSS_DIR = file(
    System.getenv("CROSS_BUILD_DIR") ?: "$PYTHON_DIR/cross-build"
)

// Mapping from GNU host triplet to Android ABI
val TRIPLET_TO_ABI = mapOf(
    "aarch64-linux-android" to "arm64-v8a",
    "x86_64-linux-android" to "x86_64",
)

// Discover available cross-build prefixes
val pythonPrefixes = TRIPLET_TO_ABI.mapNotNull { (triplet, abi) ->
    val prefix = file("$PYTHON_CROSS_DIR/$triplet/prefix")
    if (prefix.exists()) triplet to (abi to prefix) else null
}.toMap()

// Detect Python version from the first prefix.
// Returns null when cross-build output is unavailable, allowing the build
// to skip auto-generation gracefully instead of crashing at configuration time.
val pythonVersion: String? by lazy {
    val firstEntry = pythonPrefixes.values.firstOrNull() ?: return@lazy null
    val prefix = firstEntry.second
    val libDir = file("$prefix/lib")
    val filenames = libDir.list() ?: run {
        logger.warn("Cannot list Python lib directory: $libDir")
        return@lazy null
    }
    for (filename in filenames) {
        """python(\d+\.\d+[a-z]*)""".toRegex().matchEntire(filename)?.let {
            return@lazy it.groupValues[1]
        }
    }
    logger.warn("Failed to find Python version in $libDir")
    null
}

val pyPlusVer = pythonVersion?.let { "python$it" }

// ---------------------------------------------------------------------------
// Android configuration
// ---------------------------------------------------------------------------

android {
    namespace = "com.blacksquircle.ui.feature.python"

    defaultConfig {
        // Configure native build
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
                // Pass Python cross-build paths (uses {{triplet}} placeholder resolved by AGP)
                arguments += "-DPYTHON_PREFIX_DIR=$PYTHON_CROSS_DIR/{{triplet}}/prefix"
                // Pass project root for robust fallback path resolution in CMake
                arguments += "-DPYTHON_PROJECT_DIR=$PYTHON_DIR"
                if (pythonVersion != null) {
                    arguments += "-DPYTHON_VERSION=$pythonVersion"
                }
            }
        }
        
        // Specify which ABIs to build
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        ignoreAssetsPattern = ""
    }

    // Configure NDK and CMake for Python integration
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    ndkVersion = "27.0.12077973"  // Use project's NDK version
}

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------

dependencies {

    implementation(project(":core-common"))
    implementation(project(":core-navigation:api"))
    implementation(project(":core-ui"))

    implementation(project(":feature-editor:api"))
    implementation(project(":feature-explorer:api"))

    // Sora Editor with Python support
    implementation(libs.sora.editor)
    implementation(libs.sora.textmate)
    
    // Note: Python execution uses system Python via ProcessBuilder
    // For better performance, consider integrating Chaquopy separately
    
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.google.dagger)
    ksp(libs.google.dagger.compiler)

    testImplementation(project(":core-test"))
    androidTestImplementation(libs.test.junit.ext)
    androidTestImplementation(libs.test.runner)
}

// ---------------------------------------------------------------------------
// Auto-generate jniLibs and assets from cross-build output
// (follows the pattern used by CPython's Android/testbed)
// ---------------------------------------------------------------------------

if (pythonPrefixes.isNotEmpty() && pythonVersion != null) {
    val version = pythonVersion!! // already guarded above
    val pyVer = "python$version"
    androidComponents.onVariants { variant ->
        val jniLibsSources = variant.sources.jniLibs
            ?: run {
                logger.warn("variant.sources.jniLibs is null for ${variant.name}, skipping jniLibs generation")
                return@onVariants
            }
        val assetsSources = variant.sources.assets
            ?: run {
                logger.warn("variant.sources.assets is null for ${variant.name}, skipping assets generation")
                return@onVariants
            }

        // --- jniLibs: copy libpython*.so and dependent .so files ---
        generateTask(variant, jniLibsSources) {
            for ((_, pair) in pythonPrefixes) {
                val (abi, prefix) = pair
                into(abi) {
                    from("$prefix/lib")
                    include("libpython*.*.so")
                    include("libpython*.so")
                    include("libcrypto*.so")
                    include("libssl*.so")
                    include("libsqlite*.so")
                    include("lib*_python.so")
                }
                // Copy the Python executable (bin/pythonX.Y) and rename to libpython_exe.so
                // This allows Android to extract it as a "shared library" into the native lib dir,
                // where it can be executed as a subprocess.
                into(abi) {
                    from("$prefix/bin") {
                        include("python$version")
                        rename { "libpython_exe.so" }
                    }
                }
            }
        }

        // --- assets: copy Python stdlib, headers, and lib-dynload ---
        generateTask(variant, assetsSources) {
            into(pyVer) {
                // Python standard library (.py files)
                into("lib/$pyVer") {
                    // CPython source Lib/ takes priority (for debugging)
                    if (file("$PYTHON_DIR/Lib").exists()) {
                        from("$PYTHON_DIR/Lib")
                    }
                    for ((_, pair) in pythonPrefixes) {
                        val (_, prefix) = pair
                        from("$prefix/lib/$pyVer")
                    }
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    exclude("**/__pycache__")
                }

                // C headers (needed for JNI compilation)
                into("include/$pyVer") {
                    for ((_, pair) in pythonPrefixes) {
                        val (_, prefix) = pair
                        from("$prefix/include/$pyVer")
                    }
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                }
            }
        }
    }
}

// Helper: create a Sync task registered as a generated source directory.
fun generateTask(
    variant: ComponentIdentity, directories: SourceDirectories,
    configure: Sync.() -> Unit
) {
    val taskName = "generate" +
        listOf(variant.name, "Python", directories.name)
            .map { it.replaceFirstChar(Char::uppercase) }
            .joinToString("")

    val task = project.tasks.register(taskName, GeneratePythonTask::class.java) {
        outputDir.set(
            project.layout.buildDirectory.dir("generated/python/${directories.name}/${variant.name}")
        )
        into(outputDir)
        configure()
    }

    directories.addGeneratedSourceDirectory(task) { it.outputDir }
}

// addGeneratedSourceDirectory requires the task to have a DirectoryProperty.
abstract class GeneratePythonTask : Sync() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}
