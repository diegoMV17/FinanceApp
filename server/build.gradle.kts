// Raíz: solo lo común. No hay dependencias aquí a propósito.
//
// Lo que cada módulo puede usar se declara en su propio build, y es esa
// separación la que garantiza que el dominio no pueda importar Spring: no está
// en su classpath y Gradle no lo pone ahí por descuido.

allprojects {
    group = "com.personalfinance"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
