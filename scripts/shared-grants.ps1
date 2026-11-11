param([Parameter(Mandatory=$true)][string[]]$Members)
# Generates SQL only. Does not connect or execute; no passwords are stored here.
$users=@('san_nha_render')+$Members
if(($users | Select-Object -Unique).Count -ne $users.Count){throw 'Duplicate database usernames.'}
$tables=@('accounts','blocked_slots','bookings','court_chat_messages','court_conversations','court_images','courts','notifications','price_rules','reviews','stored_uploads')
foreach($name in $users) {
 if($name -notmatch '^san_nha_[a-z0-9_]{1,23}$' -or $name -eq 'san_nha_migrator'){throw 'Use distinct san_nha_ usernames; migrator is reserved.'}
 Write-Output "-- NEW account only. CREATE USER fails if it already exists. Save the generated password privately."
 Write-Output "CREATE USER '$name'@'%' IDENTIFIED BY RANDOM PASSWORD REQUIRE SSL;"
 foreach($table in $tables){Write-Output "GRANT SELECT, INSERT, UPDATE, DELETE ON defaultdb.$table TO '$name'@'%';"}
 Write-Output "SHOW GRANTS FOR '$name'@'%';"
}
Write-Output "-- Deployment-only account; never distribute to members."
Write-Output "CREATE USER 'san_nha_migrator'@'%' IDENTIFIED BY RANDOM PASSWORD REQUIRE SSL;"
Write-Output "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON defaultdb.* TO 'san_nha_migrator'@'%';"
Write-Output "SHOW GRANTS FOR 'san_nha_migrator'@'%';"
