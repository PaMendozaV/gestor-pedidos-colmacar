# Gestor de pedidos AFD · Colmacar (V3)

Backend en **Java 17 + Spring Boot 3.2.2** que controla el ciclo de vida de un pedido con un
**Autómata Finito Determinista** M = (Q, Σ, δ, q₀, F), con login real, pasarela Stripe (modo prueba)
y panel web estático (HTML/CSS/JS sin frameworks).

## Cómo ejecutarlo

Requisitos: JDK 17 y Maven 3.9.

```bash
mvn test                # pruebas
mvn spring-boot:run     # http://localhost:8080 (modo simulado, H2 en memoria)
```

Usuarios demo (claves en `application.properties`, se guardan con BCrypt; cámbialas con `DEMO_CLAVE_*`):

| usuario | clave (desarrollo) | rol |
|---|---|---|
| `admin` | `admin123` | ADMIN: ve todo, no emite eventos |
| `cliente` | `cliente123` | CLIENTE: crea, confirma, paga y reintenta sus pedidos |
| `repartidor` | `repartidor123` | REPARTIDOR: confirma la entrega con el código del cliente |

Páginas: `/login` y `/registro` (Thymeleaf), `pedidos.html` (panel/listado), `index.html?codigo=PED-0001` (detalle).

Admin: define `ADMIN_CLAVE_INICIAL` (mín. 8). En el arranque crea `admin` o migra su hash scrypt de Flask a BCrypt;
una clave BCrypt ya válida nunca se sobrescribe. Usuarios demo solo con `DATOS_DEMO=true`.

## Tienda (Pasos 3 y 4)

- Público: `/`, `/catalogo`, `/nosotros`, `/productos/buscar?q=` (JSON, solo ACTIVO). Admin: `/admin/**` (ROLE_ADMIN),
  imágenes a ImageKit con `IMAGEKIT_PRIVATE_KEY`, eliminar = baja lógica (`fkcods=0`).
- Carrito: el navegador guarda `{productoId, cantidad}`; `POST /api/carrito/sincronizar` (CLIENTE + `X-XSRF-TOKEN`)
  recalcula detal/mayorista (`colmacar.mayorista.umbral`, 50 por defecto) y total en USD. Un solo carrito por cliente.
- `/checkout` valida líneas y datos de entrega y dispara `CONFIRMAR_PEDIDO`: las líneas se congelan, se crea un
  `ttpago` PENDIENTE y en modo real Stripe cobra el total. `/mis-pedidos` lista los pedidos. WhatsApp queda como opción secundaria.

## Modos (`colmacar.modo`)

| modo | pago | despacho |
|---|---|---|
| `simulado` (por defecto) | el `MotorAutomatico` decide por tiempo con `PasarelaSimulada` | el motor, por tiempo |
| `real` | Stripe Checkout + `POST /api/webhooks/pagos` | solo `POST /api/bodega/despachos/{codigo}` |

En modo real el motor no decide nada. En ambos modos los eventos pasan por `PedidoService.procesarEvento`
(δ + auditoría); nunca se cambia el estado directamente.

### Stripe en modo prueba (Windows, PowerShell)

```powershell
stripe login                                                     # una vez
stripe listen --events checkout.session.completed,checkout.session.expired,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed --forward-to localhost:8080/api/webhooks/pagos
# en OTRA terminal (el whsec_ lo imprime stripe listen):
$env:STRIPE_SECRET_KEY="sk_test_xxx"; $env:STRIPE_WEBHOOK_SECRET="whsec_xxx"; $env:COLMACAR_MODO="real"
$env:ADMIN_CLAVE_INICIAL="ClaveAdmin2026"; $env:DB_URL="jdbc:postgresql://localhost:5432/colmacar"; $env:DB_PASSWORD="..."
mvn spring-boot:run
```

Regístrate como cliente, agrega productos, «Ir a pagar» → Stripe Checkout → tarjeta `4242 4242 4242 4242`
(fecha futura, cualquier CVC). Rechazo: `4000 0000 0000 0002`. Stripe vuelve a `/mis-pedidos` y el webhook mueve el pedido.

| evento Stripe | AFD (SISTEMA) |
|---|---|
| `checkout.session.completed` con `payment_status=paid` y `amount_total` = total congelado | `PAGO_APROBADO` |
| `checkout.session.async_payment_succeeded` | `PAGO_APROBADO` |
| `checkout.session.expired`, `checkout.session.async_payment_failed` | `PAGO_RECHAZADO` |
| `payment_intent.payment_failed` | ninguno (el cliente puede reintentar en la misma sesión) |

Firma inválida → 400. Firma válida → 200 con `PROCESADO`, `DUPLICADO`, `IGNORADO` o `MONTO_NO_COINCIDE`.
Sin CLI: `scripts\simular-pago.ps1 -Codigo PED-0005 -Resultado aprobado -Centavos 800`.

### Bodega

```bash
curl -X POST localhost:8080/api/bodega/despachos/PED-0006 -H "X-Api-Key: $BODEGA_API_KEY"
```
401 clave incorrecta, 200 despachado, 409 si δ no lo permite, 404 si no existe.

