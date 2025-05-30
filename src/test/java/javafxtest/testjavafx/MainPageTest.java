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
        Integer a = 12;
        System.out.printf("%02d", a);
    }
}