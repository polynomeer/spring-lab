package lab.experiments.messagesource;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageSourceTest {

    @Test
    void correctlyNamedMessageSourceBeanResolvesLocalizedMessages() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(CorrectlyNamedMessageSourceConfig.class)) {
            String english = context.getMessage("greeting.hello", new Object[] {"Spring"}, Locale.ENGLISH);
            String korean = context.getMessage("greeting.hello", new Object[] {"Spring"}, Locale.KOREAN);

            assertThat(english).isEqualTo("Hello, Spring!");
            assertThat(korean).isEqualTo("안녕하세요, Spring님!");
        }
    }

    @Test
    void unsupportedLocaleFallsBackToTheBaseBundle() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(CorrectlyNamedMessageSourceConfig.class)) {
            // messages_fr.properties는 없다 - ResourceBundle의 표준 폴백 체인을 따라
            // 언어 태그가 없는 기본 messages.properties(영어)로 떨어진다.
            String french = context.getMessage("greeting.hello", new Object[] {"Spring"}, Locale.FRENCH);

            assertThat(french).isEqualTo("Hello, Spring!");
        }
    }

    @Test
    void unresolvableCodeWithoutADefaultThrowsNoSuchMessageException() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(CorrectlyNamedMessageSourceConfig.class)) {
            assertThatThrownBy(() -> context.getMessage("no.such.code", null, Locale.ENGLISH))
                    .isInstanceOf(NoSuchMessageException.class);
        }
    }

    @Test
    void unresolvableCodeWithADefaultMessageReturnsTheDefaultInstead() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(CorrectlyNamedMessageSourceConfig.class)) {
            String resolved = context.getMessage("no.such.code", null, "fallback default", Locale.ENGLISH);

            assertThat(resolved).isEqualTo("fallback default");
        }
    }

    @Test
    void wronglyNamedMessageSourceBeanIsNeverWiredInEvenThoughItExistsAndCouldResolveTheCode() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(WronglyNamedMessageSourceConfig.class)) {
            // WronglyNamedMessageSourceConfig의 "myMessageSource" 빈은 실제로 존재하고,
            // "greeting.hello"를 완벽하게 해석할 수 있다 - 그런데 initMessageSource()는
            // 정확히 "messageSource"라는 이름만 찾아본다(30번 문서의 ConversionService와
            // 같은 패턴). 이름이 다르면 그 빈은 아예 없는 것과 같다 - 컨텍스트는 조용히
            // 빈 DelegatingMessageSource로 폴백한다.
            assertThatThrownBy(() -> context.getMessage("greeting.hello", new Object[] {"Spring"}, Locale.ENGLISH))
                    .isInstanceOf(NoSuchMessageException.class);
        }
    }

    @Test
    void childContextWithNoMessageSourceDelegatesToTheParentsMessageSource() {
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext(CorrectlyNamedMessageSourceConfig.class)) {
            AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
            child.setParent(parent);
            child.refresh();
            try {
                // child 자신은 "messageSource" 빈이 전혀 없다 - 빈 DelegatingMessageSource로
                // 폴백하지만, 그 DelegatingMessageSource가 부모 컨텍스트의 MessageSource를
                // parentMessageSource로 자동 연결받아서 조회를 그대로 위임한다.
                String message = child.getMessage("greeting.hello", new Object[] {"Spring"}, Locale.ENGLISH);

                assertThat(message).isEqualTo("Hello, Spring!");
            } finally {
                child.close();
            }
        }
    }
}
