FROM gradle:9.4.0-jdk17

WORKDIR /usr/src/app
COPY . .

CMD ["gradle", "shadowJar"]
