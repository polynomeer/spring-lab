dependencies {
    // 7주차 문서가 남겨 둔 "ASM 기반 메타데이터 읽기"를 채우기 위한 실제 라이브러리 -
    // 실제 Spring의 MetadataReader도 ASM을 직접 벤더링해서 쓴다(바이트코드 파서 자체를
    // 재구현하는 건 이 학습 목표 밖이다).
    implementation(libs.asm)
}
