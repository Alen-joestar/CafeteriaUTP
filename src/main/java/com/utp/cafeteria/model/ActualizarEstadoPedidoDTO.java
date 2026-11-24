package com.utp.cafeteria.model;

public record ActualizarEstadoPedidoDTO(
        String estado,
        String estadoPago,
        String motivoCancelacion) {

    public ActualizarEstadoPedidoDTO(String estado, String estadoPago) {
        this(estado, estadoPago, null);
    }
}
