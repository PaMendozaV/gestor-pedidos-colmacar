package co.colmacar.pedidos;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import co.colmacar.pedidos.repositorio.RegistroTransicionRepository;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import co.colmacar.pedidos.tienda.CarritoService;
import org.junit.jupiter.api.BeforeEach;

import co.colmacar.pedidos.servicio.PedidoService;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Webhook de pagos (formato Stripe-Signature: t=...,v1=HMAC-SHA256(secreto, t + "." + cuerpo))
 * y endpoint de bodega. Sin sesion ni CSRF. El secreto es un valor de prueba, no una llave real.
 */
@SpringBootTest(properties = {
        "colmacar.datos-demo=false",
        "colmacar.modo=simulado",
        "colmacar.motor.habilitado=false",
        "stripe.webhook-secret=whsec_valor_de_prueba",
        "colmacar.bodega.api-key=clave-bodega-test",
        "spring.datasource.url=jdbc:h2:mem:t${random.uuid};DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class WebhookPagosTest {

    static final String SECRETO = "whsec_valor_de_prueba";

    @Autowired MockMvc mvc;
    @Autowired PedidoService servicio;
    @Autowired PedidoRepository pedidos;
    @Autowired RegistroTransicionRepository registros;
    @Autowired UsuarioRepository usuarios;
    @Autowired ProductoRepository productos;
    @Autowired CarritoService carritos;
    @Autowired co.colmacar.pedidos.repositorio.PagoRepository pagos;

    @BeforeEach
    void usuarios() {
        UsuariosPrueba.asegurar(usuarios, "ana");
    }

    private String pedidoEnPagoPendiente() {
        String codigo = UsuariosPrueba.carritoListo(carritos, productos, usuarios, "ana");
        servicio.procesarEvento(codigo, EventoPedido.CONFIRMAR_PEDIDO, Rol.CLIENTE);
        return codigo;
    }

    private EstadoPedido estado(String codigo) {
        return pedidos.findByCodigo(codigo).orElseThrow().getEstado();
    }

    /** Total del carrito de prueba: 2 x 4.00 USD = 800 centavos. */
    static String evento(String id, String tipo, String codigo) {
        return evento(id, tipo, codigo, "paid", 800);
    }

    static String evento(String id, String tipo, String codigo, String estadoPago, long montoCentavos) {
        return "{\"id\":\"" + id + "\",\"object\":\"event\",\"type\":\"" + tipo + "\","
                + "\"data\":{\"object\":{\"id\":\"cs_test_1\",\"object\":\"checkout.session\","
                + "\"payment_status\":\"" + estadoPago + "\",\"amount_total\":" + montoCentavos
                + ",\"currency\":\"usd\",\"metadata\":{\"codigoPedido\":\"" + codigo + "\"}}}}";
    }

    static String firmar(String cuerpo, String secreto) throws Exception {
        long t = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal((t + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
        return "t=" + t + ",v1=" + v1;
    }

    private ResultActions enviar(String cuerpo, String firma) throws Exception {
        var req = post("/api/webhooks/pagos").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (firma != null) req = req.header("Stripe-Signature", firma);
        return mvc.perform(req);
    }

    @Test
    void firmaValidaApruebaElPago() throws Exception {
        String c = pedidoEnPagoPendiente();
        String cuerpo = evento("evt_" + UUID.randomUUID(), "checkout.session.completed", c);
        enviar(cuerpo, firmar(cuerpo, SECRETO)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("PROCESADO"));
        assertEquals(EstadoPedido.PREPARANDO_INVENTARIO, estado(c));
        var ultimo = registros.findByCodigoPedidoOrderByFechaAscIdAsc(c).get(1);
        assertEquals(Rol.SISTEMA, ultimo.getRol());
        assertEquals(EventoPedido.PAGO_APROBADO, ultimo.getEvento());
    }

    @Test
    void sesionExpiradaRechazaElPago() throws Exception {
        String c = pedidoEnPagoPendiente();
        String cuerpo = evento("evt_" + UUID.randomUUID(), "checkout.session.expired", c);
        enviar(cuerpo, firmar(cuerpo, SECRETO)).andExpect(status().isOk());
        assertEquals(EstadoPedido.PAGO_RECHAZADO, estado(c));
    }

    @Test
    void firmaInvalidaDa400SinProcesar() throws Exception {
        String c = pedidoEnPagoPendiente();
        String cuerpo = evento("evt_" + UUID.randomUUID(), "checkout.session.completed", c);
        enviar(cuerpo, firmar(cuerpo, "whsec_otro_secreto")).andExpect(status().isBadRequest());
        enviar(cuerpo, null).andExpect(status().isBadRequest());
        // cuerpo alterado despues de firmar
        String firma = firmar(cuerpo, SECRETO);
        enviar(cuerpo.replace("checkout.session.completed", "checkout.session.expired"), firma).andExpect(status().isBadRequest());
        assertEquals(EstadoPedido.PAGO_PENDIENTE, estado(c));
    }

    @Test
    void eventoRepetidoNoDuplicaLaTransicion() throws Exception {
        String c = pedidoEnPagoPendiente();
        String cuerpo = evento("evt_" + UUID.randomUUID(), "checkout.session.completed", c);
        enviar(cuerpo, firmar(cuerpo, SECRETO)).andExpect(jsonPath("$.resultado").value("PROCESADO"));
        enviar(cuerpo, firmar(cuerpo, SECRETO)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("DUPLICADO"));
        long aprobaciones = registros.findByCodigoPedidoOrderByFechaAscIdAsc(c).stream()
                .filter(r -> r.getEvento() == EventoPedido.PAGO_APROBADO).count();
        assertEquals(1, aprobaciones);
        assertEquals(EstadoPedido.PREPARANDO_INVENTARIO, estado(c));
    }

    @Test
    void siDeltaNoLoPermiteResponde200YNoCambiaNada() throws Exception {
        String c = UsuariosPrueba.carritoListo(carritos, productos, usuarios, "ana");   // sigue en CARRITO
        String cuerpo = evento("evt_" + UUID.randomUUID(), "checkout.session.completed", c);
        enviar(cuerpo, firmar(cuerpo, SECRETO)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("IGNORADO"));
        assertEquals(EstadoPedido.CARRITO, estado(c));
    }

    @Test
    void bodegaConClaveCorrectaDespacha() throws Exception {
        String c = pedidoEnPagoPendiente();
        servicio.procesarEvento(c, EventoPedido.PAGO_APROBADO, Rol.SISTEMA);
        mvc.perform(post("/api/bodega/despachos/" + c).header("X-Api-Key", "mala")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bodega/despachos/" + c)).andExpect(status().isUnauthorized());
        assertEquals(EstadoPedido.PREPARANDO_INVENTARIO, estado(c));
        mvc.perform(post("/api/bodega/despachos/" + c).header("X-Api-Key", "clave-bodega-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pedido.estado").value("EN_TRANSITO"))
                .andExpect(jsonPath("$.pedido.codigoEntrega").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/bodega/despachos/" + c).header("X-Api-Key", "clave-bodega-test"))
                .andExpect(status().isConflict());
    }

    @Test
    void montoDistintoAlTotalCongeladoNoAprueba() throws Exception {
        String c = pedidoEnPagoPendiente();
        String cuerpo = evento("evt_" + UUID.randomUUID(), "checkout.session.completed", c, "paid", 799);
        enviar(cuerpo, firmar(cuerpo, SECRETO)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("MONTO_NO_COINCIDE"));
        assertEquals(EstadoPedido.PAGO_PENDIENTE, estado(c));
    }

    @Test
    void sesionCompletadaSinPagarEsperaYElAsincronoAprueba() throws Exception {
        String c = pedidoEnPagoPendiente();
        String sinPagar = evento("evt_" + UUID.randomUUID(), "checkout.session.completed", c, "unpaid", 800);
        enviar(sinPagar, firmar(sinPagar, SECRETO)).andExpect(jsonPath("$.resultado").value("IGNORADO"));
        assertEquals(EstadoPedido.PAGO_PENDIENTE, estado(c));
        String ok = evento("evt_" + UUID.randomUUID(), "checkout.session.async_payment_succeeded", c, "paid", 800);
        enviar(ok, firmar(ok, SECRETO)).andExpect(jsonPath("$.resultado").value("PROCESADO"));
        assertEquals(EstadoPedido.PREPARANDO_INVENTARIO, estado(c));
    }

    @Test
    void unIntentoFallidoNoRechazaLaSesionAbierta() throws Exception {
        String c = pedidoEnPagoPendiente();
        String fallo = evento("evt_" + UUID.randomUUID(), "payment_intent.payment_failed", c, "unpaid", 800);
        enviar(fallo, firmar(fallo, SECRETO)).andExpect(jsonPath("$.resultado").value("IGNORADO"));
        assertEquals(EstadoPedido.PAGO_PENDIENTE, estado(c));
    }

    @Test
    void elPagoQuedaRegistradoEnTtpago() throws Exception {
        String c = pedidoEnPagoPendiente();
        String cuerpo = evento("evt_" + UUID.randomUUID(), "checkout.session.completed", c);
        enviar(cuerpo, firmar(cuerpo, SECRETO));
        Long id = pedidos.findByCodigo(c).orElseThrow().getId();
        var lista = pagos.findByPedidoIdOrderByIdAsc(id);
        assertEquals(1, lista.size());
        assertEquals(co.colmacar.pedidos.dominio.Pago.Estado.APROBADO, lista.get(0).getEstado());
    }
}
