package co.colmacar.pedidos.servicio;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.EventoWebhook;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.repositorio.EventoWebhookRepository;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduce un evento de Stripe YA verificado (firma) a un evento del AFD emitido como SISTEMA.
 * Idempotente: cada event.id se procesa una sola vez (tabla evento_webhook). Todo pasa por
 * PedidoService.procesarEvento (delta + auditoria + ttpago); aqui nunca se cambia el estado directamente.
 */
@Service
public class WebhookPagosService {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookPagosService.class);

    public enum Resultado { PROCESADO, DUPLICADO, IGNORADO, MONTO_NO_COINCIDE }

    /** Lo que importa del evento: data.object de una Checkout Session (o PaymentIntent). */
    public record AvisoPago(String idEvento, String tipo, String codigoPedido, String estadoPago,
                            Long montoCentavos, String moneda) {
    }

    private final EventoWebhookRepository eventos;
    private final PedidoRepository pedidos;
    private final PedidoService servicio;

    public WebhookPagosService(EventoWebhookRepository eventos, PedidoRepository pedidos, PedidoService servicio) {
        this.eventos = eventos;
        this.pedidos = pedidos;
        this.servicio = servicio;
    }

    /**
     * Evento del AFD para cada evento de Stripe; null = no mueve el pedido.
     * payment_intent.payment_failed NO rechaza: en Checkout el cliente puede reintentar con otra tarjeta en la
     * misma sesion. El rechazo definitivo es la sesion expirada o un pago asincrono fallido.
     */
    static EventoPedido traducir(AvisoPago a) {
        if (a.tipo() == null) {
            return null;
        }
        return switch (a.tipo()) {
            case "checkout.session.completed" -> "paid".equals(a.estadoPago()) ? EventoPedido.PAGO_APROBADO : null;
            case "checkout.session.async_payment_succeeded" -> EventoPedido.PAGO_APROBADO;
            case "checkout.session.expired", "checkout.session.async_payment_failed" -> EventoPedido.PAGO_RECHAZADO;
            default -> null;
        };
    }

    @Transactional
    public Resultado procesar(AvisoPago a) {
        if (eventos.existsById(a.idEvento())) {
            return Resultado.DUPLICADO;
        }
        eventos.save(new EventoWebhook(a.idEvento(), a.tipo(), a.codigoPedido()));

        EventoPedido evento = traducir(a);
        Pedido pedido = a.codigoPedido() == null ? null : pedidos.findByCodigo(a.codigoPedido()).orElse(null);
        if (evento == null || pedido == null) {
            return Resultado.IGNORADO;
        }
        if (evento == EventoPedido.PAGO_APROBADO && !montoCoincide(pedido, a)) {
            LOG.warn("Pago de {} por {} {} no coincide con el total {} USD: no se aprueba",
                    pedido.getCodigo(), a.montoCentavos(), a.moneda(), pedido.getTotal());
            return Resultado.MONTO_NO_COINCIDE;
        }
        ResultadoTransicion r = servicio.procesarEvento(pedido.getCodigo(), evento, Rol.SISTEMA);
        if (!r.aceptado() && evento == EventoPedido.PAGO_APROBADO && r.estadoAnterior() == EstadoPedido.PAGO_RECHAZADO) {
            LOG.warn("Pago aprobado para {} que ya estaba en PAGO_RECHAZADO: revisar manualmente", pedido.getCodigo());
        }
        return r.aceptado() ? Resultado.PROCESADO : Resultado.IGNORADO;
    }

    /** Se aprueba solo si Stripe cobro exactamente el total congelado y en la misma moneda. */
    static boolean montoCoincide(Pedido p, AvisoPago a) {
        return a.montoCentavos() != null && a.montoCentavos() == p.totalEnCentavos()
                && p.getMoneda().equalsIgnoreCase(a.moneda());
    }
}
