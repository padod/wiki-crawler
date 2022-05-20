## Description

This small project consists of two modules - asynchronous Wikipedia crawler and application to process the data,
written with Scala 2.13+Akka Streams and Scala 2.12+Spark respectively.
The processed data is then loaded to local ArangoDB instance. This is all baked together to a docker-compose + Makefile
(to pre-build local docker images and control the order of starting components).

The project is intended to be run locally.

## Getting Started

### Dependencies

* Docker 20.10.x, docker-compose
* GNU make

### Execution
Tweak the variables in [docker-compose.yaml]():
* Set the starting Wikipedia url and desired depth of crawling. Setting 0 will mean that only the
starting url is crawled, setting 1 will crawl the zero patient and its immediate children). 
Only en.wikipedia urls are allowed by default
* Depending on where the Docker virtualization machine runs, you may need to change
``COORDINATOR_ENDPOINT`` to allow
[network communication between container and host](https://docs.docker.com/desktop/mac/networking/)

### Running 
```console
make run
```
1) builds images
2) runs crawler
3) runs spark application
4) spins up ArangoDB instance with the loaded data


## Considerations:
Crawler throughput was reduced to 1 thread, so as not to DDoS Wikipedia servers.
Setting the crawing depth too big (like 10) at one go is not advisable for the same reason.