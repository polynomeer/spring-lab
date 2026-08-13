package lab.experiments.async;

import org.springframework.scheduling.annotation.Async;

// @EnableAsync가 없는 컨테이너에서 - @Async는 그냥 읽히지 않는 메타데이터로 남는다.
// 반환 타입을 일부러 Future가 아닌 평범한 String으로 뒀다 - 실제로 @Async가 적용됐다면
// AsyncExecutionAspectSupport#doSubmit()이 "Invalid return type for async method"로
// 거부했을 조합이지만, 애초에 인터셉터 자체가 없으니 그 검증도 일어나지 않는다.
public class UnenabledAsyncService {

    @Async
    public String recordThreadName() {
        return Thread.currentThread().getName();
    }
}
