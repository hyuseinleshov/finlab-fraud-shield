FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY ssl /etc/nginx/ssl

EXPOSE 80
EXPOSE 443

HEALTHCHECK CMD pgrep nginx || exit 1
