package co.colmacar.pedidos.servicio;

import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.dominio.PedidoDetalle;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stripe Checkout (modo prueba). Al entrar a PAGO_PENDIENTE abre una Checkout Session por el total CONGELADO
 * del pedido en USD, con una linea por producto y metadata.codigoPedido. El resultado llega por
 * POST /api/webhooks/pagos; aqui nunca se cambia el estado del pedido.
 */
@Component
@ConditionalOnProperty(name = "colmacar.modo", havingValue = "real")
public class PasarelaStripe implements PasarelaPagos {

    /** Minimo que Stripe acepta en USD. */
    static final long MINIMO_CENTAVOS = 50;

    private final String llaveSecreta;
    private final String urlPublica;
    private final long expiraMinutos;

    public PasarelaStripe(@Value("${stripe.secret-key:}") String llaveSecreta,
                          @Value("${colmacar.url-publica:http://localhost:8080}") String urlPublica,
                          @Value("${colmacar.stripe.expira-minutos:60}") long expiraMinutos,
                          @Value("${colmacar.stripe.permitir-live:false}") boolean permitirLive) {
        if (llaveSecreta == null || llaveSecreta.isBlank()) {
            throw new IllegalStateException("colmacar.modo=real requiere la variable de entorno STRIPE_SECRET_KEY");
        }
        if (!llaveSecreta.startsWith("sk_test_") && !permitirLive) {
            throw new IllegalStateException("Solo se aceptan llaves de prueba (sk_test_...). "
                    + "Para produccion: colmacar.stripe.permitir-live=true");
        }
        this.llaveSecreta = llaveSecreta;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
        this.expiraMinutos = Math.max(30, Math.min(expiraMinutos, 24 * 60));   // limites de Stripe
    }

    /** En modo real nadie decide el pago por tiempo: lo decide el webhook. */
    @Override
    public boolean cobrar(Pedido pedido) {
        throw new UnsupportedOperationException("En modo real el resultado del pago llega por el webhook");
    }

    @Override
    public String nombre() {
        return "STRIPE";
    }

    @Override
    public Optional<SesionPago> iniciarCobro(Pedido pedido) {
        SessionCreateParams params = parametros(pedido);
        try {
            Session s = Session.create(params, RequestOptions.builder().setApiKey(llaveSecreta).build());
            return Optional.of(new SesionPago(s.getUrl(), s.getId()));
        } catch (StripeException e) {
            throw new PasarelaNoDisponibleException("Stripe no pudo crear la sesion de pago: " + e.getMessage(), e);
        }
    }

    /** Parametros de la Checkout Session (sin red: se prueba en PasarelaStripeTest). */
    SessionCreateParams parametros(Pedido pedido) {
        if (!"USD".equalsIgnoreCase(pedido.getMoneda())) {
            throw new PasarelaNoDisponibleException("Moneda no soportada: " + pedido.getMoneda(), null);
        }
        if (pedido.totalEnCentavos() < MINIMO_CENTAVOS) {
            throw new PasarelaNoDisponibleException("Stripe exige un total minimo de 0.50 USD", null);
        }
        String codigo = pedido.getCodigo();
        SessionCreateParams.Builder b = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(urlPublica + "/mis-pedidos?pago=ok&codigo=" + codigo)
                .setCancelUrl(urlPublica + "/mis-pedidos?pago=cancelado&codigo=" + codigo)
                .setClientReferenceId(codigo)
                .setExpiresAt(Instant.now().plusSeconds(expiraMinutos * 60).getEpochSecond())
                .putMetadata("codigoPedido", codigo)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("codigoPedido", codigo).build());
        if (pedido.getUsuario().getEmail() != null) {
            b.setCustomerEmail(pedido.getUsuario().getEmail());
        }
        long suma = 0;
        for (PedidoDetalle d : pedido.getDetalles()) {
            long unitario = d.getPrecioUnitario().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            suma += unitario * d.getCantidad();
            b.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) d.getCantidad())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("usd")
                            .setUnitAmount(unitario)
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(d.getNombre() + (d.isEsMayorista() ? " (precio mayorista)" : ""))
                                    .build())
                            .build())
                    .build());
        }
        if (suma != pedido.totalEnCentavos()) {   // Stripe cobra la suma de lineas: debe ser el total congelado
            throw new PasarelaNoDisponibleException("Las lineas no suman el total del pedido " + codigo, null);
        }
        return b.build();
    }
}
