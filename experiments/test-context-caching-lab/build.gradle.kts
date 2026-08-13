dependencies {
    implementation(libs.spring.context)
    testImplementation(libs.spring.test)
    // JUnit Platform Launcher API - lab.experiments.testcontext.probes 밑의 "probe" 테스트
    // 클래스들을 TestContextCachingTest가 직접 구동하기 위해 컴파일 시점에 필요하다
    // (루트 build.gradle.kts는 이걸 testRuntimeOnly로만 선언한다).
    testImplementation(libs.junit.platform.launcher)
}

tasks.test {
    // probes 패키지의 클래스들은 TestContextCachingTest가 JUnit Platform Launcher로 직접
    // 구동한다 - Gradle이 일반 test 태스크로 또 실행하면 ContextCreationCounter가 이중으로
    // 늘어나 실험 결과가 오염된다.
    exclude("**/testcontext/probes/**")
}
