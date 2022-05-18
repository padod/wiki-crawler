ARG BUILD_IMAGE_NAME

FROM $BUILD_IMAGE_NAME as stage1

ARG VERSION
ARG PROJECT_NAME

ADD project project
ADD $PROJECT_NAME/src $PROJECT_NAME/src
ADD build.sbt build.sbt

RUN sbt clean $PROJECT_NAME/assembly

FROM openjdk:18.0.1.1-jdk-slim-bullseye as main

ARG PROJECT_NAME
ARG VERSION
ENV PROJECT_NAME=$PROJECT_NAME
ENV VERSION=$VERSION

COPY --from=stage1 ./root/$PROJECT_NAME/target/scala-2.13/${PROJECT_NAME}-assembly-$VERSION.jar /

EXPOSE 8080

CMD java -jar ${PROJECT_NAME}-assembly-$VERSION.jar
