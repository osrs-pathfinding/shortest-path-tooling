# Build context: the repository root (the service builds with tooling's Gradle build and the
# pinned shortest-path submodule). From web/: docker build -f service/Dockerfile ..
FROM gradle:8.10-jdk11 AS build
ARG RUNELITE_VERSION=latest.release
WORKDIR /src
COPY . .
RUN gradle --no-daemon -PruneLiteVersion=${RUNELITE_VERSION} :service:installDist

FROM eclipse-temurin:11-jre-jammy
RUN apt-get update \
	&& apt-get install --yes --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& useradd --system --uid 10001 --create-home route-service
WORKDIR /app
COPY --from=build --chown=route-service:route-service /src/web/service/build/install/shortest-path-service/ ./
USER route-service
EXPOSE 8080
ENTRYPOINT ["/app/bin/shortest-path-service"]
