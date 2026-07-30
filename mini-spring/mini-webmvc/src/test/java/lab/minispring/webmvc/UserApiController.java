package lab.minispring.webmvc;

class UserApiController {

    @MiniRequestMapping(path = "/api/users/{id}", method = "GET")
    public UserPayload find(@MiniPathVariable("id") long id,
            @MiniRequestParam(value = "detail", required = false) Boolean detail) {
        return new UserPayload(id, detail != null && detail);
    }

    @MiniRequestMapping(path = "/api/users/echo-body", method = "POST")
    public String echoBody(@MiniRequestBody String body) {
        return "received:" + body;
    }

    @MiniRequestMapping(path = "/api/users/wrapped", method = "GET")
    public MiniResponseEntity<UserPayload> wrapped() {
        return MiniResponseEntity.status(201, new UserPayload(999, false));
    }

    @MiniRequestMapping(path = "/api/users/boom", method = "GET")
    public UserPayload boom() {
        throw new IllegalStateException("controller exploded");
    }

    @MiniExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException ex) {
        return "handled: " + ex.getMessage();
    }
}
