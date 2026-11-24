package co.colmacar.pedidos.servicio;

import co.colmacar.pedidos.dominio.Pedido;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Pasarela de demostracion: aprueba con una probabilidad configurable. Activa con colmacar.modo=simulado. */
@Component
@ConditionalOnProperty(name = "colmacar.modo", havingValue = "simulado", matchIfMissing = true)
public class PasarelaSimulada implements PasarelaPagos {

    private final double probabilidadAprobacion;
    private final Random azar = new Random();

    public PasarelaSimulada(@Value("${colmacar.pasarela.probabilidad-aprobacion:0.8}") double probabilidadAprobacion) {
        this.probabilidadAprobacion = probabilidadAprobacion;
    }

    @Override
    public boolean cobrar(Pedido pedido) {
        return azar.nextDouble() < probabilidadAprobacion;
    }
}
