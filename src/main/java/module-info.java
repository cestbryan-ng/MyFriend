module javafxtest.testjavafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jdk.compiler;
    requires opencv;


    opens javafxtest.testjavafx to javafx.fxml;
    exports javafxtest.testjavafx;
}