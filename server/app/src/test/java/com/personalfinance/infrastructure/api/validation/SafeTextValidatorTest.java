package com.personalfinance.infrastructure.api.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * S9. Los invisibles que el dominio no ve.
 *
 * <p>{@code Text.within} rechaza lo que {@code Character.isISOControl}
 * considera de control, que es C0 y C1. Esta clase cubre el resto: lo que es
 * invisible, no es de control, y cambia lo que el dueño lee en pantalla
 * respecto a lo que está guardado.
 */
@DisplayName("El texto que puede entrar")
class SafeTextValidatorTest {

    private final SafeTextValidator validator = new SafeTextValidator();

    @ParameterizedTest
    @DisplayName("S9 · acepta el español de verdad")
    @ValueSource(strings = {
        "Almuerzo",
        "Alimentación",
        "Mamá",
        "Préstamo a Juan — comida",
        "Café ☕ con el hermano",
        "Saldos iniciales (2026)",
        "",
        "   ",
    })
    void acceptsRealSpanish(String text) {
        // Las tildes, las eñes, los guiones largos y hasta un emoji son texto
        // normal de este libro. Rechazarlos seria un filtro inservible.
        assertThat(validator.isValid(text, null)).isTrue();
    }

    @Test
    @DisplayName("S9 · lo que no viene no se juzga")
    void nullIsSomebodyElsesProblem() {
        // Que un campo falte lo dice @NotNull. Si esto protestara, un campo
        // opcional no podria existir.
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("S9 · rechaza los controles de dirección")
    @ValueSource(strings = {
        "Almuerzo‮",
        "‮Almuerzo",
        "Alm‭uerzo",
        "Almuerzo⁦raro⁩",
        "Almuerzo‏",
        "Almuerzo‎",
    })
    void rejectsBidiControls(String text) {
        // Un U+202E da la vuelta a lo que sigue: la descripción que el dueño
        // lee en el chat no es la que está en la base, y eso es exactamente lo
        // que no puede pasar en un libro de contabilidad.
        assertThat(validator.isValid(text, null)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("S9 · rechaza los separadores y los anchos cero")
    @ValueSource(strings = {
        "Almuerzo con salto",
        "Almuerzo con parrafo",
        "Almu​erzo",
        "﻿Almuerzo",
        "Almuerzo­",
    })
    void rejectsSeparatorsAndZeroWidths(String text) {
        assertThat(validator.isValid(text, null)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("S9 · y sigue rechazando lo que el dominio ya rechazaba")
    @ValueSource(strings = {
        "Almuerzo\n",
        "Almuerzo\t",
        "Almuerzo\u0000",
        "Almuerzo\u0007",
        "Almuerzo\u009F",
    })
    void stillRejectsWhatTheDomainRejects(String text) {
        // El borde no se fía de que el dominio lo pille: el dominio es la
        // ultima linea de defensa, no la unica.
        assertThat(validator.isValid(text, null)).isFalse();
    }
}
