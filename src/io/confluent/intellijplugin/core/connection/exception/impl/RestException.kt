package io.confluent.intellijplugin.core.connection.exception.impl

import io.confluent.intellijplugin.core.util.toPresentableText
import org.apache.http.HttpResponse
import java.net.SocketException
import java.net.URI

abstract class RestException(origin: Throwable? = null) : Exception(origin)

class RestExecutionException(requestDescription: String, override val cause: Throwable?) : RestException() {
    override val message: String = "Error during '$requestDescription' request.\n${this.cause?.toPresentableText()}"
}

class RestResponseExceptionData(
    val response: HttpResponse,
    private val requestDescription: String,
    val contentString: String
) {
    val actualCode get() = response.statusLine?.statusCode
    val reason get() = response.statusLine?.reasonPhrase ?: "No status received"
    val message: String
        get() = "REST '${requestDescription}' wrong response. \n" +
                "Code: $actualCode($reason), \n, " +
                "Content: ${contentString.ifBlank { "<EMPTY>" }}"
}

open class RestResponseException(val data: RestResponseExceptionData) : RestException() {
    override val message: String = data.message
    val actualCode by data::actualCode
    val contentString by data::contentString
}

class RestSocksProxyAuthenticationRequiredException(origin: SocketException) : RestException(origin)

class RestResponseOauthRequiredException(
    val redirectUrl: URI,
    data: RestResponseExceptionData,
) : RestResponseException(data) {
    override val message get() = "Resource requires OAuth, redirect to $redirectUrl"
}