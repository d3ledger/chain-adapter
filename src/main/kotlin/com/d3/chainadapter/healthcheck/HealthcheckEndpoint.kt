package com.d3.chainadapter.healthcheck

import com.d3.chainadapter.config.ChainAdapterConfig
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class HealthCheckEndpoint(private val chainAdapterConfig: ChainAdapterConfig) {

    private val started = AtomicBoolean()

    /**
     * Starts ktor based health check server
     */
    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        val server = embeddedServer(Netty, port = chainAdapterConfig.healthCheckPort) {
            install(CORS)
            {
                anyHost()
            }
            install(ContentNegotiation) {
                gson()
            }
            routing {
                get("/actuator/health") {
                    call.respond(
                        mapOf(
                            "status" to "UP"
                        )
                    )
                }
            }
        }
        server.start(wait = false)
    }
}
