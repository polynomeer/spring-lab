dependencies {
    implementation(libs.spring.context)
    // @Aspect/@Around 애노테이션 해석에 필요하다 - 전체 AspectJ 컴파일러/위빙은 쓰지 않고,
    // Spring이 프록시 기반으로 이 애노테이션들을 읽어 어드바이스를 적용한다.
    implementation(libs.aspectjweaver)
}
