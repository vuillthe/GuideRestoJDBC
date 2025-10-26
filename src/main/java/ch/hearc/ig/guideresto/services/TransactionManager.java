package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.persistence.ConnectionUtils;
import java.sql.Connection;

public final class TransactionManager {
    private TransactionManager() {}

    public static Connection begin() {
        Connection cn = ConnectionUtils.getConnection();
        try { cn.setAutoCommit(false); } catch (Exception ignored) {}
        return cn;
    }
    public static void commit() {
        try { ConnectionUtils.getConnection().commit(); } catch (Exception ignored) {}
        finally { end(); }
    }
    public static void rollback() {
        try { ConnectionUtils.getConnection().rollback(); } catch (Exception ignored) {}
        finally { end(); }
    }
    private static void end() {
        try { ConnectionUtils.getConnection().setAutoCommit(true); } catch (Exception ignored) {}
        // si vous avez un close/release, vous pouvez le faire ici (selon votre ConnectionUtils)
    }
}
