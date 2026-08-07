dependencies {
    implementation(libs.spring.webmvc)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.jackson.databind)
    // 대시보드 시나리오의 라이브 실행용 - dispatcher-servlet-trace와 동일하게, 실제 HTTP로
    // DispatcherServlet을 태우기 위한 최소 서블릿 컨테이너.
    implementation(libs.tomcat.embed.core)
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
