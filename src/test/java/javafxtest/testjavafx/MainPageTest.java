package javafxtest.testjavafx;

import org.junit.jupiter.api.Test;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Arrays;

class MainPageTest {

    @Test
    void main() throws UnknownHostException {
        String texte = "a=b=c";
        System.out.println(Arrays.toString(texte.split("=")));
    }
}