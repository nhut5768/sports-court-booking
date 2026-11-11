param([string]$AdminUser='root',[switch]$SkipBuild)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'local-credentials.ps1')
. (Join-Path $PSScriptRoot 'local-mysql.ps1')
$repo=Split-Path -Parent $PSScriptRoot
$encoded=[SanNhaLocalVault]::Read('SanNha/localhost8082')
if(-not $encoded){throw 'Run run-local.ps1 once to save local credentials first.'}
$config=$encoded | ConvertFrom-Json;$encoded=$null
$appUser=[string]$config.DB_USERNAME
if($appUser -notmatch '^[A-Za-z0-9_]+$'){throw 'Invalid database username.'}
$ready=$false
try {
 $count=Invoke-LocalMysql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='sports_court_live' AND table_name='flyway_schema_history';" $appUser $config.DB_PASSWORD
 if($count -eq '1'){$ready=$true}
} catch {}
if(-not $ready){
 Write-Host 'First live setup: creating sports_court_live separately; sports_court_booking is preserved.'
 Write-Host 'MySQL root permission is required once because the app user only has access to the old database.'
 $secure=Read-Host 'MySQL admin password (hidden; never saved)' -AsSecureString
 $adminPassword=[Net.NetworkCredential]::new('',$secure).Password
 try {
  $count=Invoke-LocalMysql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='sports_court_live' AND table_type='BASE TABLE';" $AdminUser $adminPassword
  if($count -ne '0'){throw 'Live database contains tables already. No import was performed; inspect the existing schema.'}
  [void](Invoke-LocalMysql ("CREATE DATABASE IF NOT EXISTS sports_court_live CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON sports_court_live.* TO '"+$appUser+"'@'localhost';") $AdminUser $adminPassword)
 } finally {$adminPassword=$null;$secure.Dispose()}
 $sql=[IO.File]::ReadAllText((Join-Path $repo 'database/sports_court_live.sql'))
 [void](Invoke-LocalMysql $sql $appUser $config.DB_PASSWORD)
 $count=Invoke-LocalMysql 'SELECT (SELECT COUNT(*) FROM sports_court_live.accounts)+(SELECT COUNT(*) FROM sports_court_live.courts)+(SELECT COUNT(*) FROM sports_court_live.bookings);' $appUser $config.DB_PASSWORD
 if($count -ne '0'){throw 'Expected an empty live database.'}
 Write-Host 'Clean schema imported; no business records or demo accounts were copied.'
}
$config=$null
& (Join-Path $PSScriptRoot 'run-local.ps1') -DatabaseName sports_court_live -Restart -SkipBuild:$SkipBuild
