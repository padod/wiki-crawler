# Based on https://github.com/hseeberger/scala-sbt


FROM openjdk:18.0.1.1-jdk-bullseye

ARG SBT_VERSION
ENV SBT_VERSION ${SBT_VERSION:-1.6.2}
ARG USER_ID
ENV USER_ID ${USER_ID:-1001}
ARG GROUP_ID
ENV GROUP_ID ${GROUP_ID:-1001}

# Install sbt
RUN \
  curl -fsL "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" | tar xfz - -C /usr/share && \
  chown -R root:root /usr/share/sbt && \
  chmod -R 755 /usr/share/sbt && \
  ln -s /usr/share/sbt/bin/sbt /usr/local/bin/sbt


# Add and use user sbtuser
RUN groupadd --gid $GROUP_ID sbtuser && useradd -m --gid $GROUP_ID --uid $USER_ID sbtuser --shell /bin/bash
USER sbtuser

# Switch working directory
WORKDIR /home/sbtuser

# Prepare sbt (warm cache)
RUN \
  sbt sbtVersion && \
  mkdir -p project && \
  echo "sbt.version=${SBT_VERSION}" > project/build.properties && \
  sbt compile && \
  rm -r project && rm -r target

# Link everything into root as well
# This allows users of this container to choose, whether they want to run the container as sbtuser (non-root) or as root
USER root
RUN \
  ln -s /home/sbtuser/.cache /root/.cache && \
  ln -s /home/sbtuser/.sbt /root/.sbt && \
  if [ -d "/home/sbtuser/.ivy2" ]; then ln -s /home/sbtuser/.ivy2 /root/.ivy2; fi

# Switch working didockrectory back to root
## Users wanting to use this container as non-root should combine the two following arguments
## -u sbtuser
## -w /home/sbtuser

WORKDIR /root