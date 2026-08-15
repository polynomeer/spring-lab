package lab.experiments.importselector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

// selectImports()는 @EnableGreeting을 붙인 클래스의 AnnotationMetadata를 그대로 받는다 -
// languages() 속성값을 직접 읽어서, 반환하는 클래스 이름 배열 자체를 동적으로 바꾼다. 여기서
// 돌려준 클래스들은 일반 @Import처럼 다시 ConfigurationClassParser를 거쳐 파싱된다 - "@Import
// 대상을 그때그때 계산해서 고른다"는 것이 ImportSelector의 핵심이다.
public class GreetingImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        Map<String, Object> attributes =
                importingClassMetadata.getAnnotationAttributes(EnableGreeting.class.getName());
        String[] languages = (attributes != null) ? (String[]) attributes.get("languages") : new String[0];

        List<String> classNames = new ArrayList<>();
        for (String language : languages) {
            if ("en".equals(language)) {
                classNames.add(EnglishGreetingConfig.class.getName());
            }
            else if ("ko".equals(language)) {
                classNames.add(KoreanGreetingConfig.class.getName());
            }
        }
        return classNames.toArray(new String[0]);
    }
}
