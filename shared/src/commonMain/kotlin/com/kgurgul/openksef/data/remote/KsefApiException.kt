package com.kgurgul.openksef.data.remote

class KsefApiException(
    val statusCode: Int,
    val responseBody: String,
    val url: String? = null
) : Exception(buildMessage(statusCode, url, responseBody)) {
    private companion object {
        fun buildMessage(status: Int, url: String?, body: String): String {
            val urlPart = url?.let { " ($it)" }.orEmpty()
            val trimmed = body.trim()
            val bodyPart = if (trimmed.isEmpty()) "" else ": $trimmed"
            return "KSeF API HTTP $status$urlPart$bodyPart"
        }
    }
}
