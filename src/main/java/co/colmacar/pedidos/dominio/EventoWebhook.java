package co.colmacar.pedidos.dominio;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Eventos de la pasarela ya procesados (event.id de Stripe). Garantiza la idempotencia del webhook. */
@Entity
@Table(name = "evento_webhook")
public class EventoWebhook {

    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false, length = 80)
    private String tipo;

    @Column(length = 20)
    private String codigoPedido;

    @Column(nullable = false)
    private LocalDateTime recibidoEn = LocalDateTime.now();

    protected EventoWebhook() {
    }

    public EventoWebhook(String id, String tipo, String codigoPedido) {
        this.id = id;
        this.tipo = tipo;
        this.codigoPedido = codigoPedido;
    }

    public String getId() { return id; }
    public String getTipo() { return tipo; }
    public String getCodigoPedido() { return codigoPedido; }
    public LocalDateTime getRecibidoEn() { return recibidoEn; }
}
