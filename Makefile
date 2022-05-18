BUILD_IMAGE := sbt-assembly
CRAWLER_IMAGE := crawler
SPARK_APP_IMAGE := spark-app

build-jars:
#	docker build . -t $(BUILD_IMAGE)
build-jars:
	docker build \
	--build-arg BUILD_IMAGE_NAME='$(BUILD_IMAGE)' \
	--build-arg VERSION='1.0' \
	--build-arg PROJECT_NAME='$(CRAWLER_IMAGE)' \
	-t $(CRAWLER_IMAGE) \
	-f crawler.Dockerfile .

	docker build \
	--build-arg BUILD_IMAGE='$(BUILD_IMAGE)' \
	--build-arg VERSION='1.0' \
	--build-arg PROJECT_NAME='$(SPARK_APP_IMAGE)' \
	-t $(SPARK_APP_IMAGE) ./$(SPARK_APP_IMAGE)

run-crawler:
	docker-compose up wiki-crawler

run-spark-and-db:
	docker-compose up -d spark-cluster, spark-app, arangodb

run: build-jars run-crawler run-spark-and-db








