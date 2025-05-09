package javafxtest.testjavafx;


import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;

public class BaseDeDonnee {
    private static String adresse_bd = "jdbc:mysql://172.20.10.2:3306/monapp";
    private static String nom_util = "Jean_Roland";
    private static String mot_de_passe = "Papasenegal0";

    public static Connection seConnecter() throws SQLException {
        return DriverManager.getConnection(adresse_bd, nom_util, mot_de_passe);
    }
}
