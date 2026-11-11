param(
 [Parameter(Mandatory=$true)][string]$CaPath,
 [Parameter(Mandatory=$true)][string]$Username,
 [int]$Port=8082
)
$ErrorActionPreference='Stop'
if($Username -eq 'avnadmin'){throw 'Use your own DML-only database user, not avnadmin.'}
$repo=Split-Path -Parent $PSScriptRoot
$ca=(Resolve-Path -LiteralPath $CaPath).Path
$cache=Join-Path ([IO.Path]::GetTempPath()) ('san-nha-shared-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $cache | Out-Null
$truststore=Join-Path $cache 'aiven.p12'
$previousPreference=$ErrorActionPreference
$ErrorActionPreference='Continue'
& keytool -importcert -noprompt -alias aiven-ca -file $ca -keystore $truststore -storetype PKCS12 -storepass changeit 2> (Join-Path $cache 'keytool.log') | Out-Null
$caExit=$LASTEXITCODE
$ErrorActionPreference=$previousPreference
if($caExit -ne 0){throw 'CA import failed.'}
$names=@('SHARED_DB_USERNAME','SHARED_DB_PASSWORD','SHARED_DB_TRUSTSTORE_URL','SHARED_SERVER_PORT','SPRING_PROFILES_ACTIVE')
$saved=@{}
foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try {
 $secret=Read-Host 'Your Aiven database password (hidden)' -AsSecureString
 $env:SHARED_DB_USERNAME=$Username
 $env:SHARED_DB_PASSWORD=[Net.NetworkCredential]::new('',$secret).Password
 $env:SHARED_DB_TRUSTSTORE_URL=([Uri]::new($truststore)).AbsoluteUri
 $env:SHARED_SERVER_PORT=[string]$Port
 $env:SPRING_PROFILES_ACTIVE='shared'
 Push-Location $repo
 try {
   & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=shared'
   $runExit=$LASTEXITCODE
 } finally {Pop-Location}
 if($runExit -ne 0){throw 'Shared application stopped with an error; inspect sanitized startup logs.'}
} finally {
 foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
 if($secret){$secret.Dispose()}
}
