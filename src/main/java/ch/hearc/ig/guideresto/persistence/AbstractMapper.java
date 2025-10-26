package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.IBusinessObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class AbstractMapper<T extends IBusinessObject> {

    protected static final Logger logger = LogManager.getLogger();

    // ================= Identity Map générique =================
    private final Map<Integer, T> identityMap = new HashMap<>();

    /** Récupère un objet du cache par son id (ou null s’il n’y est pas). */
    protected T getFromCache(Integer id) {
        return (id == null) ? null : identityMap.get(id);
    }

    /** L’objet avec cet id est-il déjà dans le cache ? */
    protected boolean isInCache(Integer id) {
        return id != null && identityMap.containsKey(id);
    }

    /** Le cache contient-il des éléments ? */
    protected boolean isCacheEmpty() {
        return identityMap.isEmpty();
    }

    /** Vide complètement le cache. */
    protected void resetCache() {
        identityMap.clear();
    }

    /** Ajoute/replace l’objet dans l’Identity Map (clé = getId()). */
    protected void addToCache(T objet) {
        if (objet != null && objet.getId() != null) {
            identityMap.put(objet.getId(), objet);
        }
    }

    /** Retire l’objet du cache par id. */
    protected void removeFromCache(Integer id) {
        if (id != null) {
            identityMap.remove(id);
        }
    }
    // ==========================================================

    public abstract T findById(int id);
    public abstract Set<T> findAll();
    public abstract T create(T object);
    public abstract boolean update(T object);
    public abstract boolean delete(T object);
    public abstract boolean deleteById(int id);

    protected abstract String getSequenceQuery();
    protected abstract String getExistsQuery();
    protected abstract String getCountQuery();

    /**
     * Vérifie si un objet avec l'ID donné existe.
     * @param id the ID to check
     * @return true si l'objet existe, false sinon
     */
    public boolean exists(int id) {
        Connection connection = ConnectionUtils.getConnection();

        try (PreparedStatement stmt = connection.prepareStatement(getExistsQuery())) {
            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            logger.error("SQLException: {}", ex.getMessage(), ex);
        }
        return false;
    }

    /**
     * Compte le nombre d'objets en base de données.
     */
    public int count() {
        Connection connection = ConnectionUtils.getConnection();

        try (PreparedStatement stmt = connection.prepareStatement(getCountQuery());
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException ex) {
            logger.error("SQLException: {}", ex.getMessage(), ex);
            return 0;
        }
    }

    /**
     * Obtient la valeur courante de la séquence (CURRVAL) après un INSERT.
     */
    protected Integer getSequenceValue() {
        Connection connection = ConnectionUtils.getConnection();

        try (PreparedStatement stmt = connection.prepareStatement(getSequenceQuery());
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException ex) {
            logger.error("SQLException: {}", ex.getMessage(), ex);
            return 0;
        }
    }
}
