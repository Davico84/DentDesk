<#
.SYNOPSIS
    Descifra una copia de la base (add-database-backups), la restaura y verifica que está completa y que el
    aislamiento entre clínicas sigue intacto. No imprime datos de pacientes: solo PASA / FALLA.

.DESCRIPTION
    Comprobaciones (docs/deployment.md §4.3-4.4):
      - el artifact trae solo el .age; sin la clave privada no descifra, con ella sí;
      - restaura como un rol NO superusuario, sin dueños ni permisos (--no-owner --no-acl);
      - orden de restauración: la tabla antes que sus datos, y los datos antes de la policy y del ENABLE RLS;
      - conteos por tabla iguales a los de producción (leídos con odontorisas_backup);
      - los permisos de la guía alcanzan a odontorisas_app en todas las tablas y secuencias;
      - la función SECURITY DEFINER de V24 queda con el dueño que restauró y search_path=public;
      - huella de RLS (tablas, flags, policies y expresiones) igual a la de producción;
      - odontorisas_app sin BYPASSRLS y aislado: con una clínica inexistente ve 0 pacientes y con la propia, solo
        los suyos; en local, además, con una segunda clínica de prueba cada una ve solo lo suyo.

    Dos destinos:
      - local (por defecto): PostgreSQL 16 en Docker, rol `restorer`. Para la revisión mensual.
      - -Remote: una base VACÍA de una rama de Neon, restaurando como odontorisas_admin (caso 2 de la guía,
        reemplazar producción). Antes: crear la rama y la base (ver §4.4). No toca Render.

    Las URLs y contraseñas se piden por teclado (no quedan en el historial) y viajan a los contenedores por
    variables de entorno. Al terminar borra el contenedor y los archivos descifrados. Requiere docker, gh y age.

.EXAMPLE
    # Revisión mensual: la copia más reciente, en local
    .\scripts\verify-backup-restore.ps1 -RunId 40376699092 -KeyPath "$HOME\Documents\dentdesk-claves\dentdesk-backup.key"

.EXAMPLE
    # Ensayo de reemplazo en una rama de Neon (base vacía `restored`, dueño odontorisas_admin)
    .\scripts\verify-backup-restore.ps1 -RunId 40376699092 -KeyPath ... -Remote -RemoteDatabase restored
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $RunId,
    [Parameter(Mandatory = $true)] [string] $KeyPath,
    [switch] $Remote,
    [string] $RemoteDatabase = "restored",
    # Solo para probar el script: un .age local en vez de descargar el artifact.
    [string] $AgeFile
)

# En PowerShell 5.1, 'Stop' convierte el stderr de docker en error: se revisa $LASTEXITCODE.
$ErrorActionPreference = "Continue"
$work = Join-Path $env:TEMP "dentdesk-restore-$RunId"
$ctr = "dentdesk-restore-check"
$img = "postgres:16"
$script:fails = 0

