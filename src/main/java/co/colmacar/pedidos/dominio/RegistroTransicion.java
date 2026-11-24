package co.colmacar.pedidos.dominio;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.FuenteEvento;
import co.colmacar.pedidos.automata.Rol;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro de auditoria: guarda TODO evento recibido, aceptado o rechazado.
 * Permite reconstruir quien cambio el estado de un pedido, cuando y con que evento.
 */
@Entity
@Table(name = "registro_transicion")
public class RegistroTransicion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String codigoPedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPedido estadoOrigen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventoPedido evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FuenteEvento fuente;

    /** Estado resultante; igual al de origen cuando el evento se rechaza. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPedido estadoDestino;

    /** Rol que intento emitir el evento (SISTEMA cuando lo genera el motor automatico). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    private boolean aceptado;

    @Column(length = 200)
    private String detalle;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    protected RegistroTransicion() {
    }

    public RegistroTransicion(String codigoPedido, EstadoPedido origen, EventoPedido evento,
                              EstadoPedido destino, boolean aceptado, Rol rol, String detalle) {
        this.codigoPedido = codigoPedido;
        this.estadoOrigen = origen;
        this.evento = evento;
        this.fuente = evento.getFuente();
        this.estadoDestino = destino;
        this.aceptado = aceptado;
        this.rol = rol;
        this.detalle = detalle;
    }

    public Long getId() { return id; }
    public String getCodigoPedido() { return codigoPedido; }
    public EstadoPedido getEstadoOrigen() { return estadoOrigen; }
    public EventoPedido getEvento() { return evento; }
    public FuenteEvento getFuente() { return fuente; }
    public EstadoPedido getEstadoDestino() { return estadoDestino; }
    public boolean isAceptado() { return aceptado; }
    public Rol getRol() { return rol; }
    public String getDetalle() { return detalle; }
    public LocalDateTime getFecha() { return fecha; }
}
