FROM adoptopenjdk:15

WORKDIR /app

ARG litestream_version="v0.3.8"
ARG litestream_binary_tgz_filename="litestream-${litestream_version}-linux-amd64-static.tar.gz"

RUN set -x && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
      ca-certificates \
      wget
RUN wget "https://github.com/benbjohnson/litestream/releases/download/${litestream_version}/${litestream_binary_tgz_filename}"
RUN tar -xvzf "${litestream_binary_tgz_filename}" --directory /bin

RUN wget https://github.com/jbangdev/jbang/releases/download/v0.92.2/jbang-0.92.2.zip
RUN jar xf jbang-0.92.2.zip && \
    mkdir /jbang && \
    rm jbang-0.92.2.zip && \
    mv jbang-0.92.2/* /jbang && \
    chmod +x /jbang/bin/jbang

ENV PATH="${PATH}:/jbang/bin"

## github action does not allow writing to $HOME thus routing this elsewhere
ENV JBANG_DIR="/jbang/.jbang"

# Copy our code from the current folder to inside the container
COPY ./litestream.yml /etc/litestream.yml

COPY . .

EXPOSE 8080

# litestream metrics are at http://localhost:9090/metrics 
EXPOSE 9090

# Frequency that database snapshots are replicated.
ENV DB_SYNC_INTERVAL="10s"

ENTRYPOINT ["/app/entrypoint"]