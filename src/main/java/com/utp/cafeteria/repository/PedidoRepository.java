package com.utp.cafeteria.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.utp.cafeteria.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    @Query("select distinct p from Pedido p join fetch p.cliente left join fetch p.items i left join fetch i.producto where (:estado is null or upper(p.estado) = upper(:estado)) and (:clienteId is null or p.cliente.id = :clienteId)")
    List<Pedido> buscar(@Param("estado") String estado, @Param("clienteId") Long clienteId);

    @Query("select distinct p from Pedido p join fetch p.cliente left join fetch p.items i left join fetch i.producto where p.id = :id")
    java.util.Optional<Pedido> buscarPorIdCompleto(@Param("id") Long id);

    @Query("select coalesce(sum(p.total), 0) from Pedido p where upper(p.estado) = 'ENTREGADO' and substring(p.fechaRegistro, 1, 10) = :fecha")
    Double recaudarPorFecha(@Param("fecha") String fecha);
}
