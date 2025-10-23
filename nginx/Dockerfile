FROM nginx:1.27-alpine

RUN apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

COPY nginx.conf /etc/nginx/nginx.conf
COPY ssl /etc/nginx/ssl

RUN chmod 644 /etc/nginx/nginx.conf && \
    chmod 644 /etc/nginx/ssl/*

EXPOSE 80 443

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:80/ || exit 1

LABEL maintainer="Hyusein Leshov" \
      service="nginx" \
      version="1.0.0" \
      description="Nginx reverse proxy with TLS, HTTP/2, and rate limiting"

CMD ["nginx", "-g", "daemon off;"]
