dependencies {
    implementation(libs.spring.boot.autoconfigure)
    testImplementation(libs.spring.boot.test)
    // main 소스셋에는 없다 - @ConditionalOnClass(name = "...")가 문자열 기반이라 컴파일
    // 의존성 없이도 동작한다는 것과, 테스트 클래스패스에만 Jackson이 있을 때 조건이 어떻게
    // 갈리는지를 확인하기 위해 일부러 testImplementation으로만 넣는다(19주차).
    testImplementation(libs.jackson.databind)
}
