package com.personalfinance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La arquitectura, hecha cumplir en vez de documentada.
 *
 * <p>Entre módulos ya la impone Gradle: el dominio no tiene Spring ni JPA en su
 * classpath, así que escribir {@code @Entity} sobre {@code Transaction} no
 * compila. Pero {@code application} e {@code infrastructure} viven en el mismo
 * módulo y comparten classpath, y ahí la única forma de que la regla no dependa
 * de que alguien se acuerde es un test.
 *
 * <p>La flecha va en una sola dirección: los casos de uso conocen el dominio y
 * sus propios puertos, y la infraestructura implementa esos puertos. Si se
 * invirtiera, un caso de uso podría pedirle algo a JPA directamente y las
 * reglas volverían a estar repartidas entre las dos capas.
 */
@DisplayName("La arquitectura")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionCode() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.personalfinance");
    }

    @Test
    @DisplayName("A1 · la capa de casos de uso no conoce la infraestructura")
    void applicationDoesNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("com.personalfinance.application..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.personalfinance.infrastructure..")
                .because("los casos de uso hablan con el dominio y con sus puertos; "
                        + "quien implementa esos puertos es la infraestructura, y la "
                        + "flecha no se invierte")
                .check(classes);
    }

    @Test
    @DisplayName("A2 · y el dominio no conoce a ninguna de las dos")
    void theDomainDependsOnNobody() {
        noClasses()
                .that().resideInAPackage("com.personalfinance.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.personalfinance.application..",
                        "com.personalfinance.infrastructure..",
                        "org.springframework..",
                        "jakarta.persistence..")
                .because("el dominio es Java puro y se prueba en milisegundos; "
                        + "Gradle ya lo impone, esto lo deja dicho")
                .check(classes);
    }
}
