package com.utp.cafeteria.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final String secreto;
    private static final long EXPIRACION_MS = 1000 * 60 * 60; // 1 hora

    public JwtService(@Value("${app.jwt.secret}") String secreto) {
        this.secreto = secreto;
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secreto.getBytes());
    }

    public String generarToken(String usuario, String rol) {
        return Jwts.builder()
                .subject(usuario)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRACION_MS))
                .signWith(getKey())
                .compact();
    }

    public String extraerUsuario(String token) {
        return obtenerClaims(token).getSubject();
    }

    public String extraerRol(String token) {
        return obtenerClaims(token).get("rol", String.class);
    }

    public boolean esValido(String token) {
        try {
            obtenerClaims(token); 
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims obtenerClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
