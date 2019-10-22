package ca.wikinet.opencensus_ktor

import io.opencensus.trace.AttributeValue
import io.opencensus.trace.Span
import io.opencensus.trace.Status

fun Span.setStatusFromCode(statusCode: Int) {
    when (statusCode) {
        in 200..399 -> setStatus(Status.OK)
        400 -> setStatus(Status.INVALID_ARGUMENT)
        504 -> setStatus(Status.DEADLINE_EXCEEDED)
        404 -> setStatus(Status.NOT_FOUND)
        403 -> setStatus(Status.PERMISSION_DENIED)
        401 -> setStatus(Status.UNAUTHENTICATED)
        429 -> setStatus(Status.RESOURCE_EXHAUSTED)
        501 -> setStatus(Status.UNIMPLEMENTED)
        503 -> setStatus(Status.UNAVAILABLE)
        else -> setStatus(Status.UNKNOWN)
    }
}

fun Span.putStringAttribute(key: String, value: String?) {
    if (!value.isNullOrEmpty()) {
        putAttribute(key, AttributeValue.stringAttributeValue(value))
    }
}
