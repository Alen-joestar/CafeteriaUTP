package com.utp.cafeteria.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "detalles_pedido")
public class DetallePedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;
    @Column(nullable = false)
    private Integer cantidad;
    @Column(nullable = false)
    private Double precioUnitario;
    @Column(nullable = false)
    private Double subtotal;

    public DetallePedido() {
    }

    public DetallePedido(Long id, Long productoId, String nombreProducto, Integer cantidad, Double precioUnitario, Double subtotal) {
        this.id = id;this.producto = productoId == null ? null : new Producto(productoId, nombreProducto, null, precioUnitario, null, true);
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
    }

    public Long getProductoId() {
        return producto == null ? null : producto.getId();
    }

    public void setProductoId(Long productoId) {
        this.producto = productoId == null ? null : new Producto(productoId, null, null, null, null, true);
    }
    @JsonProperty("nombreProducto")
    public String getNombreProducto() { return producto == null ? null : producto.getNombre(); }
    @JsonIgnore
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    @JsonIgnore
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public Double getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(Double precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public Double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(Double subtotal) {
        this.subtotal = subtotal;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
