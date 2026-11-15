
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    kotlin("plugin.serialization") version "2.4.20"
}

group = "com.anasdev"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.statusPages)
    implementation(libs.logback.classic)
    val exposedVersion = "0.56.0"

// 1. JetBrains Exposed (Core, JDBC, and Java Date/Time support for your release_date & timestamps)
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

// Password hashing (BCrypt)
    implementation("org.mindrot:jbcrypt:0.4")

    implementation(ktorLibs.serialization.kotlinx.json)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")


// 2. PostgreSQL Official JDBC Driver (Talks to your local PostgreSQL server)
    implementation("org.postgresql:postgresql:42.7.14")

// 3. HikariCP (Industry-standard high-speed connection pool)
    implementation("com.zaxxer:HikariCP:6.2.1")

    implementation("com.google.api-client:google-api-client:2.9.2")

    implementation("net.coobird:thumbnailator:0.4.20")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
