package co.colmacar.pedidos.servicio;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimuladorPostDespacho {

    private static final Logger log = LoggerFactory.getLogger(SimuladorPostDespacho.class);
    private static final Duration ESPERA_EN_BODEGA = Duration.ofMinutes(1);

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final String modo;

    private final Map<String, Instant> inicioBodegaPorPedido = new ConcurrentHashMap<>();

    public SimuladorPostDespacho(
            PedidoRepository pedidoRepository,
            PedidoService pedidoService,
            @Value("${colmacar.modo:simulado}") String modo) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.modo = modo;
    }

    @Scheduled(fixedDelay = 5000)
    public void simularSalidaDeBodega() {
        if (!"real".equalsIgnoreCase(modo)) {
            inicioBodegaPorPedido.clear();
            return;
        }

        Instant ahora = Instant.now();
        List<Pedido> pedidos = pedidoRepository.findAll();

        for (Pedido pedido : pedidos) {
            if (pedido == null || pedido.getCodigo() == null) {
                continue;
            }

            if (pedido.getEstado() == EstadoPedido.PREPARANDO_INVENTARIO) {
                Instant marcadoEn = inicioBodegaPorPedido.computeIfAbsent(
                        pedido.getCodigo(),
                        k -> {
                            log.info("[SimuladorBodega] Pedido {} detectado en PREPARANDO_INVENTARIO. Despachando en 1 minuto...", pedido.getCodigo());
                            return ahora;
                        }
                );

                if (!Duration.between(marcadoEn, ahora).minus(ESPERA_EN_BODEGA).isNegative()) {
                    inicioBodegaPorPedido.remove(pedido.getCodigo());
                    try {
                        pedidoService.procesarEvento(
                                pedido.getCodigo(),
                                EventoPedido.DESPACHO_ESCANEADO,
                                Rol.SISTEMA,
                                "Simulación automática: despacho escaneado 1 minuto después de entrar a bodega (modo real)"
                        );
                        log.info("[SimuladorBodega] Pedido {} despachado automáticamente a EN_TRANSITO.", pedido.getCodigo());
                    } catch (Exception e) {
                        log.error("[SimuladorBodega] Error al despachar pedido {}: {}", pedido.getCodigo(), e.getMessage());
                    }
                }
            } else {
                inicioBodegaPorPedido.remove(pedido.getCodigo());
            }
        }
    }
}