# The gateway of NetworkHelm in a container: the browser opens https://<host>:8443 and goes to the routers through it.
# The jar comes first, then the image, both from the root of the repository:
#   ./gradlew :server:buildFatJar
#   docker build -t networkhelm-gateway server
# The account, the certificate and what the gateway keeps for the browser live in the volume /data.
FROM eclipse-temurin:21-jre
RUN useradd --system --home-dir /data --shell /usr/sbin/nologin networkhelm \
    && mkdir /data \
    && chown networkhelm /data
COPY build/libs/networkhelm-gateway.jar /opt/networkhelm/networkhelm-gateway.jar
ENV NETWORKHELM_GATEWAY_HOME=/data
USER networkhelm
VOLUME /data
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "/opt/networkhelm/networkhelm-gateway.jar"]
