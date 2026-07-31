dependencies {
    implementation(libs.spring.webmvc)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.jackson.databind)
    testImplementation(libs.spring.test)
    testImplementation(libs.hamcrest)
    testImplementation(libs.json.path)
}
