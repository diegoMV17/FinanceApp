// El núcleo contable. Java y nada más.
//
// Esta lista de dependencias es la arquitectura hecha cumplir por el build: no
// hay Spring, ni JPA, ni driver de base de datos en el classpath, así que
// escribir @Entity sobre Transaction no compila. La regla no depende de que
// alguien se acuerde de respetarla.
//
// Las dependencias de test tampoco son infraestructura: los tests de este
// módulo corren en milisegundos sin levantar nada.

plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("net.jqwik:jqwik:1.9.1")
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
