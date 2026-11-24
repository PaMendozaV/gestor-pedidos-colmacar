package co.colmacar.pedidos.repositorio;

import co.colmacar.pedidos.dominio.Producto;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    @EntityGraph(attributePaths = "categoria")
    Page<Producto> findByEstado(Integer estado, Pageable pagina);

    @EntityGraph(attributePaths = "categoria")
    Page<Producto> findByEstadoAndCategoriaId(Integer estado, Integer categoriaId, Pageable pagina);

    /** Busqueda publica en vivo (solo ACTIVO). */
    @EntityGraph(attributePaths = "categoria")
    List<Producto> findTop40ByEstadoAndNombreContainingIgnoreCaseOrderByNombreAsc(Integer estado, String nombre);

    /** Listado del admin (activos y eliminados). */
    @EntityGraph(attributePaths = "categoria")
    Page<Producto> findByNombreContainingIgnoreCase(String nombre, Pageable pagina);

    @EntityGraph(attributePaths = "categoria")
    List<Producto> findTop50ByNombreContainingIgnoreCaseOrderByIdDesc(String nombre);
}
