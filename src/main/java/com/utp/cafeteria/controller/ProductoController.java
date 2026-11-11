package com.utp.cafeteria.controller;

import com.utp.cafeteria.model.Producto;
import com.utp.cafeteria.service.ProductoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Producto> listar(@RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean disponible) {
        return service.listar(categoria, disponible);
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<Producto> buscarPorId(@PathVariable Long id) {
        Producto producto = service.buscarPorId(id);
        if (producto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(producto);
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody Producto producto) {
        Producto creado = service.guardar(producto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<Producto> actualizar(@PathVariable Long id, @RequestBody Producto datos) {
        Producto actualizado = service.actualizar(id, datos);
        if (actualizado == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id:[0-9]+}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        boolean eliminado = service.eliminar(id);
        if (!eliminado) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
