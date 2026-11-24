package co.colmacar.pedidos.servicio;

import static org.junit.jupiter.api.Assertions.*;

import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.dominio.PedidoDetalle;
import co.colmacar.pedidos.dominio.Producto;
import co.colmacar.pedidos.dominio.Usuario;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Parametros de la Checkout Session sin llamar a Stripe (no hay red en las pruebas). */
class PasarelaStripeTest {

    private static Pedido pedido(int cantidadTazas) {
        Usuario u = new Usuario("ana", "x", Rol.CLIENTE);
        u.setEmail("ana@correo.com");
        Pedido p = new Pedido(u, "1234");
        p.setCodigo("PED-0007");
        Producto taza = new Producto("Taza", new BigDecimal("4.00"), new BigDecimal("3.40"), null, null);
        Producto agenda = new Producto("Agenda", new BigDecimal("10.25"), null, null, null);
        p.getDetalles().add(new PedidoDetalle(p, taza, cantidadTazas, 50));
        p.getDetalles().add(new PedidoDetalle(p, agenda, 1, 50));
        p.recalcularTotales();
        p.congelar();
        return p;
    }

    private static PasarelaStripe pasarela() {
        return new PasarelaStripe("sk_test_placeholder", "http://localhost:8080/", 60, false);
    }

    @Test
    void cobraElTotalCongeladoEnUsdConUnaLineaPorProducto() {
        Pedido p = pedido(60);                        // 60 x 3.40 (mayorista) + 10.25 = 214.25
        SessionCreateParams s = pasarela().parametros(p);
        assertEquals(21425, p.totalEnCentavos());
        assertEquals(2, s.getLineItems().size());
        long suma = s.getLineItems().stream()
                .mapToLong(l -> l.getPriceData().getUnitAmount() * l.getQuantity()).sum();
        assertEquals(21425, suma);
        assertTrue(s.getLineItems().stream().allMatch(l -> "usd".equals(l.getPriceData().getCurrency())));
        assertEquals(340L, s.getLineItems().get(0).getPriceData().getUnitAmount());
        assertEquals("PED-0007", s.getMetadata().get("codigoPedido"));
        assertEquals("PED-0007", s.getPaymentIntentData().getMetadata().get("codigoPedido"));
        assertEquals(SessionCreateParams.Mode.PAYMENT, s.getMode());
        assertEquals("http://localhost:8080/mis-pedidos?pago=ok&codigo=PED-0007", s.getSuccessUrl());
        assertEquals("ana@correo.com", s.getCustomerEmail());
    }

    @Test
    void soloLlavesDePruebaSalvoQueSePermitaLive() {
        assertThrows(IllegalStateException.class, () -> new PasarelaStripe("sk_live_x", "http://x", 60, false));
        assertThrows(IllegalStateException.class, () -> new PasarelaStripe("", "http://x", 60, false));
        assertDoesNotThrow(() -> new PasarelaStripe("sk_live_x", "http://x", 60, true));
    }

    @Test
    void rechazaTotalesBajoElMinimoDeStripe() {
        Usuario u = new Usuario("ana", "x", Rol.CLIENTE);
        Pedido p = new Pedido(u, "1234");
        p.setCodigo("PED-0008");
        p.getDetalles().add(new PedidoDetalle(p, new Producto("Pin", new BigDecimal("0.40"), null, null, null), 1, 50));
        p.recalcularTotales();
        assertThrows(PasarelaNoDisponibleException.class, () -> pasarela().parametros(p));
    }
}
