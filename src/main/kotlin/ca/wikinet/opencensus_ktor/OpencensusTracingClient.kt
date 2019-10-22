package ca.wikinet.opencensus_ktor

import io.ktor.client.HttpClient
import io.ktor.client.features.HttpClientFeature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.host
import io.ktor.client.response.HttpResponsePipeline
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import io.opencensus.contrib.http.util.HttpTraceAttributeConstants
import io.opencensus.trace.AttributeValue
import io.opencensus.trace.Span
import io.opencensus.trace.Tracing

class OpencensusTracingClient() {

    class Config()

    companion object Feature : HttpClientFeature<Config, OpencensusTracingClient> {
        override val key: AttributeKey<OpencensusTracingClient> = AttributeKey("HttpOpencensusTracing")

        private val tracer = Tracing.getTracer()
        private val textFormat = Tracing.getPropagationComponent().traceContextFormat

        override fun prepare(block: Config.() -> Unit): OpencensusTracingClient {
            return OpencensusTracingClient()
        }

        override fun install(feature: OpencensusTracingClient, scope: HttpClient) {

            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {

                val spanName = context.url.encodedPath
                tracer.spanBuilderWithExplicitParent(spanName, tracer.currentSpan).setSpanKind(Span.Kind.CLIENT)
                    .startScopedSpan()

                val span = tracer.currentSpan

                setHttpAttributes(span, this.context)

                textFormat.inject(
                    span.context, context,
                    KtorTextFormatSetter
                )
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.After) {
                val span = tracer.currentSpan
                span.putAttribute(
                    HttpTraceAttributeConstants.HTTP_STATUS_CODE,
                    AttributeValue.longAttributeValue(context.response.status.value.toLong())
                )
                span.setStatusFromCode(context.response.status.value)
                span.end()
            }
        }

        private fun setHttpAttributes(
            span: Span,
            context: HttpRequestBuilder
        ) {
            span.putStringAttribute(
                HttpTraceAttributeConstants.HTTP_USER_AGENT,
                context.headers[HttpHeaders.UserAgent]
            )
            span.putStringAttribute(HttpTraceAttributeConstants.HTTP_HOST, context.host)
            span.putStringAttribute(HttpTraceAttributeConstants.HTTP_METHOD, context.method.value)
            span.putStringAttribute(HttpTraceAttributeConstants.HTTP_PATH, context.url.encodedPath)
            span.putStringAttribute(HttpTraceAttributeConstants.HTTP_URL, context.url.buildString())
        }
    }
}
