// La infraestructura: Spring Boot, JPA, Flyway, Postgres.
//
// Depende del dominio. El dominio no depende de esto, y Gradle lo impone: la
// flecha va en una sola dirección y no hay forma de invertirla por descuido.

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // El BOM de Spring Boot fija las versiones de Spring, JPA, Flyway y el
    // driver de Postgres. Una sola versión que mantener al día para todo eso.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))

    // Testcontainers va aparte: el BOM de Spring Boot 4 ya no gestiona las
    // versiones de org.testcontainers, solo su propio módulo de integración.
    // Sin este BOM, Gradle pide "org.testcontainers:junit-jupiter:" sin versión.
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))

    implementation(project(":domain"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // En Spring Boot 4 las autoconfiguraciones se repartieron en módulos por
    // tecnología: FlywayAutoConfiguration vive en spring-boot-flyway y ya no en
    // spring-boot-autoconfigure. Con solo flyway-core en el classpath, Flyway
    // queda en el proyecto pero nadie lo arranca: la base queda vacía y la
    // aplicación levanta igual, sin una sola línea de log que lo delate.
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    // Flyway 10 sacó el soporte por motor a artefactos propios; Postgres no
    // viene en el core.
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
