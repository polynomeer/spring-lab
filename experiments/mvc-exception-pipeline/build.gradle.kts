dependencies {
    implementation(libs.spring.webmvc)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.jackson.databind)
    // Bean Validation(@Valid) 실패 실험을 위한 jakarta.validation 구현체.
    implementation(libs.hibernate.validator)
    // hibernate-validator의 기본 메시지 보간기(ResourceBundleMessageInterpolator)는 EL
    // 구현체가 없으면 ValidatorFactory 생성 자체가 실패한다 - Spring의
    // OptionalValidatorFactoryBean은 이 실패를 조용히 삼키고 검증을 통째로 no-op으로
    // 만들어 버리므로(직접 겪은 버그, 문서 참고), 반드시 필요하다.
    runtimeOnly(libs.jakarta.el)
    testImplementation(libs.spring.test)
    testImplementation(libs.hamcrest)
    testImplementation(libs.json.path)
}
