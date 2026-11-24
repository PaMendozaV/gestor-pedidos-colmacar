package co.colmacar.pedidos.dominio;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Linea del pedido. Nombre y precio se copian del producto y se congelan al confirmar. */
@Entity
@Table(name = "ttpedidodetalle",
        uniqueConstraints = @UniqueConstraint(name = "ux_detalle_producto", columnNames = {"fkcodpedido", "fkcodproducto"}))
public class PedidoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkcoddetalle")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fkcodpedido")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fkcodproducto")
    private Producto producto;

    @Column(name = "nombre_p", nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "es_mayorista", nullable = false)
    private boolean esMayorista;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    protected PedidoDetalle() {
    }

    public PedidoDetalle(Pedido pedido, Producto producto, int cantidad, int umbral) {
        this.pedido = pedido;
        this.producto = producto;
        recalcular(cantidad, umbral);
    }

    /** Recalcula con el precio vigente del producto. Prohibido si el pedido ya esta congelado. */
    public void recalcular(int cantidad, int umbral) {
        if (pedido.isCongelado()) {
            throw new IllegalStateException("Las lineas del pedido " + pedido.getCodigo() + " estan congeladas");
        }
        this.cantidad = cantidad;
        this.nombre = producto.getNombre();
        this.precioUnitario = producto.precioPara(cantidad, umbral);
        this.esMayorista = producto.aplicaMayorista(cantidad, umbral);
        this.subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public Producto getProducto() { return producto; }
    public String getNombre() { return nombre; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public boolean isEsMayorista() { return esMayorista; }
    public BigDecimal getSubtotal() { return subtotal; }
}
