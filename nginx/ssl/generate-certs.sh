openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout nginx-selfsigned.key \
  -out nginx-selfsigned.crt \
  -subj "/C=BG/ST=Sofia/L=Sofia/O=FinLab Fraud Shield/CN=localhost" \
  -addext "subjectAltName=DNS:localhost"

chmod 600 nginx-selfsigned.key
chmod 644 nginx-selfsigned.crt

echo "SSL certificate and key generated for localhost."