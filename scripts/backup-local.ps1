param(
 [ValidatePattern('^[a-zA-Z][a-zA-Z0-9_]{0,63}$')][string]$DatabaseName='sports_court_booking',
 [ValidatePattern('^[A-Za-z0-9_]+$')][string]$DatabaseUser='',
 [string]$BackupRoot='',
 [string]$UploadDir='',
 [string]$MysqlBin=''
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
. (Join-Path $PSScriptRoot 'local-credentials.ps1')
. (Join-Path $PSScriptRoot 'local-mysql.ps1')
# SQL transaction snapshots do not freeze filesystem images. Stop writers first.
$writers=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
 $_.CommandLine -match 'sports-court-booking.*[.]jar|vn[.]uth[.]court[.]CourtApplication'
})
if($writers.Count){throw 'Stop the local San Nha application before backup, then run again. No process was stopped by this script.'}
if(-not $BackupRoot){$BackupRoot=Join-Path (Split-Path -Parent $repo) 'san-nha-backups'}
$BackupRoot=[IO.Path]::GetFullPath($BackupRoot)
if($BackupRoot.Equals($repo,[StringComparison]::OrdinalIgnoreCase) -or $BackupRoot.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
 throw 'BackupRoot must be outside the project/Git directory.'
}
if($BackupRoot -match '["\r\n]'){throw 'Invalid backup directory.'}
if(-not $UploadDir){$UploadDir=$env:UPLOAD_DIR;if(-not $UploadDir){$UploadDir=Join-Path $repo 'uploads'}}
if(-not [IO.Path]::IsPathRooted($UploadDir)){$UploadDir=Join-Path $repo $UploadDir}
$UploadDir=[IO.Path]::GetFullPath($UploadDir)
if(-not $MysqlBin){
 $command=Get-Command mysqldump.exe -ErrorAction SilentlyContinue
 if($command){$MysqlBin=Split-Path -Parent $command.Source}else{
  $MysqlBin=Get-ChildItem 'C:\Program Files\MySQL' -Directory -ErrorAction SilentlyContinue |
   ForEach-Object {Join-Path $_.FullName 'bin'} | Where-Object {Test-Path -LiteralPath (Join-Path $_ 'mysqldump.exe')} | Select-Object -First 1
 }
}
if(-not $MysqlBin -or -not (Test-Path -LiteralPath (Join-Path $MysqlBin 'mysqldump.exe'))){throw 'mysqldump.exe not found; supply -MysqlBin.'}
$vault=$null;$password=$null;$secure=$null;$child=$null;$info=$null;$folder=$null
try {
 $encoded=[SanNhaLocalVault]::Read('SanNha/localhost8082')
 if($encoded){$vault=$encoded | ConvertFrom-Json};$encoded=$null
 if(-not $DatabaseUser){$resolvedUser=$env:DB_USERNAME;if(-not $resolvedUser -and $vault){$resolvedUser=$vault.DB_USERNAME};if(-not $resolvedUser){$resolvedUser='court_app'};$DatabaseUser=$resolvedUser}
 if($DatabaseUser -notmatch '^[A-Za-z0-9_]+$'){throw 'Invalid database username.'}
 # Do not use another account's remembered password when a different user was requested.
 $password=$env:DB_PASSWORD
 if(-not $password -and $vault -and $vault.DB_USERNAME -eq $DatabaseUser){$password=$vault.DB_PASSWORD}
 if(-not $password){$secure=Read-Host 'Local MySQL application password (hidden)' -AsSecureString;$password=[Net.NetworkCredential]::new('',$secure).Password}
 $engineCheck=Invoke-LocalMysql ("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+$DatabaseName+"' AND table_type='BASE TABLE' AND engine<>'InnoDB';") $DatabaseUser $password
 if($engineCheck -ne '0'){throw 'Consistent export requires all source tables to use InnoDB.'}
 $history=Invoke-LocalMysql ("SELECT COUNT(*) FROM "+$DatabaseName+".flyway_schema_history WHERE success=0;") $DatabaseUser $password
 if($history -ne '0'){throw 'Flyway has failed entries; inspect migrations before backing up.'}
 # Read only object keys, never print account data or filenames.
 $references=Invoke-LocalMysql ("SELECT CONCAT('courts/',court_id,'/',stored_name) FROM "+$DatabaseName+".court_images UNION ALL SELECT CONCAT('payment-qr/',id,'/',payment_qr) FROM "+$DatabaseName+".accounts WHERE payment_qr IS NOT NULL;") $DatabaseUser $password
 $blobKeys=Invoke-LocalMysql ("SELECT object_key FROM "+$DatabaseName+".stored_uploads;") $DatabaseUser $password
 $knownBlobs=@{};foreach($key in ($blobKeys -split '\r?\n')){if($key){$knownBlobs[$key]=$true}}
 $diskAssets=0
 foreach($key in ($references -split '\r?\n')){
  if(-not $key){continue}
  if($key -notmatch '^(courts|payment-qr)/[1-9][0-9]*/[A-Fa-f0-9-]+[.](jpg|png|webp)$'){throw 'Unexpected image object key; no backup was produced.'}
  $path=Join-Path $UploadDir $key
  if(Test-Path -LiteralPath $path -PathType Leaf){$diskAssets++}
  elseif(-not $knownBlobs.ContainsKey($key)){throw 'Referenced image/QR is missing from both filesystem and stored_uploads. Restore the missing file before producing a complete backup.'}
 }
 if(Test-Path -LiteralPath $UploadDir){
  $links=@(Get-Item -LiteralPath $UploadDir;Get-ChildItem -LiteralPath $UploadDir -Force -Recurse) | Where-Object {($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0}
  if($links){throw 'Upload directory contains filesystem links; choose an ordinary local upload directory.'}
 }
 $folder=Join-Path $BackupRoot ($DatabaseName+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8))
 [void][IO.Directory]::CreateDirectory($folder)
 # Restrict the backup before mysqldump creates sensitive files.
 $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
 $identity=[Security.Principal.WindowsIdentity]::GetCurrent().User
 $inherit=[Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
 foreach($sid in @($identity,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){
  $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl',$inherit,'None','Allow'))
 }
 Set-Acl -LiteralPath $folder -AclObject $acl
 $partial=Join-Path $folder 'database.sql.partial';$sqlFile=Join-Path $folder 'database.sql'
 $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=Join-Path $MysqlBin 'mysqldump.exe'
 # A single database argument: deliberately no --databases, USE, CREATE DATABASE or DROP.
 $info.Arguments='--host=127.0.0.1 --port=3306 --user='+$DatabaseUser+' --default-character-set=utf8mb4 --single-transaction --quick --skip-lock-tables --skip-add-locks --skip-add-drop-table --no-tablespaces --set-gtid-purged=OFF --hex-blob --column-statistics=0 --result-file="'+$partial+'" '+$DatabaseName
 $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 $info.EnvironmentVariables['MYSQL_PWD']=$password
 $child=[Diagnostics.Process]::new();$child.StartInfo=$info
 [void]$child.Start();$stdout=$child.StandardOutput.ReadToEndAsync();$stderr=$child.StandardError.ReadToEndAsync()
 if(-not $child.WaitForExit(300000)){$child.Kill();throw 'Backup timed out. Partial files are not valid backups.'}
 if($child.ExitCode -ne 0){throw 'mysqldump failed; check local privileges and client compatibility. Provider output was not printed. Partial files are not valid backups.'}
 if(-not (Test-Path -LiteralPath $partial) -or (Get-Item -LiteralPath $partial).Length -eq 0){throw 'SQL dump is empty.'}
 [IO.File]::Move($partial,$sqlFile)
 $zipFile=$null
 if(Test-Path -LiteralPath $UploadDir){
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zipFile=Join-Path $folder 'uploads.zip'
  [IO.Compression.ZipFile]::CreateFromDirectory($UploadDir,$zipFile,[IO.Compression.CompressionLevel]::Optimal,$false)
 }
 $manifest=[ordered]@{database=$DatabaseName;createdUtc=[DateTime]::UtcNow.ToString('o');host='127.0.0.1';port=3306;sqlSha256=(Get-FileHash -LiteralPath $sqlFile -Algorithm SHA256).Hash;uploadsSha256=$null;filesystemReferences=$diskAssets;flywayIncluded=$true;requiresEmptyRestoreTarget=$true}
 if($zipFile){$manifest.uploadsSha256=(Get-FileHash -LiteralPath $zipFile -Algorithm SHA256).Hash}
 [IO.File]::WriteAllText((Join-Path $folder 'manifest.json'),($manifest | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
 [IO.File]::WriteAllText((Join-Path $folder 'COMPLETE.txt'),'SQL and uploads backup finished. Restore only into a NEW EMPTY database. Keep this directory private.',[Text.UTF8Encoding]::new($false))
 Write-Host ('Backup complete: '+$folder)
 Write-Host 'SQL includes all tables/data and Flyway history. Existing records were exported without inventing sample data. Never import over the active database.'
} finally {
 if($child){if(-not $child.HasExited){$child.Kill()};$child.Dispose()}
 if($info){$info.EnvironmentVariables.Clear()}
 if($secure){$secure.Dispose()}
 $password=$null;$vault=$null;$references=$null;$blobKeys=$null
}
