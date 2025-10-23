FROM alpine:3.18

ENV JMETER_VERSION=5.6.3
ENV JMETER_HOME=/opt/apache-jmeter-${JMETER_VERSION}
ENV PATH=${JMETER_HOME}/bin:${PATH}

# Install dependencies
RUN apk add --no-cache openjdk17-jre wget ca-certificates bash

# Download and install JMeter
RUN wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz \
    && tar -xzf apache-jmeter-${JMETER_VERSION}.tgz -C /opt \
    && rm apache-jmeter-${JMETER_VERSION}.tgz

# Set working directory
WORKDIR /jmeter

# Default command will be overridden by docker-compose
ENTRYPOINT ["jmeter"]
