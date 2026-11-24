package co.colmacar.pedidos.web;

import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.servicio.PedidoService;
import co.colmacar.pedidos.servicio.ResultadoTransicion;
import co.colmacar.pedidos.servicio.WebhookPagosService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Entradas de maquinas (sin sesion ni CSRF). Ambas emiten eventos como SISTEMA a traves de
 * PedidoService.procesarEvento, asi que delta y la auditoria siguen mandando.
 */
@RestController
@RequestMapping("/api")
public class IntegracionesController {

    private static final Logger LOG = LoggerFactory.getLogger(IntegracionesController.class);

    private final WebhookPagosService webhooks;
    private final PedidoService servicio;
    private final ObjectMapper json;
    private final String secretoWebhook;
    private final String claveBodega;

    public IntegracionesController(WebhookPagosService webhooks, PedidoService servicio, ObjectMapper json,
                                   @Value("${stripe.webhook-secret:}") String secretoWebhook,
                                   @Value("${colmacar.bodega.api-key:}") String claveBodega) {
        this.webhooks = webhooks;
        this.servicio = servicio;
        this.json = json;
        this.secretoWebhook = secretoWebhook;
        this.claveBodega = claveBodega;
    }

    /**
     * Aviso de la pasarela (Stripe). Firma invalida -> 400 sin procesar.
     * Firma valida -> siempre 200 (aunque delta lo rechace o sea repetido) para que Stripe no reintente.
     */
    @PostMapping("/webhooks/pagos")
    public ResponseEntity<Map<String, String>> pago(@RequestBody byte[] cuerpo,
            @RequestHeader(value = "Stripe-Signature", required = false) String firma) {
        if (secretoWebhook.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Webhook sin configurar: falta STRIPE_WEBHOOK_SECRET"));
        }
        String payload = new String(cuerpo, StandardCharsets.UTF_8);
        Event evento;
        WebhookPagosService.AvisoPago aviso;
        try {
            if (firma == null || firma.isBlank()) {
                throw new SignatureVerificationException("Falta Stripe-Signature", null);
            }
            evento = Webhook.constructEvent(payload, firma, secretoWebhook);   // HMAC-SHA256 + tolerancia 5 min
            JsonNode obj = json.readTree(payload).path("data").path("object");
            aviso = new WebhookPagosService.AvisoPago(evento.getId(), evento.getType(),
                    texto(obj.path("metadata"), "codigoPedido"), texto(obj, "payment_status"),
                    obj.hasNonNull("amount_total") ? obj.get("amount_total").asLong() : null, texto(obj, "currency"));
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Firma invalida"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Evento mal formado"));
        }

        WebhookPagosService.Resultado r;
        try {
            r = webhooks.procesar(aviso);
        } catch (DataIntegrityViolationException e) {
            r = WebhookPagosService.Resultado.DUPLICADO;   // dos entregas simultaneas del mismo event.id
        }
        LOG.info("Webhook {} {} pedido={} -> {}", evento.getId(), evento.getType(), aviso.codigoPedido(), r);
        return ResponseEntity.ok(Map.of("resultado", r.name()));
    }

    private static String texto(JsonNode nodo, String campo) {
        return nodo.hasNonNull(campo) ? nodo.get(campo).asText() : null;
    }

    /** Escaner de bodega: emite DESPACHO_ESCANEADO como SISTEMA. Protegido con X-Api-Key. */
    @PostMapping("/bodega/despachos/{codigo}")
    public ResponseEntity<?> despacho(@PathVariable String codigo,
            @RequestHeader(value = "X-Api-Key", required = false) String clave) {
        if (claveBodega.isBlank() || clave == null || !MessageDigest.isEqual(
                claveBodega.getBytes(StandardCharsets.UTF_8), clave.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Clave de API invalida"));
        }
        ResultadoTransicion r = servicio.procesarEvento(codigo, EventoPedido.DESPACHO_ESCANEADO, Rol.SISTEMA);
        HttpStatus status = r.aceptado() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Dtos.ResultadoDto.de(r, false));
    }
}
