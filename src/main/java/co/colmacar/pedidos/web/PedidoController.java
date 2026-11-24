package co.colmacar.pedidos.web;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.seguridad.UsuarioActual;
import co.colmacar.pedidos.servicio.PedidoService;
import co.colmacar.pedidos.servicio.ResultadoTransicion;
import co.colmacar.pedidos.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * API de las personas (con sesion). El rol sale del usuario autenticado; no existe encabezado X-Rol.
 * SISTEMA nunca entra por aqui: sus eventos llegan por IntegracionesController o por el motor.
 */
@RestController
@RequestMapping("/api")
public class PedidoController {

    private final PedidoService servicio;

    public PedidoController(PedidoService servicio) {
        this.servicio = servicio;
    }

    /** Usuario de la sesion (para la barra superior del frontend). */
    @GetMapping("/me")
    public ResponseEntity<?> yo(Authentication auth) {
        return UsuarioActual.de(auth)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(new SesionDto(u.username(), u.rol())))
                .orElseGet(() -> prohibido("Usuario sin rol valido"));
    }

    /** Crea un pedido vacio en CARRITO. Solo un CLIENTE crea pedidos y queda como su duenio. */
    @PostMapping("/pedidos")
    public ResponseEntity<?> crear(Authentication auth) {
        UsuarioActual u = UsuarioActual.de(auth).orElse(null);
        if (u == null || !u.esCliente()) {
            return prohibido("Solo un cliente puede crear pedidos");
        }
        Pedido p = servicio.crear(u.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(PedidoDto.de(p, true));
    }

    /** Listado con filtro por codigo (parcial) y estado. El cliente solo recibe los suyos. */
    @GetMapping("/pedidos")
    public ResponseEntity<?> listar(@RequestParam(required = false) String codigo,
                                    @RequestParam(required = false) EstadoPedido estado, Authentication auth) {
        UsuarioActual u = UsuarioActual.de(auth).orElse(null);
        if (u == null) {
            return prohibido("Usuario sin rol valido");
        }
        List<PedidoDto> lista = servicio.listar(codigo, estado, u).stream()
                .map(p -> PedidoDto.de(p, esDuenio(u, p))).toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/pedidos/{codigo}")
    public ResponseEntity<?> obtener(@PathVariable String codigo, Authentication auth) {
        UsuarioActual u = UsuarioActual.de(auth).orElse(null);
        if (u == null) {
            return prohibido("Usuario sin rol valido");
        }
        Pedido p = servicio.obtenerVisible(codigo, u);
        return ResponseEntity.ok(PedidoDto.de(p, esDuenio(u, p)));
    }

    /**
     * Evento emitido por una persona.
     * 200 aceptado, 403 rol no autorizado (o SISTEMA), 409 delta no definida,
     * 422 codigo de entrega incorrecto, 404 pedido inexistente o ajeno, 401 sin sesion.
     */
    @PostMapping("/pedidos/{codigo}/eventos")
    public ResponseEntity<?> evento(@PathVariable String codigo, @Valid @RequestBody EventoRequest req,
                                    Authentication auth) {
        UsuarioActual u = UsuarioActual.de(auth).orElse(null);
        if (u == null) {
            return prohibido("Usuario sin rol valido");
        }
        if (u.rol() == Rol.SISTEMA) {
            return prohibido("El rol SISTEMA es interno y no se acepta por HTTP");
        }
        Pedido visible = servicio.obtenerVisible(codigo, u);
        ResultadoTransicion r = servicio.procesarEvento(visible.getCodigo(), req.evento(), u.rol(),
                req.codigoEntrega());
        HttpStatus status = switch (r.motivo()) {
            case ACEPTADO -> HttpStatus.OK;
            case NO_AUTORIZADO -> HttpStatus.FORBIDDEN;
            case TRANSICION_NO_DEFINIDA -> HttpStatus.CONFLICT;
            case CODIGO_ENTREGA_INVALIDO, PEDIDO_INVALIDO -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(ResultadoDto.de(r, esDuenio(u, r.pedido())));
    }

    /** Auditoria: todos los eventos recibidos por el pedido, aceptados y rechazados. */
    @GetMapping("/pedidos/{codigo}/historial")
    public ResponseEntity<?> historial(@PathVariable String codigo, Authentication auth) {
        UsuarioActual u = UsuarioActual.de(auth).orElse(null);
        if (u == null) {
            return prohibido("Usuario sin rol valido");
        }
        return ResponseEntity.ok(servicio.historial(codigo, u).stream().map(RegistroDto::de).toList());
    }

    /** Definicion formal del AFD (Q, Sigma, delta, q0, F). */
    @GetMapping("/automata")
    public AutomataDto automata() {
        return AutomataDto.actual();
    }

    private static boolean esDuenio(UsuarioActual u, Pedido p) {
        return u.esCliente() && u.username().equals(p.getCliente());
    }

    private static ResponseEntity<Map<String, String>> prohibido(String mensaje) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", mensaje));
    }
}
