## step build static file
FROM node:lts-alpine as builder

WORKDIR /app

COPY . /app

RUN npm install

RUN npm run build

## COPY static file from builder to nginx
FROM nginx:alpine

COPY nginx/default.conf /etc/nginx/conf.d/

RUN rm -rf /usr/share/nginx/html/*

COPY --from=builder /app/out /usr/share/nginx/html

CMD ["nginx","-g" , "daemon off;"]

