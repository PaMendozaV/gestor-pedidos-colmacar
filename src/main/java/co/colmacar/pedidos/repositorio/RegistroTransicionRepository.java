package co.colmacar.pedidos.repositorio;

import co.colmacar.pedidos.dominio.RegistroTransicion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroTransicionRepository extends JpaRepository<RegistroTransicion, Long> {

    List<RegistroTransicion> findByCodigoPedidoOrderByFechaAscIdAsc(String codigoPedido);
}