function Ok($m)  { Write-Host "PASA  $m" -ForegroundColor Green }
function Bad($m) { Write-Host "FALLA $m" -ForegroundColor Red; $script:fails++ }
function WriteLf($path, $text) { [IO.File]::WriteAllText($path, ($text -replace "`r`n", "`n")) }
function ReadSecret($prompt) {
    $sec = Read-Host -AsSecureString $prompt
    return [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
}

# SQL contra una URL (variable de entorno $envName): el SQL va en un archivo, la URL nunca en la línea de comando
# (PowerShell 5.1 rompe las comillas dobles al pasar argumentos a docker).
function UrlSql($envName, $sql) {
    WriteLf (Join-Path $work "q.sql") $sql
    $env:VBR_URL = (Get-Item "env:$envName").Value
    $out = docker run --rm -e VBR_URL -v "${work}:/w:ro" $img sh -c 'psql -X -q -At -v ON_ERROR_STOP=1 -d $VBR_URL -f /w/q.sql' 2>$null
    $code = $LASTEXITCODE
    Remove-Item env:VBR_URL -ErrorAction SilentlyContinue
    if ($code -ne 0) { throw "psql falló contra $envName (detalle oculto)" }
    return $out
}
# SQL en el contenedor local, como $user, en la base restore. El stderr se descarta: un error de PostgreSQL puede
# traer valores de filas en su DETAIL (p. ej. una violación de unicidad).
function LocalSql($user, $sql) {
    WriteLf (Join-Path $work "q.sql") $sql
    docker cp (Join-Path $work "q.sql") "${ctr}:/tmp/q.sql" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "no se pudo copiar el SQL al contenedor" }
    $out = docker exec -e PGPASSWORD=local $ctr psql -X -q -At -v ON_ERROR_STOP=1 -h localhost -U $user -d restore -f /tmp/q.sql 2>$null
    if ($LASTEXITCODE -ne 0) { throw "psql local falló ($user, detalle oculto)" }
    return $out
}
# Tres papeles en la base restaurada: el dueño (restaura y concede), el lector (catálogo y conteos, salta RLS)
# y la app (aislamiento).
function OwnerSql($sql)  { if ($Remote) { UrlSql "VBR_ADMIN_URL" $sql } else { LocalSql "restorer" $sql } }
function ReaderSql($sql) { if ($Remote) { UrlSql "VBR_READER_URL" $sql } else { LocalSql "postgres" $sql } }
function AppSql($sql)    { if ($Remote) { UrlSql "VBR_APP_URL" $sql } else { LocalSql "odontorisas_app" $sql } }
function ProdSql($sql)   { UrlSql "VBR_PROD_URL" $sql }

