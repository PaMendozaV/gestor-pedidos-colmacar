package co.colmacar.pedidos.tienda;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Carrito del cliente (ROLE_CLIENTE en SeguridadConfig). Escrituras con X-XSRF-TOKEN. */
@RestController
@RequestMapping("/api/carrito")
public class CarritoApiController {

    public record SincronizarRequest(List<CarritoService.LineaPedida> lineas) {
    }

    private final CarritoService carritos;

    public CarritoApiController(CarritoService carritos) {
        this.carritos = carritos;
    }

    @GetMapping
    public CarritoDto actual(Authentication auth) {
        return carritos.actual(auth.getName());
    }

    /** Recibe solo {productoId, cantidad}; precios y total los calcula el servidor. */
    @PostMapping("/sincronizar")
    public ResponseEntity<?> sincronizar(@RequestBody SincronizarRequest req, Authentication auth) {
        try {
            return ResponseEntity.ok(carritos.sincronizar(auth.getName(), req.lineas()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
