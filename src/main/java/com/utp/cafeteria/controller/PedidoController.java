package com.utp.cafeteria.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.utp.cafeteria.model.ActualizarEstadoPedidoDTO;
import com.utp.cafeteria.model.Pedido;
import com.utp.cafeteria.model.SolicitudPedidoDTO;
import com.utp.cafeteria.repository.UsuarioRepository;
import com.utp.cafeteria.service.PedidoService;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;
    private final UsuarioRepository usuarios;

    public PedidoController(PedidoService service, UsuarioRepository usuarios) {
        this.service = service;
        this.usuarios = usuarios;
    }

    @GetMapping
    public List<Pedido> listar(@RequestParam(required = false) String estado,
            @RequestParam(required = false) Long clienteId, Authentication authentication) {
        return service.listar(estado, esCafeteria(authentication) ? clienteId : usuarioId(authentication));
    }

    @GetMapping("/recaudacion")
    public ResponseEntity<Map<String, Object>> recaudacion(
            @RequestParam(required = false) String fecha) {
        String fechaConsulta = (fecha == null || fecha.isBlank()) ? LocalDate.now().toString() : fecha.trim();
        Double totalRecaudado = service.recaudacionPorFecha(fechaConsulta);
        return ResponseEntity.ok(Map.of(
                "fecha", fechaConsulta,
                "totalRecaudado", totalRecaudado != null ? totalRecaudado : 0.0));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<Pedido> buscarPorId(@PathVariable Long id, Authentication authentication) {
        Pedido pedido = service.buscarPorId(id);
        if (pedido == null || (!esCafeteria(authentication)
                && !usuarioId(authentication).equals(pedido.getClienteId()))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pedido);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody SolicitudPedidoDTO dto, Authentication authentication) {
        if (!esCafeteria(authentication))
            dto.setClienteId(usuarioId(authentication));
        Pedido creado = service.crearPedido(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PatchMapping("/{id:[0-9]+}/estado")
    public ResponseEntity<Pedido> actualizarEstado(@PathVariable Long id,
            @RequestBody ActualizarEstadoPedidoDTO dto, Authentication authentication) {
        boolean cafeteria = esCafeteria(authentication);
        Long uid = cafeteria ? null : usuarioId(authentication);
        Pedido actualizado = service.actualizarEstado(id, dto, cafeteria, uid);
        if (actualizado == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(actualizado);
    }

    private boolean esCafeteria(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CAFETERIA"));
    }

    private Long usuarioId(Authentication authentication) {
        return usuarios.buscarPorIdentificador(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("El usuario autenticado no existe"))
                .getId();
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody SolicitudPedidoDTO dto) {
        Pedido actualizado = service.actualizar(id, dto);
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
