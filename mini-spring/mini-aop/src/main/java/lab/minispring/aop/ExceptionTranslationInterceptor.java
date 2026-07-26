package lab.minispring.aop;

import java.util.function.Function;

public final class ExceptionTranslationInterceptor implements MethodInterceptor {

    private final Class<? extends Throwable> from;
    private final Function<Throwable, ? extends RuntimeException> translator;

    public ExceptionTranslationInterceptor(Class<? extends Throwable> from,
            Function<Throwable, ? extends RuntimeException> translator) {
        this.from = from;
        this.translator = translator;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } catch (Throwable ex) {
            if (from.isInstance(ex)) {
                throw translator.apply(ex);
            }
            throw ex;
        }
    }
}
