FROM node:25-alpine AS build

WORKDIR /app

RUN corepack enable

COPY package.json ./
RUN pnpm install --no-frozen-lockfile

COPY . .

ARG VITE_EVALUATION_API_BASE_URL=http://localhost:8081
ENV VITE_EVALUATION_API_BASE_URL=${VITE_EVALUATION_API_BASE_URL}

RUN pnpm build

FROM nginx:1.29-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
