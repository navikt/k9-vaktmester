package no.nav.k9.vaktmester.db

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import java.io.File

class InMemoryDb {
    private fun embeddedPostgres(tempDir: File) = EmbeddedPostgres.builder()
        .setOverrideWorkingDirectory(tempDir)
        .setDataDirectory(tempDir.resolve("datadir"))
        .start()

    internal fun build() = embeddedPostgres(createTempDir("tmp_dir"))
}
