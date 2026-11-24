package com.utp.cafeteria.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.utp.cafeteria.model.Usuario;
import com.utp.cafeteria.repository.UsuarioRepository;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    public Usuario buscarPorId(Long id) { return usuarioRepository.findById(id).orElse(null); }

    public Usuario registrar(Usuario usuario) {
        validarDatos(usuario);
        usuario.setId(null);
        if (usuario.getRol() == null || !List.of("ESTUDIANTE", "CAFETERIA").contains(usuario.getRol().toUpperCase(Locale.ROOT)))
            throw new IllegalArgumentException("El rol debe ser ESTUDIANTE o CAFETERIA");
        usuario.setRol(usuario.getRol().toUpperCase(Locale.ROOT));
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    public Usuario actualizar(Long id, Usuario datos) {
        Usuario existente = buscarPorId(id);
        if (existente == null) return null;
        if (datos.getCodigo() == null || datos.getCodigo().isBlank() || datos.getCorreo() == null || datos.getCorreo().isBlank()
                || datos.getNombreCompleto() == null || datos.getNombreCompleto().isBlank())
            throw new IllegalArgumentException("Código, correo y nombre son obligatorios");
        if ("ADMIN".equalsIgnoreCase(existente.getCodigo())) {
            if (!"ADMIN".equalsIgnoreCase(datos.getCodigo())) {
                throw new IllegalArgumentException("No se puede cambiar el código de la cuenta principal ADMIN");
            }
            if (datos.getRol() != null && !"CAFETERIA".equalsIgnoreCase(datos.getRol())) {
                throw new IllegalArgumentException("No se puede degradar el rol de la cuenta principal ADMIN");
            }
        }
        existente.setCodigo(datos.getCodigo());
        existente.setCorreo(datos.getCorreo());
        existente.setNombreCompleto(datos.getNombreCompleto());
        if (datos.getRol() != null) {
            if (!List.of("ESTUDIANTE", "CAFETERIA").contains(datos.getRol().toUpperCase(Locale.ROOT)))
                throw new IllegalArgumentException("El rol debe ser ESTUDIANTE o CAFETERIA");
            existente.setRol(datos.getRol().toUpperCase(Locale.ROOT));
        }
        if (datos.getPassword() != null && !datos.getPassword().isBlank()) {
            if (datos.getPassword().length() < 8) throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
            existente.setPassword(passwordEncoder.encode(datos.getPassword()));
        }
        return usuarioRepository.save(existente);
    }

    public boolean eliminar(Long id) {
        Usuario existente = buscarPorId(id);
        if (existente == null) return false;
        if ("ADMIN".equalsIgnoreCase(existente.getCodigo())) {
            throw new IllegalArgumentException("Por seguridad, no se puede eliminar la cuenta principal ADMIN del sistema");
        }
        usuarioRepository.deleteById(id);
        return true;
    }

    private void validarDatos(Usuario usuario) {
        if (usuario.getCodigo() == null || usuario.getCodigo().isBlank() || usuario.getCorreo() == null || usuario.getCorreo().isBlank()
                || usuario.getNombreCompleto() == null || usuario.getNombreCompleto().isBlank())
            throw new IllegalArgumentException("Código, correo y nombre son obligatorios");
        if (usuario.getPassword() == null || usuario.getPassword().length() < 8)
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
    }
}
