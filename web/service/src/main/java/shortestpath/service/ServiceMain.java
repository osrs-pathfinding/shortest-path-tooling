package shortestpath.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import shortestpath.routeapi.RouteApi;

/** The route service's HTTP API: {@code POST /v1/route}, {@code GET /v1/items}, health and metrics. */
public final class ServiceMain {
    private ServiceMain() { }

    public static void main(String[] args) {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        RoutingEngine engine = new RoutingEngine(mapper);
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        app(mapper, engine).start(port);
    }

    static Javalin app(ObjectMapper mapper, RoutingEngine engine) {
        SchemaValidator validator = new SchemaValidator();
        PrometheusMeterRegistry metrics = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Counter requests = Counter.builder("route_requests_total").register(metrics);
        Counter rejected = Counter.builder("route_requests_rejected_total").register(metrics);
        Timer duration = Timer.builder("route_duration").publishPercentileHistogram().register(metrics);
        int workers = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        ThreadPoolExecutor executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());

        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson(mapper)));
        app.events(events -> events.serverStopping(executor::shutdown));
        app.before(ctx -> {
            String requestId = ctx.header("X-Request-ID");
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            ctx.attribute("requestId", requestId);
            ctx.header("X-Request-ID", requestId);
        });
        app.get("/live", ctx -> ctx.json(Map.of("status", "ok")));
        app.get("/ready", ctx -> ctx.json(Map.of("status", "ready")));
        app.get("/metrics", ctx -> ctx.contentType("text/plain; version=0.0.4").result(metrics.scrape()));
        app.get("/v1/items", ctx -> ctx.json(ItemCatalog.find(ctx.queryParam("q"), ctx.queryParam("ids"))));
        app.post("/v1/route", ctx -> {
            JsonNode body = mapper.readTree(ctx.body());
            validator.validate(body);
            RouteApi.RouteRequest request = mapper.treeToValue(body, RouteApi.RouteRequest.class);
            requests.increment();
            Timer.Sample sample = Timer.start(metrics);
            try {
                CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> engine.route(request), executor)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .handle((plan, error) -> {
                        sample.stop(duration);
                        if (error == null) {
                            ctx.json(plan);
                        } else if (cause(error) instanceof TimeoutException) {
                            error(ctx, 504, "route calculation timed out");
                        } else {
                            error(ctx, 500, "route calculation failed");
                        }
                        return null;
                    });
                ctx.future(() -> future);
            } catch (RejectedExecutionException busy) {
                sample.stop(duration);
                rejected.increment();
                error(ctx, 503, "route service is busy");
            }
        });
        app.exception(IllegalArgumentException.class, (error, ctx) -> error(ctx, 400, error.getMessage()));
        app.exception(JsonProcessingException.class, (error, ctx) -> error(ctx, 400, "request body is not valid JSON"));
        app.exception(Exception.class, (error, ctx) -> error(ctx, 500, "internal server error"));
        return app;
    }

    private static Throwable cause(Throwable error) {
        Throwable result = error;
        while ((result instanceof CompletionException || result instanceof ExecutionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    private static void error(Context ctx, int status, String message) {
        ctx.status(status).json(new ErrorResponse(message, ctx.attribute("requestId")));
    }

    /** The body of every error response. */
    public static final class ErrorResponse {
        public final String error;
        public final String requestId;

        ErrorResponse(String error, String requestId) {
            this.error = error;
            this.requestId = requestId;
        }
    }
}
