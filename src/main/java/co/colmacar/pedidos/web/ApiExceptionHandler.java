package co.colmacar.pedidos.web;

import co.colmacar.pedidos.servicio.PasarelaNoDisponibleException;
import co.colmacar.pedidos.servicio.PedidoNoEncontradoException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PedidoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(PedidoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage()).findFirst().orElse("Solicitud invalida");
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    /** JSON mal formado o evento/estado que no existe en el alfabeto. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> peticionInvalida(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error",
                "Solicitud invalida: el evento o estado no pertenece al alfabeto del automata"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> concurrencia(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                "El pedido fue modificado por otro evento al mismo tiempo. Reintente."));
    }

    /** Por ejemplo, un segundo carrito abierto para el mismo cliente (ux_ttpedido_carrito). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integridad(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                "La operacion viola una regla de datos (por ejemplo, ya tienes un carrito abierto)."));
    }

    @ExceptionHandler(PasarelaNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> pasarela(PasarelaNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error",
                "La pasarela de pagos no respondio. Intenta de nuevo en unos minutos."));
    }
}
