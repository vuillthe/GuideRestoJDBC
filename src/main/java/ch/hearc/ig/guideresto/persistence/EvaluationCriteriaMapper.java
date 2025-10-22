package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.EvaluationCriteria;

import java.sql.*;
import java.util.*;

/**
 * Data Mapper pour EvaluationCriteria ↔ table CRITERES_EVALUATION.
 * - PK : NUMERO
 * - Colonnes : NOM (UNIQUE), DESCRIPTION (VARCHAR2(512))
 * - Séquence/trigger : SEQ_CRITERES_EVALUATION + TR_BIF_CRITERES_EVALUATION
 *
 * Transactions : create/update/delete -> commit() si OK, rollback() en cas d'erreur.
 * Cache : Identity Map (Map<Integer, EvaluationCriteria>)
 */
public class EvaluationCriteriaMapper extends AbstractMapper<EvaluationCriteria> {

    // -------------------------------
    // Identity Map
    // -------------------------------
    private final Map<Integer, EvaluationCriteria> cache = new HashMap<>();

    // -------------------------------
    // Requêtes "génériques" (Abstract)
    // -------------------------------
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

    // -------------------------------
    // CRUD & Finders
    // -------------------------------
    @Override
    public EvaluationCriteria findById(int id) {
        if (cache.containsKey(id)) return cache.get(id);

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

    /** Finder utile grâce à la contrainte d'unicité sur NOM. */
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

    /** Recherche partielle sur NOM (LIKE, insensible à la casse). */
    public Set<EvaluationCriteria> searchByName(String nameLike) {
        String sql = "SELECT NUMERO, NOM, DESCRIPTION FROM CRITERES_EVALUATION WHERE UPPER(NOM) LIKE UPPER(?) ORDER BY NOM";
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

            cn.commit();
            addToCache(object);
            return object;
        } catch (SQLException ex) {
            logger.error("create(EvaluationCriteria) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("create(EvaluationCriteria) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
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
            cn.commit();

            if (updated > 0) {
                removeFromCache(object.getId());
                addToCache(object);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("update(EvaluationCriteria) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("update(EvaluationCriteria) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
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
    private EvaluationCriteria mapRow(ResultSet rs) throws SQLException {
        return new EvaluationCriteria(
                rs.getInt("NUMERO"),
                rs.getString("NOM"),
                rs.getString("DESCRIPTION")
        );
        // Pas de chargement des dépendances ici (objet autonome)
    }

    // -------------------------------
    // Implémentation du cache
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
    protected void addToCache(EvaluationCriteria objet) {
        if (objet != null && objet.getId() != null && !cache.containsKey(objet.getId())) {
            cache.put(objet.getId(), objet);
        }
    }

    @Override
    protected void removeFromCache(Integer id) {
        if (id != null) cache.remove(id);
    }
}
