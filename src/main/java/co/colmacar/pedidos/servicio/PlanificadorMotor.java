package co.colmacar.pedidos.servicio;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Ejecuta el motor automatico cada cierto tiempo. Se puede apagar con colmacar.motor.habilitado=false. */
@Component
@ConditionalOnProperty(name = "colmacar.motor.habilitado", havingValue = "true", matchIfMissing = true)
public class PlanificadorMotor {

    private final MotorAutomatico motor;

    public PlanificadorMotor(MotorAutomatico motor) {
        this.motor = motor;
    }

    @Scheduled(fixedDelayString = "${colmacar.motor.intervalo-ms:1000}", initialDelay = 1000)
    public void tick() {
        motor.ejecutarCiclo();
    }
}
