package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.RestaurantType;

import java.sql.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Data Mapper pour RestaurantType ↔ table TYPES_GASTRONOMIQUES.
 * - Transactions : AUCUN commit/rollback ici → gérés par la couche service.
 * - Cache : Identity Map générique (AbstractMapper).
 */
public class RestaurantTypeMapper extends AbstractMapper<RestaurantType> {

    @Override
    protected String getSequenceQuery() {
        return "SELECT SEQ_TYPES_GASTRONOMIQUES.CURRVAL FROM dual";
    }

    @Override
    protected String getExistsQuery() {
        return "SELECT 1 FROM TYPES_GASTRONOMIQUES WHERE NUMERO = ?";
    }

    @Override
    protected String getCountQuery() {
        return "SELECT COUNT(*) FROM TYPES_GASTRONOMIQUES";
    }

    // -------------------- Finders --------------------
    @Override
    public RestaurantType findById(int id) {
        RestaurantType cached = getFromCache(id);
        if (cached != null) return cached;

        String sql = "SELECT NUMERO, LIBELLE, DESCRIPTION FROM TYPES_GASTRONOMIQUES WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RestaurantType t = mapRow(rs);
                    addToCache(t);
                    return t;
                }
            }
        } catch (SQLException ex) {
            logger.error("findById({}) - SQLException: {}", id, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public Set<RestaurantType> findAll() {
        String sql = "SELECT NUMERO, LIBELLE, DESCRIPTION FROM TYPES_GASTRONOMIQUES ORDER BY LIBELLE";
        Set<RestaurantType> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RestaurantType t = mapRow(rs);
                addToCache(t);
                out.add(t);
            }
        } catch (SQLException ex) {
            logger.error("findAll() - SQLException: {}", ex.getMessage(), ex);
        }
        return out;
    }

    /** Finder par libellé (unique). */
    public RestaurantType findByLabel(String label) {
        String sql = "SELECT NUMERO, LIBELLE, DESCRIPTION FROM TYPES_GASTRONOMIQUES WHERE LIBELLE = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, label);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RestaurantType t = mapRow(rs);
                    addToCache(t);
                    return t;
                }
            }
        } catch (SQLException ex) {
            logger.error("findByLabel({}) - SQLException: {}", label, ex.getMessage(), ex);
        }
        return null;
    }

    // -------------------- CRUD (sans commit/rollback) --------------------
    @Override
    public RestaurantType create(RestaurantType object) {
        String sql = "INSERT INTO TYPES_GASTRONOMIQUES (LIBELLE, DESCRIPTION) VALUES (?, ?)";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, object.getLabel());
            ps.setString(2, object.getDescription());
            ps.executeUpdate();

            // Récupération de l'ID (trigger/séquence)
            try (PreparedStatement psSeq = cn.prepareStatement(getSequenceQuery());
                 ResultSet rs = psSeq.executeQuery()) {
                if (rs.next()) object.setId(rs.getInt(1));
            }

            addToCache(object);
            return object;
        } catch (SQLException ex) {
            logger.error("create(RestaurantType) - SQLException: {}", ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public boolean update(RestaurantType object) {
        if (object.getId() == null) {
            logger.warn("update(RestaurantType) appelé avec un ID null.");
            return false;
        }
        String sql = "UPDATE TYPES_GASTRONOMIQUES SET LIBELLE = ?, DESCRIPTION = ? WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, object.getLabel());
            ps.setString(2, object.getDescription());
            ps.setInt(3, object.getId());
            int updated = ps.executeUpdate();

            if (updated > 0) {
                addToCache(object); // refresh Identity Map
                return true;
            }
        } catch (SQLException ex) {
            logger.error("update(RestaurantType) - SQLException: {}", ex.getMessage(), ex);
        }
        return false;
    }

    @Override
    public boolean delete(RestaurantType object) {
        if (object == null || object.getId() == null) {
            logger.warn("delete(RestaurantType) appelé avec un objet/ID null.");
            return false;
        }
        return deleteById(object.getId());
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM TYPES_GASTRONOMIQUES WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int deleted = ps.executeUpdate();

            if (deleted > 0) {
                removeFromCache(id);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("deleteById({}) - SQLException: {}", id, ex.getMessage(), ex);
        }
        return false;
    }

    // -------------------- Helper --------------------
    private RestaurantType mapRow(ResultSet rs) throws SQLException {
        return new RestaurantType(
                rs.getInt("NUMERO"),
                rs.getString("LIBELLE"),
                rs.getString("DESCRIPTION")
        );
    }
}
