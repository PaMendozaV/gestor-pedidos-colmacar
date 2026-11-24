package co.colmacar.pedidos;

import static co.colmacar.pedidos.automata.EstadoPedido.*;
import static co.colmacar.pedidos.automata.EventoPedido.*;
import static org.junit.jupiter.api.Assertions.*;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.automata.TablaTransiciones;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Cubre el 100 % de los pares (estado, evento): 6 x 6 = 36. */
class TablaTransicionesTest {

    /** Tabla esperada escrita de forma independiente a la implementacion. */
    private static final Map<String, EstadoPedido> ESPERADA = new HashMap<>();

    static {
        esperar(CARRITO, CONFIRMAR_PEDIDO, PAGO_PENDIENTE);
        esperar(PAGO_PENDIENTE, PAGO_APROBADO, PREPARANDO_INVENTARIO);
        esperar(PAGO_PENDIENTE, EventoPedido.PAGO_RECHAZADO, EstadoPedido.PAGO_RECHAZADO);
        esperar(EstadoPedido.PAGO_RECHAZADO, REINTENTAR_PAGO, PAGO_PENDIENTE);
        esperar(PREPARANDO_INVENTARIO, DESPACHO_ESCANEADO, EN_TRANSITO);
        esperar(EN_TRANSITO, ENTREGA_CONFIRMADA, ENTREGADO);
    }

    private static void esperar(EstadoPedido o, EventoPedido e, EstadoPedido d) {
        ESPERADA.put(o + "|" + e, d);
    }

    @Test
    void cadaParEstadoEventoDaLoEsperado() {
        int pares = 0;
        for (EstadoPedido estado : EstadoPedido.values()) {
            for (EventoPedido evento : EventoPedido.values()) {
                pares++;
                Optional<EstadoPedido> real = TablaTransiciones.siguiente(estado, evento);
                EstadoPedido esperado = ESPERADA.get(estado + "|" + evento);
                if (esperado == null) {
                    assertTrue(real.isEmpty(), "Debe rechazar " + evento + " en " + estado);
                } else {
                    assertEquals(Optional.of(esperado), real, estado + " + " + evento);
                }
            }
        }
        assertEquals(36, pares);
        assertEquals(ESPERADA.size(), TablaTransiciones.totalTransiciones());
    }

    @Test
    void elEstadoEntregadoNoTieneSalidas() {
        assertTrue(TablaTransiciones.eventosPermitidos(ENTREGADO).isEmpty());
        assertTrue(ENTREGADO.esFinal());
    }

    @Test
    void noSePuedeDespacharSinPagoAprobado() {
        for (EstadoPedido e : List.of(CARRITO, PAGO_PENDIENTE, EstadoPedido.PAGO_RECHAZADO)) {
            assertTrue(TablaTransiciones.siguiente(e, DESPACHO_ESCANEADO).isEmpty(), "Desde " + e);
        }
    }

    @Test
    void palabraDelCicloNormalSeAcepta() {
        EstadoPedido estado = EstadoPedido.INICIAL;
        for (EventoPedido e : List.of(CONFIRMAR_PEDIDO, PAGO_APROBADO, DESPACHO_ESCANEADO, ENTREGA_CONFIRMADA)) {
            estado = TablaTransiciones.siguiente(estado, e).orElseThrow();
        }
        assertTrue(estado.esFinal());
    }

    @Test
    void palabraConRechazoYReintentoSeAcepta() {
        EstadoPedido estado = EstadoPedido.INICIAL;
        for (EventoPedido e : List.of(CONFIRMAR_PEDIDO, EventoPedido.PAGO_RECHAZADO, REINTENTAR_PAGO,
                PAGO_APROBADO, DESPACHO_ESCANEADO, ENTREGA_CONFIRMADA)) {
            estado = TablaTransiciones.siguiente(estado, e).orElseThrow();
        }
        assertEquals(ENTREGADO, estado);
    }

    @Test
    void cadaEstadoNoFinalTieneUnSoloResponsable() {
        for (EstadoPedido estado : EstadoPedido.values()) {
            if (estado.esFinal()) {
                assertTrue(TablaTransiciones.responsable(estado).isEmpty());
            } else {
                assertTrue(TablaTransiciones.responsable(estado).isPresent(),
                        estado + " mezcla eventos de distintos roles");
            }
        }
    }

    @Test
    void quienMueveCadaEstado() {
        assertEquals(Rol.CLIENTE, TablaTransiciones.responsable(CARRITO).orElseThrow());
        assertEquals(Rol.SISTEMA, TablaTransiciones.responsable(PAGO_PENDIENTE).orElseThrow());
        assertEquals(Rol.CLIENTE, TablaTransiciones.responsable(EstadoPedido.PAGO_RECHAZADO).orElseThrow());
        assertEquals(Rol.SISTEMA, TablaTransiciones.responsable(PREPARANDO_INVENTARIO).orElseThrow());
        assertEquals(Rol.REPARTIDOR, TablaTransiciones.responsable(EN_TRANSITO).orElseThrow());
    }

    @Test
    void elAdministradorNoEmiteNingunEvento() {
        for (EventoPedido e : EventoPedido.values()) {
            assertNotEquals(Rol.ADMIN, e.getRol(), e + " no debe depender del administrador");
        }
    }

    @Test
    void soloLaEntregaDependeDelRepartidor() {
        for (EventoPedido e : EventoPedido.values()) {
            assertEquals(e == ENTREGA_CONFIRMADA, e.getRol() == Rol.REPARTIDOR, e.toString());
        }
    }
}
