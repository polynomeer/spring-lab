// core를 그냥 가져다 쓰는 게 아니라 그 API(RequestObservationProperties 등)를 이 모듈을
// 의존하는 쪽에도 그대로 노출해야 하므로 java-library의 api 구성이 필요하다.
plugins {
    `java-library`
}

dependencies {
    api(project(":spring-extensions:mini-observability-starter:core"))
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.0")
    implementation("org.springframework:spring-webmvc:6.2.19")
    testImplementation("org.springframework.boot:spring-boot-test:3.5.0")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    testImplementation("org.springframework:spring-test:6.2.19")
}
