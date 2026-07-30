package com.odontorisas.infra.config;

import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El loader idempotente del catálogo CIE10 (design.md D6, `add-clinical-history`). El propio
 * arranque del contexto ya lo ejecuta una vez (tabla vacía → puebla); aquí se verifica que un
 * <b>segundo</b> arranque lógico (invocar {@code run} de nuevo con la tabla ya poblada) no
 * duplica, y que el parseo de numerales romanos usado para los códigos sintéticos es correcto.
 */
@SpringBootTest
class Cie10CatalogLoaderIT extends AbstractIntegrationTest {

    @Autowired
    private Cie10CatalogLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void the_startup_populates_the_catalog() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM cie10_code", Long.class);
        assertThat(count).isNotNull().isGreaterThan(1000);

        Long chapters = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM cie10_code WHERE nivel = 'CAPITULO'", Long.class);
        assertThat(chapters).isEqualTo(22);
    }

    @Test
    void running_the_loader_again_does_not_duplicate() throws Exception {
        Long before = jdbcTemplate.queryForObject("SELECT count(*) FROM cie10_code", Long.class);

        loader.run();

        Long after = jdbcTemplate.queryForObject("SELECT count(*) FROM cie10_code", Long.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void a_failed_load_rolls_back_without_leaving_duplicates() {
        // Tabla ya poblada (arranque del contexto). Llamar a loadCatalog() directamente (bypass
        // del chequeo "vacía") choca con la PK de los códigos ya existentes: la transacción
        // completa revierte, sin dejar el catálogo a medias ni duplicado (design.md M1).
        Long before = jdbcTemplate.queryForObject("SELECT count(*) FROM cie10_code", Long.class);

        assertThatThrownBy(loader::loadCatalog).isInstanceOf(Exception.class);

        Long after = jdbcTemplate.queryForObject("SELECT count(*) FROM cie10_code", Long.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void chapter_roman_numerals_parse_to_the_expected_number() {
        assertThat(Cie10CatalogLoader.romanChapterNumber("CAPITULO I: ALGO")).isEqualTo(1);
        assertThat(Cie10CatalogLoader.romanChapterNumber("CAPITULO IX: ALGO")).isEqualTo(9);
        assertThat(Cie10CatalogLoader.romanChapterNumber("CAPITULO XIV: ALGO")).isEqualTo(14);
        assertThat(Cie10CatalogLoader.romanChapterNumber("CAPITULO XXII: ALGO")).isEqualTo(22);
    }
}
