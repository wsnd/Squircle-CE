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

package com.blacksquircle.ui.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Simple event bus for cross-module communication
 */
object EventBus {
    
    private val _events = MutableSharedFlow<AppEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events
    
    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }
}

/**
 * Application events
 */
sealed class AppEvent {
    data class ExecutePythonCommand(val command: String) : AppEvent()
}
