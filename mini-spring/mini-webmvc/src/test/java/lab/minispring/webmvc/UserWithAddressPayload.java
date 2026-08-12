package lab.minispring.webmvc;

record UserWithAddressPayload(long id, String name, AddressPayload address) {
}
