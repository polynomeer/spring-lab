package lab.experiments.taskdecorator;

import org.springframework.core.task.TaskDecorator;

// decorate()는 작업을 "제출하는" 스레드 위에서 실행된다(ThreadPoolTaskExecutor#execute()가
// taskDecorator.decorate(command)를 호출한 뒤에야 실제 풀에 제출하기 때문) - 그래서 여기서
// RequestContext.get()을 호출하면 호출자의 값을 정확히 캡처할 수 있다. 반환하는 Runnable의
// 본문(run())은 나중에 풀 스레드 위에서 실행되므로, 그 안에서 캡처해 둔 값을 다시 심어 준다.
//
// finally에서 풀 스레드의 이전 상태를 복원하는 것도 중요하다 - 스레드 풀은 스레드를
// 재사용하므로, 복원하지 않으면 이번 작업이 남긴 값이 같은 스레드에서 실행될 "다음" 무관한
// 작업으로 새어 나갈 수 있다.
public class RequestContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String captured = RequestContext.get();
        return () -> {
            String previousOnThisThread = RequestContext.get();
            RequestContext.set(captured);
            try {
                runnable.run();
            } finally {
                if (previousOnThisThread != null) {
                    RequestContext.set(previousOnThisThread);
                } else {
                    RequestContext.clear();
                }
            }
        };
    }
}
