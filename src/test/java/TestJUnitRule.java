import embedded.keycloak.EmbeddedKeyCloakJUnitRule;
import org.junit.ClassRule;
import org.junit.Test;

public class TestJUnitRule {

    @ClassRule
    public static EmbeddedKeyCloakJUnitRule cloak = new EmbeddedKeyCloakJUnitRule();

    @Test
    public void test() {
        System.out.println("TEST EXECUTED");
    }
}
