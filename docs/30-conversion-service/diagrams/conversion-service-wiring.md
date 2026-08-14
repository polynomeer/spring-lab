# 빈 이름 문자열 하나가 갈라놓는 두 경로

[`conversion-service.md`](../conversion-service.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것. `prepareBeanFactory()`의 이름 검사 지점 하나에서 "타입 변환이 전부 켜지는 경로"와 "이름이 다른 경우와 아예 없는 경우가 완전히 같은 실패로 수렴하는 경로"가 갈라진다.

```mermaid
flowchart TD
    A["refresh() 2번째 단계: prepareBeanFactory()"] --> B{"beanFactory.containsBean(\"conversionService\")<br/>&& isTypeMatch(..., ConversionService.class)?"}

    B -->|"예 - 이름이 정확히 일치"| C["beanFactory.setConversionService(bean)"]
    C --> D["SimpleTypeConverter.conversionService = 그 인스턴스"]
    D --> E["@Value(\"${app.point}\") → Point 변환 성공<br/>@Value(\"${app.numbers}\") → List&lt;Integer&gt; 변환 성공<br/>(DefaultConversionService 내장 컨버터)"]

    B -->|"아니오 - 이름이 다르거나(myConversionService)<br/>빈 자체가 없음"| F["setConversionService() 호출 안 됨"]
    F --> G["SimpleTypeConverter.conversionService = null"]
    G --> H["TypeConverterDelegate가 ConversionService 분기를<br/>완전히 건너뜀 → PropertyEditor 탐색만 남음"]
    H --> I["Point에 맞는 PropertyEditor 없음<br/>→ IllegalStateException<br/>→ ConversionNotSupportedException<br/>→ UnsatisfiedDependencyException<br/>→ refresh() 실패"]

    style C fill:#161,color:#fff
    style F fill:#333,color:#fff
    style I fill:#611,color:#fff
```
