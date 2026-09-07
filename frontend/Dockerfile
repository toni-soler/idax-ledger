FROM node:22-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci
COPY . ./
RUN node scripts/build.mjs

FROM nginx:1.27-alpine
COPY --from=build /src/dist/extensions /usr/share/nginx/html/extensions/ledger
