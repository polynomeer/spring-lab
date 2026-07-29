package lab.experiments.mvc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    // "/users/me"는 리터럴 경로라서, "/users/{id}"라는 변수 경로보다 더 구체적인 것으로
    // 취급되어 항상 우선 매칭된다 - 등록 순서와 무관하다(8번 절 참고).
    @GetMapping("/me")
    public UserResponse me() {
        return new UserResponse(0L, false);
    }

    @GetMapping("/{id}")
    public UserResponse find(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean detail) {
        return new UserResponse(id, detail);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(new UserResponse(999L, false));
    }

    @GetMapping("/boom")
    public UserResponse boom() {
        throw new IllegalStateException("controller exploded");
    }

    // priorityHandlerMapping이 더 높은 우선순위로 같은 경로를 선점하는지 확인하기 위한
    // 대조군 - MvcTraceConfig 참고.
    @GetMapping("/priority-test")
    public String priorityTest() {
        return "from-annotation-mapping";
    }

    @GetMapping("/blocked")
    public UserResponse blocked() {
        BlockingInterceptor.controllerReached.set(true);
        return new UserResponse(-1L, false);
    }
}
