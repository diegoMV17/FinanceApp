package com.personalfinance.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * S14. Dónde escucha la API si nadie dice nada.
 *
 * <p>El cifrado lo pondrá Tailscale cuando esto viva en Oracle, pero un bind a
 * {@code 0.0.0.0} por descuido publica la API en toda la red de la máquina, y
 * eso no lo arregla la autenticación: lo arregla no escuchar ahí. Un token se
 * puede filtrar, rotar o adivinar algún día; un puerto que no está abierto no
 * se puede atacar.
 *
 * <p>Por eso el valor por omisión se prueba, en vez de confiarlo a que nadie
 * toque esa línea del {@code application.yml}. Es el tipo de error que no se
 * nota nunca: la aplicación arranca igual, los tests pasan igual, y lo único
 * que cambia es quién puede llegar.
 *
 * <p>Sin Spring y sin Docker, a propósito: los tests de integración se saltan
 * donde no hay Docker, y este no puede ser uno de los que se salten. El
 * resolvedor se arma con <strong>solo</strong> el {@code application.yml} y sin
 * el entorno de la máquina, que es justo lo que significa «sin la variable
 * {@code SERVER_ADDRESS} definida»: con el entorno de verdad, el test daría
 * otro resultado en la máquina de quien la tenga puesta.
 */
@DisplayName("Dónde escucha la API")
class ApiBindsToLoopbackTest {

    private static final String LOOPBACK = "127.0.0.1";

    private final EnumerablePropertySource<?> applicationYml = loadApplicationYml();

    @Test
    @DisplayName("S14 · sin SERVER_ADDRESS, solo en loopback")
    void theDefaultAddressIsLoopback() {
        assertThat(resolved("server.address"))
                .as("un bind a 0.0.0.0 publica la API en toda la red de la máquina")
                .isEqualTo(LOOPBACK);
    }

    @Test
    @DisplayName("S14 · y la variable existe para el día del despliegue")
    void theVariableIsStillThereToOverrideIt() {
        // Que el valor por omisión sea seguro no debe significar que esté
        // clavado: el día del despliegue hay que poder decir otra cosa, y que
        // decirlo sea una decisión visible en el docker compose.
        assertThat(applicationYml.getProperty("server.address"))
                .isEqualTo("${SERVER_ADDRESS:" + LOOPBACK + "}");
    }

    @Test
    @DisplayName("S14 · y ninguna otra clave abre el puerto a la red")
    void nothingElseOpensIt() {
        // El valor por omisión de arriba no sirve de nada si otra clave del
        // mismo archivo vuelve a abrir el puerto, así que esto mira el archivo
        // entero y no solo la clave que ya se sabe.
        for (String name : applicationYml.getPropertyNames()) {
            assertThat(String.valueOf(applicationYml.getProperty(name)))
                    .as("%s abre la API a la red", name)
                    .doesNotContain("0.0.0.0")
                    .doesNotContain("[::]");
        }
    }

    /** El valor con los placeholders resueltos contra el archivo y nada más. */
    private String resolved(String name) {
        MutablePropertySources only = new MutablePropertySources();
        only.addFirst(applicationYml);

        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(only);
        resolver.setIgnoreUnresolvableNestedPlaceholders(true);
        return resolver.getProperty(name);
    }

    private static EnumerablePropertySource<?> loadApplicationYml() {
        try {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));

            assertThat(loaded).as("el application.yml se leyó").hasSize(1);
            return (EnumerablePropertySource<?>) loaded.getFirst();
        } catch (IOException unreadable) {
            throw new AssertionError("no se pudo leer application.yml", unreadable);
        }
    }
}
