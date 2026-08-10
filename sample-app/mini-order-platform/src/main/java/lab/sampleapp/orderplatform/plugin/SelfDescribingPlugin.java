package lab.sampleapp.orderplatform.plugin;

/**
 * {@link PluginRegistrationBeanPostProcessor}가 초기화 완료 시점에 자동으로 수집하는
 * 플러그인 성격의 빈이 구현하는 마커. "어떤 종류(kind)의, 어떤 키(key)를 가진 플러그인인지"만
 * 노출하면 되고, 등록 방식(컴포넌트 스캔 vs @Bean)은 신경 쓰지 않는다.
 */
public interface SelfDescribingPlugin {

    String pluginKind();

    String pluginKey();
}
