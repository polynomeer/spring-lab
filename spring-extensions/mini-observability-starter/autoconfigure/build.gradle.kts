// core를 그냥 가져다 쓰는 게 아니라 그 API(RequestObservationProperties 등)를 이 모듈을
// 의존하는 쪽에도 그대로 노출해야 하므로 java-library의 api 구성이 필요하다.
plugins {
    `java-library`
}

dependencies {
    api(project(":spring-extensions:mini-observability-starter:core"))
    // ObservationRegistry가 이 모듈의 @Bean 메서드 시그니처(공개 API)에 그대로 노출되므로
    // implementation이 아니라 api여야 한다 - starter를 거쳐 이 스타터를 쓰는 애플리케이션
    // (예: sample-app/mini-order-platform)도 이 타입을 직접 참조할 수 있어야 한다.
    api(libs.micrometer.observation)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.webmvc)
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.spring.test)
}
