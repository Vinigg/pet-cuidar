package com.petcuidar;

import org.junit.jupiter.api.Test;
import org.postgresql.Driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverPostgresqlTest {

    @Test
    void reconheceUrlPostgresqlComVerificacaoTls() throws Exception {
        String url = "jdbc:postgresql://db.example.test:5432/petcuidar"
                + "?sslmode=verify-full&sslrootcert=/run/certs/rds-ca.pem";

        assertTrue(new Driver().acceptsURL(url));
        var parametros = Driver.parseURL(url, null);
        assertNotNull(parametros);
        assertEquals("petcuidar", parametros.getProperty("PGDBNAME"));
        assertEquals("verify-full", parametros.getProperty("sslmode"));
        assertEquals("/run/certs/rds-ca.pem", parametros.getProperty("sslrootcert"));
    }
}
