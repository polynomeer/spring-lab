dependencies {
    implementation(libs.spring.context)
    // ExternalClientRegistrar가 YAML 설정을 직접 파싱한다 - Spring의 YamlMapFactoryBean/
    // YamlPropertiesFactoryBean(spring-beans에 이미 있음)을 쓸 수도 있었지만, 이 프로젝트의
    // 핵심은 YAML 파싱이 아니라 BeanDefinitionRegistryPostProcessor라, SnakeYAML을 직접 써서
    // "설정을 읽는 부분"과 "빈을 등록하는 부분"의 경계를 코드에서도 눈에 보이게 했다.
    implementation(libs.snakeyaml)
}
