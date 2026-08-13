package lab.experiments.bootlifecycle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// SpringApplication.run()이 끝난(ApplicationReadyEvent까지 지난) 뒤 실제 HTTP 요청이
// 정상적으로 처리되는지 확인하기 위한 최소 컨트롤러 - 그 이상의 의미는 없다.
@RestController
public class LifecycleProbeController {

    @GetMapping("/probe")
    public String probe() {
        return "ok";
    }
}
