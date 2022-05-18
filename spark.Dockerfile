ARG BUILD_IMAGE_NAME

FROM $BUILD_IMAGE_NAME as stage1

ARG VERSION
ARG PROJECT_NAME

ADD project project
ADD $PROJECT_NAME/src $PROJECT_NAME/src
ADD build.sbt build.sbt

RUN sbt clean $PROJECT_NAME/assembly

FROM bitnami/spark:3.1.2 as main

ARG PROJECT_NAME
ARG VERSION
ENV PROJECT_NAME=$PROJECT_NAME
ENV VERSION=$VERSION
ENV SPARK_MASTER_NAME=spark-master
ENV SPARK_MASTER_PORT=7077

COPY --from=stage1 ./root/$PROJECT_NAME/target/scala-2.12/${PROJECT_NAME}-assembly-$VERSION.jar /
COPY submit.sh /submit.sh

EXPOSE 8080

CMD ["/submit.sh"]
