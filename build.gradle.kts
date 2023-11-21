import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val k9rapidVersion = "1.20231002100147-90c2022"
val flywayVersion = "9.22.3"
val hikariVersion = "5.1.0"
val kotliqueryVersion = "1.9.0"
val postgresVersion = "42.7.0"
val ktorVersion = "2.3.6"
val dusseldorfVersion = "4.1.1"
val vaultJdbcVersion = "1.3.10"
val orgJsonVersion = "20231013"

// Test avhengigheter
val junitJupiterVersion = "5.10.1"
val embeddedPostgres = "2.0.4"
val embeddedPostgresBinaries = "12.9.0"
val mockkVersion = "1.13.8"
val assertjVersion = "3.24.2"
val jsonassertVersion = "1.5.1"

val mainClass = "no.nav.k9.AppKt"

plugins {
    kotlin("jvm") version "1.9.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.sonarqube") version "4.4.1.3373"
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
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
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    //implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresVersion")

    // Test
    testImplementation("io.zonky.test:embedded-postgres:$embeddedPostgres")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:$embeddedPostgresBinaries"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("no.nav.helse:dusseldorf-test-support:$dusseldorfVersion")
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
        gradleVersion = "8.4"
    }

}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        csv.required.set(false)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
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
