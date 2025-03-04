package no.nav.k9

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.application.*
import io.ktor.serialization.jackson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import no.nav.helse.dusseldorf.ktor.health.HealthReporter
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.k9.vaktmester.river.ArkivRiver
import no.nav.k9.vaktmester.river.InFlightRiver

fun main() {
    val applicationContext = ApplicationContext.Builder().build()
    RapidApplication.create(
        env = applicationContext.env,
        builder = { withKtorModule { k9Vaktmester(applicationContext) } }
    )
        .apply { registerApplicationContext(applicationContext) }
        .start()
}

internal fun RapidsConnection.registerApplicationContext(applicationContext: ApplicationContext) {
    ArkivRiver(
        rapidsConnection = this,
        arkivRepository = applicationContext.arkivRepository,
        inflightRepository = applicationContext.inFlightRepository
    )
    InFlightRiver(
        rapidsConnection = this,
        inFlightRepository = applicationContext.inFlightRepository,
        arkivRepository = applicationContext.arkivRepository
    )

    register(object : RapidsConnection.StatusListener {
        override fun onStartup(rapidsConnection: RapidsConnection) {
            applicationContext.start()
        }

        override fun onShutdown(rapidsConnection: RapidsConnection) {
            applicationContext.stop()
        }
    })
}

internal fun Application.k9Vaktmester(applicationContext: ApplicationContext) {
    install(ContentNegotiation) {
        jackson()
    }

    HealthReporter(
        app = applicationContext.appNavn,
        healthService = applicationContext.healthService
    )

    routing {
        HealthRoute(healthService = applicationContext.healthService)
    }
}

