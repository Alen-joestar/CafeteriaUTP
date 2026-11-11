package com.utp.cafeteria.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.utp.cafeteria.model.ActualizarEstadoPedidoDTO;
import com.utp.cafeteria.model.ItemPedidoDTO;
import com.utp.cafeteria.model.Pedido;
import com.utp.cafeteria.model.Producto;
import com.utp.cafeteria.model.SolicitudPedidoDTO;
import com.utp.cafeteria.model.Usuario;
import com.utp.cafeteria.repository.PedidoRepository;
import com.utp.cafeteria.repository.ProductoRepository;
import com.utp.cafeteria.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    @Mock PedidoRepository pedidos;
    @Mock ProductoRepository productos;
    @Mock UsuarioRepository usuarios;
    @Mock PasarelaPago pasarela;
    private PedidoService service;
    private Usuario usuario;
    private Producto producto1;
    private Producto producto3;

    @BeforeEach void setUp() {
        service = new PedidoService(pedidos, productos, usuarios, pasarela);
        usuario = new Usuario(3L, "U2303001", "carlos@utp.edu.pe", "Carlos", "hash", "ESTUDIANTE");
        producto1 = new Producto(1L, "Pan con pollo", "", 4.0, "SANDWICH", true);
        producto3 = new Producto(3L, "Hamburguesa", "", 7.0, "SANDWICH", true);
    }

    @Test void creaPedidoResolviendoProductosYCalculandoTotal() {
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        when(productos.findById(1L)).thenReturn(Optional.of(producto1));
        when(productos.findById(3L)).thenReturn(Optional.of(producto3));
        when(pasarela.procesar("YAPE", "token", 15.0)).thenReturn("PENDIENTE");
        when(pedidos.save(any(Pedido.class))).thenAnswer(call -> call.getArgument(0));
        var request = new SolicitudPedidoDTO(3L, "Sin cebolla", List.of(new ItemPedidoDTO(1L, 2), new ItemPedidoDTO(3L, 1)), "YAPE", "token");

        Pedido result = service.crearPedido(request);

        assertEquals(15.0, result.getTotal());
        assertEquals("YAPE", result.getMetodoPago());
        assertEquals("PENDIENTE", result.getEstadoPago());
        assertEquals("Pan con pollo", result.getItems().get(0).getNombreProducto());
        assertEquals(8.0, result.getItems().get(0).getSubtotal());
    }

    @Test void rechazaProductoNoExistente() {
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        when(productos.findById(999L)).thenReturn(Optional.empty());
        var request = new SolicitudPedidoDTO(3L, null, List.of(new ItemPedidoDTO(999L, 1)), "PLIN", null);
        assertThrows(IllegalArgumentException.class, () -> service.crearPedido(request));
        verify(pedidos, never()).save(any());
    }

    @Test void rechazaProductoNoDisponible() {
        producto1.setDisponible(false);
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        when(productos.findById(1L)).thenReturn(Optional.of(producto1));
        var request = solicitud(1L, 1, "PLIN");

        assertThrows(IllegalArgumentException.class, () -> service.crearPedido(request));
        verify(pedidos, never()).save(any());
        verifyNoInteractions(pasarela);
    }

    @Test void rechazaPedidoSinItemsOCantidadValida() {
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        var sinItems = new SolicitudPedidoDTO(3L, null, List.of(), "PLIN", null);
        var cantidadCero = solicitud(1L, 0, "PLIN");

        assertThrows(IllegalArgumentException.class, () -> service.crearPedido(sinItems));
        assertThrows(IllegalArgumentException.class, () -> service.crearPedido(cantidadCero));
        verify(pedidos, never()).save(any());
        verifyNoInteractions(pasarela);
    }

    @Test void rechazaMetodoDePagoNoSoportadoYClienteInexistente() {
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarios.findById(99L)).thenReturn(Optional.empty());
        var metodoInvalido = solicitud(1L, 1, "EFECTIVO");
        var clienteInexistente = new SolicitudPedidoDTO(99L, null,
                List.of(new ItemPedidoDTO(1L, 1)), "PLIN", null);

        assertThrows(IllegalArgumentException.class, () -> service.crearPedido(metodoInvalido));
        assertThrows(IllegalArgumentException.class, () -> service.crearPedido(clienteInexistente));
        verify(pedidos, never()).save(any());
        verifyNoInteractions(pasarela);
    }

    @Test void enlazaDetalleConPedidoAlCrear() {
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        when(productos.findById(1L)).thenReturn(Optional.of(producto1));
        when(pasarela.procesar("YAPE", null, 4.0)).thenReturn("PENDIENTE");
        when(pedidos.save(any(Pedido.class))).thenAnswer(call -> call.getArgument(0));

        Pedido result = service.crearPedido(solicitud(1L, 1, "YAPE"));

        assertEquals(result, result.getItems().get(0).getPedido());
        assertEquals(producto1, result.getItems().get(0).getProducto());
    }

    @Test void descuentaStockYReponeAlCancelarPedido() {
        producto1.setStock(2);
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        when(productos.findById(1L)).thenReturn(Optional.of(producto1));
        when(pasarela.procesar("YAPE", null, 8.0)).thenReturn("PENDIENTE");
        when(pedidos.save(any(Pedido.class))).thenAnswer(call -> call.getArgument(0));

        Pedido creado = service.crearPedido(solicitud(1L, 2, "YAPE"));
        creado.setId(10L);
        assertEquals(0, producto1.getStock());
        assertFalse(producto1.getDisponible());

        when(pedidos.buscarPorIdCompleto(10L)).thenReturn(Optional.of(creado));
        Pedido cancelado = service.actualizarEstado(10L, new ActualizarEstadoPedidoDTO("CANCELADO", null), false, 3L);

        assertEquals("CANCELADO", cancelado.getEstado());
        assertEquals(2, producto1.getStock());
        assertTrue(producto1.getDisponible());
    }

    private SolicitudPedidoDTO solicitud(Long productoId, Integer cantidad, String metodo) {
        return new SolicitudPedidoDTO(3L, null, List.of(new ItemPedidoDTO(productoId, cantidad)), metodo, null);
    }
}
