package co.colmacar.pedidos.repositorio;

import co.colmacar.pedidos.dominio.Pago;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findFirstByPedidoIdAndEstadoOrderByIdDesc(Long pedidoId, Pago.Estado estado);

    List<Pago> findByPedidoIdOrderByIdAsc(Long pedidoId);
}
