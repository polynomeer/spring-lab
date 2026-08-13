dependencies {
    implementation(libs.spring.context)
    // Bean Validation(JSR-380) provider - MethodValidationInterceptor가 위임하는 실제 구현체.
    implementation(libs.hibernate.validator)
    // hibernate-validator의 기본 메시지 보간기는 EL 구현체가 없으면 ValidatorFactory 생성
    // 자체에 실패한다(mvc-exception-pipeline에서 먼저 겪은 문제, docs/22 참고) - 이 모듈의
    // 실험 대부분이 실제로 검증을 통과시켜야 하므로 반드시 필요하다.
    runtimeOnly(libs.jakarta.el)
}
