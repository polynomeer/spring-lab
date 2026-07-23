package lab.minispring.container;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleBeanFactory {

    private final Map<String, Object> singletonObjects =
            new ConcurrentHashMap<>();

    public void registerSingleton(String name, Object bean) {
        singletonObjects.put(name, bean);
    }

    public Object getBean(String name) {
        Object bean = singletonObjects.get(name);

        if (bean == null) {
            throw new NoSuchBeanException(name);
        }

        return bean;
    }

    public boolean containsBean(String name) {
        return singletonObjects.containsKey(name);
    }
}
