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

plugins {
    id("com.blacksquircle.application")
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.baselineprofile)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.blacksquircle.ui"

    defaultConfig {
        applicationId = "com.blacksquircle.ui"
        versionCode = 10028
        versionName = "2025.1.3"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        jniLibs {
            // termux-native module provides 16KB-aligned libs built via CMake.
            // When both termux-native and Termux AAR ship the same .so, prefer
            // the version from termux-native (which does not link libstdc++).
            // Gradle resolves projects before external AARs, so termux-native
            // wins by default; pickFirsts handles any remaining duplicates.
            pickFirsts += "**/liblocal-socket.so"
            pickFirsts += "**/libtermux.so"
            // AndroidManifest sets android:extractNativeLibs="true", so the
            // packaged jniLibs/<abi>/ directory is extracted to nativeLibraryDir
            // at install time. Without useLegacyPackaging, AGP silently filters
            // out anything not matching lib*.so and drops the Termux git
            // binaries (git, git-upload-pack, scalar, curl, ...).
            useLegacyPackaging = true

            // Termux git binaries are rewritten with patchelf (rpath/needed/
            // soname fixes) which appends an extra PT_LOAD segment. AGP's
            // stripDebugSymbols (llvm-objcopy) then compacts the file layout
            // and breaks PT_LOAD congruence (p_offset ≡ p_vaddr mod page size),
            // so on-device bionic maps the segment shifted and every git/git-*
            // invocation dies with a silent SIGSEGV. Skipping the strip for
            // these files fixes it; Termux ships release binaries anyway.
            // NOTE: must live in the app module — library-level
            // keepDebugSymbols does not propagate to the app's strip task.
            keepDebugSymbols += listOf(
                "**/libgit*.so",
                "**/libscalar*.so",
                "**/libwcurl*.so",
                "**/libcurl.so",
                "**/libcrypto.so",
                "**/libssl.so",
                "**/libz.so",
                "**/libexpat.so",
                "**/libiconv.so",
                "**/libcharset.so",
                "**/libpcre2-*.so",
                "**/libnghttp2.so",
                "**/libnghttp3.so",
                "**/libngtcp2*.so",
                "**/libssh2.so",
            )
        }
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/versions/11/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/versions/15/OSGI-INF/MANIFEST.MF"
        }
    }
    buildFeatures {
        compose = true
    }

    androidResources {
        ignoreAssetsPattern = ""
    }
}

dependencies {

    implementation(project(":core-common"))
    implementation(project(":core-navigation:api"))
    implementation(project(":core-navigation:impl"))
    implementation(project(":core-redux"))
    implementation(project(":core-ui"))

    implementation(project(":feature-editor:api"))
    implementation(project(":feature-editor:impl"))
    implementation(project(":feature-explorer:api"))
    implementation(project(":feature-explorer:impl"))
    implementation(project(":feature-fonts:api"))
    implementation(project(":feature-fonts:impl"))
    implementation(project(":feature-git:api"))
    implementation(project(":feature-git:impl"))
    implementation(project(":feature-python"))
    implementation(project(":feature-servers:api"))
    implementation(project(":feature-servers:impl"))
    implementation(project(":feature-settings:api"))
    implementation(project(":feature-settings:impl"))
    implementation(project(":feature-shortcuts:api"))
    implementation(project(":feature-shortcuts:impl"))
    implementation(project(":feature-terminal:api"))
    implementation(project(":feature-terminal:impl"))
    implementation(project(":feature-themes:api"))
    implementation(project(":feature-themes:impl"))
    baselineProfile(project(":benchmark"))

    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.viewmodel.navigation)
    implementation(libs.androidx.profileinstaller)
    gmsImplementation(libs.google.appupdate)

    coreLibraryDesugaring(libs.android.tools.desugaring)

    implementation(libs.google.dagger)
    ksp(libs.google.dagger.compiler)

    testImplementation(project(":core-test"))
    androidTestImplementation(libs.test.junit.ext)
    androidTestImplementation(libs.test.runner)
}

baselineProfile {
    mergeIntoMain = true
}