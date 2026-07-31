dependencies {
    implementation(libs.spring.context)
    // @TransactionalEventListener 실험을 위한 진짜 PlatformTransactionManager - 다른 트랜잭션
    // 실험(experiments/transaction-propagation-playground)과 동일하게 H2 + DataSourceTransactionManager를 쓴다.
    implementation(libs.spring.jdbc)
    implementation(libs.h2)
}
