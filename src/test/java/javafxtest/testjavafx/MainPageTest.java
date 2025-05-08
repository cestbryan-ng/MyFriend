package javafxtest.testjavafx;

import org.junit.jupiter.api.Test;
import java.net.Inet4Address;
import java.net.UnknownHostException;

class MainPageTest {

    @Test
    void main() throws UnknownHostException {
        System.out.println(Inet4Address.getLocalHost().toString().split("/")[1]);
    }
}