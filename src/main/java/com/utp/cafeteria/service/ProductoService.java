package com.utp.cafeteria.service;

import com.utp.cafeteria.model.Producto;
import com.utp.cafeteria.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductoService {

    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    public Producto buscarPorId(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<Producto> listar(String categoria, Boolean disponible) {
        return repository.buscar(categoria, disponible);
    }

    public Producto guardar(Producto producto) {
        validar(producto);
        producto.setId(null);
        if (producto.getDisponible() == null) {
            producto.setDisponible(true);
        }
        return repository.save(producto);
    }

    public Producto actualizar(Long id, Producto datos) {
        Producto existente = buscarPorId(id);
        if (existente == null) {
            return null;
        }
        validar(datos);
        existente.setNombre(datos.getNombre());
        existente.setDescripcion(datos.getDescripcion());
        existente.setPrecio(datos.getPrecio());
        existente.setCategoria(datos.getCategoria());
        if (datos.getDisponible() != null) existente.setDisponible(datos.getDisponible());
        return repository.save(existente);
    }

    public boolean eliminar(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    private void validar(Producto producto) {
        if (producto.getNombre() == null || producto.getNombre().isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
        if (producto.getCategoria() == null || producto.getCategoria().isBlank()) throw new IllegalArgumentException("La categoría es obligatoria");
        if (producto.getPrecio() == null || producto.getPrecio() <= 0) throw new IllegalArgumentException("El precio debe ser mayor a cero");
    }
}
