package no.nav.k9.testutils

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.ApplicationContext
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.io.File
import java.util.concurrent.CompletableFuture

internal class ApplicationContextExtension : ParameterResolver {

    internal companion object {

        private fun embeddedPostgress(tempDir: File) = EmbeddedPostgres.builder()
            .setOverrideWorkingDirectory(tempDir)
            .setDataDirectory(tempDir.resolve("datadir"))
            .start()

        private val embeddedPostgres = embeddedPostgress(createTempDir("tmp_postgres"))
        private val applicationContext = ApplicationContext.Builder(
            dataSource = testDataSource(embeddedPostgres),
            env = System.getenv()
                .plus(
                    mapOf(
                        "SCHEDULE_INIT_DELAY" to "_10",
                        "SCHEDULE_INTERVAL" to "_100"
                    )
                ),
            kafkaProducer = mockk<KafkaProducer<String, String>>().also {
                every { it.send(any()) }.returns(
                    CompletableFuture.completedFuture(
                        RecordMetadata(
                            TopicPartition("foo", 1),
                            1L,
                            1L,
                            System.currentTimeMillis(),
                            1L,
                            1,
                            1
                        )
                    )
                )
            }
        ).build()

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    embeddedPostgres.postgresDatabase.connection.close()
                    embeddedPostgres.close()
                }
            )
        }

        private val støttedeParametre = listOf(
            ApplicationContext::class.java
        )
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return støttedeParametre.contains(parameterContext.parameter.type)
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return applicationContext
    }
}
