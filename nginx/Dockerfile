FROM nginx:1.27-alpine

# Copy nginx.conf file
COPY nginx.conf /etc/nginx/nginx.conf

# Copy SSL certificates (for local dev)
COPY ssl /etc/nginx/ssl

EXPOSE 80
EXPOSE 443

HEALTHCHECK CMD pgrep nginx || exit 1
