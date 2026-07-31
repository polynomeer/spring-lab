// 실제 Spring Boot 스타터의 관례를 그대로 따른다 - 이 모듈에는 코드가 한 줄도 없다.
// "어떤 의존성들을 함께 가져올지"를 선언하는 것 자체가 스타터의 유일한 역할이다.
plugins {
    `java-library`
}

dependencies {
    api(project(":spring-extensions:mini-observability-starter:autoconfigure"))
    api(libs.spring.boot.starter.web)
}
