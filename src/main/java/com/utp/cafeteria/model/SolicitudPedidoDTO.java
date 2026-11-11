package com.utp.cafeteria.model;

import java.util.List;

public class SolicitudPedidoDTO {
    private Long clienteId;
    private String mensaje;
    private List<ItemPedidoDTO> items;
    private String metodoPago;
    private String referenciaPago;
    private String estado;

    public SolicitudPedidoDTO() {
    }

    public SolicitudPedidoDTO(Long clienteId, String mensaje, List<ItemPedidoDTO> items, String metodoPago,
            String referenciaPago) {
        this.clienteId = clienteId;
        this.mensaje = mensaje;
        this.items = items;
        this.metodoPago = metodoPago;
        this.referenciaPago = referenciaPago;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public List<ItemPedidoDTO> getItems() {
        return items;
    }

    public void setItems(List<ItemPedidoDTO> items) {
        this.items = items;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getReferenciaPago() {
        return referenciaPago;
    }

    public void setReferenciaPago(String referenciaPago) {
        this.referenciaPago = referenciaPago;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
