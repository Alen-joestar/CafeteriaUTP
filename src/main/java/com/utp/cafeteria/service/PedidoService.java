package com.utp.cafeteria.service;

import com.utp.cafeteria.model.DetallePedido;
import com.utp.cafeteria.model.ItemPedidoDTO;
import com.utp.cafeteria.model.Pedido;
import com.utp.cafeteria.model.SolicitudPedidoDTO;
import com.utp.cafeteria.repository.PedidoRepository;
import com.utp.cafeteria.repository.ProductoRepository;
import com.utp.cafeteria.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class PedidoService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final PedidoRepository pedidos;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final PasarelaPago pasarelaPago;

    public PedidoService(PedidoRepository pedidos, ProductoRepository productos, UsuarioRepository usuarios,
            PasarelaPago pasarelaPago) {
        this.pedidos = pedidos;
        this.productos = productos;
        this.usuarios = usuarios;
        this.pasarelaPago = pasarelaPago;
    }

    @Transactional(readOnly = true)
    public List<Pedido> listar(String estado, Long clienteId) {
        return pedidos.buscar(estado, clienteId);
    }

    @Transactional(readOnly = true)
    public Pedido buscarPorId(Long id) {
        return pedidos.buscarPorIdCompleto(id).orElse(null);
    }

    public Pedido crearPedido(SolicitudPedidoDTO solicitud) {
        var cliente = usuarios.findById(solicitud.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException("El cliente indicado no existe"));
        String metodo = normalizarMetodoPago(solicitud.getMetodoPago());
        List<DetallePedido> detalles = resolverItems(solicitud.getItems());
        double total = detalles.stream().mapToDouble(DetallePedido::getSubtotal).sum();

        Pedido pedido = new Pedido();
        pedido.setCodigo("CAF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        pedido.setCliente(cliente);
        pedido.setMensaje(solicitud.getMensaje());
        pedido.setItems(detalles);
        pedido.setTotal(total);
        pedido.setEstado("NUEVO");
        pedido.setMetodoPago(metodo);
        pedido.setEstadoPago(pasarelaPago.procesar(metodo, solicitud.getReferenciaPago(), total));
        pedido.setFechaRegistro(LocalDateTime.now().format(FECHA));
        return pedidos.save(pedido);
    }

    public Pedido actualizar(Long id, SolicitudPedidoDTO solicitud) {
        Pedido pedido = pedidos.buscarPorIdCompleto(id).orElse(null);
        if (pedido == null) return null;
        var cliente = usuarios.findById(solicitud.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException("El cliente indicado no existe"));
        List<DetallePedido> detalles = resolverItems(solicitud.getItems());
        pedido.setCliente(cliente);
        pedido.setMensaje(solicitud.getMensaje());
        pedido.setItems(detalles);
        pedido.setTotal(detalles.stream().mapToDouble(DetallePedido::getSubtotal).sum());
        if (solicitud.getEstado() != null) {
            String estado = solicitud.getEstado().trim().toUpperCase(Locale.ROOT);
            if (!List.of("NUEVO", "LISTO", "ENTREGADO").contains(estado))
                throw new IllegalArgumentException("Estado no soportado: NUEVO, LISTO o ENTREGADO");
            pedido.setEstado(estado);
        }
        return pedidos.save(pedido);
    }

    private List<DetallePedido> resolverItems(List<ItemPedidoDTO> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("El pedido debe contener al menos un producto");
        List<DetallePedido> detalles = new ArrayList<>();
        for (ItemPedidoDTO item : items) {
            if (item.productoId() == null || item.cantidad() == null || item.cantidad() < 1)
                throw new IllegalArgumentException("Cada producto requiere productoId y cantidad mayor a cero");
            var producto = productos.findById(item.productoId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + item.productoId()));
            if (!Boolean.TRUE.equals(producto.getDisponible()))
                throw new IllegalArgumentException("Producto no disponible: " + item.productoId());
            DetallePedido detalle = new DetallePedido();
            detalle.setProducto(producto);
            detalle.setCantidad(item.cantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setSubtotal(producto.getPrecio() * item.cantidad());
            detalles.add(detalle);
        }
        return detalles;
    }

    private String normalizarMetodoPago(String metodo) {
        if (metodo == null) throw new IllegalArgumentException("El método de pago es obligatorio");
        String normalizado = metodo.trim().toUpperCase(Locale.ROOT);
        if (!List.of("TARJETA", "YAPE", "PLIN").contains(normalizado))
            throw new IllegalArgumentException("Método no soportado: TARJETA, YAPE o PLIN");
        return normalizado;
    }

    public boolean eliminar(Long id) {
        if (!pedidos.existsById(id)) return false;
        pedidos.deleteById(id);
        return true;
    }

    @Transactional(readOnly = true)
    public Double recaudacionPorFecha(String fecha) {
        return pedidos.recaudarPorFecha(fecha);
    }
}
