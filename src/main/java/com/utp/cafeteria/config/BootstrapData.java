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
                new Producto(null, "Pan con pollo", "Clásico sandwich con pollo deshilachado y cremas", 4.0, "SANDWICH", true,
                        "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?auto=format&fit=crop&w=600&q=80", 30),
                new Producto(null, "Pan con pollo al horno", "Sandwich con trozos de pollo al horno jugoso", 4.0, "SANDWICH", true,
                        "https://images.unsplash.com/photo-1554433607-66b5efe9d304?auto=format&fit=crop&w=600&q=80", 25),
                new Producto(null, "Hamburguesa", "Hamburguesa clásica de carne con papas al hilo y cremas", 7.0, "SANDWICH", true,
                        "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=600&q=80", 20),
                new Producto(null, "Chicharrón", "Sandwich de chicharrón con camote y salsa criolla", 8.0, "SANDWICH", true,
                        "https://images.unsplash.com/photo-1619860860774-1e2e17343432?auto=format&fit=crop&w=600&q=80", 15),
                new Producto(null, "Papa rellena", "Papa rellena tradicional de carne con sarsa criolla", 3.5, "ANTOJITOS", true,
                        "https://images.unsplash.com/photo-1541592106381-b31e9677c0e5?auto=format&fit=crop&w=600&q=80", 25),
                new Producto(null, "Empanada de pollo", "Empanada horneada rellena de pollo", 4.0, "ANTOJITOS", true,
                        "https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&w=600&q=80", 30),
                new Producto(null, "Alitas broaster", "Porción de alitas crocantes acompañadas de papas", 8.0, "ANTOJITOS", true,
                        "https://images.unsplash.com/photo-1567620832903-9fc6debc209f?auto=format&fit=crop&w=600&q=80", 20),
                new Producto(null, "Café pasado", "Café pasado caliente tradicional", 3.0, "BEBIDAS", true,
                        "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=600&q=80", 40),
                new Producto(null, "Refresco", "Vaso de refresco natural helado", 1.5, "BEBIDAS", true,
                        "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=600&q=80", 50),
                new Producto(null, "Alfajor", "Alfajor relleno con abundante manjar blanco", 2.0, "ENDULZATE", true,
                        "https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&w=600&q=80", 35),
                new Producto(null, "Mazamorra", "Porción de mazamorra morada tradicional", 3.0, "ENDULZATE", true,
                        "https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=600&q=80", 25)
        );
        productos.saveAll(catalogoInicial);
    }
}
