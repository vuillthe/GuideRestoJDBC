package ch.hearc.ig.guideresto.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Provide helper methods to deal with database connections.
 * Ideally, this should also manage connection pools in a bigger application.
 *
 * @author arnaud.geiser
 * @author alain.matile
 */
public class ConnectionUtils {

    private static final Logger logger = LogManager.getLogger();

    private static Connection connection;

    public static Connection getConnection() {
        try {
            // Load database credentials from resources/database.properties
            ResourceBundle dbProps = ResourceBundle.getBundle("database");
            String url = dbProps.getString("database.url");
            String username = dbProps.getString("database.username");
            String password = dbProps.getString("database.password");

            //logger.info("Trying to connect to user schema '{}' with JDBC string '{}'", username, url);

            // Initialize a connection if required
            if (ConnectionUtils.connection == null || ConnectionUtils.connection.isClosed()) {
                Connection connection = DriverManager.getConnection(url, username, password);
                connection.setAutoCommit(false);
                ConnectionUtils.connection = connection;
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (MissingResourceException ex) {
            logger.error(ex.getMessage(), ex);
        }
        return ConnectionUtils.connection;
    }

    public static void closeConnection() {
        try {
            if (ConnectionUtils.connection != null && !ConnectionUtils.connection.isClosed()) {
                ConnectionUtils.connection.close();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
