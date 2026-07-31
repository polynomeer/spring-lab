package lab.experiments.mvcerror;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/widgets")
public class DemoController {

    // id에 숫자가 아닌 값이 오면 MethodArgumentTypeMismatchException -> 400(9번 절).
    @GetMapping("/{id}")
    public String find(@PathVariable long id) {
        return "widget-" + id;
    }

    // @Valid 실패는 MethodArgumentNotValidException, JSON 파싱 자체 실패는
    // HttpMessageNotReadableException - 둘 다 DefaultHandlerExceptionResolver가 400으로
    // 처리한다(9번 절).
    @PostMapping
    public String create(@Valid @RequestBody CreateWidgetRequest request) {
        return "created:" + request.name();
    }

    @GetMapping("/boom-local")
    public String boomLocal() {
        throw new LocalOnlyException("local failure");
    }

    // 같은 컨트롤러 안의 @ExceptionHandler는 어떤 @ControllerAdvice보다도 먼저 확인된다
    // (ExceptionHandlerExceptionResolver#getExceptionHandlerMethod, handlerMethod != null
    // 분기가 advice 캐시보다 먼저 조회되고, 매칭되면 그 자리에서 바로 반환한다).
    @ExceptionHandler(LocalOnlyException.class)
    public ResponseEntity<String> handleLocally(LocalOnlyException ex) {
        return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).body("handled-locally:" + ex.getMessage());
    }

    @GetMapping("/boom-advice-only")
    public String boomAdviceOnly() {
        throw new AdviceOnlyException("advice failure");
    }

    @GetMapping("/boom-shared")
    public String boomShared() {
        throw new SharedFailureException("shared failure");
    }

    @GetMapping("/boom-response-status-exception")
    public String boomResponseStatusException() {
        throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "pay up first");
    }

    @GetMapping("/boom-annotated-exception")
    public String boomAnnotatedException() {
        throw new CustomNotFoundException("widget missing");
    }
}
