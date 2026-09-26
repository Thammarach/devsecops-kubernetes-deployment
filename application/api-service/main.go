// api-service/main.go
package main

import (
	"context"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.4.0"
	"os"
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
	shutdown := InitTracer("api-service")
	defer shutdown(context.Background())

	// Set global propagator
	otel.SetTextMapPropagator(propagation.TraceContext{})

	tracer := otel.Tracer("api-service")

	http.Handle("/start", otelhttp.NewHandler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ctx, span := tracer.Start(r.Context(), "api-service-start")
		defer span.End()
		 log.Println("get env : ", os.Getenv("URL_MONITOR"))
		// ใช้ otelhttp transport → inject header อัตโนมัติ
		client := http.Client{Transport: otelhttp.NewTransport(http.DefaultTransport)}
		req, _ := http.NewRequestWithContext(ctx, "GET", os.Getenv("URL_REPORT_SERVICE") , nil)

		resp, err := client.Do(req)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		defer resp.Body.Close()
		body, _ := ioutil.ReadAll(resp.Body)

		fmt.Fprintf(w, "API Service Done → %s", string(body))
	}), "start"))

	log.Println("API Service running on :8080")
	log.Fatal(http.ListenAndServe(":8080", nil))
}
