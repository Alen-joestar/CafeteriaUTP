package com.utp.cafeteria.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.utp.cafeteria.model.Producto;
import com.utp.cafeteria.model.Usuario;
import com.utp.cafeteria.repository.ProductoRepository;
import com.utp.cafeteria.repository.UsuarioRepository;

@Component
public class BootstrapData implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final boolean seedProductos;

    public BootstrapData(UsuarioRepository usuarios, ProductoRepository productos, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-password:}") String adminPassword,
            @Value("${app.bootstrap.seed-productos:true}") boolean seedProductos) {
        this.usuarios = usuarios;
        this.productos = productos;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
        this.seedProductos = seedProductos;
    }

    @Override
    public void run(String... args) {
        inicializarAdmin();
        if (seedProductos) {
            inicializarProductos();
        }
    }

    private void inicializarAdmin() {
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

    private void inicializarProductos() {
        if (productos.count() > 0) {
            return;
        }
        List<Producto> catalogoInicial = List.of(
                new Producto(null, "Pan con pollo", "Clásico sandwich con pollo deshilachado y cremas", 4.0, "SANDWICH", true),
                new Producto(null, "Pan con pollo al horno", "Sandwich con trozos de pollo al horno jugoso", 4.0, "SANDWICH", true),
                new Producto(null, "Hamburguesa", "Hamburguesa clásica de carne con papas al hilo y cremas", 7.0, "SANDWICH", true),
                new Producto(null, "Chicharrón", "Sandwich de chicharrón con camote y salsa criolla", 8.0, "SANDWICH", true),
                new Producto(null, "Papa rellena", "Papa rellena tradicional de carne con sarsa criolla", 3.5, "ANTOJITOS", true),
                new Producto(null, "Empanada de pollo", "Empanada horneada rellena de pollo", 4.0, "ANTOJITOS", true),
                new Producto(null, "Alitas broaster", "Porción de alitas crocantes acompañadas de papas", 8.0, "ANTOJITOS", true),
                new Producto(null, "Café pasado", "Café pasado caliente tradicional", 3.0, "BEBIDAS", true),
                new Producto(null, "Refresco", "Vaso de refresco natural helado", 1.5, "BEBIDAS", true),
                new Producto(null, "Alfajor", "Alfajor relleno con abundante manjar blanco", 2.0, "ENDULZATE", true),
                new Producto(null, "Mazamorra", "Porción de mazamorra morada tradicional", 3.0, "ENDULZATE", true)
        );
        productos.saveAll(catalogoInicial);
    }
}
