package com.personalfinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Arranque de la aplicación.
 *
 * <p>Esta clase es infraestructura y vive en el módulo {@code app} por eso
 * mismo. El dominio no sabe que Spring existe.
 *
 * <p>{@code @ConfigurationPropertiesScan} para que la configuración validada
 * (hoy {@code finance.book-lock}) se ligue y se valide al arrancar: un valor
 * absurdo impide el arranque en vez de aparecer como un error raro la primera
 * vez que dos peticiones coincidan.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
