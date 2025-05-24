package javafxtest.testjavafx;


import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;

public interface BaseDeDonnee {
    String adresse_bd = "jdbc:mysql://localhost:3306/monapp";
    String nom_util = "Jean_Roland";
    String mot_de_passe = "Papasenegal0";

    static Connection seConnecter() throws SQLException {
        return DriverManager.getConnection(adresse_bd, nom_util, mot_de_passe);
    }
}
