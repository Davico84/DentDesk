package com.odontorisas.health;

import com.odontorisas.AbstractIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Scenarios del pool que deja dormir a la base (add-cold-start-warmup D3). El retiro de
 * conexiones se prueba con {@code idle-timeout} de 10 s (el mínimo de Hikari) solo aquí; la
 * espera llega a 90 s porque la revisión periódica de Hikari corre cada 30 s.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "DB_POOL_MIN_IDLE=0", "DB_POOL_MAX_SIZE=5", "spring.datasource.hikari.idle-timeout=10000"})
class DataSourcePoolIT extends AbstractIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void pool_keeps_no_minimum_and_no_keepalive() {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getMinimumIdle()).isZero();
        assertThat(hikari.getKeepaliveTime()).isZero();
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
    }

    /** Scenarios "Retiro de conexiones ociosas" y "La base responde tras quedar sin conexiones". */
    @Test
    void idle_connections_are_retired_and_the_next_query_reconnects() throws Exception {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        List<Connection> opened = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            opened.add(hikari.getConnection());
        }
        for (Connection c : opened) {
            c.close();
        }
        assertThat(hikari.getHikariPoolMXBean().getTotalConnections()).isPositive();

        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(90))
            .until(() -> hikari.getHikariPoolMXBean().getTotalConnections() == 0);

        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }
}
