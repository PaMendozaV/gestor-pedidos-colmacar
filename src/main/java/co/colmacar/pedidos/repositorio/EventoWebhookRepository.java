package co.colmacar.pedidos.repositorio;

import co.colmacar.pedidos.dominio.EventoWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoWebhookRepository extends JpaRepository<EventoWebhook, String> {
}
