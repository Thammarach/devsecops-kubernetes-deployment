FROM golang:alpine as build-env

ENV GO111MODULE=on
ENV CGO_ENABLED=0
ENV GOOS=linux
ENV GOARCH=amd64

WORKDIR /go/src/gitlab.com/

COPY go.mod .
COPY go.sum .

RUN apk update && apk add ca-certificates

COPY . .

RUN go build -ldflags="-w -s" -o app .

################################################################################
FROM alpine:latest

RUN apk --no-cache add ca-certificates tzdata

RUN cp /usr/share/zoneinfo/Asia/Bangkok /etc/localtime && \
    echo "Asia/Bangkok" > /etc/timezone

COPY --from=build-env /go/src/gitlab.com/app /go/bin/app

ENTRYPOINT ["/go/bin/app"]
