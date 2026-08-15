package lab.experiments.resource;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceAbstractionTest {

    @Test
    void noPrefixResolvesAsAClassPathResourceForAnnotationConfigApplicationContext() throws Exception {
        // AnnotationConfigApplicationContext는 getResourceByPath()를 오버라이드하지 않는다 -
        // DefaultResourceLoader의 기본 동작(접두어 없는 경로 = 클래스패스 상대 경로)이 그대로
        // 적용된다. refresh() 없이도 리소스 로딩은 독립적으로 동작한다.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            Resource resource = context.getResource("lab/experiments/resource/greeting.txt");

            assertThat(resource).isInstanceOf(ClassPathResource.class);
            assertThat(resource.exists()).isTrue();
            assertThat(readAsString(resource)).isEqualTo("hello resource\n");
        }
    }

    @Test
    void noPrefixResolvesAsAFileSystemResourceForFileSystemXmlApplicationContext() {
        // FileSystemXmlApplicationContext는 getResourceByPath()를 오버라이드해서 접두어 없는
        // 경로를 파일 시스템 상대 경로로 취급한다 - 완전히 같은 문자열이 컨텍스트 구현체에
        // 따라 다른 타입의 Resource로 해석된다.
        FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext();
        Resource resource = context.getResource("lab/experiments/resource/greeting.txt");

        assertThat(resource).isInstanceOf(FileSystemResource.class);
        // 이 상대 경로는 실제 파일 시스템에는 없다 - 같은 문자열이 두 컨텍스트에서 얼마나
        // 다르게 취급되는지 보여주는 것이 핵심이지, 이 파일이 실제로 존재하는지는 이번
        // 실험의 관심사가 아니다.
        assertThat(resource.exists()).isFalse();
    }

    @Test
    void explicitClasspathPrefixOverridesTheContextTypeDefault() throws Exception {
        // FileSystemXmlApplicationContext에서도 "classpath:" 접두어를 명시하면 컨텍스트
        // 구현체의 기본 해석 방식과 무관하게 항상 클래스패스 리소스로 해석된다.
        FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext();
        Resource resource = context.getResource("classpath:lab/experiments/resource/greeting.txt");

        assertThat(resource).isInstanceOf(ClassPathResource.class);
        assertThat(resource.exists()).isTrue();
        assertThat(readAsString(resource)).isEqualTo("hello resource\n");
    }

    @Test
    void getFileThrowsForAClassPathResourceInsideAJarWhileGetInputStreamStillWorks() throws Exception {
        // spring-context-6.2.19.jar 안에 실제로 들어 있는 클래스 파일을 가리킨다 - 진짜 jar
        // 안의 리소스로 이 차이를 재현하기 위해 별도 테스트 자원을 준비할 필요가 없었다.
        Resource resource = new ClassPathResource("org/springframework/context/annotation/Configuration.class");

        assertThat(resource.exists()).isTrue();

        // 스트림으로 읽는 것은 jar 안이든 밖이든 항상 가능하다 - 클래스로더가 스트림을 열어
        // 주기 때문이다.
        byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
        assertThat(bytes.length).isGreaterThan(0);

        // 반면 getFile()은 "이 리소스가 실제로 로컬 파일 시스템의 File 하나에 대응한다"는
        // 것을 전제로 한다 - jar 안에 있는 항목은 그 전제를 만족하지 못하므로 예외가 난다.
        assertThatThrownBy(resource::getFile).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void resourcePatternResolverDiscoversAllFilesMatchingAWildcardPattern() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources("classpath*:lab/experiments/resource/data/*.properties");

        // 3개의 파일(a/b/c.properties)을 미리 나눠서 배치해 뒀다 - 와일드카드 하나로 전부
        // 찾아낸다는 것을 개수로 직접 확인한다.
        assertThat(resources).hasSize(3);
    }

    private String readAsString(Resource resource) throws Exception {
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