### Producción (`application-prod.properties`)

`--spring.profiles.active=prod` con PostgreSQL y todo por variables de entorno: `DB_URL`, `DB_USER`, `DB_PASSWORD`,
`URL_PUBLICA`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `BODEGA_API_KEY` y, solo en el primer arranque,
`DATOS_DEMO=true` + `DEMO_CLAVE_ADMIN/CLIENTE/REPARTIDOR` para crear los usuarios.

## El autómata

| elemento | definición |
|---|---|
| Q | CARRITO, PAGO_PENDIENTE, PAGO_RECHAZADO, PREPARANDO_INVENTARIO, EN_TRANSITO, ENTREGADO |
| Σ | CONFIRMAR_PEDIDO, PAGO_APROBADO, PAGO_RECHAZADO, REINTENTAR_PAGO, DESPACHO_ESCANEADO, ENTREGA_CONFIRMADA |
| q₀ | CARRITO |
| F | ENTREGADO |

| estado \ evento | CONFIRMAR_PEDIDO | PAGO_APROBADO | PAGO_RECHAZADO | REINTENTAR_PAGO | DESPACHO_ESCANEADO | ENTREGA_CONFIRMADA |
|---|---|---|---|---|---|---|
| CARRITO | PAGO_PENDIENTE | — | — | — | — | — |
| PAGO_PENDIENTE | — | PREPARANDO_INVENTARIO | PAGO_RECHAZADO | — | — | — |
| PAGO_RECHAZADO | — | — | — | PAGO_PENDIENTE | — | — |
| PREPARANDO_INVENTARIO | — | — | — | — | EN_TRANSITO | — |
| EN_TRANSITO | — | — | — | — | — | ENTREGADO |
| ENTREGADO | — | — | — | — | — | — |

`—` significa que δ no está definida: el evento **se rechaza**, el pedido no cambia y el intento
queda registrado en el historial. La tabla vive en una sola clase:
`automata/TablaTransiciones.java`. No hay condicionales anidados en el resto del sistema.

## API REST (con sesión; el rol sale del usuario autenticado)

Las peticiones que cambian algo llevan `X-XSRF-TOKEN` (valor de la cookie `XSRF-TOKEN`). Sin sesión: **401**.

| método y ruta | qué hace |
|---|---|
| `POST /login` (form `username`, `password`) · `POST /logout` | sesión |
| `GET /api/me` | usuario y rol |
| `POST /api/pedidos` `{"clienteRef":"Ref: 342"}` | solo CLIENTE; crea en CARRITO con código de entrega de 4 dígitos |
| `GET /api/pedidos?codigo=&estado=` | el cliente ve solo los suyos; ADMIN y REPARTIDOR, todos |
| `GET /api/pedidos/{codigo}` | `codigoEntrega` y `urlPago` solo para su cliente; pedido ajeno = 404 |
| `POST /api/pedidos/{codigo}/eventos` `{"evento":"ENTREGA_CONFIRMADA","codigoEntrega":"1234"}` | **200** aceptado, **403** rol no autorizado (o SISTEMA), **409** δ no definida, **422** código de entrega incorrecto, **404** inexistente o ajeno |
| `GET /api/pedidos/{codigo}/historial` | auditoría de eventos aceptados y rechazados |
| `GET /api/automata` | definición formal del AFD |
| `POST /api/webhooks/pagos` | público, firma `Stripe-Signature`, sin CSRF |
| `POST /api/bodega/despachos/{codigo}` | público, `X-Api-Key`, sin CSRF |

## Supuestos

1. El webhook usa la firma de Stripe (`stripe.webhook-secret`), que es HMAC-SHA256; reemplaza a `colmacar.pasarela.secreto` de la Parte 1.
2. Se añadieron `stripe-java` (pedido en la sección Stripe) y el driver `postgresql` (sin él el perfil prod no arranca).
3. Los pedidos no tienen total: Stripe cobra un monto fijo configurable (`colmacar.pago.monto`, `colmacar.moneda`).
4. `payment_intent.payment_failed` puede llegar y luego el cliente pagar en la misma sesión; queda PAGO_RECHAZADO y un aviso en el log para revisión.
5. Las llaves tienen valor vacío por defecto en desarrollo para que el modo simulado arranque sin variables; en prod son obligatorias.

## Estructura

```
automata/     EstadoPedido, EventoPedido, Rol, FuenteEvento, TablaTransiciones (delta y responsable) - sin cambios
dominio/      Pedido (cliente, codigoEntrega, urlPago), RegistroTransicion, Usuario, EventoWebhook
seguridad/    SeguridadConfig (form login, sesión, CSRF), UsuarioDetallesService, UsuarioActual
servicio/     PedidoService, MotorAutomatico (+Planificador), PasarelaPagos, PasarelaSimulada, PasarelaStripe, WebhookPagosService
web/          PedidoController, IntegracionesController (webhook + bodega), Dtos, ApiExceptionHandler
static/       login.html, pedidos.html, index.html, css/estilos.css, js/{comun,login,listado,consulta}.js
scripts/      simular-pago.sh, simular-pago.ps1
```
