# Envia al webhook un evento de pago firmado igual que Stripe (Stripe-Signature: t=...,v1=HMAC-SHA256),
# para probar sin la CLI de Stripe.
# Uso:  $env:STRIPE_WEBHOOK_SECRET = "whsec_..."
#       .\scripts\simular-pago.ps1 -Codigo PED-0005 -Resultado aprobado -Centavos 800
#       (Centavos = total del pedido en USD x 100; si no coincide el pago no se aprueba)
param(
    [Parameter(Mandatory = $true)][string]$Codigo,
    [ValidateSet("aprobado", "rechazado")][string]$Resultado = "aprobado",
    [long]$Centavos = 0,
    [string]$Url = "http://localhost:8080/api/webhooks/pagos"
)
$ErrorActionPreference = "Stop"

$secreto = $env:STRIPE_WEBHOOK_SECRET
if ([string]::IsNullOrEmpty($secreto)) {
    Write-Error "Define la variable de entorno STRIPE_WEBHOOK_SECRET (el mismo valor que usa la aplicacion)"
    exit 1
}
if ($Codigo -notmatch '^PED-\d+$') { Write-Error "Codigo invalido: $Codigo (ej. PED-0005)"; exit 1 }

if ($Resultado -eq "aprobado" -and $Centavos -le 0) { Write-Error "Indica -Centavos con el total del pedido"; exit 1 }
$tipo = if ($Resultado -eq "aprobado") { "checkout.session.completed" } else { "checkout.session.expired" }
$t = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$id = "evt_sim_{0}_{1}" -f $t, (Get-Random)
$payload = '{"id":"' + $id + '","object":"event","type":"' + $tipo + '","data":{"object":{"object":"checkout.session","payment_status":"paid","amount_total":' + $Centavos + ',"currency":"usd","metadata":{"codigoPedido":"' + $Codigo + '"}}}}'

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secreto)
$hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes("$t.$payload"))
$v1 = -join ($hash | ForEach-Object { $_.ToString("x2") })

Write-Host "POST $Url  ($tipo para $Codigo, evento $id)"
$cuerpo = [System.Text.Encoding]::UTF8.GetBytes($payload)
Invoke-RestMethod -Method Post -Uri $Url -ContentType "application/json" `
    -Headers @{ "Stripe-Signature" = "t=$t,v1=$v1" } -Body $cuerpo | ConvertTo-Json
