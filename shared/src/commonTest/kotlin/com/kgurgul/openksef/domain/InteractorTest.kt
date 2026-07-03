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

package com.kgurgul.openksef.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class InteractorTest {

    @Test
    fun immutableInteractor_observe_emitsValuesForGivenParams() = runTest {
        val interactor = DoublingObservable(UnconfinedTestDispatcher(testScheduler))

        assertEquals(10, interactor.observe(5).first())
    }

    @Test
    fun immutableInteractor_unitExtension_observes() = runTest {
        val interactor = ConstantObservable(UnconfinedTestDispatcher(testScheduler))

        assertEquals("value", interactor.observe().first())
    }

    @Test
    fun resultInteractor_invoke_returnsWorkResult() = runTest {
        val interactor = DoublingResultInteractor(UnconfinedTestDispatcher(testScheduler))

        assertEquals(42, interactor(21))
    }

    @Test
    fun resultInteractor_unitExtension_invokes() = runTest {
        val interactor = ConstantResultInteractor(UnconfinedTestDispatcher(testScheduler))

        assertEquals("done", interactor())
    }

    @Test
    fun mutableInteractor_observeWithParams_emitsForInitialAndNewParams() = runTest {
        val interactor = DoublingMutableInteractor(UnconfinedTestDispatcher(testScheduler))

        val values = mutableListOf<Int>()
        val collector = launch { interactor.observe(1).take(2).toList(values) }
        runCurrent()
        interactor(3)
        collector.join()

        assertEquals(listOf(2, 6), values)
    }

    @Test
    fun mutableInteractor_observe_replaysLastParams() = runTest {
        val interactor = DoublingMutableInteractor(UnconfinedTestDispatcher(testScheduler))

        interactor(4)

        assertEquals(8, interactor.observe().first())
    }

    private class DoublingObservable(override val dispatcher: CoroutineDispatcher) :
        ImmutableInteractor<Int, Int>() {
        override fun createObservable(params: Int) = flowOf(params * 2)
    }

    private class ConstantObservable(override val dispatcher: CoroutineDispatcher) :
        ImmutableInteractor<Unit, String>() {
        override fun createObservable(params: Unit) = flowOf("value")
    }

    private class DoublingResultInteractor(override val dispatcher: CoroutineDispatcher) :
        ResultInteractor<Int, Int>() {
        override suspend fun doWork(params: Int): Int = params * 2
    }

    private class ConstantResultInteractor(override val dispatcher: CoroutineDispatcher) :
        ResultInteractor<Unit, String>() {
        override suspend fun doWork(params: Unit): String = "done"
    }

    private class DoublingMutableInteractor(override val dispatcher: CoroutineDispatcher) :
        MutableInteractor<Int, Int>() {
        override fun createObservable(params: Int) = flowOf(params * 2)
    }
}
