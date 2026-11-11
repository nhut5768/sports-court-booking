param(
 [string]$DatabaseUser='court_app',
 [ValidatePattern('^[a-zA-Z][a-zA-Z0-9_]{0,63}$')]
 [string]$DatabaseName='sports_court_booking',
 [switch]$Restart,
 [switch]$SkipBuild,
 [switch]$ForgetCredentials,
 [switch]$NoRemember
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$jar=Join-Path $repo 'target/sports-court-booking-1.0.0.jar'
. (Join-Path $PSScriptRoot 'local-credentials.ps1')
$vaultTarget='SanNha/localhost8082'
if($ForgetCredentials){[SanNhaLocalVault]::Delete($vaultTarget);Write-Host 'Saved local credentials removed. Running website unchanged.';return}
$saved=@{}
if(-not $NoRemember){
 try {
  $stored=[SanNhaLocalVault]::Read($vaultTarget)
  if($stored){$decoded=$stored | ConvertFrom-Json;foreach($property in $decoded.PSObject.Properties){$saved[$property.Name]=[string]$property.Value}}
 } catch {Write-Host 'Local vault unavailable; secure prompts will be used.'}
 finally {$stored=$null;$decoded=$null}
}
$process=$null
$started=$false
$secretObjects=@()
$redactions=@()
$http=$null

function Read-SecretValue([string]$Prompt) {
 $secure=Read-Host $Prompt -AsSecureString
 $script:secretObjects+=,$secure
 return [Net.NetworkCredential]::new('',$secure).Password
}
function Write-SafeLine([string]$Line) {
 if($null -eq $Line){return}
 foreach($secret in $script:redactions){if(-not [string]::IsNullOrEmpty($secret)){$Line=$Line.Replace($secret,'[REDACTED]')}}
 Write-Host $Line
}
function Has-HeaderSafeValue([string]$Value) {
 return [string]::IsNullOrEmpty($Value) -or ($Value.Length -le 4096 -and $Value -cmatch '^[!-~]+$')
}
function Get-StartupStatus {
 try {
  $health=$http.GetAsync('http://127.0.0.1:8082/health').GetAwaiter().GetResult()
  if([int]$health.StatusCode -ne 200){return $null}
  $login=$http.GetStringAsync('http://127.0.0.1:8082/login').GetAwaiter().GetResult()
  $ai=$http.GetStringAsync('http://127.0.0.1:8082/api/assistant/status').GetAwaiter().GetResult() | ConvertFrom-Json
  $chooser=$false
  if($login -match 'href="/oauth2/authorization/google"'){
   $oauth=$http.GetAsync('http://127.0.0.1:8082/oauth2/authorization/google').GetAwaiter().GetResult()
   $chooser=([int]$oauth.StatusCode -eq 302 -and $oauth.Headers.Location.Host -eq 'accounts.google.com')
  }
  return [pscustomobject]@{
   GoogleButton=($login -match 'href="/oauth2/authorization/google"')
   GoogleRedirect=$chooser
   AiAvailable=[bool]$ai.available
   OwnerRegistration=($login -match '/register/owner')
  }
 } catch {return $null}
}

try {
 # Stop only the recognizable San Nha JAR bound explicitly to local port 8082.
 $running=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
  $_.CommandLine -match 'sports-court-booking-1[.]0[.]0[.]jar' -and
  $_.CommandLine -match '--server[.]port=8082(?:\s|$)' -and
  $_.CommandLine -match '--spring[.]profiles[.]active=mysql(?:\s|$)'
 })
 if($running.Count -gt 0) {
  if(-not $Restart){throw 'San Nha is already running. Stop it with Ctrl+C, or run this script with -Restart.'}
  foreach($old in $running){Stop-Process -Id $old.ProcessId;Wait-Process -Id $old.ProcessId -Timeout 15 -ErrorAction SilentlyContinue}
 }
 $listener=@(Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue)
 if($listener.Count -gt 0){throw 'Port 8082 is occupied by another process; no unrelated process was stopped.'}
 if(-not $SkipBuild) {
  Push-Location $repo
  try {& .\mvnw.cmd -q -DskipTests package; $buildExit=$LASTEXITCODE} finally {Pop-Location}
  if($buildExit -ne 0){throw 'Build failed before requesting secrets.'}
 }
 if(-not (Test-Path -LiteralPath $jar)){throw 'Application JAR missing. Run without -SkipBuild.'}

 # Reuse process environment, then user environment; never display values.
 function Existing([string]$Name) {
  $value=[Environment]::GetEnvironmentVariable($Name,'Process')
  if([string]::IsNullOrWhiteSpace($value)){$value=[Environment]::GetEnvironmentVariable($Name,'User')}
  if([string]::IsNullOrWhiteSpace($value) -and $saved.ContainsKey($Name)){$value=$saved[$Name]}
  return $value
 }
 $databasePassword=Existing 'DB_PASSWORD'
 if(-not $databasePassword){$databasePassword=Read-SecretValue 'Mat khau MySQL local (nhap an)'}
 if(-not $databasePassword){throw 'DB_PASSWORD is required.'}
 $existingUser=Existing 'DB_USERNAME'
 if($existingUser -and -not $PSBoundParameters.ContainsKey('DatabaseUser')){$DatabaseUser=$existingUser}
 $clientId=Existing 'GOOGLE_CLIENT_ID'
 $googleSecret=Existing 'GOOGLE_CLIENT_SECRET'
 if(($clientId -or $googleSecret -or -not $saved.ContainsKey('GOOGLE_CLIENT_ID')) -and ($clientId -notmatch '^[A-Za-z0-9.-]+[.]apps[.]googleusercontent[.]com$' -or -not $googleSecret)){
  $clientId=(Read-Host 'GOOGLE_CLIENT_ID (Enter de tat Google)').Trim()
  $googleSecret=Read-SecretValue 'GOOGLE_CLIENT_SECRET (nhap an; Enter de tat Google)'
 }
 if(($clientId -and -not $googleSecret) -or ($googleSecret -and -not $clientId)){throw 'Provide both Google credentials.'}
 if($clientId -and $clientId -notmatch '^[A-Za-z0-9.-]+[.]apps[.]googleusercontent[.]com$'){throw 'Invalid Google Client ID format.'}
 $geminiKey=Existing 'GEMINI_API_KEY'
 if(-not $geminiKey -and -not $saved.ContainsKey('GEMINI_API_KEY')){$geminiKey=Read-SecretValue 'GEMINI_API_KEY (nhap an; Enter de tat AI)'}
 $model=Existing 'GEMINI_MODEL'
 $redactions=@($databasePassword,$googleSecret,$geminiKey) | Where-Object {$_}
 if(-not (Has-HeaderSafeValue $googleSecret) -or -not (Has-HeaderSafeValue $geminiKey)){throw 'Invalid secret format; values are not printed.'}
 if($geminiKey){
  Add-Type -AssemblyName System.Net.Http
  $probe=[Net.Http.HttpClient]::new();$probe.Timeout=[TimeSpan]::FromSeconds(20)
  [void]$probe.DefaultRequestHeaders.TryAddWithoutValidation('x-goog-api-key',$geminiKey)
  try {
   $models=@();$pageToken=''
   do {
    $url='https://generativelanguage.googleapis.com/v1beta/models?pageSize=100'
    if($pageToken){$url+='&pageToken='+[Uri]::EscapeDataString($pageToken)}
    $response=$probe.GetAsync($url).GetAwaiter().GetResult()
    if(-not $response.IsSuccessStatusCode){throw 'Model discovery failed.'}
    $catalog=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    $models+=@($catalog.models | Where-Object {$_.supportedGenerationMethods -contains 'generateContent' -and $_.name -match '^models/gemini-[A-Za-z0-9.-]{1,70}$'} | ForEach-Object {$_.name.Substring(7)})
    $pageToken=$catalog.nextPageToken
   } while($pageToken)
   if($models.Count -eq 0){throw 'No supported models.'}
   if($model -notin $models){
    Write-Host 'Models returned by Gemini for this key:'
    for($i=0;$i -lt $models.Count;$i++){Write-Host (('{0}: {1}' -f ($i+1),$models[$i]))}
    $choice=0
    if(-not [int]::TryParse((Read-Host 'Chon so model'),[ref]$choice) -or $choice -lt 1 -or $choice -gt $models.Count){throw 'Invalid model selection.'}
    $model=$models[$choice-1]
   }
   $payload='{"contents":[{"parts":[{"text":"Reply OK"}]}],"generationConfig":{"maxOutputTokens":2048}}'
   $body=[Net.Http.StringContent]::new($payload,[Text.Encoding]::UTF8,'application/json')
   $result=$probe.PostAsync(('https://generativelanguage.googleapis.com/v1beta/models/'+$model+':generateContent'),$body).GetAwaiter().GetResult()
   if(-not $result.IsSuccessStatusCode){throw 'Generation probe failed.'}
   $reply=$result.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
   if(-not $reply.candidates[0].content.parts.text){throw 'Empty generation response.'}
   Write-Host 'Gemini model discovered and actual generateContent call succeeded.'
  } catch {
   Write-Host 'Gemini verification failed (key, quota, model or network). AI probe unavailable; valid saved configuration is retained. Website and owner messages remain available.'
   # Keep configured credentials on transient provider/network errors; later chats can recover.
   if($model -cnotmatch '^gemini-[A-Za-z0-9.-]{1,70}$'){$model=''}
  } finally {$probe.Dispose()}
 } else {$model='';Write-Host 'GEMINI_API_KEY missing: AI unavailable.'}
 if(-not $clientId){Write-Host 'Google credentials missing: Google login unavailable.'}

 $info=[Diagnostics.ProcessStartInfo]::new()
 $info.FileName=(Get-Command java -ErrorAction Stop).Source
 $info.Arguments='-jar "'+$jar+'" --spring.profiles.active=mysql --server.port=8082 --server.address=127.0.0.1 --spring.jpa.hibernate.ddl-auto=validate --spring.flyway.enabled=true --app.admin.bootstrap=false --app.owner.create=false --app.demo.seed=false --spring.mvc.log-request-details=false --logging.level.org.springframework.web=WARN --logging.level.org.springframework.security=INFO --logging.level.org.hibernate.SQL=WARN'
 $info.WorkingDirectory=$repo
 $info.UseShellExecute=$false
 $info.CreateNoWindow=$true
 $info.RedirectStandardOutput=$true
 $info.RedirectStandardError=$true
 $variables=@{
  DB_URL=('jdbc:mysql://127.0.0.1:3306/'+$DatabaseName+'?serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8')
  DB_USERNAME=$DatabaseUser
  DB_PASSWORD=$databasePassword
  GOOGLE_CLIENT_ID=$clientId
  GOOGLE_CLIENT_SECRET=$googleSecret
  GEMINI_API_KEY=$geminiKey
  GEMINI_MODEL=$model
  SPRING_PROFILES_ACTIVE='mysql'
  DEBUG='false'
  TRACE='false'
  JAVA_TOOL_OPTIONS='-Djdk.httpclient.HttpClient.log=none'
 }
 # Prevent inherited Render/shared datasource/Flyway overrides in a member's terminal.
 foreach($name in @('SPRING_APPLICATION_JSON','SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','SPRING_FLYWAY_URL','SPRING_FLYWAY_USER','SPRING_FLYWAY_PASSWORD','JDK_JAVA_OPTIONS')){
  $info.EnvironmentVariables.Remove($name)
 }
 foreach($entry in $variables.GetEnumerator()){$info.EnvironmentVariables[$entry.Key]=[string]$entry.Value}
 $process=[Diagnostics.Process]::new();$process.StartInfo=$info
 [void]$process.Start();$started=$true
 Write-Host 'Java started with profile mysql on http://localhost:8082. Secret values are never printed.'
 Add-Type -AssemblyName System.Net.Http
 $handler=[Net.Http.HttpClientHandler]::new();$handler.AllowAutoRedirect=$false
 $http=[Net.Http.HttpClient]::new($handler);$http.Timeout=[TimeSpan]::FromSeconds(2)
 $output=$process.StandardOutput.ReadLineAsync();$errorLine=$process.StandardError.ReadLineAsync()
 $reported=$false;$nextCheck=[DateTime]::UtcNow.AddSeconds(3)
 while(-not $process.HasExited) {
  if($output.IsCompleted){$line=$output.GetAwaiter().GetResult();Write-SafeLine $line;if($null -ne $line){$output=$process.StandardOutput.ReadLineAsync()}}
  if($errorLine.IsCompleted){$line=$errorLine.GetAwaiter().GetResult();Write-SafeLine $line;if($null -ne $line){$errorLine=$process.StandardError.ReadLineAsync()}}
  if(-not $reported -and [DateTime]::UtcNow -ge $nextCheck) {
   $status=Get-StartupStatus
   if($null -ne $status) {
    Write-Host ('Google button: '+$status.GoogleButton+'; Google redirect: '+$status.GoogleRedirect+'; AI configured: '+$status.AiAvailable+'; Owner signup: '+$status.OwnerRegistration)
    Write-Host 'Status checks do not validate Google credentials or Gemini quota. Test one login and one chat in the browser.'
    if(-not $NoRemember){
     try {
      $remember=@{DB_USERNAME=$DatabaseUser;DB_PASSWORD=$databasePassword;GOOGLE_CLIENT_ID=$clientId;GOOGLE_CLIENT_SECRET=$googleSecret;GEMINI_API_KEY=$geminiKey;GEMINI_MODEL=$model}
      [SanNhaLocalVault]::Write($vaultTarget,($remember | ConvertTo-Json -Compress))
      Write-Host 'Local configuration saved in Windows Credential Manager. Next launch will reuse it.'
     } catch {Write-Host 'Could not save local vault; website continues. No secrets were printed.'}
     finally {if($remember){$remember.Clear()}}
    }
    $reported=$true
   }
   $nextCheck=[DateTime]::UtcNow.AddSeconds(3)
  }
  Start-Sleep -Milliseconds 100
 }
 # Finish any pending asynchronous read before draining, without writing a file.
 if($output){Write-SafeLine ($output.GetAwaiter().GetResult())}
 if($errorLine){Write-SafeLine ($errorLine.GetAwaiter().GetResult())}
 Write-SafeLine $process.StandardOutput.ReadToEnd()
 Write-SafeLine $process.StandardError.ReadToEnd()
 if($process.ExitCode -ne 0){throw 'Java stopped with an error. Only redacted console output is shown.'}
} finally {
 if($http){$http.Dispose()}
 if($process){if($started -and -not $process.HasExited){$process.Kill();$process.WaitForExit(5000) | Out-Null};$process.Dispose()}
 foreach($item in $secretObjects){$item.Dispose()}
 if($variables){$variables.Clear()}
 if($info){$info.EnvironmentVariables.Clear()}
 $saved.Clear()
 $redactions=@();$databasePassword=$null;$googleSecret=$null;$geminiKey=$null
}
