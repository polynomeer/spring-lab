package lab.minispring.autoproxy;

import java.lang.reflect.Method;

public final class MiniTransactionalPointcut implements MiniPointcut {

    @Override
    public boolean matches(Method method) {
        return method.isAnnotationPresent(MiniTransactional.class);
    }
}
