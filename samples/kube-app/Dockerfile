FROM gradle:4.9.0-jdk8 AS builder

ADD src ./src
ADD build.gradle ./build.gradle
ADD settings.gradle ./settings.gradle
RUN gradle build

FROM java:8
COPY --from=builder /home/gradle/build/libs/kube-app.jar kube-app.jar

ADD entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]