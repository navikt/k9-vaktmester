package no.nav.k9.testutils

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.ApplicationContext
import no.nav.k9.vaktmester.Arbeidstider
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.nio.file.Files.createTempDirectory

internal class ApplicationContextExtension : ParameterResolver {

    internal companion object {

        private fun embeddedPostgress(tempDir: File) = EmbeddedPostgres.builder()
            .setOverrideWorkingDirectory(tempDir)
            .setDataDirectory(tempDir.resolve("datadir"))
            .start()

        private val embeddedPostgres = embeddedPostgress(createTempDirectory("tmp_postgres").toFile())
        private val applicationContext = ApplicationContext.Builder(
            dataSource = testDataSource(embeddedPostgres),
            env = System.getenv()
                .plus(
                    mapOf(
                        "KAFKA_RAPID_TOPIC" to "TEST"
                    )
                ),
            kafkaProducer = mockk<KafkaProducer<String, String>>().also {
                it.mockSend()
            },
            arbeidstider = Arbeidstider {
                LocalDateTime.parse("2020-11-23T10:45:00")
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

        internal fun KafkaProducer<String, String>.mockSend() {
            every { send(any()) }.returns(
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
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return støttedeParametre.contains(parameterContext.parameter.type)
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return applicationContext
    }
}
