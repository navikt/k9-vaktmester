package no.nav.k9

import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.k9.rapid.river.Environment
import no.nav.k9.rapid.river.KafkaBuilder.kafkaProducer
import no.nav.k9.rapid.river.hentRequiredEnv
import no.nav.k9.vaktmester.Arbeidstider
import no.nav.k9.vaktmester.RyddeScheduler
import no.nav.k9.vaktmester.RyddeService
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.DataSourceBuilder
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.db.migrate
import org.apache.kafka.clients.producer.KafkaProducer
import javax.sql.DataSource

internal class ApplicationContext(
    val env: Environment,
    val dataSource: DataSource,
    val arkivRepository: ArkivRepository,
    val inFlightRepository: InFlightRepository,
    val healthService: HealthService,
    val ryddeService: RyddeService,
    val ryddeScheduler: RyddeScheduler,
    val kafkaProducer: KafkaProducer<String, String>
) {

    val appNavn = env.hentRequiredEnv("NAIS_APP_NAME")

    internal fun start() {
        dataSource.migrate()
        ryddeScheduler.start()
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
        var arbeidstider: Arbeidstider? = null
    ) {

        internal fun build(): ApplicationContext {
            val benyttetEnv = env ?: System.getenv()
            val benyttetDataSource = dataSource ?: DataSourceBuilder(benyttetEnv).build()
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