$countsSql = "SELECT table_name || '|' || (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name), false, true, '')))[1]::text FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY 1"
$huellaSql = @"
SELECT md5(string_agg(f.fila, E'\n' ORDER BY f.fila)) FROM (
  SELECT concat_ws('|', c.relname, c.relrowsecurity, c.relforcerowsecurity,
         COALESCE(p.policyname, '<SIN_POLICY>'), COALESCE(p.cmd, '<NULL>'),
         COALESCE((SELECT string_agg(r, ',' ORDER BY r) FROM unnest(p.roles) AS r), '<NULL>'),
         COALESCE(p.qual, '<NULL>'), COALESCE(p.with_check, '<NULL>')) AS fila
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
  LEFT JOIN pg_policies p ON p.schemaname = 'public' AND p.tablename = c.relname
  WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')) f
"@
# El CASE evita que PostgreSQL evalúe la función sobre otro tipo de relación (falla con una tabla TOAST).
$sinPermisoSql = @"
SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND CASE WHEN c.relkind IN ('r', 'p') THEN NOT has_table_privilege('odontorisas_app', c.oid, 'SELECT, INSERT, UPDATE, DELETE')
           WHEN c.relkind = 'S' THEN NOT has_sequence_privilege('odontorisas_app', c.oid, 'USAGE, SELECT')
           ELSE false END
"@

try {
    New-Item -ItemType Directory -Force $work | Out-Null

    # --- Descarga y descifrado ----------------------------------------------------------------------
    if ($AgeFile) { Copy-Item $AgeFile $work } else { gh run download $RunId -D $work | Out-Null }
    $enc = Get-ChildItem $work -Recurse -Filter "*.dump.age" | Select-Object -First 1
    if (-not $enc) { throw "El run $RunId no tiene un .dump.age" }
    $others = Get-ChildItem $work -Recurse -File | Where-Object { $_.FullName -ne $enc.FullName }
    if ($others) { Bad "el artifact trae más que el .age: $($others.Name -join ', ')" } else { Ok "el artifact contiene solo $($enc.Name)" }

    $null = & age -d -o (Join-Path $work "sin-clave.dump") $enc.FullName 2>&1
    if ($LASTEXITCODE -ne 0) { Ok "sin la clave privada, age no descifra" } else { Bad "age descifró SIN la clave" }
    Remove-Item (Join-Path $work "sin-clave.dump") -ErrorAction SilentlyContinue

    $dump = Join-Path $work "db.dump"
    & age -d -i $KeyPath -o $dump $enc.FullName
    if ($LASTEXITCODE -ne 0) { throw "age no pudo descifrar con la clave (¿es la clave de Dentdesk?)" }
    Ok "con la clave privada, la copia descifra"

    # --- Conexiones -----------------------------------------------------------------------------------
    if (-not $env:VBR_PROD_URL) { $env:VBR_PROD_URL = ReadSecret "URL de odontorisas_backup en PRODUCCIÓN (no se muestra)" }
    if ($Remote) {
        if ($env:VBR_PROD_URL -notmatch '^postgres(ql)?://([^:]+):([^@]+)@([^/]+)/([^?]+)(\?.*)?$') { throw "La URL de producción no tiene la forma postgresql://usuario:clave@host/base?..." }
        $readerPwd = $Matches[3]; $query = if ($Matches[6]) { $Matches[6] } else { "?sslmode=require" }
        # Si ya están en el entorno (p. ej. al probar el script), no se piden.
        if (-not $env:VBR_ADMIN_URL) {
            $branchHost = Read-Host "Host DIRECTO de la rama de Neon (sin -pooler)"
            $adminPwd = ReadSecret "Contraseña de odontorisas_admin (no se muestra)"
            $appPwd = ReadSecret "Contraseña de odontorisas_app (no se muestra)"
            $env:VBR_ADMIN_URL = "postgresql://odontorisas_admin:$adminPwd@$branchHost/$RemoteDatabase$query"
            $env:VBR_APP_URL = "postgresql://odontorisas_app:$appPwd@$branchHost/$RemoteDatabase$query"
            $env:VBR_READER_URL = "postgresql://odontorisas_backup:$readerPwd@$branchHost/$RemoteDatabase$query"
        }
        $owner = "odontorisas_admin"
        $hay = UrlSql "VBR_ADMIN_URL" "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relkind = 'r'"
        if ($hay -ne "0") { throw "La base $RemoteDatabase de la rama no está vacía ($hay tablas): no se restaura encima" }
    } else {
        $owner = "restorer"
        docker rm -f $ctr 2>$null | Out-Null
        docker run -d --name $ctr -e POSTGRES_PASSWORD=local $img | Out-Null
        for ($i = 0; $i -lt 30; $i++) { docker exec $ctr pg_isready -q -U postgres 2>$null; if ($LASTEXITCODE -eq 0) { break }; Start-Sleep 1 }
        Start-Sleep 2
        # Una sentencia por llamada: psql -c con varias las corre en una transacción y CREATE DATABASE no puede.
        foreach ($stmt in "CREATE ROLE restorer LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'local'",
                          "CREATE ROLE odontorisas_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'local'",
                          # V32: las políticas `TO CURRENT_USER` llegan al volcado como `TO odontorisas_admin`;
                          # sin el rol, pg_restore aborta. Sin login: solo existe para recrear la política.
                          "CREATE ROLE odontorisas_admin NOLOGIN NOSUPERUSER NOBYPASSRLS",
                          "CREATE DATABASE restore OWNER restorer") {
            docker exec -e PGPASSWORD=local $ctr psql -X -q -v ON_ERROR_STOP=1 -h localhost -U postgres -c $stmt | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "no se pudo preparar la base local ($stmt)" }
        }
        docker exec -e PGPASSWORD=local $ctr psql -X -q -v ON_ERROR_STOP=1 -h localhost -U postgres -d restore -c "ALTER SCHEMA public OWNER TO restorer" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "no se pudo preparar el esquema local" }
    }

    # --- Restauración como el dueño (no superusuario) ------------------------------------------------
    if ($Remote) {
        docker run --rm -e VBR_ADMIN_URL -v "${work}:/w:ro" $img sh -c 'pg_restore --no-owner --no-acl --exit-on-error -d $VBR_ADMIN_URL /w/db.dump' 2>$null
    } else {
        docker cp $dump "${ctr}:/tmp/db.dump" | Out-Null
        docker exec -e PGPASSWORD=local $ctr pg_restore --no-owner --no-acl --exit-on-error -h localhost -U restorer -d restore /tmp/db.dump 2>$null
    }
    if ($LASTEXITCODE -eq 0) { Ok "restaurada como rol no superusuario ($owner)" } else { throw "pg_restore como $owner falló (detalle oculto)" }

    # --- Orden de restauración (el de pg_restore, no el del índice --list) --------------------------
    # FORCE va con la tabla (pre-data) y ENABLE al final (post-data). Solo se imprimen números de línea.
    WriteLf (Join-Path $work "orden.sh") @'
