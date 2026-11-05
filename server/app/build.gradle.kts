// La infraestructura: Spring Boot, JPA, Flyway, Postgres.
//
// Depende del dominio. El dominio no depende de esto, y Gradle lo impone: la
// flecha va en una sola dirección y no hay forma de invertirla por descuido.

plugins {
    java
    id("org.springframework.boot") version "4.1.2"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // El BOM de Spring Boot fija las versiones de Spring, JPA, Flyway y el
    // driver de Postgres. Una sola versión que mantener al día para todo eso.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))

    // Testcontainers va aparte: el BOM de Spring Boot 4 ya no gestiona las
    // versiones de org.testcontainers, solo su propio módulo de integración.
    // Sin este BOM, Gradle pide "org.testcontainers:junit-jupiter:" sin versión.
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))

    implementation(project(":domain"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // La puerta HTTP. En Spring Boot 4 las autoconfiguraciones viven en módulos
    // por tecnología (la lección de spring-boot-flyway), así que el starter es
    // lo que garantiza que llegue la autoconfiguración y no solo la librería:
    // con la librería sola, nadie levanta el servidor y no hay log que lo diga.
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Validación en el borde. Los límites de verdad siguen viviendo en los
    // constructores del dominio; esto rechaza antes lo que el dominio no puede
    // ver (un UUID mal formado, un tipo desconocido, un campo obligatorio que
    // falta) y referencia las constantes del dominio en vez de repetir números.
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Security, por una razón concreta: con anyRequest().authenticated()
    // el "todo exige token" es estructural. Un filtro propio sobre /* también
    // funcionaría, pero dependería de que nadie registre mañana una ruta por
    // fuera, y esa es exactamente la clase de regla que no debe depender de la
    // memoria de nadie.
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Jackson, solo para armar el JSON de before/after de la auditoría. En
    // Spring Boot 4 el starter trae Jackson 3 (paquete tools.jackson), que es
    // inmutable y se configura con un builder.
    implementation("org.springframework.boot:spring-boot-starter-json")

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

    // springdoc, solo para escribir ../contract/openapi.json desde un test
    // (ContractTest). La 3.x es la línea de Spring Boot 4: la 2.x es de Boot 3 y
    // no arranca aquí. El starter -api, sin interfaz: nunca el -ui. Y sus rutas
    // quedan apagadas en application.yml, porque una descripción completa de la
    // API es una herramienta de administración.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0")

    // La arquitectura, probada: application no puede importar infrastructure.
    // Gradle ya impide que el dominio vea Spring porque son módulos distintos,
    // pero application e infrastructure viven en el mismo módulo y ahí la única
    // forma de que la regla no dependa de la memoria de nadie es un test.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // MockMvc no viene en spring-boot-starter-test. En Boot 4 se mudó a su
    // propio módulo y @AutoConfigureMockMvc ya no está en
    // spring-boot-test-autoconfigure (ese jar tiene 37 entradas y ninguna lo
    // menciona): sin este starter, la anotación no existe ni para importarla.
    // Es la misma leccion de spring-boot-flyway, en los tests.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Un nombre fijo, sin la versión: el Dockerfile lo copia por nombre, y un
// comodín se llevaría también el jar "-plain" que Gradle deja al lado.
tasks.bootJar {
    archiveFileName = "finance-api.jar"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }

    // LeastPrivilegeRolesTest prueba el script de roles del despliegue, que
    // vive fuera de server/. Sin declararlo, Gradle no lo cuenta como entrada:
    // un cambio solo en el script deja la tarea "al día" y el test no corre.
    // Lo descubrió una prueba de mutación que salía verde en un segundo.
    inputs.file(rootProject.layout.projectDirectory.file("../deploy/postgres/10-roles.sh"))
        .withPropertyName("rolesScript")
        .withPathSensitivity(PathSensitivity.NONE)

    // Y ContractTest compara la especificación con ../contract/openapi.json.
    // `files` y no `file`: el archivo puede no existir todavía, y entonces el
    // test lo escribe y falla.
    inputs.files(rootProject.layout.projectDirectory.file("../contract/openapi.json"))
        .withPropertyName("contract")
        .withPathSensitivity(PathSensitivity.NONE)
}
