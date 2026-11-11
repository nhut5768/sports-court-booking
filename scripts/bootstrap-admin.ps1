param(
 [Parameter(Mandatory=$true)][string]$Email,
 [string]$Name='Quản trị Sân Nhà',
 [string]$DatabaseUser='court_app',
 [ValidateSet('sports_court_booking','sports_court_live')]
 [string]$DatabaseName='sports_court_booking'
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$names=@('DB_URL','DB_USERNAME','DB_PASSWORD','ADMIN_EMAIL','ADMIN_NAME','ADMIN_PASSWORD','SPRING_PROFILES_ACTIVE','DEBUG','TRACE')
$saved=@{}
foreach($key in $names){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
try {
 $dbSecret=Read-Host 'Local MySQL password (hidden)' -AsSecureString
 $adminSecret=Read-Host 'New unique administrator password, 12-64 characters (hidden)' -AsSecureString
 $env:DB_URL='jdbc:mysql://127.0.0.1:3306/'+$DatabaseName+'?serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8'
 $env:DB_USERNAME=$DatabaseUser
 $env:DB_PASSWORD=[Net.NetworkCredential]::new('',$dbSecret).Password
 $env:ADMIN_EMAIL=$Email
 $env:ADMIN_NAME=$Name
 $env:ADMIN_PASSWORD=[Net.NetworkCredential]::new('',$adminSecret).Password
 $env:SPRING_PROFILES_ACTIVE='mysql'
 $env:DEBUG='false';$env:TRACE='false'
 Push-Location $repo
 try {
  & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=mysql' '-Dspring-boot.run.arguments=--app.admin.bootstrap=true --app.owner.create=false --app.demo.seed=false --server.port=0 --server.address=127.0.0.1'
  $code=$LASTEXITCODE
 } finally {Pop-Location}
 if($code -ne 0){throw 'Bootstrap failed. Existing admin/account is never overwritten; inspect the safe error message.'}
} finally {
 foreach($key in $names){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
 if($dbSecret){$dbSecret.Dispose()};if($adminSecret){$adminSecret.Dispose()}
}
