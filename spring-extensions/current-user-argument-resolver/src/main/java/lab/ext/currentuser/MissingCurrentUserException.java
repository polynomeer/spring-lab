package lab.ext.currentuser;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MissingCurrentUserException extends ResponseStatusException {

    public MissingCurrentUserException() {
        super(HttpStatus.UNAUTHORIZED, "no authenticated user for this request");
    }
}
