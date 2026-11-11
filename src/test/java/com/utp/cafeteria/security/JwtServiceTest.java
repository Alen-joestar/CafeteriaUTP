package com.utp.cafeteria.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "test-secret-key-with-at-least-thirty-two-bytes";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void emiteTokenValidoConUsuarioYRol() {
        String token = jwtService.generarToken("U2400001", "ESTUDIANTE");

        assertTrue(jwtService.esValido(token));
        assertEquals("U2400001", jwtService.extraerUsuario(token));
        assertEquals("ESTUDIANTE", jwtService.extraerRol(token));
    }

    @Test
    void rechazaTokenManipulado() {
        String token = jwtService.generarToken("U2400001", "ESTUDIANTE");
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String manipulado = token.substring(0, signatureStart) + replacement
                + token.substring(signatureStart + 1);

        assertFalse(jwtService.esValido(manipulado));
    }

    @Test
    void rechazaTokenVacio() {
        assertFalse(jwtService.esValido(""));
    }
}
