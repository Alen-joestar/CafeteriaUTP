package com.utp.cafeteria.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.utp.cafeteria.model.LoginResponse;
import com.utp.cafeteria.model.Usuario;
import com.utp.cafeteria.repository.UsuarioRepository;
import com.utp.cafeteria.security.JwtService;
import com.utp.cafeteria.service.UsuarioService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarios;
    private final UsuarioService usuarioService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
            UsuarioRepository usuarios, UsuarioService usuarioService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarios = usuarios;
        this.usuarioService = usuarioService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.identificador(), req.password()));

        String rol = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_ESTUDIANTE")
                .replace("ROLE_", "");

        String token = jwtService.generarToken(auth.getName(), rol);
        Usuario usuario = usuarios.buscarPorIdentificador(auth.getName()).orElse(null);

        LoginResponse response = new LoginResponse(
                token,
                "Bearer",
                usuario != null ? usuario.getId() : null,
                usuario != null ? usuario.getCodigo() : auth.getName(),
                usuario != null ? usuario.getCorreo() : null,
                usuario != null ? usuario.getNombreCompleto() : null,
                rol);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/registro")
    public ResponseEntity<Usuario> registrarEstudiante(@RequestBody RegistroRequest req) {
        Usuario nuevo = new Usuario(
                null,
                req.codigo(),
                req.correo(),
                req.nombreCompleto(),
                req.password(),
                "ESTUDIANTE");
        Usuario registrado = usuarioService.registrar(nuevo);
        return ResponseEntity.status(HttpStatus.CREATED).body(registrado);
    }

    @GetMapping("/me")
    public ResponseEntity<Usuario> perfilActual(Authentication authentication) {
        return usuarios.buscarPorIdentificador(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
