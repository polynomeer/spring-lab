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

    @MiniRequestMapping(path = "/api/users", method = "POST")
    public UserPayload create(@MiniRequestBody UserPayload payload) {
        return payload;
    }

    @MiniRequestMapping(path = "/api/users/with-address", method = "POST")
    public UserWithAddressPayload createWithAddress(@MiniRequestBody UserWithAddressPayload payload) {
        return payload;
    }

    @MiniRequestMapping(path = "/api/users/wrapped", method = "GET")
    public MiniResponseEntity<UserPayload> wrapped() {
        return MiniResponseEntity.status(201, new UserPayload(999, false));
    }

    @MiniRequestMapping(path = "/api/users/boom", method = "GET")
    public UserPayload boom() {
        throw new IllegalStateException("controller exploded");
    }

    @MiniRequestMapping(path = "/api/users/boom-unhandled-locally", method = "GET")
    public String boomUnhandledLocally() {
        throw new IllegalArgumentException("no local handler for this one");
    }

    @MiniRequestMapping(path = "/api/users/boom-both", method = "GET")
    public String boomBoth() {
        throw new NullPointerException("both handle this");
    }

    @MiniExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException ex) {
        return "handled: " + ex.getMessage();
    }

    @MiniExceptionHandler(NullPointerException.class)
    public String handleNullPointerLocally(NullPointerException ex) {
        return "local-npe: " + ex.getMessage();
    }
}
