package com.utp.cafeteria.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.utp.cafeteria.model.Usuario;
import com.utp.cafeteria.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock UsuarioRepository repository;
    @Mock PasswordEncoder encoder;
    @InjectMocks UsuarioService service;

    @Test void registraUsuarioConClaveHasheada() {
        var usuario = new Usuario(null, "U2400001", "user@utp.edu.pe", "Usuario", "Clave12345", "ESTUDIANTE");
        when(encoder.encode("Clave12345")).thenReturn("$2a$hash");
        when(repository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));
        Usuario creado = service.registrar(usuario);
        assertEquals("$2a$hash", creado.getPassword());
        verify(repository).save(usuario);
    }

    @Test void reemplazaClaveSoloCuandoSeEnvía() {
        var existente = new Usuario(4L, "U2400001", "user@utp.edu.pe", "Usuario", "$2a$old", "ESTUDIANTE");
        var cambios = new Usuario(null, "U2400001", "user@utp.edu.pe", "Nombre nuevo", null, "ESTUDIANTE");
        when(repository.findById(4L)).thenReturn(Optional.of(existente));
        when(repository.save(existente)).thenReturn(existente);
        assertEquals("Nombre nuevo", service.actualizar(4L, cambios).getNombreCompleto());
        assertEquals("$2a$old", existente.getPassword());
        verify(encoder, never()).encode(any());
    }

    @Test void normalizaRolYEliminaIdRecibidoAlRegistrar() {
        var usuario = new Usuario(88L, "U2400002", "otro@utp.edu.pe", "Otro Usuario", "Clave12345", "estudiante");
        when(encoder.encode("Clave12345")).thenReturn("hash-seguro");
        when(repository.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));

        Usuario creado = service.registrar(usuario);

        assertNull(creado.getId());
        assertEquals("ESTUDIANTE", creado.getRol());
        assertEquals("hash-seguro", creado.getPassword());
        verify(repository).save(usuario);
    }

    @Test void rechazaUsuarioIncompletoConClaveCortaORolNoPermitido() {
        var sinCodigo = new Usuario(null, " ", "correo@test.local", "Nombre", "Clave12345", "ESTUDIANTE");
        var claveCorta = new Usuario(null, "U2400003", "corto@test.local", "Nombre", "123", "ESTUDIANTE");
        var rolInvalido = new Usuario(null, "U2400004", "rol@test.local", "Nombre", "Clave12345", "ADMIN");

        assertThrows(IllegalArgumentException.class, () -> service.registrar(sinCodigo));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(claveCorta));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(rolInvalido));
        verify(repository, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test void actualizarInexistenteNoGuarda() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertNull(service.actualizar(404L,
                new Usuario(null, "U", "u@test.local", "Usuario", null, "ESTUDIANTE")));
        verify(repository, never()).save(any());
    }
}
