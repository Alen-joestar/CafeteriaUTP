package com.utp.cafeteria.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedidos")
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;
    private String mensaje;
    @Column(nullable = false)
    private Double total;
    @Column(nullable = false, length = 30)
    private String estado;
    @Column(nullable = false)
    private String fechaRegistro;
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> items = new ArrayList<>();
    @Column(nullable = false, length = 30)
    private String metodoPago;
    @Column(nullable = false, length = 30)
    private String estadoPago;

    public Pedido() {
    }

    public Pedido(Long id, String codigo, Long clienteId, String mensaje, Double total, String estado,
            String fechaRegistro, List<DetallePedido> items) {
        this.id = id;
        this.codigo = codigo;
        this.cliente = clienteId == null ? null : new Usuario(clienteId, null, null, null, null, null);
        this.mensaje = mensaje;
        this.total = total;
        this.estado = estado;
        this.fechaRegistro = fechaRegistro;
        this.items = items;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    @JsonIgnore
    public Usuario getCliente() {
        return cliente;
    }

    public void setCliente(Usuario cliente) {
        this.cliente = cliente;
    }

    @JsonProperty("clienteId")
    public Long getClienteId() {
        return cliente == null ? null : cliente.getId();
    }

    public void setClienteId(Long clienteId) {
        this.cliente = clienteId == null ? null : new Usuario(clienteId, null, null, null, null, null);
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(String fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public List<DetallePedido> getItems() {
        return items;
    }

    public void setItems(List<DetallePedido> items) {
        if (this.items == null)
            this.items = new ArrayList<>();
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
            this.items.forEach(item -> item.setPedido(this));
        }
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getEstadoPago() {
        return estadoPago;
    }

    public void setEstadoPago(String estadoPago) {
        this.estadoPago = estadoPago;
    }

}
