package com.utp.cafeteria.controller;

public record RegistroRequest(
        String codigo,
        String correo,
        String nombreCompleto,
        String password) {
}
