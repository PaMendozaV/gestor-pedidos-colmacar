package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.dominio.Pedido;
import java.math.BigDecimal;
import java.util.List;

/** Carrito calculado SOLO en el servidor (precios, mayorista, total). */
public record CarritoDto(String codigo, String moneda, List<Linea> lineas, int unidades, BigDecimal subtotal,
                         BigDecimal total, int umbralMayorista, List<Integer> omitidos) {

    public record Linea(Integer productoId, String nombre, String imagen, int cantidad, BigDecimal precioUnitario,
                        boolean mayorista, BigDecimal subtotal) {
    }

    public static CarritoDto vacio(int umbral) {
        return new CarritoDto(null, "USD", List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, umbral, List.of());
    }

    /** Debe llamarse dentro de una transaccion (lineas y productos son LAZY). */
    public static CarritoDto de(Pedido p, int umbral, List<Integer> omitidos) {
        List<Linea> lineas = p.getDetalles().stream().map(d -> new Linea(d.getProducto().getId(), d.getNombre(),
                d.getProducto().getImagen(), d.getCantidad(), d.getPrecioUnitario(), d.isEsMayorista(),
                d.getSubtotal())).toList();
        int unidades = lineas.stream().mapToInt(Linea::cantidad).sum();
        return new CarritoDto(p.getCodigo(), p.getMoneda(), lineas, unidades, p.getSubtotal(), p.getTotal(), umbral,
                omitidos);
    }
}
