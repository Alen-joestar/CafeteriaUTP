package com.utp.cafeteria.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.utp.cafeteria.model.Producto;
import com.utp.cafeteria.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {
    @Mock ProductoRepository repository;
    @InjectMocks ProductoService service;

    @Test void filtraProductoEnConsultaDelRepositorio() {
        var producto = new Producto(1L, "Pan con pollo", "Sandwich", 4.0, "SANDWICH", true);
        when(repository.buscar("SANDWICH", true)).thenReturn(List.of(producto));
        assertEquals(List.of(producto), service.listar("SANDWICH", true));
        verify(repository).buscar("SANDWICH", true);
    }

    @Test void actualizaProductoPorId() {
        var existente = new Producto(1L, "Pan", "Viejo", 4.0, "SANDWICH", true);
        var datos = new Producto(null, "Pan especial", "Nuevo", 5.0, "SANDWICH", false);
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(existente)).thenReturn(existente);
        assertEquals("Pan especial", service.actualizar(1L, datos).getNombre());
        assertFalse(existente.getDisponible());
    }

    @Test void registraProductoValidoYLoMarcaDisponiblePorDefecto() {
        var producto = new Producto(null, "Jugo", "Natural", 6.0, "BEBIDA", null);
        when(repository.save(any(Producto.class))).thenAnswer(call -> call.getArgument(0));

        Producto guardado = service.guardar(producto);

        assertTrue(guardado.getDisponible());
        assertNull(guardado.getId());
        verify(repository).save(producto);
    }

    @Test void rechazaDatosDeProductoInvalidosSinGuardar() {
        var sinNombre = new Producto(null, " ", "", 2.0, "BEBIDA", true);
        var sinCategoria = new Producto(null, "Jugo", "", 2.0, " ", true);
        var precioInvalido = new Producto(null, "Jugo", "", 0.0, "BEBIDA", true);

        assertThrows(IllegalArgumentException.class, () -> service.guardar(sinNombre));
        assertThrows(IllegalArgumentException.class, () -> service.guardar(sinCategoria));
        assertThrows(IllegalArgumentException.class, () -> service.guardar(precioInvalido));
        verify(repository, never()).save(any());
    }

    @Test void devuelveNullAlActualizarUnProductoInexistente() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertNull(service.actualizar(404L,
                new Producto(null, "Jugo", "", 2.0, "BEBIDA", true)));
        verify(repository, never()).save(any());
    }
}
