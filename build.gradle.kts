import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.example.mutlabocnotes"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)

    implementation("org.jetbrains.exposed:exposed-core:1.1.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.1.1")
    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("org.flywaydb:flyway-core:10.18.2")
    implementation("org.flywaydb:flyway-database-postgresql:10.18.2")
    implementation("org.jetbrains.exposed:exposed-java-time:1.1.1")

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("com.auth0:java-jwt")
    implementation("org.mindrot:jbcrypt:0.4")

    // Google ID token verification
    implementation("com.google.api-client:google-api-client:2.7.0")
    implementation("com.google.http-client:google-http-client-gson:1.46.3")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("notes-backend-all.jar")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
