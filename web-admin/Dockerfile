FROM node:22-alpine AS build
WORKDIR /app

COPY web-admin/package.json web-admin/package-lock.json ./
RUN npm ci
COPY web-admin ./
RUN npm run build

FROM nginx:1.27-alpine
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
