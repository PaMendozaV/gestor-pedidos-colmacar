package co.colmacar.pedidos.servicio;

/** La pasarela externa no pudo crear la sesion de pago. */
public class PasarelaNoDisponibleException extends RuntimeException {
    public PasarelaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
