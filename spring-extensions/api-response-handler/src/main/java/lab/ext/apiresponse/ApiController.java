package lab.ext.apiresponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    @GetMapping("/api/via-return-value-handler")
    @WrapInApiResponse
    public UserResponse viaReturnValueHandler() {
        return new UserResponse(1, "Ada");
    }

    @GetMapping("/api/via-response-body-advice")
    public UserResponse viaResponseBodyAdvice() {
        return new UserResponse(2, "Grace");
    }
}
