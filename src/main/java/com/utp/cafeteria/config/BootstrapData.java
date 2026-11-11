package com.utp.cafeteria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.utp.cafeteria.model.Usuario;
import com.utp.cafeteria.repository.UsuarioRepository;

@Component
public class BootstrapData implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public BootstrapData(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-password:}") String adminPassword) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (usuarios.count() > 0) {
            return;
        }
        if (adminPassword == null || adminPassword.length() < 8) {
            throw new IllegalStateException(
                    "Configura CAFETERIA_ADMIN_PASSWORD con al menos 8 caracteres para crear el administrador inicial");
        }

        Usuario admin = new Usuario(null, "ADMIN", "admin@cafeteria.local", "Administrador de Cafetería",
                passwordEncoder.encode(adminPassword), "CAFETERIA");
        usuarios.save(admin);
    }
}
