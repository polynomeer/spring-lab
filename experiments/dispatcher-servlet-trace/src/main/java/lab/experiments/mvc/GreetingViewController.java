package lab.experiments.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// @RestController(UserController)와 대조하기 위한 평범한 @Controller - 반환값이 뷰 이름
// 문자열로 해석된다는 점에서 ReturnValueHandler/HttpMessageConverter 경로 자체가 다르다.
@Controller
public class GreetingViewController {

    @GetMapping("/greeting")
    public String greet(Model model) {
        model.addAttribute("message", "hello");
        return "greeting-view";
    }
}
