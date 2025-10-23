FROM alpine:3.18

ENV JMETER_VERSION=5.6.3
ENV JMETER_HOME=/opt/apache-jmeter-${JMETER_VERSION}
ENV PATH=${JMETER_HOME}/bin:${PATH}

RUN apk add --no-cache openjdk17-jre wget ca-certificates bash

RUN wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz \
    && tar -xzf apache-jmeter-${JMETER_VERSION}.tgz -C /opt \
    && rm apache-jmeter-${JMETER_VERSION}.tgz

WORKDIR /jmeter

RUN echo "javax.net.ssl.trustAll=true" >> ${JMETER_HOME}/bin/system.properties && \
    echo "httpclient.socket.https.cps=0" >> ${JMETER_HOME}/bin/system.properties

ENTRYPOINT ["jmeter", "-Djavax.net.ssl.trustAll=true"]
