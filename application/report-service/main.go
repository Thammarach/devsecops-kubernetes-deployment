// service2/main.go
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
	shutdown := InitTracer("report-service")
	defer shutdown(context.Background())

	// ตั้ง global propagator
	otel.SetTextMapPropagator(propagation.TraceContext{})

	tracer := otel.Tracer("report-service")

	mux := http.NewServeMux()
	mux.Handle("/step2", otelhttp.NewHandler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// extract context จาก request
		ctx := otel.GetTextMapPropagator().Extract(r.Context(), propagation.HeaderCarrier(r.Header))

		ctx, span := tracer.Start(ctx, "report-service-step")
		defer span.End()

		// ส่งต่อไป service3 พร้อม inject trace context
		req, _ := http.NewRequestWithContext(ctx, "GET", os.Getenv("URL_USER_SERVICE"), nil)
		client := http.Client{Transport: otelhttp.NewTransport(http.DefaultTransport)}
		resp, err := client.Do(req)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		defer resp.Body.Close()
		body, _ := ioutil.ReadAll(resp.Body)

		fmt.Fprintf(w, "Service2 Done → %s", string(body))
	}), "step2"))

	log.Println("Service2 running on :8081")
	log.Fatal(http.ListenAndServe(":8081", mux))
}
