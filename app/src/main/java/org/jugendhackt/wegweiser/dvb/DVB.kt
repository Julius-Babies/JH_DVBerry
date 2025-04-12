package org.jugendhackt.wegweiser.dvb

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

object Dvb {
    private var baseUrl = "https://webapi.vvo-online.de"

    suspend fun departureMonitor(stopID: Int, limit: Int): String {
        val client = HttpClient(CIO)
        val response: HttpResponse = client.post("https://webapi.vvo-online.de/dm") {
            setBody("{stopid: $stopID; limit: $limit}")
            contentType(ContentType.Application.Json)
        }
        return response.bodyAsText()
    }
}
