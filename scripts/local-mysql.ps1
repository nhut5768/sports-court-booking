# Shared local MySQL client helper. Passwords stay out of argv and files.
function Invoke-LocalMysql([string]$Sql,[string]$Username,[string]$Password) {
 if($Username -notmatch '^[A-Za-z0-9_]+$'){throw 'Invalid local database username.'}
 $command=Get-Command mysql.exe -ErrorAction SilentlyContinue
 if($command){$executable=$command.Source}else{
  $candidate=Get-ChildItem 'C:\Program Files\MySQL' -Directory -ErrorAction SilentlyContinue | ForEach-Object {Join-Path $_.FullName 'bin/mysql.exe'} | Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
  if(-not $candidate){throw 'MySQL client not found.'};$executable=$candidate
 }
 $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$executable
 $info.Arguments='--host=127.0.0.1 --port=3306 --user='+$Username+' --default-character-set=utf8mb4 --batch --skip-column-names'
 $info.UseShellExecute=$false;$info.CreateNoWindow=$true
 $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false)
 $info.EnvironmentVariables['MYSQL_PWD']=$Password
 $child=[Diagnostics.Process]::new();$child.StartInfo=$info
 try {
  [void]$child.Start();$output=$child.StandardOutput.ReadToEndAsync();$errors=$child.StandardError.ReadToEndAsync()
  $bytes=[Text.Encoding]::UTF8.GetBytes($Sql)
  $child.StandardInput.BaseStream.Write($bytes,0,$bytes.Length);$child.StandardInput.BaseStream.Flush();$child.StandardInput.Close()
  if(-not $child.WaitForExit(60000)){$child.Kill();throw 'Local MySQL operation timed out.'}
  if($child.ExitCode -ne 0){throw 'MySQL operation failed. Check database permissions and connection; no credentials were printed.'}
  return $output.GetAwaiter().GetResult().Trim()
 } finally {$child.Dispose();$info.EnvironmentVariables.Clear();$Password=$null}
}
