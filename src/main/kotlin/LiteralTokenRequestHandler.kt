import dev.kord.rest.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization

class LiteralTokenRequestHandler private constructor(override val token: String, private val requestHandler: KtorRequestHandler) :
    RequestHandler by requestHandler {
    
    constructor(token: String) : this(token, KtorRequestHandler(token))
    
    override suspend fun <B : Any, R> handle(request: Request<B, R>): R {
        val newHeaders = Headers.build {
            appendAll(request.headers)
            set(Authorization, token)
        }
        
        val newRequest: Request<B, R> = when (request) {
            is JsonRequest -> JsonRequest(request.route, request.routeParams, request.parameters, newHeaders, request.body)
            is MultipartRequest -> MultipartRequest(request.route, request.routeParams, request.parameters, newHeaders, request.body, request.files)
        }
        
        return requestHandler.handle(newRequest)
    }
}
