package co.colmacar.pedidos.automata;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Funcion de transicion delta: Q x Sigma -> Q, declarada como una tabla de pares
 * (estado, evento) -> estado siguiente. Es la UNICA fuente de reglas del proceso:
 * no hay condicionales anidados en el resto del sistema.
 *
 * Si el par (estado, evento) no esta en la tabla, delta no esta definida y el
 * evento se rechaza.
 */
public final class TablaTransiciones {

    private static final Map<EstadoPedido, Map<EventoPedido, EstadoPedido>> DELTA =
            new EnumMap<>(EstadoPedido.class);

    static {
        for (EstadoPedido e : EstadoPedido.values()) {
            DELTA.put(e, new EnumMap<>(EventoPedido.class));
        }
        // Ciclo normal
        agregar(EstadoPedido.CARRITO,               EventoPedido.CONFIRMAR_PEDIDO,    EstadoPedido.PAGO_PENDIENTE);
        agregar(EstadoPedido.PAGO_PENDIENTE,        EventoPedido.PAGO_APROBADO,       EstadoPedido.PREPARANDO_INVENTARIO);
        agregar(EstadoPedido.PREPARANDO_INVENTARIO, EventoPedido.DESPACHO_ESCANEADO,  EstadoPedido.EN_TRANSITO);
        agregar(EstadoPedido.EN_TRANSITO,           EventoPedido.ENTREGA_CONFIRMADA,  EstadoPedido.ENTREGADO);
        // Rama de pago rechazado
        agregar(EstadoPedido.PAGO_PENDIENTE,        EventoPedido.PAGO_RECHAZADO,      EstadoPedido.PAGO_RECHAZADO);
        agregar(EstadoPedido.PAGO_RECHAZADO,        EventoPedido.REINTENTAR_PAGO,     EstadoPedido.PAGO_PENDIENTE);
    }

    private TablaTransiciones() {
    }

    private static void agregar(EstadoPedido origen, EventoPedido evento, EstadoPedido destino) {
        DELTA.get(origen).put(evento, destino);
    }

    /** delta(estado, evento); vacio si la transicion no esta definida. */
    public static Optional<EstadoPedido> siguiente(EstadoPedido estado, EventoPedido evento) {
        return Optional.ofNullable(DELTA.get(estado).get(evento));
    }

    /** Eventos que delta acepta desde un estado dado. */
    public static Set<EventoPedido> eventosPermitidos(EstadoPedido estado) {
        Map<EventoPedido, EstadoPedido> fila = DELTA.get(estado);
        return fila.isEmpty() ? EnumSet.noneOf(EventoPedido.class) : EnumSet.copyOf(fila.keySet());
    }

    /**
     * Rol responsable de mover el pedido desde este estado: el rol de los eventos que delta acepta.
     * Vacio si el estado es final o si hubiera mas de un rol (el test lo prohibe).
     * Es lo que decide si un estado se automatiza (SISTEMA) o espera a una persona.
     */
    public static Optional<Rol> responsable(EstadoPedido estado) {
        Set<Rol> roles = EnumSet.noneOf(Rol.class);
        for (EventoPedido e : eventosPermitidos(estado)) {
            roles.add(e.getRol());
        }
        return roles.size() == 1 ? roles.stream().findFirst() : Optional.empty();
    }

    /** Vista de solo lectura de toda la tabla (para el endpoint /api/automata y las pruebas). */
    public static Map<EstadoPedido, Map<EventoPedido, EstadoPedido>> tabla() {
        return Collections.unmodifiableMap(DELTA);
    }

    /** Numero de transiciones definidas en delta. */
    public static int totalTransiciones() {
        return DELTA.values().stream().mapToInt(Map::size).sum();
    }
}
