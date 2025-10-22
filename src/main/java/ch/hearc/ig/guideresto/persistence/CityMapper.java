package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.City;

import java.sql.*;
import java.util.*;

/**
 * Data Mapper pour la classe métier City ↔ table VILLES.
 * - PK : VILLES.NUMERO (NUMBER)
 * - Champs : CODE_POSTAL, NOM_VILLE
 * - Séquence/trigger : SEQ_VILLES + TR_BIF_VILLES (génère l'ID à l'INSERT)
 *
 * Transactions :
 * - create/update/delete -> commit() si OK, rollback() en cas d'erreur.
 *
 * Cache (Identity Map) :
 * - Map<Integer, City> cache : évite les doublons en mémoire et limite les allers/retours DB.
 */
public class CityMapper extends AbstractMapper<City> {

    // -------------------------------
    // Identity Map (cache par mapper)
    // -------------------------------
    private final Map<Integer, City> cache = new HashMap<>();

    // -------------------------------
    // Requêtes "génériques" (Abstract)
    // -------------------------------
    @Override
    protected String getSequenceQuery() {
        // Après un INSERT, le trigger a appelé SEQ_VILLES.NEXTVAL, CURRVAL est donc accessible dans la même connexion
        return "SELECT SEQ_VILLES.CURRVAL FROM dual";
    }

    @Override
    protected String getExistsQuery() {
        return "SELECT 1 FROM VILLES WHERE NUMERO = ?";
    }

    @Override
    protected String getCountQuery() {
        return "SELECT COUNT(*) FROM VILLES";
    }

    // -------------------------------
    // CRUD & finders
    // -------------------------------
    @Override
    public City findById(int id) {
        // 1) cache d'abord
        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        String sql = "SELECT NUMERO, CODE_POSTAL, NOM_VILLE FROM VILLES WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    City c = mapRow(rs);
                    addToCache(c);
                    return c;
                }
            }
        } catch (SQLException ex) {
            logger.error("findById({}) - SQLException: {}", id, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public Set<City> findAll() {
        String sql = "SELECT NUMERO, CODE_POSTAL, NOM_VILLE FROM VILLES ORDER BY NOM_VILLE";
        Set<City> results = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                City c = mapRow(rs);
                addToCache(c);
                results.add(c);
            }
        } catch (SQLException ex) {
            logger.error("findAll() - SQLException: {}", ex.getMessage(), ex);
        }
        return results;
    }

    @Override
    public City create(City object) {
        // Insert sans NUMERO (le trigger le génère)
        String sql = "INSERT INTO VILLES (CODE_POSTAL, NOM_VILLE) VALUES (?, ?)";
        Connection cn = ConnectionUtils.getConnection();

        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, object.getZipCode());
            ps.setString(2, object.getCityName());
            ps.executeUpdate();

            // Récupérer l'ID généré par la séquence (grâce au trigger)
            try (PreparedStatement psSeq = cn.prepareStatement(getSequenceQuery());
                 ResultSet rs = psSeq.executeQuery()) {
                if (rs.next()) {
                    int newId = rs.getInt(1);
                    object.setId(newId);
                }
            }

            cn.commit();
            addToCache(object); // tient le cache à jour
            return object;
        } catch (SQLException ex) {
            logger.error("create(City) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("create(City) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
        }
        return null;
    }

    @Override
    public boolean update(City object) {
        if (object.getId() == null) {
            logger.warn("update(City) appelé avec un ID null.");
            return false;
        }

        String sql = "UPDATE VILLES SET CODE_POSTAL = ?, NOM_VILLE = ? WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, object.getZipCode());
            ps.setString(2, object.getCityName());
            ps.setInt(3, object.getId());
            int updated = ps.executeUpdate();
            cn.commit();

            if (updated > 0) {
                // rafraîchir le cache
                removeFromCache(object.getId());
                addToCache(object);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("update(City) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("update(City) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
        }
        return false;
    }

    @Override
    public boolean delete(City object) {
        if (object == null || object.getId() == null) {
            logger.warn("delete(City) appelé avec un objet/ID null.");
            return false;
        }
        return deleteById(object.getId());
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM VILLES WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int deleted = ps.executeUpdate();
            cn.commit();

            if (deleted > 0) {
                removeFromCache(id);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("deleteById({}) - SQLException: {}", id, ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("deleteById({}) - rollback error: {}", id, rollEx.getMessage(), rollEx);
            }
        }
        return false;
    }

    // -------------------------------
    // Helpers
    // -------------------------------
    private City mapRow(ResultSet rs) throws SQLException {
        City c = new City(
                rs.getInt("NUMERO"),
                rs.getString("CODE_POSTAL"),
                rs.getString("NOM_VILLE")
        );
        // c.setRestaurants(...) -> lazy: on ne charge pas ici
        return c;
    }

    // -------------------------------
    // Implémentation du cache (AbstractMapper)
    // -------------------------------
    @Override
    protected boolean isCacheEmpty() {
        return cache.isEmpty();
    }

    @Override
    protected void resetCache() {
        cache.clear();
    }

    @Override
    protected void addToCache(City objet) {
        if (objet != null && objet.getId() != null && !cache.containsKey(objet.getId())) {
            cache.put(objet.getId(), objet);
        }
    }

    @Override
    protected void removeFromCache(Integer id) {
        if (id != null) {
            cache.remove(id);
        }
    }
}
