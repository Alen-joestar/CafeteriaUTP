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

        String ref = solicitud.getReferenciaPago() != null ? solicitud.getReferenciaPago().trim() : null;
        if (ref != null && !ref.isEmpty() && pedidos.existsByReferenciaPagoIgnoreCase(ref)) {
            throw new IllegalArgumentException(
                    "El código de operación '" + ref + "' ya fue utilizado en otro pedido (validación antifraude)");
        }

        boolean seraPagoDirecto = ref != null && ref.toUpperCase(Locale.ROOT).startsWith("UTPPAY-");
        if (!seraPagoDirecto) {
            long pendientesActivos = pedidos.buscar(null, cliente.getId()).stream()
                    .filter(p -> ("NUEVO".equalsIgnoreCase(p.getEstado()) || "EN_PREPARACION".equalsIgnoreCase(p.getEstado()))
                            && "PENDIENTE".equalsIgnoreCase(p.getEstadoPago()))
                    .count();
            if (pendientesActivos >= 2) {
                throw new IllegalArgumentException(
                        "Ya tienes 2 pedidos pendientes de verificación de pago en caja. Espera su validación o paga con QR.");
            }
        }

        List<DetallePedido> detalles = resolverItemsYDescontarStock(solicitud.getItems(), true);
        double montoExtras = (solicitud.getMontoExtras() != null && solicitud.getMontoExtras() > 0)
                ? solicitud.getMontoExtras()
                : 0.0;
        double total = detalles.stream().mapToDouble(DetallePedido::getSubtotal).sum() + montoExtras;

        Pedido pedido = new Pedido();
        pedido.setCodigo("CAF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        pedido.setCliente(cliente);
        pedido.setMensaje(solicitud.getMensaje());
        pedido.setItems(detalles);
        pedido.setTotal(total);
        pedido.setEstado("NUEVO");
        pedido.setMetodoPago(metodo);
        pedido.setReferenciaPago(ref);
        pedido.setEstadoPago(pasarelaPago.procesar(metodo, ref, total));
        pedido.setFechaRegistro(LocalDateTime.now().format(FECHA));
        pedido.setPinEntrega(String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000)));
        String horario = (solicitud.getHorarioRecojo() == null || solicitud.getHorarioRecojo().isBlank())
                ? "INMEDIATO"
                : solicitud.getHorarioRecojo().trim();
        pedido.setHorarioRecojo(horario);
        pedido.setCremasYExtras(solicitud.getCremasYExtras());
        pedido.setMontoExtras(montoExtras);
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
            String motivoEst = (dto.motivoCancelacion() != null && !dto.motivoCancelacion().isBlank())
                    ? dto.motivoCancelacion().trim()
                    : "Cancelado por el estudiante";
            pedido.setMotivoCancelacion(motivoEst);
            return pedidos.save(pedido);
        }

        if (dto.estadoPago() != null && !dto.estadoPago().isBlank()) {
            String nuevoEstadoPago = dto.estadoPago().trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS_PAGO_VALIDOS.contains(nuevoEstadoPago)) {
                throw new IllegalArgumentException("Estado de pago no soportado: PENDIENTE, PAGADO o RECHAZADO");
            }
            if ("CANCELADO".equalsIgnoreCase(pedido.getEstado()) && "PAGADO".equals(nuevoEstadoPago)) {
                throw new IllegalArgumentException("No se puede confirmar el pago de una orden que ya fue CANCELADA");
            }
            pedido.setEstadoPago(nuevoEstadoPago);
            if ("RECHAZADO".equals(nuevoEstadoPago)) {
                if (!"CANCELADO".equalsIgnoreCase(pedido.getEstado())) {
                    devolverStock(pedido);
                }
                pedido.setEstado("CANCELADO");
                String motivoRechazo = (dto.motivoCancelacion() != null && !dto.motivoCancelacion().isBlank())
                        ? dto.motivoCancelacion().trim()
                        : "Pago rechazado en caja: N° de operación no verificado";
                pedido.setMotivoCancelacion(motivoRechazo);
            }
        }

        if (dto.estado() != null && !dto.estado().isBlank()) {
            String nuevoEstado = validarEstado(dto.estado());
            String estadoActual = pedido.getEstado() == null ? "NUEVO" : pedido.getEstado().toUpperCase(Locale.ROOT);

            if ("CANCELADO".equals(estadoActual) && !"CANCELADO".equals(nuevoEstado)) {
                throw new IllegalArgumentException(
                        "Un pedido CANCELADO ya devolvió su stock al inventario y no puede reactivarse");
            }
            if ("ENTREGADO".equals(estadoActual) && !"ENTREGADO".equals(nuevoEstado)) {
                throw new IllegalArgumentException(
                        "Un pedido ENTREGADO ya fue cerrado contablemente y no puede cambiar de estado");
            }

            List<String> flujoProgresivo = List.of("NUEVO", "EN_PREPARACION", "LISTO", "ENTREGADO");
            int idxActual = flujoProgresivo.indexOf(estadoActual);
            int idxNuevo = flujoProgresivo.indexOf(nuevoEstado);
            if (idxActual >= 0 && idxNuevo >= 0 && idxNuevo < idxActual) {
                throw new IllegalArgumentException(
                        "Flujo inválido: no se puede retroceder un pedido de " + estadoActual + " a " + nuevoEstado);
            }

            if (List.of("EN_PREPARACION", "LISTO", "ENTREGADO").contains(nuevoEstado)
                    && !"PAGADO".equalsIgnoreCase(pedido.getEstadoPago())) {
                throw new IllegalArgumentException(
                        "Primero debes confirmar el pago del pedido antes de pasarlo a Cocina o Barra");
            }

            if ("CANCELADO".equals(nuevoEstado) && !"CANCELADO".equalsIgnoreCase(estadoActual)) {
                devolverStock(pedido);
            }
            if ("CANCELADO".equals(nuevoEstado)) {
                if (dto.motivoCancelacion() != null && !dto.motivoCancelacion().isBlank()) {
                    pedido.setMotivoCancelacion(dto.motivoCancelacion().trim());
                } else if (pedido.getMotivoCancelacion() == null || pedido.getMotivoCancelacion().isBlank()) {
                    pedido.setMotivoCancelacion("Cancelado en control de Caja/Cocina");
                }
            }
            pedido.setEstado(nuevoEstado);
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
            if (item.cantidad() > 10)
                throw new IllegalArgumentException("El máximo permitido por plato en un mismo ticket es de 10 unidades");
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

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> arqueoPorFecha(String fecha) {
        Double totalBase = pedidos.recaudarPorFecha(fecha);
        double totalRecaudado = totalBase != null ? totalBase : 0.0;

        List<Pedido> todos = pedidos.buscar(null, null);
        double totalYape = 0.0;
        double totalPlin = 0.0;
        double totalTarjeta = 0.0;
        int cantidadEntregados = 0;
        java.util.Map<String, int[]> conteoPlatos = new java.util.HashMap<>();
        java.util.Map<String, Double> montoPlatos = new java.util.HashMap<>();

        for (Pedido p : todos) {
            if ("ENTREGADO".equalsIgnoreCase(p.getEstado())
                    && p.getFechaRegistro() != null
                    && p.getFechaRegistro().startsWith(fecha)) {
                cantidadEntregados++;
                double monto = p.getTotal() != null ? p.getTotal() : 0.0;
                if ("YAPE".equalsIgnoreCase(p.getMetodoPago())) {
                    totalYape += monto;
                } else if ("PLIN".equalsIgnoreCase(p.getMetodoPago())) {
                    totalPlin += monto;
                } else if ("TARJETA".equalsIgnoreCase(p.getMetodoPago())) {
                    totalTarjeta += monto;
                }
                if (p.getItems() != null) {
                    for (DetallePedido det : p.getItems()) {
                        String nom = det.getNombreProducto() != null ? det.getNombreProducto() : "Plato";
                        int cant = det.getCantidad() != null ? det.getCantidad() : 0;
                        double sub = det.getSubtotal() != null ? det.getSubtotal() : 0.0;
                        conteoPlatos.computeIfAbsent(nom, k -> new int[]{0})[0] += cant;
                        montoPlatos.put(nom, montoPlatos.getOrDefault(nom, 0.0) + sub);
                    }
                }
            }
        }

        List<java.util.Map<String, Object>> topPlatos = conteoPlatos.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(3)
                .map(e -> java.util.Map.<String, Object>of(
                        "nombre", e.getKey(),
                        "cantidad", e.getValue()[0],
                        "recaudado", montoPlatos.getOrDefault(e.getKey(), 0.0)))
                .toList();

        double sumaCalificaciones = 0.0;
        int totalCalificaciones = 0;
        for (Pedido p : todos) {
            if (p.getCalificacion() != null && p.getCalificacion() >= 1 && p.getCalificacion() <= 5) {
                sumaCalificaciones += p.getCalificacion();
                totalCalificaciones++;
            }
        }
        double promedioCalificacion = totalCalificaciones > 0
                ? Math.round((sumaCalificaciones / totalCalificaciones) * 10.0) / 10.0
                : 0.0;

        java.util.Map<String, Object> resultado = new java.util.LinkedHashMap<>();
        resultado.put("fecha", fecha);
        resultado.put("totalRecaudado", totalRecaudado);
        resultado.put("totalYape", totalYape);
        resultado.put("totalPlin", totalPlin);
        resultado.put("totalTarjeta", totalTarjeta);
        resultado.put("cantidadEntregados", cantidadEntregados);
        resultado.put("topPlatos", topPlatos);
        resultado.put("promedioCalificacion", promedioCalificacion);
        resultado.put("totalCalificaciones", totalCalificaciones);
        return resultado;
    }

    public Pedido calificarPedido(Long id, Integer calificacion, Long usuarioAutenticadoId) {
        if (calificacion == null || calificacion < 1 || calificacion > 5) {
            throw new IllegalArgumentException("La calificación debe estar entre 1 y 5 estrellas");
        }
        Pedido pedido = pedidos.buscarPorIdCompleto(id).orElse(null);
        if (pedido == null) return null;
        if (usuarioAutenticadoId != null && !usuarioAutenticadoId.equals(pedido.getClienteId())) {
            throw new IllegalArgumentException("Solo puedes calificar tus propios pedidos");
        }
        if (!"ENTREGADO".equalsIgnoreCase(pedido.getEstado())) {
            throw new IllegalArgumentException("Solo se pueden calificar pedidos que ya fueron ENTREGADOS");
        }
        pedido.setCalificacion(calificacion);
        return pedidos.save(pedido);
    }

    public Pedido validarEntregaPorPinOCodigo(String codigoOPin) {
        if (codigoOPin == null || codigoOPin.isBlank()) {
            throw new IllegalArgumentException("Ingresa el código del boleto (#CAF-XXXX) o el PIN de 4 dígitos");
        }
        String limpio = codigoOPin.trim().replace("#", "").toUpperCase(Locale.ROOT);
        List<Pedido> todos = pedidos.buscar(null, null);
        Pedido encontrado = todos.stream()
                .filter(p -> !"CANCELADO".equalsIgnoreCase(p.getEstado())
                        && !"ENTREGADO".equalsIgnoreCase(p.getEstado())
                        && ((p.getCodigo() != null && p.getCodigo().toUpperCase(Locale.ROOT).contains(limpio))
                                || (p.getPinEntrega() != null && p.getPinEntrega().equalsIgnoreCase(limpio))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró ningún pedido activo pendiente de entrega con el código/PIN: " + codigoOPin));

        if (!"LISTO".equalsIgnoreCase(encontrado.getEstado())) {
            throw new IllegalArgumentException(
                    "El boleto #" + encontrado.getCodigo() + " está en estado " + encontrado.getEstado()
                            + ". Solo se pueden entregar pedidos que ya estén LISTOS en barra.");
        }

        encontrado.setEstado("ENTREGADO");
        encontrado.setEstadoPago("PAGADO");
        return pedidos.save(encontrado);
    }
}
