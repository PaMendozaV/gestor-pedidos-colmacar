package co.colmacar.pedidos.servicio;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.automata.TablaTransiciones;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Motor que hace que el sistema funcione como un automata: los estados cuyo responsable es SISTEMA
 * avanzan solos, sin que nadie (ni el administrador) tenga que tocarlos.
 *
 * Como se decide que estados son automaticos: no hay una lista aparte; se pregunta a la tabla delta
 * quien emite los eventos de cada estado (TablaTransiciones.responsable). Si es SISTEMA, el motor
 * lo mueve. Asi, si se cambia delta, el motor se adapta.
 *
 * Solo actua con colmacar.modo=simulado. Con colmacar.modo=real no decide nada: PAGO_APROBADO /
 * PAGO_RECHAZADO llegan por el webhook de la pasarela y DESPACHO_ESCANEADO por el endpoint de bodega.
 *
 * Funciona por barrido: cada ciclo revisa los pedidos en estados automaticos y, si ya paso el tiempo
 * de espera del estado, emite el evento correspondiente como SISTEMA. Al no depender de temporizadores
 * en memoria, tambien reanuda los pedidos pendientes si la aplicacion se reinicia.
 */
@Service
public class MotorAutomatico {

    private static final Logger LOG = LoggerFactory.getLogger(MotorAutomatico.class);

    private final PedidoRepository pedidos;
    private final PedidoService servicio;
    private final PasarelaPagos pasarela;
    private final long esperaPagoMs;
    private final long esperaPreparacionMs;
    private final boolean simulado;

    public MotorAutomatico(PedidoRepository pedidos, PedidoService servicio, PasarelaPagos pasarela,
                           @Value("${colmacar.motor.pago-ms:4000}") long esperaPagoMs,
                           @Value("${colmacar.motor.preparacion-ms:6000}") long esperaPreparacionMs,
                           @Value("${colmacar.modo:simulado}") String modo) {
        this.pedidos = pedidos;
        this.servicio = servicio;
        this.pasarela = pasarela;
        this.esperaPagoMs = esperaPagoMs;
        this.esperaPreparacionMs = esperaPreparacionMs;
        this.simulado = !"real".equalsIgnoreCase(modo.trim());
    }

    /** Un barrido. Devuelve cuantos pedidos avanzaron. */
    public int ejecutarCiclo() {
        if (!simulado) {
            return 0;
        }
        List<EstadoPedido> automaticos = Arrays.stream(EstadoPedido.values())
                .filter(e -> TablaTransiciones.responsable(e).filter(r -> r == Rol.SISTEMA).isPresent())
                .toList();

        int avanzados = 0;
        for (Pedido p : pedidos.findByEstadoIn(automaticos)) {
            EventoPedido evento = decidir(p);
            if (evento == null) {
                continue;
            }
            try {
                if (servicio.procesarEvento(p.getCodigo(), evento, Rol.SISTEMA).aceptado()) {
                    avanzados++;
                    LOG.info("Motor: {} {} -> {}", p.getCodigo(), p.getEstado(), evento);
                }
            } catch (OptimisticLockingFailureException e) {
                LOG.debug("Motor: {} cambio al mismo tiempo, se reintenta en el proximo ciclo", p.getCodigo());
            }
        }
        return avanzados;
    }

    /** Que evento emite el sistema para este pedido, o null si todavia no toca. */
    private EventoPedido decidir(Pedido p) {
        long esperaMs = Duration.between(p.getActualizadoEn(), LocalDateTime.now()).toMillis();
        return switch (p.getEstado()) {
            case PAGO_PENDIENTE -> esperaMs < esperaPagoMs ? null
                    : (pasarela.cobrar(p) ? EventoPedido.PAGO_APROBADO : EventoPedido.PAGO_RECHAZADO);
            case PREPARANDO_INVENTARIO -> esperaMs < esperaPreparacionMs ? null
                    : EventoPedido.DESPACHO_ESCANEADO;
            default -> null;
        };
    }
}
