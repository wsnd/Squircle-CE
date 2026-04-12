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
    id("com.blacksquircle.feature")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.blacksquircle.ui.feature.python"

    defaultConfig {
        // Configure native build
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
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

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }

    // Configure native library packaging
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
