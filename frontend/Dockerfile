# ---------- Étape 1 : build Angular ----------
FROM node:22-alpine AS build
WORKDIR /app

# Cache des dépendances (ne se relance que si package.json / yarn.lock changent) ; yarn comme en CI
COPY package.json yarn.lock ./
RUN yarn install --frozen-lockfile

COPY . .
RUN npx ng build --configuration production

# ---------- Étape 2 : serveur web ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
