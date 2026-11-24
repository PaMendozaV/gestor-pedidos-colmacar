package co.colmacar.pedidos.repositorio;

import co.colmacar.pedidos.dominio.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
}
