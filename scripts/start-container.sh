#!/bin/sh
set -eu
# Render Secret Files are plaintext; convert the public CA at runtime.
if [ -f /etc/secrets/aiven-ca.pem ]; then
  umask 077
  tls_dir="$(mktemp -d /tmp/san-nha-tls.XXXXXX)"
  keytool -importcert -noprompt -alias aiven-ca -file /etc/secrets/aiven-ca.pem \
    -keystore "$tls_dir/truststore.p12" -storetype PKCS12 -storepass changeit >/dev/null 2>&1 || {
      echo "Cannot initialize MySQL TLS truststore." >&2; exit 1;
    }
  mv -f "$tls_dir/truststore.p12" /tmp/aiven-truststore.p12
  rmdir "$tls_dir"
fi
exec java -jar /app/app.jar "$@"
