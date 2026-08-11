dependencies {
    // CGLIB 상당의 서브클래스 프록시를 만들려면 런타임 바이트코드 생성이 필요하다 - ASM을
    // 직접 다루는 대신(그 자체가 이 학습 목표 밖이다) 실제 라이브러리를 그대로 가져다 쓴다.
    // 실제 Spring도 CGLIB 자체를 재구현하지 않고 벤더링해서 쓴다는 점에서 같은 선택이다.
    implementation(libs.byte.buddy)
}
