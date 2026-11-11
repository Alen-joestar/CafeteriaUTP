package com.utp.cafeteria.model;

public record LoginResponse(
        String token,
        String tipo,
        Long usuarioId,
        String codigo,
        String correo,
        String nombreCompleto,
        String rol) {
}
