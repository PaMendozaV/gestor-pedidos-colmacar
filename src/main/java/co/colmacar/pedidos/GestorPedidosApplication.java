package co.colmacar.pedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GestorPedidosApplication {
    public static void main(String[] args) {
        SpringApplication.run(GestorPedidosApplication.class, args);
    }
}
