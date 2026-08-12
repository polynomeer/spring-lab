package lab.minispring.webmvc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniJsonReaderTest {

    @Test
    void readsScalarFieldsFromAFlatRecord() {
        UserPayload payload = MiniJsonReader.read("{\"id\":7,\"detail\":true}", UserPayload.class);

        assertThat(payload).isEqualTo(new UserPayload(7, true));
    }

    @Test
    void fieldOrderInTheJsonDoesNotHaveToMatchTheRecordComponentOrder() {
        UserPayload payload = MiniJsonReader.read("{\"detail\":true,\"id\":7}", UserPayload.class);

        assertThat(payload).isEqualTo(new UserPayload(7, true));
    }

    @Test
    void missingPrimitiveFieldsFallBackToTheirDefaultValue() {
        UserPayload payload = MiniJsonReader.read("{}", UserPayload.class);

        assertThat(payload).isEqualTo(new UserPayload(0, false));
    }

    @Test
    void unknownFieldsAreSilentlyIgnoredRatherThanRejected() {
        UserPayload payload = MiniJsonReader.read("{\"id\":7,\"detail\":true,\"nickname\":\"nobody-asked\"}",
                UserPayload.class);

        assertThat(payload).isEqualTo(new UserPayload(7, true));
    }

    @Test
    void unknownObjectAndArrayFieldsAreSkippedWithoutBeingMisledByNestedBraces() {
        UserPayload payload = MiniJsonReader.read(
                "{\"ignored\":{\"a\":[1,2,{\"b\":\"c}}\"}]},\"id\":7,\"detail\":true}", UserPayload.class);

        assertThat(payload).isEqualTo(new UserPayload(7, true));
    }

    @Test
    void nestedRecordsAreDeserializedRecursively() {
        UserWithAddressPayload payload = MiniJsonReader.read(
                "{\"id\":1,\"name\":\"ada\",\"address\":{\"street\":\"1 Infinite Loop\",\"city\":\"Cupertino\"}}",
                UserWithAddressPayload.class);

        assertThat(payload).isEqualTo(new UserWithAddressPayload(1, "ada", new AddressPayload("1 Infinite Loop", "Cupertino")));
    }

    @Test
    void stringEscapeSequencesIncludingUnicodeAreDecoded() {
        UserWithAddressPayload payload = MiniJsonReader.read(
                "{\"id\":1,\"name\":\"line\\nbreak \\u0041\",\"address\":{\"street\":\"a\",\"city\":\"b\"}}",
                UserWithAddressPayload.class);

        assertThat(payload.name()).isEqualTo("line\nbreak A");
    }

    @Test
    void deserializingAnObjectIntoANonRecordTypeThrows() {
        assertThatThrownBy(() -> MiniJsonReader.read("{\"id\":7}", String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot deserialize a JSON object");
    }

    @Test
    void malformedJsonMissingClosingBraceThrows() {
        assertThatThrownBy(() -> MiniJsonReader.read("{\"id\":7,\"detail\":true", UserPayload.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trailingContentAfterTheJsonValueThrows() {
        assertThatThrownBy(() -> MiniJsonReader.read("{\"id\":7,\"detail\":true}garbage", UserPayload.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing content");
    }
}
