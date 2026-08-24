dependencies {
    implementation(libs.spring.context)
    // AnnotationAwareAspectJAutoProxyCreator가 @Aspect의 포인트컷 표현식을 파싱하는 데 필요 -
    // 위빙은 안 하지만 파서는 진짜 AspectJ 라이브러리의 것을 그대로 쓴다.
    implementation(libs.aspectjweaver)
}
