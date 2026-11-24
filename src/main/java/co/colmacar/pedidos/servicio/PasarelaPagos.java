package co.colmacar.pedidos.servicio;

import co.colmacar.pedidos.dominio.Pedido;
import java.util.Optional;

/**
 * Punto de integracion con la pasarela de pagos. Se elige con colmacar.modo:
 * simulado -> PasarelaSimulada (el motor decide por tiempo), real -> PasarelaStripe (decide el webhook).
 */
public interface PasarelaPagos {

    /** Decision sincrona del cobro (true = aprobado). Solo la usa el motor en modo simulado. */
    boolean cobrar(Pedido pedido);

    /**
     * Se llama al entrar a PAGO_PENDIENTE (lineas ya congeladas). Una pasarela real abre aqui la sesion de cobro
     * por el total del pedido; la simulada no necesita nada. Si falla lanza PasarelaNoDisponibleException.
     */
    default Optional<SesionPago> iniciarCobro(Pedido pedido) {
        return Optional.empty();
    }

    /** Proveedor que se guarda en ttpago.proveedor. */
    default String nombre() {
        return "SANDBOX";
    }
}
