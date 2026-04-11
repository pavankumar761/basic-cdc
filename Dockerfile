FROM quay.io/debezium/connect:3.4

USER root

# Install unzip using the package manager in the Debezium image
RUN microdnf install unzip

# Create the directory
RUN mkdir -p /kafka/connect/

# Copy the file you downloaded manually into the image
COPY confluent-elasticsearch-sink.zip /tmp/elastic-sink.zip

# Unzip and cleanup
RUN unzip /tmp/elastic-sink.zip -d /kafka/connect/ && \
    rm /tmp/elastic-sink.zip

USER kafka