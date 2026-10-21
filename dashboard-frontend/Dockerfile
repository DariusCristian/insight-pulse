# syntax=docker/dockerfile:1
# --------------------------------------------------------------------------- #
# Stage 1 — deps: install production + dev dependencies                       #
# --------------------------------------------------------------------------- #
FROM node:20-alpine AS deps
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

# --------------------------------------------------------------------------- #
# Stage 2 — builder: compile the Next.js application                          #
# --------------------------------------------------------------------------- #
FROM node:20-alpine AS builder
WORKDIR /app

# Re-use installed node_modules from deps stage
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build-time env vars (values are baked into the client bundle at build time)
ARG NEXT_PUBLIC_WS_ENDPOINT=ws://localhost:8080/ws/stream
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080

ENV NEXT_PUBLIC_WS_ENDPOINT=$NEXT_PUBLIC_WS_ENDPOINT
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL

# next.config.ts has output: 'standalone' which produces a self-contained
# server bundle under .next/standalone.
RUN npm run build

# --------------------------------------------------------------------------- #
# Stage 3 — runner: minimal production image                                  #
# --------------------------------------------------------------------------- #
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
# Docker sets HOSTNAME to the container id; the standalone server binds to it,
# which would make it unreachable on localhost (and break the healthcheck).
ENV HOSTNAME=0.0.0.0

# Run as a non-root user (the node image ships a `node` user).
USER node

# next/standalone copies only the files required to run the server
COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static
COPY --from=builder --chown=node:node /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]
