package co.colmacar.pedidos.dominio;

import co.colmacar.pedidos.automata.EstadoPedido;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ttpedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkcodpedido")
    private Long id;

    /** Codigo visible, por ejemplo PED-0011. */
    @Column(unique = true, length = 20)
    private String codigo;

    /** CLIENTE duenio del pedido. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fkcodusuario")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPedido estado = EstadoPedido.INICIAL;

    /** Codigo de 4 digitos que el cliente entrega al repartidor. */
    @Column(name = "codigo_entrega", nullable = false, length = 4)
    private String codigoEntrega;

    @Column(nullable = false, length = 3)
    private String moneda = "USD";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "url_pago", length = 600)
    private String urlPago;

    @Column(name = "nombre_contacto", length = 100)
    private String nombreContacto;

    @Column(name = "telefono_contacto", length = 20)
    private String telefonoContacto;

    @Column(name = "direccion_entrega", length = 200)
    private String direccionEntrega;

    @Column(length = 300)
    private String nota;

    /** Momento en que las lineas quedaron congeladas (al aceptar CONFIRMAR_PEDIDO). */
    @Column(name = "congelado_en")
    private LocalDateTime congeladoEn;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    /** Bloqueo optimista: evita que dos eventos simultaneos pisen el mismo pedido. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PedidoDetalle> detalles = new ArrayList<>();

    protected Pedido() {
    }

    public Pedido(Usuario usuario, String codigoEntrega) {
        this.usuario = usuario;
        this.codigoEntrega = codigoEntrega;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public Usuario getUsuario() { return usuario; }
    /** Username del duenio (lo usan la autorizacion y los DTO). */
    public String getCliente() { return usuario.getUsername(); }
    public String getCodigoEntrega() { return codigoEntrega; }
    public String getMoneda() { return moneda; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public String getUrlPago() { return urlPago; }
    public void setUrlPago(String urlPago) { this.urlPago = urlPago; }
    public LocalDateTime getCongeladoEn() { return congeladoEn; }
    public boolean isCongelado() { return congeladoEn != null; }
    public void congelar() { this.congeladoEn = LocalDateTime.now(); }
    public List<PedidoDetalle> getDetalles() { return detalles; }
    public EstadoPedido getEstado() { return estado; }
    public String getNombreContacto() { return nombreContacto; }
    public String getTelefonoContacto() { return telefonoContacto; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public String getNota() { return nota; }

    /** Solo un pedido en CARRITO (no congelado) admite cambios de lineas o de datos de entrega. */
    public boolean isEditable() {
        return estado == EstadoPedido.CARRITO && congeladoEn == null;
    }

    public void exigirEditable() {
        if (!isEditable()) {
            throw new IllegalStateException("El pedido " + codigo + " ya no se puede modificar (" + estado + ")");
        }
    }

    public void datosEntrega(String nombre, String telefono, String direccion, String nota) {
        exigirEditable();
        this.nombreContacto = nombre;
        this.telefonoContacto = telefono;
        this.direccionEntrega = direccion;
        this.nota = nota;
        this.actualizadoEn = LocalDateTime.now();
    }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }

    /** Total en la unidad minima de la moneda (USD: centavos). */
    public long totalEnCentavos() {
        return total.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    /** Suma las lineas. Sin impuestos ni envio en el MVP: total = subtotal. */
    public void recalcularTotales() {
        this.actualizadoEn = LocalDateTime.now();
        this.subtotal = detalles.stream().map(PedidoDetalle::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.total = subtotal;
    }

    public void cambiarEstado(EstadoPedido nuevo) {
        this.estado = nuevo;
        this.actualizadoEn = LocalDateTime.now();
    }
}
