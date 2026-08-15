package lab.experiments.objectprovider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 등록 순서(French → English → Korean)와 @Order 값 순서(English=10, Korean=20,
// French=30)가 일부러 다르다 - stream()과 orderedStream()이 실제로 다른 순서를 돌려주는지
// 확인하기 위한 용도.
@Configuration
public class OrderedGreeterConfig {

    @Bean
    public Greeter frenchGreeter() {
        return new FrenchGreeter();
    }

    @Bean
    public Greeter englishGreeter() {
        return new EnglishGreeter();
    }

    @Bean
    public Greeter koreanGreeter() {
        return new KoreanGreeter();
    }

    @Bean
    public GreeterConsumer greeterConsumer(ObjectProvider<Greeter> greeters) {
        return new GreeterConsumer(greeters);
    }
}
