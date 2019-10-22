package ca.wikinet.opencensus_ktor

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.request.header
import io.ktor.request.host
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.request.uri
import io.ktor.request.userAgent
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.opencensus.contrib.http.util.HttpTraceAttributeConstants
import io.opencensus.trace.MessageEvent
import io.opencensus.trace.Sampler
import io.opencensus.trace.Span
import io.opencensus.trace.Status
import io.opencensus.trace.Tracing
import io.opencensus.trace.propagation.SpanContextParseException
import io.opencensus.trace.samplers.Samplers

class OpencensusTracingServer() {

    class Configuration {
        var serviceName = "TraceNet SVC"
        var traceSampler: Sampler = Samplers.probabilitySampler(1 / 1000.0)
        var exporterSetup: (() -> Unit)? = null
        var ignoredPath = emptyList<String>()
    }

    companion object Feature :
        ApplicationFeature<Application, Configuration, OpencensusTracingServer> {

        override val key = AttributeKey<OpencensusTracingServer>("OpencensusTracing")

        override fun install(
            pipeline: Application,
            configure: Configuration.() -> Unit
        ): OpencensusTracingServer {
            val configuration = Configuration()
                .apply(configure)

            val feature = OpencensusTracingServer()

            configuration.exporterSetup?.let { it() }

            pipeline.intercept(ApplicationCallPipeline.Call) {
                if (checkPathIgnored(call.request.path(), configuration.ignoredPath)) {
                    intercept(this, configuration.traceSampler)
                }
            }
            return feature
        }

        private fun checkPathIgnored(requestPath: String, ignoredPath: List<String>): Boolean {
            for (path in ignoredPath) {
                if (requestPath.startsWith(path)) {
                    return false
                }
            }
            return true
        }

        private suspend fun intercept(
            context: PipelineContext<Unit, ApplicationCall>,
            traceSampler: Sampler
        ) {
            val tracer = Tracing.getTracer()
            val textFormat = Tracing.getPropagationComponent().traceContextFormat
            val call = context.call

            val spanContext = try {
                textFormat.extract(
                    call.request,
                    KtorTextFormatGetter
                )
            } catch (e: SpanContextParseException) {
                null
            }

            tracer.spanBuilderWithRemoteParent(call.request.uri, spanContext)
                .setSpanKind(Span.Kind.SERVER)
                .setRecordEvents(true)
                .setSampler(traceSampler)
                .startScopedSpan().use {
                    val span = tracer.currentSpan
                    span.addMessageEvent(
                        MessageEvent.builder(
                            MessageEvent.Type.SENT,
                            System.currentTimeMillis()
                        ).setUncompressedMessageSize(
                            call.request.header("content-length")?.toLong() ?: 0
                        ).build()
                    )

                    try {
                        context.proceed()
                    } finally {
                        span.setStatusFromCode(call.response.status()?.value ?: 0)
                        span.setStatus(Status.INTERNAL.withDescription(call.response.status().toString()))

                        setHTTPAttributes(span, call)

                        span.addMessageEvent(
                            MessageEvent.builder(
                                MessageEvent.Type.RECEIVED,
                                System.currentTimeMillis()
                            ).setUncompressedMessageSize(
                                call.response.headers["content-length"]?.toLong() ?: 0
                            ).build()
                        )
                    }
                }
        }

        private fun setHTTPAttributes(span: Span, call: ApplicationCall) {
            span.putStringAttribute(
                "ktor.env", call.application.environment.config.property(
                    "ktor.environment"
                ).getString()
            )

            span.putStringAttribute(HttpTraceAttributeConstants.HTTP_HOST, call.request.host())
            span.putStringAttribute(
                HttpTraceAttributeConstants.HTTP_METHOD,
                call.request.httpMethod.value
            )
            span.putStringAttribute(HttpTraceAttributeConstants.HTTP_PATH, call.request.path())
            span.putStringAttribute(
                HttpTraceAttributeConstants.HTTP_USER_AGENT,
                call.request.userAgent() ?: "NoAgent"
            )
            span.putStringAttribute(
                HttpTraceAttributeConstants.HTTP_STATUS_CODE,
                call.response.status()?.value.toString() ?: "0"
            )
            span.putStringAttribute(HttpTraceAttributeConstants.HTTP_URL, call.request.uri)
        }
    }
}
