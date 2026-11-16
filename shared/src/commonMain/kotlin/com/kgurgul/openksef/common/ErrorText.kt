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

package com.kgurgul.openksef.common

import com.kgurgul.openksef.data.remote.KsefApiException
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_rate_limited
import openksef.shared.generated.resources.error_rate_limited_retry_after
import org.jetbrains.compose.resources.StringResource

/**
 * Maps an error to a text which can be shown to the user: a dedicated message when KSeF throttles
 * the requests, the description provided with the error otherwise and [fallback] when there is
 * none.
 */
fun Throwable.toUiText(fallback: StringResource): UiText {
    if (this is KsefApiException && isRateLimited) {
        val retryAfter = retryAfterSeconds
        return if (retryAfter != null && retryAfter > 0) {
            UiText.Resource(Res.string.error_rate_limited_retry_after, listOf(retryAfter))
        } else {
            UiText.Resource(Res.string.error_rate_limited)
        }
    }
    return message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.Resource(fallback)
}
