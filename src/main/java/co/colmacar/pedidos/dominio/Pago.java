package co.colmacar.pedidos.dominio;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Intento de pago de un pedido (ttpago). */
@Entity
@Table(name = "ttpago")
public class Pago {

    public enum Estado { PENDIENTE, APROBADO, RECHAZADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkcodpago")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fkcodpedido")
    private Pedido pedido;

    @Column(nullable = false, length = 20)
    private String proveedor;

    @Column(name = "referencia_externa", length = 255)
    private String referenciaExterna;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda = "USD";

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    protected Pago() {
    }

    public Pago(Pedido pedido, String proveedor, String referenciaExterna, BigDecimal monto) {
        this.pedido = pedido;
        this.proveedor = proveedor;
        this.referenciaExterna = referenciaExterna;
        this.monto = monto;
    }

    public void marcar(Estado nuevo) {
        this.estado = nuevo;
        this.actualizadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public String getProveedor() { return proveedor; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public Estado getEstado() { return estado; }
    public BigDecimal getMonto() { return monto; }
    public String getMoneda() { return moneda; }
}
