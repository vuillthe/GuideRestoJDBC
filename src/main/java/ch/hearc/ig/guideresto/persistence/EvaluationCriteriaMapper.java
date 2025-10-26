package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.EvaluationCriteria;

import java.sql.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Data Mapper pour EvaluationCriteria ↔ table CRITERES_EVALUATION.
 * - Transactions : AUCUN commit/rollback ici → gérés par la couche service.
 * - Cache : Identity Map générique (AbstractMapper).
 */
public class EvaluationCriteriaMapper extends AbstractMapper<EvaluationCriteria> {

    @Override
    protected String getSequenceQuery() {
        return "SELECT SEQ_CRITERES_EVALUATION.CURRVAL FROM dual";
    }

    @Override
    protected String getExistsQuery() {
        return "SELECT 1 FROM CRITERES_EVALUATION WHERE NUMERO = ?";
    }

    @Override
    protected String getCountQuery() {
        return "SELECT COUNT(*) FROM CRITERES_EVALUATION";
    }

    // --------------- Finders ---------------
    @Override
    public EvaluationCriteria findById(int id) {
        EvaluationCriteria cached = getFromCache(id);
        if (cached != null) return cached;

        String sql = "SELECT NUMERO, NOM, DESCRIPTION FROM CRITERES_EVALUATION WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EvaluationCriteria ec = mapRow(rs);
                    addToCache(ec);
                    return ec;
                }
            }
        } catch (SQLException ex) {
            logger.error("findById({}) - SQLException: {}", id, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public Set<EvaluationCriteria> findAll() {
        String sql = "SELECT NUMERO, NOM, DESCRIPTION FROM CRITERES_EVALUATION ORDER BY NOM";
        Set<EvaluationCriteria> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                EvaluationCriteria ec = mapRow(rs);
                addToCache(ec);
                out.add(ec);
            }
        } catch (SQLException ex) {
            logger.error("findAll() - SQLException: {}", ex.getMessage(), ex);
        }
        return out;
    }

    /** Finder exact sur NOM (unique). */
    public EvaluationCriteria findByName(String exactName) {
        String sql = "SELECT NUMERO, NOM, DESCRIPTION FROM CRITERES_EVALUATION WHERE NOM = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, exactName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EvaluationCriteria ec = mapRow(rs);
                    addToCache(ec);
                    return ec;
                }
            }
        } catch (SQLException ex) {
            logger.error("findByName({}) - SQLException: {}", exactName, ex.getMessage(), ex);
        }
        return null;
    }

    /** Recherche partielle (LIKE, insensible à la casse). */
    public Set<EvaluationCriteria> searchByName(String nameLike) {
        String sql = "SELECT NUMERO, NOM, DESCRIPTION FROM CRITERES_EVALUATION " +
                "WHERE UPPER(NOM) LIKE UPPER(?) ORDER BY NOM";
        Set<EvaluationCriteria> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, "%" + nameLike + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EvaluationCriteria ec = mapRow(rs);
                    addToCache(ec);
                    out.add(ec);
                }
            }
        } catch (SQLException ex) {
            logger.error("searchByName({}) - SQLException: {}", nameLike, ex.getMessage(), ex);
        }
        return out;
    }

    // --------------- CRUD (sans commit/rollback) ---------------
    @Override
    public EvaluationCriteria create(EvaluationCriteria object) {
        String sql = "INSERT INTO CRITERES_EVALUATION (NOM, DESCRIPTION) VALUES (?, ?)";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, object.getName());
            ps.setString(2, object.getDescription());
            ps.executeUpdate();

            // ID via trigger/séquence
            try (PreparedStatement psSeq = cn.prepareStatement(getSequenceQuery());
                 ResultSet rs = psSeq.executeQuery()) {
                if (rs.next()) object.setId(rs.getInt(1));
            }

            addToCache(object);
            return object;
        } catch (SQLException ex) {
            logger.error("create(EvaluationCriteria) - SQLException: {}", ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public boolean update(EvaluationCriteria object) {
        if (object.getId() == null) {
            logger.warn("update(EvaluationCriteria) appelé avec un ID null.");
            return false;
        }
        String sql = "UPDATE CRITERES_EVALUATION SET NOM = ?, DESCRIPTION = ? WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, object.getName());
            ps.setString(2, object.getDescription());
            ps.setInt(3, object.getId());
            int updated = ps.executeUpdate();

            if (updated > 0) {
                addToCache(object); // refresh dans l’Identity Map
                return true;
            }
        } catch (SQLException ex) {
            logger.error("update(EvaluationCriteria) - SQLException: {}", ex.getMessage(), ex);
        }
        return false;
    }

    @Override
    public boolean delete(EvaluationCriteria object) {
        if (object == null || object.getId() == null) {
            logger.warn("delete(EvaluationCriteria) appelé avec un objet/ID null.");
            return false;
        }
        return deleteById(object.getId());
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM CRITERES_EVALUATION WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                removeFromCache(id); // purge Identity Map
                return true;
            }
        } catch (SQLException ex) {
            logger.error("deleteById({}) - SQLException: {}", id, ex.getMessage(), ex);
        }
        return false;
    }

    // --------------- Helper ---------------
    private EvaluationCriteria mapRow(ResultSet rs) throws SQLException {
        return new EvaluationCriteria(
                rs.getInt("NUMERO"),
                rs.getString("NOM"),
                rs.getString("DESCRIPTION")
        );
    }
}
