package com.utp.cafeteria.service;

import com.utp.cafeteria.model.ActualizarEstadoPedidoDTO;
import com.utp.cafeteria.model.DetallePedido;
import com.utp.cafeteria.model.ItemPedidoDTO;
import com.utp.cafeteria.model.Pedido;
import com.utp.cafeteria.model.Producto;
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
    private static final List<String> ESTADOS_VALIDOS = List.of(
            "NUEVO", "EN_PREPARACION", "LISTO", "ENTREGADO", "CANCELADO");
    private static final List<String> ESTADOS_PAGO_VALIDOS = List.of(
            "PENDIENTE", "PAGADO", "RECHAZADO");

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
        List<DetallePedido> detalles = resolverItemsYDescontarStock(solicitud.getItems(), true);
        double total = detalles.stream().mapToDouble(DetallePedido::getSubtotal).sum();

        Pedido pedido = new Pedido();
        pedido.setCodigo("CAF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        pedido.setCliente(cliente);
        pedido.setMensaje(solicitud.getMensaje());
        pedido.setItems(detalles);
        pedido.setTotal(total);
        pedido.setEstado("NUEVO");
        pedido.setMetodoPago(metodo);
        pedido.setReferenciaPago(solicitud.getReferenciaPago());
        pedido.setEstadoPago(pasarelaPago.procesar(metodo, solicitud.getReferenciaPago(), total));
        pedido.setFechaRegistro(LocalDateTime.now().format(FECHA));
        return pedidos.save(pedido);
    }

    public Pedido actualizar(Long id, SolicitudPedidoDTO solicitud) {
        Pedido pedido = pedidos.buscarPorIdCompleto(id).orElse(null);
        if (pedido == null) return null;
        var cliente = usuarios.findById(solicitud.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException("El cliente indicado no existe"));
        List<DetallePedido> detalles = resolverItemsYDescontarStock(solicitud.getItems(), false);
        pedido.setCliente(cliente);
        pedido.setMensaje(solicitud.getMensaje());
        pedido.setItems(detalles);
        pedido.setTotal(detalles.stream().mapToDouble(DetallePedido::getSubtotal).sum());
        if (solicitud.getEstado() != null) {
            pedido.setEstado(validarEstado(solicitud.getEstado()));
        }
        return pedidos.save(pedido);
    }

    public Pedido actualizarEstado(Long id, ActualizarEstadoPedidoDTO dto, boolean esCafeteria, Long usuarioAutenticadoId) {
        Pedido pedido = pedidos.buscarPorIdCompleto(id).orElse(null);
        if (pedido == null) return null;

        if (!esCafeteria) {
            if (!usuarioAutenticadoId.equals(pedido.getClienteId())) {
                return null;
            }
            String estadoSolicitado = dto.estado() == null ? "" : dto.estado().trim().toUpperCase(Locale.ROOT);
            if (!"CANCELADO".equals(estadoSolicitado)) {
                throw new IllegalArgumentException("El estudiante solo puede cancelar su pedido");
            }
            if (!"NUEVO".equalsIgnoreCase(pedido.getEstado())) {
                throw new IllegalArgumentException("Solo se pueden cancelar pedidos que aún estén en estado NUEVO");
            }
            devolverStock(pedido);
            pedido.setEstado("CANCELADO");
            return pedidos.save(pedido);
        }

        if (dto.estado() != null && !dto.estado().isBlank()) {
            String nuevoEstado = validarEstado(dto.estado());
            if ("CANCELADO".equals(nuevoEstado) && !"CANCELADO".equalsIgnoreCase(pedido.getEstado())) {
                devolverStock(pedido);
            }
            pedido.setEstado(nuevoEstado);
        }

        if (dto.estadoPago() != null && !dto.estadoPago().isBlank()) {
            String nuevoEstadoPago = dto.estadoPago().trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS_PAGO_VALIDOS.contains(nuevoEstadoPago)) {
                throw new IllegalArgumentException("Estado de pago no soportado: PENDIENTE, PAGADO o RECHAZADO");
            }
            pedido.setEstadoPago(nuevoEstadoPago);
        }

        return pedidos.save(pedido);
    }

    private String validarEstado(String estadoRaw) {
        String estado = estadoRaw.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS_VALIDOS.contains(estado)) {
            throw new IllegalArgumentException(
                    "Estado no soportado: NUEVO, EN_PREPARACION, LISTO, ENTREGADO o CANCELADO");
        }
        return estado;
    }

    private void devolverStock(Pedido pedido) {
        if (pedido.getItems() == null) return;
        for (DetallePedido item : pedido.getItems()) {
            Producto prod = item.getProducto();
            if (prod != null && prod.getStock() != null) {
                prod.setStock(prod.getStock() + item.getCantidad());
                if (prod.getStock() > 0) {
                    prod.setDisponible(true);
                }
                productos.save(prod);
            }
        }
    }

    private List<DetallePedido> resolverItemsYDescontarStock(List<ItemPedidoDTO> items, boolean descontarStock) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("El pedido debe contener al menos un producto");
        List<DetallePedido> detalles = new ArrayList<>();
        for (ItemPedidoDTO item : items) {
            if (item.productoId() == null || item.cantidad() == null || item.cantidad() < 1)
                throw new IllegalArgumentException("Cada producto requiere productoId y cantidad mayor a cero");
            var producto = productos.findById(item.productoId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + item.productoId()));
            if (!Boolean.TRUE.equals(producto.getDisponible()))
                throw new IllegalArgumentException("Producto no disponible: " + item.productoId());
            if (descontarStock && producto.getStock() != null) {
                if (producto.getStock() < item.cantidad()) {
                    throw new IllegalArgumentException("Stock insuficiente para " + producto.getNombre()
                            + " (disponible: " + producto.getStock() + ")");
                }
                int nuevoStock = producto.getStock() - item.cantidad();
                producto.setStock(nuevoStock);
                if (nuevoStock == 0) {
                    producto.setDisponible(false);
                }
                productos.save(producto);
            }
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
