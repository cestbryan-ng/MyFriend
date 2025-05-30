module javafxtest.testjavafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jdk.compiler;
    requires opencv;
    requires javafx.swing;
    requires com.zaxxer.hikari;


    opens javafxtest.testjavafx to javafx.fxml;
    exports javafxtest.testjavafx;
}