pg_restore -f - /w/db.dump > /tmp/full.sql
for t in patients consultation_note; do
  for pat in "^CREATE TABLE public\.$t " "^COPY public\.$t " "^CREATE POLICY organization_isolation ON public\.$t " "^ALTER TABLE public\.$t ENABLE ROW LEVEL SECURITY"; do
    n=$(grep -n -m1 -E "$pat" /tmp/full.sql | cut -d: -f1); echo "$t ${n:--1}"
  done
done
'@
    $orderOut = docker run --rm -v "${work}:/w:ro" $img sh /w/orden.sh
    foreach ($t in "patients", "consultation_note") {
        $pos = @($orderOut | Where-Object { $_ -like "$t *" } | ForEach-Object { [int](($_ -split " ")[1]) })
        if (($pos -notcontains -1) -and $pos[0] -lt $pos[1] -and $pos[1] -lt $pos[2] -and $pos[1] -lt $pos[3]) { Ok "orden en ${t}: tabla -> datos -> policy y ENABLE RLS" }
        else { Bad "orden en ${t}: líneas $($pos -join ', ')" }
    }

    # --- Conteos contra producción ---------------------------------------------------------------
    $prodCounts = ProdSql $countsSql
    $restCounts = ReaderSql $countsSql
    $diff = Compare-Object @($prodCounts) @($restCounts)
    if (@($restCounts).Count -eq 0) { Bad "la copia restaurada no tiene tablas" }
    elseif (-not $diff) { Ok "conteos por tabla iguales a producción ($(@($restCounts).Count) tablas)" }
    else { Bad "conteos distintos en: $((($diff | ForEach-Object { ($_.InputObject -split '\|')[0] }) | Sort-Object -Unique) -join ', ') (si hubo actividad en producción después de la copia, es esperable)" }

    # --- Permisos de la guía (§4.4 paso 3), como el dueño --------------------------------------------
    $base = if ($Remote) { $RemoteDatabase } else { "restore" }
    OwnerSql @"
