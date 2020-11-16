import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val junitJupiterVersion = "5.7.0"
val k9rapidVersion = "1.31aa518"
val flywayVersion = "7.0.3"
val hikariVersion = "3.4.5"
val kotliqueryVersion = "1.3.1"
val postgresVersion = "42.2.18"
val embeddedPostgres = "0.13.3"
val ktorVersion = "1.4.1"
val dusseldorfVersion = "1.4.1.4754df6"
val jsonassertVersion = "1.5.0"
val vaultJdbcVersion = "1.3.7"
val assertjVersion = "3.11.1"
val mockkVersion = "1.10.0"
val orgJsonVersion = "20201115"

val mainClass = "no.nav.k9.AppKt"

plugins {
    kotlin("jvm") version "1.4.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

dependencies {
    implementation("no.nav.k9.rapid:river:$k9rapidVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-health:$dusseldorfVersion")
    implementation("no.nav.helse:dusseldorf-ktor-auth:$dusseldorfVersion")
    implementation("org.json:json:$orgJsonVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresVersion")

    // Test
    testImplementation("com.opentable.components:otj-pg-embedded:$embeddedPostgres")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("no.nav.helse:dusseldorf-test-support:$dusseldorfVersion")
    testImplementation ("org.skyscreamer:jsonassert:$jsonassertVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

repositories {
    mavenLocal()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/k9-rapid")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
    jcenter()
}

tasks {

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        manifest {
            attributes(
                    mapOf(
                            "Main-Class" to mainClass
                    )
            )
        }
    }

    withType<Wrapper> {
        gradleVersion = "6.7"
    }

}
