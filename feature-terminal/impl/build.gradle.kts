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

import java.io.File
import java.net.URI
import org.gradle.api.GradleException

plugins {
    id("com.blacksquircle.feature")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.blacksquircle.ui.feature.terminal"

    buildFeatures {
        compose = true
    }

    // Package Termux git/scalar/curl binaries along with .so files into
    // jniLibs/<abi>/. Without useLegacyPackaging, AGP filters out anything
    // not matching lib*.so and drops git, git-upload-pack, scalar, curl, etc.
    // app's AndroidManifest sets extractNativeLibs="true", so the packaged
    // directory is extracted at install time to nativeLibraryDir — where
    // SessionManagerImpl.setupGitSymlinks() creates per-name symlinks into
    // filesDir/bin.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// ---------------------------------------------------------------------------
// Termux Git binaries
//
// Pulls git + its dynamic-link dependencies (pcre2, openssl, curl, zlib,
// expat, iconv) from the Termux apt pool and copies them into jniLibs/<abi>/
// so the terminal feature can run `git`, `git-upload-pack`, etc. directly.
// Output is cached in build/termux-git/ for incremental builds.
// ---------------------------------------------------------------------------

val termuxGitAbis = listOf("aarch64", "x86_64")
val androidJniAbis = listOf("arm64-v8a", "x86_64")

// Source package paths under Termux apt pool/main/<letter>/<name>/ — the last
// path segment must equal the binary package name (used to locate the .deb).
// libcurl + its transitive deps (nghttp2/3, ngtcp2, libssh2) are required by
// git-remote-https / curl for HTTP(S) remotes.
val termuxGitPackages = listOf(
    "g/git",
    "p/pcre2",
    "c/curl",
    "libc/libcurl",
    "libn/libnghttp2",
    "libn/libnghttp3",
    "libn/libngtcp2",
    "libs/libssh2",
    "libe/libexpat",
    "o/openssl",
    "z/zlib",
    "libi/libiconv",
)

val termuxGitMirror = "https://packages.termux.dev/apt/termux-main"
val termuxGitExtractRoot = layout.buildDirectory.dir("termux-git")

val extractTermuxGitBinaries = tasks.register("extractTermuxGitBinaries") {
    group = "git"
    description = "Downloads and extracts git + dependencies from Termux apt repository"

    // The task body uses raw java.net / ProcessBuilder to avoid capturing
    // Gradle script references — incompatible with configuration cache.
    notCompatibleWithConfigurationCache("Uses raw URL/exec to download and unpack Termux .debs")

    termuxGitAbis.forEach { abi ->
        termuxGitPackages.forEach { pkgPath ->
            outputs.dir(
                termuxGitExtractRoot.map { it.dir("$abi/${pkgPath.substringAfterLast('/')}") }
            )
        }
    }

    doLast {
        termuxGitAbis.forEach { abi ->
            termuxGitPackages.forEach { pkgPath ->
                val pkgName = pkgPath.substringAfterLast('/')
                val outDir = termuxGitExtractRoot.get().dir("$abi/$pkgName").asFile

                if (File(outDir, "bin").exists() || File(outDir, "lib").exists()) {
                    logger.lifecycle("Already extracted: $pkgName $abi")
                    return@forEach
                }
                outDir.deleteRecursively()
                outDir.mkdirs()

                val indexUrl = "$termuxGitMirror/pool/main/$pkgPath/"
                logger.lifecycle("Indexing $indexUrl")
                val listing = URI(indexUrl).toURL().openStream().bufferedReader().readText()
                val debName = Regex("href=\"(${Regex.escape(pkgName)}_[^\"]+_${abi}\\.deb)\"")
                    .findAll(listing)
                    .map { it.groupValues[1] }
                    .maxByOrNull { it }
                    ?: error("No .deb found for $pkgName $abi at $indexUrl")

                val debFile = File(outDir, debName)
                logger.lifecycle("Downloading $indexUrl$debName")
                URI("$indexUrl$debName").toURL().openStream().use { input ->
                    debFile.outputStream().use { input.copyTo(it) }
                }

                // .deb is an ar archive containing data.tar.{xz,zst,gz}
                runCommand(listOf("ar", "x", debFile.absolutePath), outDir)
                val dataFile = outDir.listFiles()
                    ?.filter { it.name.startsWith("data.tar") }
                    ?.maxByOrNull { it.name }
                    ?: error("data.tar.* not found in $debFile")
                runCommand(listOf("tar", "-xf", dataFile.absolutePath), outDir)
                outDir.listFiles { f -> f != dataFile && f.name != "bin" && f.name != "lib" }
                    ?.forEach { it.delete() }
                dataFile.delete()
                debFile.delete()
            }
        }
    }
}

val copyTermuxGitBinaries = tasks.register("copyTermuxGitBinaries") {
    group = "git"
    description = "Copies extracted Termux git binaries into jniLibs/<abi>/"
    dependsOn(extractTermuxGitBinaries)

    // The task body uses raw ProcessBuilder to invoke patchelf on the build
    // host. Same configuration-cache incompatibility as extractTermuxGitBinaries.
    notCompatibleWithConfigurationCache(
        "Invokes patchelf directly via ProcessBuilder — incompatible with configuration cache",
    )

    doLast {
        // Termux .deb packages extract to <root>/data/data/com.termux/files/usr/{bin,lib,libexec}
        val termuxPrefix = "data/data/com.termux/files/usr"

        termuxGitAbis.forEachIndexed { index, termuxAbi ->
            val androidAbi = androidJniAbis[index]
            val destDir = file("src/main/jniLibs/$androidAbi")
            destDir.deleteRecursively()
            destDir.mkdirs()

            // The git executables themselves are owned by :core-git so that the
            // terminal and the Git panel run the very same binaries. Their
            // dynamic-link dependencies stay here.
            val gitDestDir = rootProject.file("core-git/src/main/jniLibs/$androidAbi")
            gitDestDir.deleteRecursively()
            gitDestDir.mkdirs()

            termuxGitPackages.forEach { pkgPath ->
                val pkgName = pkgPath.substringAfterLast('/')
                val srcDir = termuxGitExtractRoot.get().dir("$termuxAbi/$pkgName").asFile
                val prefixDir = File(srcDir, termuxPrefix)

                // Copy ELF executables from <prefix>/bin and git's libexec/git-core.
                // Each binary is renamed to add a "lib" prefix and ".so" suffix so AGP's
                // copyDebugJniLibsProjectOnly task — which only includes files matching
                // lib*.so — keeps them in the APK. The renaming is purely cosmetic: ELF
                // execution doesn't care about the filename, and the dynamic linker
                // resolves NEEDED libraries via RUNPATH=$ORIGIN (see fixupTermuxElf).
                // GitBinary.install() creates per-name symlinks into filesDir/bin so
                // both the shell and the Git panel resolve them as `git`,
                // `git-upload-pack`, `scalar`, `curl` etc.
                listOf("bin", "libexec/git-core").forEach { rel ->
                    val src = File(prefixDir, rel)
                    if (!src.exists()) return@forEach
                    src.listFiles()?.forEach { f ->
                        if (!f.isFile) return@forEach
                        val dest = File(gitDestDir, "lib${f.name}.so")
                        f.copyTo(dest, overwrite = true)
                        dest.setExecutable(true, false)
                        fixupTermuxElf(dest, gitDestDir)
                    }
                }

                // Copy shared libraries from <prefix>/lib — only flat *.so files.
                // Versioned aliases (libz.so.1, libcrypto.so.3, ...) are skipped:
                // AGP drops anything not ending in .so, and fixupTermuxElf rewrites
                // all DT_NEEDED references to the unversioned names instead.
                // Subdirectories (pkgconfig/, cmake/, engines-3/, ...) are skipped too.
                File(prefixDir, "lib").takeIf { it.exists() }?.listFiles()?.forEach { f ->
                    if (!f.isFile || !f.name.endsWith(".so")) return@forEach
                    val dest = File(destDir, f.name)
                    f.copyTo(dest, overwrite = true)
                    dest.setExecutable(true, false)
                    fixupTermuxElf(dest, destDir)
                }
            }
        }
    }
}

androidComponents.onVariants { variant ->
    val capitalized = variant.name.replaceFirstChar { it.uppercase() }
    tasks.findByName("assemble$capitalized")?.dependsOn(copyTermuxGitBinaries)
}

/**
 * Runs an external command via [ProcessBuilder] so the call site doesn't capture
 * references to the Gradle script object — required for configuration-cache
 * compatibility elsewhere in this module.
 */
fun runCommand(command: List<String>, workingDir: File) {
    val process = ProcessBuilder(command)
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exit = process.waitFor()
    if (exit != 0) {
        throw GradleException("Command failed (exit=$exit): ${command.joinToString(" ")}\n$output")
    }
}

/**
 * Lightweight ELF detection via the magic header (0x7f 'E' 'L' 'F'). Avoids
 * shelling out to `file` and keeps `doLast` actions self-contained.
 */
fun isElfExecutable(file: File): Boolean {
    if (!file.isFile || file.length() < 4) return false
    return try {
        file.inputStream().use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 &&
                header[0] == 0x7f.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() &&
                header[3] == 'F'.code.toByte()
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * Rewrite a Termux-built ELF so it is self-contained inside the app's
 * nativeLibraryDir:
 *
 *  1. `--set-rpath $ORIGIN` — Termux embeds RUNPATH=/data/data/com.termux/files/usr/lib,
 *     which does not exist on devices without Termux. $ORIGIN makes the dynamic
 *     linker resolve dependencies from the directory the binary itself lives in
 *     (the extracted nativeLibraryDir, where all bundled .so files sit).
 *
 *  2. `--replace-needed` — Termux ELFs reference versioned sonames
 *     (libz.so.1, libcrypto.so.3, libssl.so.3), but AGP only packages files
 *     matching lib*.so, so those versioned filenames never reach the device.
 *     Rewriting DT_NEEDED to the unversioned bundled names fixes resolution
 *     without shipping duplicate versioned files.
 *
 *  3. `--set-soname` — the bundled libz.so/libcrypto.so/libssl.so still declare
 *     their versioned sonames (libz.so.1, libcrypto.so.3, libssl.so.3), which
 *     would never match the rewritten DT_NEEDED entries during bionic's
 *     soname lookup. Aligning DT_SONAME with the packaged filename keeps the
 *     linker's soname cache consistent.
 *
 * Non-ELF files (e.g. git-cvsserver shell script) are silently skipped.
 * patchelf is a build-host tool (x86_64 linux), but it edits the ELF directly
 * without executing it, so it works on cross-architecture objects.
 */
fun fixupTermuxElf(file: File, workingDir: File) {
    if (!isElfExecutable(file)) return

    val args = mutableListOf(
        "patchelf",
        "--set-rpath", "\$ORIGIN",
        "--replace-needed", "libz.so.1", "libz.so",
        "--replace-needed", "libcrypto.so.3", "libcrypto.so",
        "--replace-needed", "libssl.so.3", "libssl.so",
        "--replace-needed", "libexpat.so.1", "libexpat.so",
    )

    // Align DT_SONAME of the bundled zlib/openssl/expat with the packaged filename.
    val sonameFix = mapOf(
        "libz.so" to "libz.so",
        "libcrypto.so" to "libcrypto.so",
        "libssl.so" to "libssl.so",
        "libexpat.so" to "libexpat.so",
    )
    val newSoname = sonameFix[file.name]
    if (newSoname != null) {
        args += listOf("--set-soname", newSoname)
    }

    runCommand(args + file.absolutePath, workingDir)
}

dependencies {

    implementation(project(":core-common"))
    implementation(project(":core-git"))
    implementation(project(":core-navigation:api"))
    implementation(project(":core-ui"))

    implementation(project(":feature-python"))
    implementation(project(":feature-terminal:api"))
    implementation(project(":feature-terminal:termux-native"))

    implementation(libs.google.guava.empty)
    implementation(libs.termux.shared)

    implementation(libs.google.dagger)
    ksp(libs.google.dagger.compiler)

    testImplementation(project(":core-test"))
    androidTestImplementation(libs.test.junit.ext)
    androidTestImplementation(libs.test.runner)
}