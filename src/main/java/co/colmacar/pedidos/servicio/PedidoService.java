package co.colmacar.pedidos.servicio;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.automata.TablaTransiciones;
import co.colmacar.pedidos.dominio.Pago;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.dominio.PedidoDetalle;
import co.colmacar.pedidos.dominio.RegistroTransicion;
import co.colmacar.pedidos.repositorio.PagoRepository;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import co.colmacar.pedidos.repositorio.RegistroTransicionRepository;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import co.colmacar.pedidos.seguridad.UsuarioActual;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private static final SecureRandom AZAR = new SecureRandom();

    private final PedidoRepository pedidos;
    private final RegistroTransicionRepository registros;
    private final PasarelaPagos pasarela;
    private final UsuarioRepository usuarios;
    private final PagoRepository pagos;
    private final int umbralMayorista;

    public PedidoService(PedidoRepository pedidos, RegistroTransicionRepository registros, PasarelaPagos pasarela,
                         UsuarioRepository usuarios, PagoRepository pagos,
                         @Value("${colmacar.mayorista.umbral:50}") int umbralMayorista) {
        this.pedidos = pedidos;
        this.registros = registros;
        this.pasarela = pasarela;
        this.usuarios = usuarios;
        this.pagos = pagos;
        this.umbralMayorista = umbralMayorista;
    }

    /** Devuelve el carrito abierto del cliente o crea uno en q0 = CARRITO (uno solo por cliente). */
    @Transactional
    public Pedido crear(String username) {
        Optional<Pedido> abierto = pedidos.findFirstByUsuarioUsernameAndEstado(username, EstadoPedido.CARRITO);
        if (abierto.isPresent()) {
            return abierto.get();
        }
        var usuario = usuarios.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("No existe el usuario " + username));
        String codigoEntrega = String.format("%04d", AZAR.nextInt(10_000));
        Pedido p = pedidos.save(new Pedido(usuario, codigoEntrega));
        p.setCodigo(String.format("PED-%04d", p.getId()));
        return pedidos.save(p);
    }

    @Transactional(readOnly = true)
    public Pedido obtener(String codigo) {
        return pedidos.findByCodigo(normalizar(codigo))
                .orElseThrow(() -> new PedidoNoEncontradoException(normalizar(codigo)));
    }

    /** Igual que obtener, pero un CLIENTE solo ve los suyos: los ajenos responden como inexistentes (404). */
    @Transactional(readOnly = true)
    public Pedido obtenerVisible(String codigo, UsuarioActual u) {
        Pedido p = obtener(codigo);
        if (!u.puedeVer(p.getCliente())) {
            throw new PedidoNoEncontradoException(p.getCodigo());
        }
        return p;
    }

    @Transactional(readOnly = true)
    public List<Pedido> listar(String texto, EstadoPedido estado, UsuarioActual u) {
        // Filtro en memoria (suficiente para pocos pedidos). Para volumen alto: Specification + paginacion.
        String t = (texto == null || texto.isBlank()) ? null : normalizar(texto);
        return pedidos.findAllByOrderByActualizadoEnDescIdDesc().stream()
                .filter(p -> u.puedeVer(p.getCliente()))
                .filter(p -> t == null || p.getCodigo().contains(t))
                .filter(p -> estado == null || p.getEstado() == estado)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegistroTransicion> historial(String codigo, UsuarioActual u) {
        Pedido p = obtenerVisible(codigo, u);
        return registros.findByCodigoPedidoOrderByFechaAscIdAsc(p.getCodigo());
    }

    @Transactional
    public ResultadoTransicion procesarEvento(String codigo, EventoPedido evento, Rol rol) {
        return procesarEvento(codigo, evento, rol, null);
    }

    /**
     * Aplica un evento al pedido:
     * 1) autorizacion: solo el rol duenio del evento puede emitirlo;
     * 2) delta: la transicion debe estar definida para el estado actual;
     * 3) ENTREGA_CONFIRMADA exige ademas el codigo de entrega del pedido.
     * Si falla cualquiera, el estado NO cambia y el intento queda en la auditoria.
     * Al entrar a PAGO_PENDIENTE se pide a la pasarela iniciar el cobro (Stripe crea la Checkout Session).
     */
    @Transactional
    public ResultadoTransicion procesarEvento(String codigo, EventoPedido evento, Rol rol, String codigoEntrega) {
        Pedido pedido = obtener(codigo);
        EstadoPedido actual = pedido.getEstado();

        if (evento.getRol() != rol) {
            String msg = rol == Rol.ADMIN
                    ? "El administrador solo supervisa: " + evento + " lo emite " + evento.getRol() + "."
                    : "El rol " + rol + " no puede emitir " + evento + ": lo emite " + evento.getRol() + ".";
            return rechazar(pedido, evento, rol, Motivo.NO_AUTORIZADO, msg);
        }

        Optional<EstadoPedido> siguiente = TablaTransiciones.siguiente(actual, evento);
        if (siguiente.isEmpty()) {
            String msg = "Evento " + evento + " no permitido en el estado " + actual
                    + ". Permitidos: " + TablaTransiciones.eventosPermitidos(actual);
            return rechazar(pedido, evento, rol, Motivo.TRANSICION_NO_DEFINIDA, msg);
        }

        if (evento == EventoPedido.ENTREGA_CONFIRMADA && !codigoCorrecto(pedido, codigoEntrega)) {
            return rechazar(pedido, evento, rol, Motivo.CODIGO_ENTREGA_INVALIDO,
                    "Codigo de entrega incorrecto o ausente.");
        }
        if (evento == EventoPedido.CONFIRMAR_PEDIDO) {
            String problema = prepararConfirmacion(pedido);
            if (problema != null) {
                return rechazar(pedido, evento, rol, Motivo.PEDIDO_INVALIDO, problema);
            }
            pedido.congelar();   // desde aqui las lineas y el total ya no cambian
        }

        pedido.cambiarEstado(siguiente.get());
        SesionPago sesion = siguiente.get() == EstadoPedido.PAGO_PENDIENTE
                ? pasarela.iniciarCobro(pedido).orElse(null) : null;   // Stripe: Checkout Session por el total
        pedido.setUrlPago(sesion == null ? null : sesion.url());
        pedidos.save(pedido);
        registrarPago(pedido, evento, siguiente.get(), sesion == null ? null : sesion.referencia());
        String msg = actual + " --" + evento + "--> " + siguiente.get();
        registros.save(new RegistroTransicion(pedido.getCodigo(), actual, evento, siguiente.get(), true, rol, msg));
        return new ResultadoTransicion(Motivo.ACEPTADO, pedido, actual, evento, msg);
    }

    /**
     * Validacion previa a CONFIRMAR_PEDIDO (no toca delta): lineas, productos activos y datos de entrega.
     * Recalcula por ultima vez con los precios vigentes antes de congelar. Devuelve el problema o null.
     */
    private String prepararConfirmacion(Pedido p) {
        if (p.getDetalles().isEmpty()) {
            return "El carrito esta vacio.";
        }
        if (p.getDireccionEntrega() == null || p.getDireccionEntrega().isBlank()
                || p.getTelefonoContacto() == null || p.getTelefonoContacto().isBlank()) {
            return "Faltan los datos de entrega (direccion y telefono).";
        }
        for (PedidoDetalle d : p.getDetalles()) {
            if (!d.getProducto().isActivo()) {
                return "El producto '" + d.getNombre() + "' ya no esta disponible.";
            }
            d.recalcular(d.getCantidad(), umbralMayorista);
        }
        p.recalcularTotales();
        return p.getTotal().signum() > 0 ? null : "El total debe ser mayor a 0.";
    }

    /** Lleva ttpago al dia: un intento PENDIENTE al entrar a PAGO_PENDIENTE y su resultado despues. */
    private void registrarPago(Pedido pedido, EventoPedido evento, EstadoPedido destino, String referencia) {
        if (destino == EstadoPedido.PAGO_PENDIENTE) {
            pagos.save(new Pago(pedido, pasarela.nombre(), referencia, pedido.getTotal()));
        } else if (evento == EventoPedido.PAGO_APROBADO || evento == EventoPedido.PAGO_RECHAZADO) {
            Pago.Estado resultado = evento == EventoPedido.PAGO_APROBADO ? Pago.Estado.APROBADO : Pago.Estado.RECHAZADO;
            pagos.findFirstByPedidoIdAndEstadoOrderByIdDesc(pedido.getId(), Pago.Estado.PENDIENTE)
                    .ifPresent(pg -> pg.marcar(resultado));
        }
    }

    private ResultadoTransicion rechazar(Pedido pedido, EventoPedido evento, Rol rol, Motivo motivo, String msg) {
        EstadoPedido actual = pedido.getEstado();
        registros.save(new RegistroTransicion(pedido.getCodigo(), actual, evento, actual, false, rol, msg));
        return new ResultadoTransicion(motivo, pedido, actual, evento, msg);
    }

    /** Comparacion en tiempo constante para no filtrar el codigo por diferencias de tiempo. */
    private static boolean codigoCorrecto(Pedido p, String recibido) {
        if (recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(p.getCodigoEntrega().getBytes(StandardCharsets.UTF_8),
                recibido.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String normalizar(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase().replace(':', '-');
    }
}
