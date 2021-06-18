package no.nav.k9

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import no.nav.helse.dusseldorf.ktor.health.HealthReporter
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.k9.rapid.river.Environment
import no.nav.k9.rapid.river.KafkaBuilder.kafkaProducer
import no.nav.k9.rapid.river.KafkaBuilder.kafkaProducerAiven
import no.nav.k9.rapid.river.hentRequiredEnv
import no.nav.k9.vaktmester.Arbeidstider
import no.nav.k9.vaktmester.RyddeService
import no.nav.k9.vaktmester.RyddeScheduler
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.DataSourceBuilder
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.river.ArkivRiver
import no.nav.k9.vaktmester.river.InFlightRiver
import no.nav.k9.vaktmester.river.OnPremTilAivenRiver
import org.apache.kafka.clients.producer.KafkaProducer
import javax.sql.DataSource

fun main() {
    val applicationContext = ApplicationContext.Builder().build()
    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(applicationContext.env))
        .withKtorModule { k9Vaktmester(applicationContext) }
        .build()
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

    if (applicationContext.erAssistent) {
        OnPremTilAivenRiver(
            rapidsConnection = this,
            kafkaProducer = applicationContext.env.kafkaProducerAiven("on-prem-til-aiven")
        )
    }
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

internal class ApplicationContext(
    val env: Environment,
    val dataSource: DataSource,
    val arkivRepository: ArkivRepository,
    val inFlightRepository: InFlightRepository,
    val healthService: HealthService,
    val ryddeService: RyddeService,
    val ryddeScheduler: RyddeScheduler,
    val kafkaProducer: KafkaProducer<String, String>) {
    val appNavn = env.hentRequiredEnv("NAIS_APP_NAME").also {
        require(it == "k9-vaktmesterassistent" || it  == "k9-vaktmester") {
            "Ugyldig appNavn $it"
        }
    }
    val erAssistent = appNavn == "k9-vaktmesterassistent"

    internal fun start() {
        DataSourceBuilder(env).migrateAsAdmin()
    }
    internal fun stop() {
        ryddeScheduler.stop()
    }

    internal class Builder(
        var env: Environment? = null,
        var dataSource: DataSource? = null,
        var arkivRepository: ArkivRepository? = null,
        var inFlightRepository: InFlightRepository? = null,
        var ryddeService: RyddeService? = null,
        var ryddeScheduler: RyddeScheduler? = null,
        var kafkaProducer: KafkaProducer<String, String>? = null,
        var arbeidstider: Arbeidstider? = null) {

        internal fun build(): ApplicationContext {
            val benyttetEnv = env ?: System.getenv()
            val benyttetDataSource = dataSource ?: DataSourceBuilder(benyttetEnv).getDataSource()
            val benyttetArkivRepository = arkivRepository ?: ArkivRepository(benyttetDataSource)
            val benyttetInFlightRepository = inFlightRepository ?: InFlightRepository(benyttetDataSource)
            val benyttetKafkaProducer = kafkaProducer ?: benyttetEnv.kafkaProducer("ryddejobb")
            val benyttetRepubliseringService = ryddeService ?: RyddeService(
                inFlightRepository = benyttetInFlightRepository,
                arkivRepository = benyttetArkivRepository,
                kafkaProducer = benyttetKafkaProducer,
                env = benyttetEnv,
                arbeidstider = arbeidstider ?: Arbeidstider()
            )
            val benyttetInFlightScheduler = ryddeScheduler ?: RyddeScheduler(
                ryddeService = benyttetRepubliseringService
            )

            return ApplicationContext(
                env = benyttetEnv,
                dataSource = benyttetDataSource,
                arkivRepository = benyttetArkivRepository,
                inFlightRepository = benyttetInFlightRepository,
                ryddeService = benyttetRepubliseringService,
                ryddeScheduler = benyttetInFlightScheduler,
                kafkaProducer = benyttetKafkaProducer,
                healthService = HealthService(
                    healthChecks = setOf(
                        benyttetArkivRepository,
                        benyttetInFlightRepository
                    )
                )
            )
        }
    }
}
