dependencies {
    implementation(libs.spring.webmvc)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.jackson.databind)
    // 대시보드 6.4 시나리오의 라이브 실행용 - 실제 HTTP로 DispatcherServlet을 태우기 위한
    // 최소 서블릿 컨테이너다. JSP/EL 등은 쓰지 않으므로 tomcat-embed-core만으로 충분하다.
    implementation(libs.tomcat.embed.core)
    testImplementation(libs.spring.test)
    testImplementation(libs.hamcrest)
    testImplementation(libs.json.path)
}
