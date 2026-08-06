package lab.experiments.mvc;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

@Configuration
@EnableWebMvc
@ComponentScan(basePackageClasses = MvcTraceConfig.class)
public class MvcTraceConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceInterceptor());
        registry.addInterceptor(new BlockingInterceptor()).addPathPatterns("/users/blocked");
    }

    // RequestMappingHandlerMapping(기본 order=0)보다 먼저 확인되도록 HIGHEST_PRECEDENCE로
    // 등록한다 - "/users/priority-test"는 UserController에도 매핑돼 있지만, 이 매핑이 먼저
    // 선택되어야 한다(8번 절, 여러 HandlerMapping 우선순위 실험).
    @Bean
    public SimpleUrlHandlerMapping priorityHandlerMapping(HttpRequestHandler priorityRequestHandler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        mapping.setUrlMap(Map.of("/users/priority-test", priorityRequestHandler));
        return mapping;
    }

    @Bean
    public HttpRequestHandler priorityRequestHandler() {
        return (request, response) -> {
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("from-simple-url-mapping");
        };
    }

    // JSP 인프라 없이도 /greeting이 실제 HTTP로 렌더까지 끝나도록 하는 최소 ViewResolver -
    // 뷰 이름과 모델을 그대로 평문으로 찍는다. MockMvc 테스트(view().name(), model())는 렌더
    // 이전의 ModelAndViewContainer만 보므로 이 리졸버 유무와 무관하게 그대로 통과한다.
    @Bean
    public ViewResolver viewResolver() {
        return (viewName, locale) -> (model, request, response) -> {
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("view=" + viewName + " model=" + model);
        };
    }
}
