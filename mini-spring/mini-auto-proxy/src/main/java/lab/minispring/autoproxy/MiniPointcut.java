package lab.minispring.autoproxy;

import java.lang.reflect.Method;

public interface MiniPointcut {

    boolean matches(Method method);
}
