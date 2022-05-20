BUILD_IMAGE := sbt-assembly
CRAWLER_IMAGE := crawler
SPARK_APP_IMAGE := sparkapp

build:

#	docker build . -t $(BUILD_IMAGE)
#
#	docker build \
#	--build-arg BUILD_IMAGE_NAME='$(BUILD_IMAGE)' \
#	--build-arg VERSION='1.0' \
#	--build-arg PROJECT_NAME='$(CRAWLER_IMAGE)' \
#	-t $(CRAWLER_IMAGE) \
#	-f crawler.Dockerfile .

	docker build \
	--build-arg BUILD_IMAGE='$(BUILD_IMAGE)' \
	--build-arg VERSION='1.0' \
	--build-arg PROJECT_NAME='$(SPARK_APP_IMAGE)' \
	-t $(SPARK_APP_IMAGE) -f spark.Dockerfile .

run: build
	docker-compose up








