package lab.ext.observability.autoconfigure;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DemoController {

    @GetMapping("/api/hello")
    public String hello() {
        return "hello";
    }
}
