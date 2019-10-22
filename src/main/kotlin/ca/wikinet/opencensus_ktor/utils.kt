package ca.wikinet.opencensus_ktor

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.request.ApplicationRequest
import io.opencensus.trace.propagation.TextFormat

object KtorTextFormatSetter : TextFormat.Setter<HttpRequestBuilder>() {
    override fun put(carrier: HttpRequestBuilder, key: String, value: String) {
        carrier.header(key, value)
    }
}

object KtorTextFormatGetter : TextFormat.Getter<ApplicationRequest>() {
    override fun get(carrier: ApplicationRequest, key: String): String? {
        return carrier.headers[key]
    }
}
