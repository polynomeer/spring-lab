dependencies {
    implementation(libs.spring.boot)
    // 17주차 문서가 남겨 뒀던 "웹 서버가 실제로 언제 뜨는가" 관찰을 위한 실제 내장 톰캣 -
    // WebApplicationType.NONE만 다루던 원래 실험(LifecycleConfig)에는 필요 없었지만, 이
    // 관찰을 위해서는 진짜 ServletWebServerApplicationContext가 있어야 한다.
    implementation(libs.spring.boot.starter.web)
}
