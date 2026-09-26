package main

import (
	"context"
	"fmt"
	"log"
	"net/http"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.4.0"
)

func InitTracer(serviceName string) func(context.Context) error {
	ctx := context.Background()

	// Exporter ไปที่ Tempo
	exporter, err := otlptracehttp.New(ctx,
		otlptracehttp.WithEndpoint(os.Getenv("URL_MONITOR")),
		otlptracehttp.WithInsecure(),
	)
	if err != nil {
		log.Fatal(err)
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithResource(resource.NewWithAttributes(
			semconv.SchemaURL,
			semconv.ServiceNameKey.String(serviceName),
		)),
	)

	otel.SetTracerProvider(tp)
	return tp.Shutdown
}

func main() {
	shutdown := InitTracer("user-service")
	defer shutdown(context.Background())

	// ตั้ง global propagator
	otel.SetTextMapPropagator(propagation.TraceContext{})

	tracer := otel.Tracer("user-service")

	// ใช้ otelhttp.NewHandler ครอบ handler → auto extract context
	mux := http.NewServeMux()
	mux.Handle("/step3", otelhttp.NewHandler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// extract context จาก request → span จะอยู่ใต้ trace id เดิม
		ctx := otel.GetTextMapPropagator().Extract(r.Context(), propagation.HeaderCarrier(r.Header))

		_, span := tracer.Start(ctx, "user-service-final")
		defer span.End()

		fmt.Fprintf(w, "Service3 Completed ✅")
	}), "step3"))

	log.Println("Service3 running on :8083")
	log.Fatal(http.ListenAndServe(":8083", mux))
}
