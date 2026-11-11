package com.utp.cafeteria.model;

public record LoginResponse(String token, String tipo, Long usuarioId, String codigo, String nombreCompleto,
                String rol) {
}
