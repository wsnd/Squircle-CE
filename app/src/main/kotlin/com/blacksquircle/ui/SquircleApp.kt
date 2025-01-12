/*
 * Copyright 2023 Squircle CE contributors.
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

package com.blacksquircle.ui

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.blacksquircle.ui.core.logger.AndroidTree
import com.blacksquircle.ui.core.storage.keyvalue.SettingsManager
import com.blacksquircle.ui.core.theme.Theme
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

// 触发hilt代码生成，为应用创建一个应用级的依赖容器
@HiltAndroidApp
// 在运行时动态地提供配置信息
class SquircleApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // 重写这个方法，在应用的上下文被附加之前执行自定义逻辑
    override fun attachBaseContext(base: Context) {
        // 主题设置
        val settingsManager = SettingsManager(base)
        Theme.of(settingsManager.theme).apply()
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        // 日志初始化
        Timber.plant(AndroidTree())
    }

    // Configuration 是 WorkManager 的配置类，用于定义 WorkManager 的行为和特性
    override fun getWorkManagerConfiguration(): Configuration {
        // 允许 WorkManager 在创建 Worker 时使用 Hilt 进行依赖注入,
        // 当需要在 Worker 中使用依赖注入时，可以通过 HiltWorkerFactory 来实现
        // 这样，Worker 可以像其他 Android 组件一样，通过依赖注入获取所需的依赖项
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}