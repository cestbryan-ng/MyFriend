package javafxtest.testjavafx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.net.UnknownHostException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

class MainPageTest {

    @Test
    void testmethodes() throws UnknownHostException {
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String dateformat = date.format(format);
        dateformat = "le " + dateformat;
        System.out.println(dateformat);
    }

    public static void main(String[] args) throws InterruptedException {
        Integer minute = 0, seconde = 0;
        while (true) {
            if (seconde == 60) {
                seconde = 1;
                minute++;
                System.out.println(minute + " : 0" );
            } else {
                System.out.println(minute + ":" + seconde);
                seconde++;
            }

            Thread.sleep(1000);
        }
    }
}