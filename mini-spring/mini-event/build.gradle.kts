dependencies {
    // "커밋 후 실행" 리스너를 위해 13~14주차의 JdbcMiniTransactionManager를 재사용한다.
    implementation(project(":mini-spring:mini-transaction"))
    // mini-transaction의 h2 의존성은 implementation이라 전이되지 않는다 - 테스트에서 직접
    // DataSource를 띄우려면 여기서도 선언해야 한다.
    testImplementation(libs.h2)
}
