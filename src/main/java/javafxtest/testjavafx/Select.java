package javafxtest.testjavafx;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ArrayList;

public interface Select {
    static List<Object> elements(String recepteur) throws SQLException {
        String indice_de_connexion = "", adresse_recepteur = "";
        Integer self = null, other = null;
        List<Object> liste = new ArrayList<>() ;

        try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet1 = stmt.executeQuery("select statut from connected_user\n" +
                    "where user_id in (\n" +
                    "select user_id from user\n" +
                    "where username = \""+ recepteur +"\");");
            while(resultSet1.next()) {
                indice_de_connexion = resultSet1.getString(1);
            }
            liste.add(indice_de_connexion);
            resultSet1.close();

            ResultSet resultSet2 = stmt.executeQuery("select connected_userid from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \""+ MainPageController.nomutilisateur +"\") ;");
            while (resultSet2.next()) {
                self = resultSet2.getInt(1);
            }
            liste.add(self);
            resultSet2.close();

            ResultSet resultSet3 = stmt.executeQuery("select connected_userid from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \""+ recepteur +"\") ;");
            while (resultSet3.next()) {
                other = resultSet3.getInt(1);
            }
            liste.add(other);
            resultSet3.close();

            ResultSet resultSet4 = stmt.executeQuery("select adresse_ip from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \""+ recepteur +"\");");
            while (resultSet4.next()) {
                adresse_recepteur = resultSet4.getString(1);
                adresse_recepteur = "/" + adresse_recepteur.split("/")[1];
            }
            liste.add(adresse_recepteur);
            resultSet4.close();
        }

        return liste;
    }
}
