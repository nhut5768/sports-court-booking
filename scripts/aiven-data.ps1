param(
  [ValidateSet('Inspect', 'Prepare', 'Transfer')]
  [string]$Mode = 'Inspect',

  [string]$CaPath,
  [string]$SourceHost = '127.0.0.1',
  [int]$SourcePort = 3306,
  [string]$SourceDatabase = 'sports_court_booking',
  [string]$SourceUser = 'court_app',
  [switch]$ConfirmApplicationsPaused
)

$ErrorActionPreference = 'Stop'

if ($Mode -eq 'Transfer' -and -not $ConfirmApplicationsPaused) {
  throw 'Pause the local app and suspend Render before Transfer. Use Inspect first.'
}

$repo = Split-Path -Parent $PSScriptRoot

$driver = Get-ChildItem `
    (Join-Path $env:USERPROFILE '.m2/repository/com/mysql/mysql-connector-j') `
    -Recurse -Filter '*.jar' |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $driver) {
  throw 'Run mvnw.cmd -DskipTests package once to obtain the JDBC driver.'
}

$vars = @(
  'SOURCE_DB_URL',
  'SOURCE_DB_USERNAME',
  'SOURCE_DB_PASSWORD',
  'SOURCE_UPLOAD_DIR',
  'TARGET_DB_URL',
  'TARGET_DB_USERNAME',
  'TARGET_DB_PASSWORD',
  'TRANSFER_BACKUP_DIR',
  'TRANSFER_APPLICATIONS_PAUSED'
)

$saved = @{}
foreach ($name in $vars) {
  $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

$backupName = (
  'aiven-' + $Mode.ToLower() + '-' +
  (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' +
  [guid]::NewGuid().ToString('N').Substring(0, 6)
)

$backup = Join-Path $repo "backups/$backupName"
$previousConsoleEncoding = [Console]::OutputEncoding
$sourceSecret = $null
$targetSecret = $null

try {
  [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

  New-Item -ItemType Directory -Path $backup | Out-Null

  if ($env:OS -eq 'Windows_NT') {
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name

    & icacls.exe $backup /inheritance:r `
      /grant:r "$($identity):(OI)(CI)F" | Out-Null

    if ($LASTEXITCODE -ne 0) {
      throw 'Could not restrict backup directory permissions.'
    }
  }

  $sourceSecret = Read-Host 'Local MySQL password (hidden)' -AsSecureString

  $env:SOURCE_DB_PASSWORD = [System.Net.NetworkCredential]::new(
    '', $sourceSecret
  ).Password

  $env:SOURCE_DB_URL = "jdbc:mysql://$($SourceHost):$SourcePort/$($SourceDatabase)?serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8&connectTimeout=10000&socketTimeout=60000"
  $env:SOURCE_DB_USERNAME = $SourceUser
  $env:SOURCE_UPLOAD_DIR = Join-Path $repo 'uploads'
  $env:TRANSFER_BACKUP_DIR = $backup
  $env:TRANSFER_APPLICATIONS_PAUSED = if ($ConfirmApplicationsPaused) {
    'yes'
  } else {
    'no'
  }

  if ($Mode -ne 'Prepare') {
    if ([string]::IsNullOrWhiteSpace($CaPath)) {
      $CaPath = Read-Host 'Full path to the CA PEM downloaded from Aiven'
    }

    $ca = (Resolve-Path -LiteralPath $CaPath).Path
    $truststore = Join-Path $backup 'aiven-truststore.p12'

    # Password for the CA-only truststore.
    $previousPreference = $ErrorActionPreference

    try {
      $ErrorActionPreference = 'Continue'

      & keytool -importcert -noprompt `
        -alias aiven-ca `
        -file $ca `
        -keystore $truststore `
        -storetype PKCS12 `
        -storepass changeit `
        2> (Join-Path $backup 'keytool.log') | Out-Null

      $keytoolExit = $LASTEXITCODE
    } finally {
      $ErrorActionPreference = $previousPreference
    }

    if ($keytoolExit -ne 0) {
      throw 'CA import failed; no target connection was made.'
    }

    $trustUri = ([System.Uri]::new($truststore)).AbsoluteUri

    $env:TARGET_DB_URL = "jdbc:mysql://mysql-3bbe504b-ut-59dc.l.aivencloud.com:23004/defaultdb?sslMode=VERIFY_IDENTITY&trustCertificateKeyStoreUrl=$trustUri&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8&connectTimeout=10000&socketTimeout=60000"
    $env:TARGET_DB_USERNAME = 'avnadmin'

    $targetSecret = Read-Host `
      'Aiven MySQL password (hidden; not stored in a file)' `
      -AsSecureString

    $env:TARGET_DB_PASSWORD = [System.Net.NetworkCredential]::new(
      '', $targetSecret
    ).Password
  }

  $javaArguments = @(
    '-Dstdout.encoding=UTF-8'
    '-Dstderr.encoding=UTF-8'
    '--class-path'
    $driver.FullName
    (Join-Path $PSScriptRoot 'CourtDataTransfer.java')
    $Mode.ToLower()
  )

  & java @javaArguments
  $transferExit = $LASTEXITCODE

  if ($transferExit -ne 0) {
    throw "Stopped (code $transferExit). Do not retry with a dump import or force overwrite."
  }

  Write-Output "Completed $Mode. Private backup directory: $backup"
} finally {
  [Console]::OutputEncoding = $previousConsoleEncoding

  foreach ($name in $vars) {
    [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
  }

  if ($sourceSecret) {
    $sourceSecret.Dispose()
  }

  if ($targetSecret) {
    $targetSecret.Dispose()
  }
}