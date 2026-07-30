package lab.ext.currentuser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/me")
    public AuthenticatedUser me(@CurrentUser AuthenticatedUser user) {
        return user;
    }

    @GetMapping("/me/optional")
    public String meOptional(@CurrentUser(required = false) AuthenticatedUser user) {
        return user == null ? "anonymous" : user.name();
    }
}
