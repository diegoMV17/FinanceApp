package com.personalfinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Arranque de la aplicación.
 *
 * <p>Esta clase es infraestructura y vive en el módulo {@code app} por eso
 * mismo. El dominio no sabe que Spring existe.
 */
@SpringBootApplication
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
