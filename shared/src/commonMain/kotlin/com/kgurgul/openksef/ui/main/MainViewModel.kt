/*
 * Copyright KG Soft
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

package com.kgurgul.openksef.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.domain.observable.SessionExpiredObservable
import com.kgurgul.openksef.domain.observe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

/** One-shot events emitted by [MainViewModel]. */
sealed interface MainEvent {
    data object SessionExpired : MainEvent
}

/**
 * Root-screen [ViewModel]. It owns the single, app-wide reaction to session expiry: it observes
 * [SessionExpiredObservable] and emits a [MainEvent.SessionExpired] event through a buffered
 * channel so the navigation host can react via `ObserveAsEvents` without missing the signal.
 */
class MainViewModel(sessionExpiredObservable: SessionExpiredObservable) : ViewModel() {

    private val eventChannel = Channel<MainEvent>(Channel.BUFFERED)
    val events: Flow<MainEvent> = eventChannel.receiveAsFlow()

    init {
        sessionExpiredObservable
            .observe()
            .onEach { eventChannel.send(MainEvent.SessionExpired) }
            .launchIn(viewModelScope)
    }
}
