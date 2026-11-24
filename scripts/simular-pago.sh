#!/usr/bin/env bash
# Envia al webhook un evento de pago firmado igual que Stripe (Stripe-Signature: t=...,v1=HMAC-SHA256),
# para probar sin la CLI de Stripe.
# Uso:  STRIPE_WEBHOOK_SECRET=whsec_... ./scripts/simular-pago.sh PED-0005 aprobado 800 [url]
#       (800 = total del pedido en centavos de USD; debe coincidir o el pago no se aprueba)
#       ./scripts/simular-pago.sh PED-0005 rechazado
set -euo pipefail

codigo="${1:?Uso: simular-pago.sh PED-0005 aprobado|rechazado [centavos] [url]}"
resultado="${2:-aprobado}"
monto="${3:-0}"
url="${4:-http://localhost:8080/api/webhooks/pagos}"
[[ "$monto" =~ ^[0-9]+$ ]] || { echo "Monto invalido (centavos): $monto" >&2; exit 1; }
secreto="${STRIPE_WEBHOOK_SECRET:?Define la variable de entorno STRIPE_WEBHOOK_SECRET (el mismo valor que usa la aplicacion)}"

[[ "$codigo" =~ ^PED-[0-9]+$ ]] || { echo "Codigo invalido: $codigo (ej. PED-0005)" >&2; exit 1; }
case "$resultado" in
  aprobado)  tipo="checkout.session.completed"; [[ "$monto" -gt 0 ]] || { echo "Indica el total en centavos" >&2; exit 1; } ;;
  rechazado) tipo="checkout.session.expired" ;;
  *) echo "Resultado invalido: use aprobado o rechazado" >&2; exit 1 ;;
esac

t="$(date +%s)"
id="evt_sim_${t}_${RANDOM}"
payload="{\"id\":\"$id\",\"object\":\"event\",\"type\":\"$tipo\",\"data\":{\"object\":{\"object\":\"checkout.session\",\"payment_status\":\"paid\",\"amount_total\":$monto,\"currency\":\"usd\",\"metadata\":{\"codigoPedido\":\"$codigo\"}}}}"
firma="$(printf '%s' "$t.$payload" | openssl dgst -sha256 -hmac "$secreto" | sed 's/^.*= //')"

echo "POST $url  ($tipo para $codigo, evento $id)"
curl -sS -X POST "$url" \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: t=$t,v1=$firma" \
  --data-binary "$payload"
echo
