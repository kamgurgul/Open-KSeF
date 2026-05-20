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

package com.kgurgul.openksef.data.remote

class KsefApiException(val statusCode: Int, val responseBody: String, val url: String? = null) :
    Exception(buildMessage(statusCode, url, responseBody)) {
    private companion object {
        fun buildMessage(status: Int, url: String?, body: String): String {
            val urlPart = url?.let { " ($it)" }.orEmpty()
            val trimmed = body.trim()
            val bodyPart = if (trimmed.isEmpty()) "" else ": $trimmed"
            return "KSeF API HTTP $status$urlPart$bodyPart"
        }
    }
}
