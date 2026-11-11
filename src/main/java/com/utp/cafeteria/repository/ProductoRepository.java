package com.utp.cafeteria.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.utp.cafeteria.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    @Query("select p from Producto p where (:categoria is null or upper(p.categoria) = upper(:categoria)) and (:disponible is null or p.disponible = :disponible)")
    List<Producto> buscar(@Param("categoria") String categoria, @Param("disponible") Boolean disponible);
}
