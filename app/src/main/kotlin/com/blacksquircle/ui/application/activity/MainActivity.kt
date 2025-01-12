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

package com.blacksquircle.ui.application.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.updatePadding
import com.blacksquircle.ui.R
import com.blacksquircle.ui.application.viewmodel.MainViewModel
import com.blacksquircle.ui.core.extensions.applySystemWindowInsets
import com.blacksquircle.ui.core.extensions.decorFitsSystemWindows
import com.blacksquircle.ui.core.extensions.fullscreenMode
import com.blacksquircle.ui.databinding.ActivityMainBinding
import com.blacksquircle.ui.utils.InAppUpdate
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var inAppUpdate: InAppUpdate

    // mainViewModel是与activity生命周期相关的实例
    private val mainViewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 显示自定义的启动屏幕
        installSplashScreen()
        // 调用父类的onCreate方法完成activity创建流程
        super.onCreate(savedInstanceState)
        // 使用数据绑定加载布局文件
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置窗口的装饰视图不适应系统窗口，通常用于实现全屏效果或自定义状态栏样式
        window.decorFitsSystemWindows(false)
        // 调用自定义的 fullscreenMode 方法，根据 MainViewModel 中的 fullScreenMode 属性设置窗口的全屏模式
        window.fullscreenMode(mainViewModel.fullScreenMode)

        // 自定义的 applySystemWindowInsets 方法来处理系统窗口内边距
        // 这里将navHost的左、右内边距更新为系统窗口内边距的值
        binding.navHost.applySystemWindowInsets(false) { left, _, right, _ ->
            binding.navHost.updatePadding(left = left, right = right)
        }

        // 如果发现更新，显示一个 Snackbar 提示用户
        inAppUpdate.checkForUpdates(this) {
            Snackbar.make(binding.root, R.string.message_in_app_update_ready, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.action_restart) { inAppUpdate.completeUpdate() }
                .show()
        }

        if (savedInstanceState == null) {
            mainViewModel.handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        mainViewModel.handleIntent(intent)
    }
}