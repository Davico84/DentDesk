<#
.SYNOPSIS
    Da de alta una clínica en Dentdesk como PLATFORM_ADMIN y envía la invitación a su primer administrador.

.DESCRIPTION
    Hace dos llamadas a la API (add-platform-admin):
      1. POST {BaseUrl}/platform/auth/login         -> token de plataforma (sin oid, dura JWT_ACCESS_TTL)
      2. POST {BaseUrl}/platform/organizations      -> clínica + configuración + invitación ADMIN
    La invitación llega por correo a -AdminEmail con el enlace y el código de verificación.
    La contraseña se pide por teclado (no queda en el historial) y el token no se guarda en ningún lado.

    Procedimiento completo y errores frecuentes: docs/deployment.md §11.

.EXAMPLE
    # Local (backend en el puerto 8080)
    .\scripts\new-clinic.ps1 -PlatformEmail admin@dentdesk.local -ClinicName "Clínica Sonrisas" -AdminEmail ana@correo.com

.EXAMPLE
    # Producción (la API va detrás del proxy de Vercel)
    .\scripts\new-clinic.ps1 -BaseUrl https://dentdeskapp.vercel.app/api -PlatformEmail ... -ClinicName ... -AdminEmail ...
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = "http://localhost:8080/api",
    [Parameter(Mandatory = $true)] [string] $PlatformEmail,
    [Parameter(Mandatory = $true)] [string] $ClinicName,
    [Parameter(Mandatory = $true)] [string] $AdminEmail
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

# Windows PowerShell 5.1 negocia TLS 1.0 por defecto; Render/Vercel exigen 1.2.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

<#
  POST JSON en UTF-8 y respuesta leída como UTF-8. Invoke-RestMethod de PowerShell 5.1 envía el cuerpo
  en ISO-8859-1 y decodifica la respuesta igual si no trae charset: "Clínica" llegaría rota.
  Devuelve el JSON ya parseado, o lanza un error con el detalle RFC 9457 del backend.
#>
function Invoke-DentdeskPost([string] $Path, [hashtable] $Body, [string] $Token) {
    $json = $Body | ConvertTo-Json -Compress
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    try {
        $response = Invoke-WebRequest -Method Post -Uri "$BaseUrl$Path" -Headers $headers -UseBasicParsing `
            -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($json))
        $reader = New-Object IO.StreamReader($response.RawContentStream, [Text.Encoding]::UTF8)
        return $reader.ReadToEnd() | ConvertFrom-Json
    } catch [Net.WebException] {
        $status = $null
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int] $_.Exception.Response.StatusCode
            $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream(), [Text.Encoding]::UTF8)
            $raw = $reader.ReadToEnd()
            try { $detail = ($raw | ConvertFrom-Json).detail } catch { $detail = $raw }
        }
        throw "POST $Path -> $status. $detail"
    }
}

Write-Host ""
Write-Host "Alta de clínica en $BaseUrl" -ForegroundColor Cyan
Write-Host "  Clínica:                $ClinicName"
Write-Host "  Primer administrador:   $AdminEmail  (recibirá la invitación por correo)"
Write-Host ""

$secure = Read-Host "Contraseña de $PlatformEmail" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    $login = Invoke-DentdeskPost "/platform/auth/login" @{ email = $PlatformEmail; password = $password }
} catch {
    Write-Host "No se pudo iniciar sesión: $_" -ForegroundColor Red
    Write-Host "401: correo/clave incorrectos, la identidad no es PLATFORM_ADMIN o está bloqueada 15 min por intentos fallidos."
    exit 1
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    Remove-Variable password -ErrorAction SilentlyContinue
}
Write-Host "Sesión de plataforma iniciada." -ForegroundColor Green

$answer = Read-Host "Se creará la clínica y se enviará un correo real a $AdminEmail. ¿Continuar? (s/N)"
if ($answer -notmatch '^(s|si|sí)$') {
    Write-Host "Cancelado. No se creó nada."
    exit 0
}

try {
    $clinic = Invoke-DentdeskPost "/platform/organizations" @{ organizationName = $ClinicName; adminEmail = $AdminEmail } $login.accessToken
} catch {
    Write-Host "No se creó la clínica: $_" -ForegroundColor Red
    Write-Host "409: el correo ya pertenece a una cuenta (usa otro). 503: no se pudo enviar el correo (se revirtió todo; reintenta). 400: datos inválidos."
    exit 1
}

Write-Host ""
Write-Host "Clínica creada e invitación enviada." -ForegroundColor Green
Write-Host "  Id:          $($clinic.organizationId)"
Write-Host "  Nombre:      $($clinic.name)"
Write-Host "  Slug:        $($clinic.slug)"
Write-Host "  Invitado:    $($clinic.adminEmail)"
Write-Host "  Vence:       $($clinic.invitationExpiresAt)"
Write-Host ""
Write-Host "El invitado debe abrir el enlace del correo e ingresar el código en menos de 10 minutos."
