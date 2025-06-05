module javafxtest.testjavafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jdk.compiler;
    requires javafx.swing;
    requires com.zaxxer.hikari;
    requires opencv;


    opens javafxtest.testjavafx to javafx.fxml;
    exports javafxtest.testjavafx;
}