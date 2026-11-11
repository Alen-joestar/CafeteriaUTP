package com.utp.cafeteria.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.utp.cafeteria.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("select u from Usuario u where lower(u.codigo) = lower(:identificador) or lower(u.correo) = lower(:identificador)")
    Optional<Usuario> buscarPorIdentificador(@Param("identificador") String identificador);
}
