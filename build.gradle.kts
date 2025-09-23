import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val k9rapidVersion = "1.20250916105515-e0bf24e"
val flywayVersion = "11.13.1"
val hikariVersion = "7.0.2"
val kotliqueryVersion = "1.9.1"
val postgresVersion = "42.7.8"
val ktorVersion = "3.2.3"
val dusseldorfVersion = "7.0.4"
val vaultJdbcVersion = "1.3.10"
val orgJsonVersion = "20250517"

// Test avhengigheter
val junitJupiterVersion = "5.13.4"
val junitPlatformVersion = "1.13.4"
val embeddedPostgres = "2.1.1"
val embeddedPostgresBinaries = "12.9.0"
val mockkVersion = "1.14.5"
val assertjVersion = "3.27.6"
val jsonassertVersion = "1.5.3"

val mainClass = "no.nav.k9.AppKt"

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.sonarqube") version "6.3.1.5724"
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("no.nav.k9.rapid:river:$k9rapidVersion")
    implementation("no.nav.helse:dusseldorf-ktor-health:$dusseldorfVersion")
    implementation("no.nav.helse:dusseldorf-ktor-auth:$dusseldorfVersion")
    implementation("no.nav.helse:dusseldorf-ktor-core:$dusseldorfVersion")
    implementation("no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("org.json:json:$orgJsonVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresVersion")

    // Test
    testImplementation("io.zonky.test:embedded-postgres:$embeddedPostgres")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:$embeddedPostgresBinaries"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
    testImplementation("no.nav.helse:dusseldorf-test-support:$dusseldorfVersion")
    testImplementation("no.nav.k9.rapid:river-test:$k9rapidVersion")
    testImplementation ("org.skyscreamer:jsonassert:$jsonassertVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
}

repositories {
    mavenLocal()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/k9-rapid")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: "k9-vaktmester"
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
}

tasks {

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        finalizedBy(jacocoTestReport) // report is always generated after tests run
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
        gradleVersion = "8.6"
    }

    withType<JacocoReport> {
        dependsOn(test) // tests are required to run before generating the report
        reports {
            xml.required.set(true)
            csv.required.set(false)
        }
    }
}

sonarqube {
    properties {
        property("sonar.projectKey", "navikt_k9-vaktmester")
        property("sonar.organization", "navikt")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.login", System.getenv("SONAR_TOKEN"))
        property("sonar.sourceEncoding", "UTF-8")
    }
}
