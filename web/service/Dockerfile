FROM gradle:8.10-jdk11 AS build
WORKDIR /src
COPY --from=shortest-path . shortest-path
COPY service service
RUN gradle --no-daemon -p service -PshortestPathDir=/src/shortest-path installDist

FROM eclipse-temurin:11-jre-jammy
RUN apt-get update \
	&& apt-get install --yes --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& useradd --system --uid 10001 --create-home route-service
WORKDIR /app
COPY --from=build --chown=route-service:route-service /src/service/build/install/shortest-path-service/ ./
USER route-service
EXPOSE 8080
ENTRYPOINT ["/app/bin/shortest-path-service"]
