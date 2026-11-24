package co.colmacar.pedidos.repositorio;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.dominio.Pedido;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    Optional<Pedido> findByCodigo(String codigo);

    List<Pedido> findByEstadoIn(Collection<EstadoPedido> estados);

    List<Pedido> findAllByOrderByActualizadoEnDescIdDesc();

    /** Carrito abierto del cliente (a lo sumo uno: indice ux_ttpedido_carrito). */
    Optional<Pedido> findFirstByUsuarioUsernameAndEstado(String username, EstadoPedido estado);

    /** Pedidos ya confirmados del cliente, con sus lineas (para /mis-pedidos). */
    @EntityGraph(attributePaths = "detalles")
    List<Pedido> findByUsuarioUsernameAndEstadoNotOrderByIdDesc(String username, EstadoPedido estado);
}