GRANT CONNECT ON DATABASE $base TO odontorisas_app;
GRANT USAGE ON SCHEMA public TO odontorisas_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO odontorisas_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO odontorisas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE $owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO odontorisas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE $owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO odontorisas_app;
"@ | Out-Null
    $sinPermiso = ReaderSql $sinPermisoSql
    if ($sinPermiso -eq "0") { Ok "odontorisas_app tiene permisos en todas las tablas y secuencias" } else { Bad "$sinPermiso tablas o secuencias sin permiso para odontorisas_app" }

    # --- Función de V24, huella de RLS, rol de la app y aislamiento -----------------------------------
    $fn = ReaderSql "SELECT r.rolname || '|' || p.prosecdef || '|' || array_to_string(p.proconfig, ',') FROM pg_proc p JOIN pg_roles r ON r.oid = p.proowner WHERE p.proname = 'current_user_is_org_admin'"
    # @(...).Count: con dos filas, -eq sobre un array filtra en vez de comparar y pasaría igual.
    if (@($fn).Count -eq 1 -and $fn -eq "$owner|true|search_path=public") { Ok "función de V24: dueño $owner, SECURITY DEFINER, search_path=public" } else { Bad "función de V24: $fn" }

    $hProd = ProdSql $huellaSql
    $hRest = ReaderSql $huellaSql
    if ($hProd -and $hProd -eq $hRest) { Ok "huella de RLS igual a producción" } else { Bad "huella de RLS distinta (prod $hProd / restaurada $hRest)" }

    $rolApp = ReaderSql "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = 'odontorisas_app'"
    if ($rolApp -eq "f") { Ok "odontorisas_app sin SUPERUSER ni BYPASSRLS" } else { Bad "odontorisas_app salta RLS ($rolApp)" }

    # Sin pacientes no hay nada que aislar: contar 0 en todas partes no prueba nada.
    function AppCount($orgId) { AppSql "BEGIN; SET LOCAL app.user_id = '0'; SET LOCAL app.organization_id = '$orgId'; SELECT count(*) FROM patients; COMMIT;" }
    $org = ReaderSql "SELECT organization_id FROM patients ORDER BY id LIMIT 1"
    if (-not $org) { Bad "aislamiento: la copia no tiene pacientes, no se puede verificar" }
    else {
        $deOrg = ReaderSql "SELECT count(*) FROM patients WHERE organization_id = $org"
        $veOtra = AppCount 999999999
        $veSuya = AppCount $org
        if ($veOtra -eq "0" -and $veSuya -eq $deOrg) { Ok "aislamiento: una clínica inexistente ve 0 pacientes; la propia ve los suyos ($veSuya de $deOrg)" }
        else { Bad "aislamiento: una clínica inexistente ve $veOtra; la propia ve $veSuya de $deOrg" }

        # Dos clínicas reales, solo en local (el contenedor se descarta; en -Remote la base puede pasar a ser
        # producción y no se le escriben datos de prueba). Producción tiene una sola clínica: se crea la segunda
        # copiando un paciente de la primera, como superusuario (salta RLS), y se cuenta como la app en los dos
        # sentidos. La huella de RLS ya probó que las policies son las de producción.
        if (-not $Remote) {
            $orgB = LocalSql "postgres" "INSERT INTO organizations (name, slug) VALUES ('Prueba de aislamiento', 'prueba-aislamiento-' || md5(random()::text)) RETURNING id;"
            LocalSql "postgres" @"
CREATE TEMP TABLE copia AS SELECT * FROM patients WHERE organization_id = $org ORDER BY id LIMIT 1;
UPDATE copia SET id = (SELECT max(id) + 1 FROM patients), organization_id = $orgB;
INSERT INTO patients SELECT * FROM copia;
"@ | Out-Null
            $aVe = AppCount $org
            $bVe = AppCount $orgB
            if ($aVe -eq $deOrg -and $bVe -eq "1") { Ok "dos clínicas: cada una ve solo lo suyo (A $aVe de $deOrg, B $bVe de 1)" }
            else { Bad "dos clínicas: A ve $aVe (esperado $deOrg), B ve $bVe (esperado 1)" }
        }
    }
}
catch {
    Bad $_.Exception.Message
}
finally {
    foreach ($v in "VBR_PROD_URL", "VBR_ADMIN_URL", "VBR_APP_URL", "VBR_READER_URL") { Remove-Item "env:$v" -ErrorAction SilentlyContinue }
    if (-not $Remote) {
        docker rm -f $ctr 2>$null | Out-Null
        if (docker ps -a -q -f "name=^${ctr}$" 2>$null) { Bad "Limpieza: el contenedor $ctr sigue existiendo y tiene la copia restaurada: docker rm -f $ctr" }
    }
    # Un archivo bloqueado (antivirus, un editor abierto) puede impedir el borrado: se reintenta y se comprueba.
    for ($i = 0; $i -lt 5 -and (Test-Path $work); $i++) {
        Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
        if (Test-Path $work) { Start-Sleep 1 }
    }
    if (Test-Path $work) { Bad "Limpieza: NO se pudo borrar $work, que puede tener la copia descifrada: borrarlo a mano" }
    else { Write-Host "Limpieza: archivos descifrados borrados$(if (-not $Remote) { ' y contenedor eliminado' })." }
}
if ($script:fails -eq 0) { Write-Host "`nTodo OK." -ForegroundColor Green } else { Write-Host "`n$($script:fails) falla(s)." -ForegroundColor Red; exit 1 }
