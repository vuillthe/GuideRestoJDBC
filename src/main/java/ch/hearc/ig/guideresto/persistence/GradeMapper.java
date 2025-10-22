package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.CompleteEvaluation;
import ch.hearc.ig.guideresto.business.EvaluationCriteria;
import ch.hearc.ig.guideresto.business.Grade;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class GradeMapper extends AbstractMapper<Grade> {

    // Identity Map
    private final Map<Integer, Grade> cache = new HashMap<>();

    // ❗ IMPORTANT : pas de CompleteEvaluationMapper ici (pour éviter le cycle)
    private final EvaluationCriteriaMapper criteriaMapper = new EvaluationCriteriaMapper();

    @Override
    protected String getSequenceQuery() { return "SELECT SEQ_NOTES.CURRVAL FROM dual"; }

    @Override
    protected String getExistsQuery() { return "SELECT 1 FROM NOTES WHERE NUMERO = ?"; }

    @Override
    protected String getCountQuery() { return "SELECT COUNT(*) FROM NOTES"; }

    // -------------------- Finders --------------------
    @Override
    public Grade findById(int id) {
        if (cache.containsKey(id)) return cache.get(id);
        String sql = "SELECT NUMERO, NOTE, FK_COMM, FK_CRIT FROM NOTES WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Grade g = mapRow(rs, null);
                    addToCache(g);
                    return g;
                }
            }
        } catch (SQLException ex) {
            logger.error("findById({}) - {}", id, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public Set<Grade> findAll() {
        String sql = "SELECT NUMERO, NOTE, FK_COMM, FK_CRIT FROM NOTES ORDER BY NUMERO";
        Set<Grade> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Grade g = mapRow(rs, null);
                addToCache(g);
                out.add(g);
            }
        } catch (SQLException ex) {
            logger.error("findAll() - {}", ex.getMessage(), ex);
        }
        return out;
    }

    /** Toutes les notes d’une évaluation (FK_COMM = evaluationId). */
    public Set<Grade> findByEvaluationId(int evaluationId) {
        String sql = "SELECT NUMERO, NOTE, FK_COMM, FK_CRIT FROM NOTES WHERE FK_COMM = ? ORDER BY NUMERO";
        Set<Grade> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, evaluationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Grade g = mapRow(rs, null); // on ne pose pas l'évaluation ici
                    addToCache(g);
                    out.add(g);
                }
            }
        } catch (SQLException ex) {
            logger.error("findByEvaluationId({}) - {}", evaluationId, ex.getMessage(), ex);
        }
        return out;
    }

    // -------------------- CRUD --------------------
    @Override
    public Grade create(Grade object) {
        if (object.getEvaluation() == null || object.getEvaluation().getId() == null) {
            logger.warn("create(Grade) : evaluation (FK_COMM) manquante.");
            return null;
        }
        if (object.getCriteria() == null || object.getCriteria().getId() == null) {
            logger.warn("create(Grade) : criteria (FK_CRIT) manquant.");
            return null;
        }

        String sql = "INSERT INTO NOTES (NOTE, FK_COMM, FK_CRIT) VALUES (?, ?, ?)";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, object.getGrade());
            ps.setInt(2, object.getEvaluation().getId());
            ps.setInt(3, object.getCriteria().getId());
            ps.executeUpdate();

            try (PreparedStatement psSeq = cn.prepareStatement(getSequenceQuery());
                 ResultSet rs = psSeq.executeQuery()) {
                if (rs.next()) object.setId(rs.getInt(1));
            }
            cn.commit();
            addToCache(object);
            return object;
        } catch (SQLException ex) {
            logger.error("create(Grade) - {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException e) { logger.error("rollback - {}", e.getMessage(), e); }
        }
        return null;
    }

    @Override
    public boolean update(Grade object) {
        if (object.getId() == null) return false;
        String sql = "UPDATE NOTES SET NOTE = ?, FK_COMM = ?, FK_CRIT = ? WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, object.getGrade());
            ps.setInt(2, object.getEvaluation().getId());
            ps.setInt(3, object.getCriteria().getId());
            ps.setInt(4, object.getId());
            int upd = ps.executeUpdate();
            cn.commit();
            if (upd > 0) {
                removeFromCache(object.getId());
                addToCache(object);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("update(Grade) - {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException e) { logger.error("rollback - {}", e.getMessage(), e); }
        }
        return false;
    }

    @Override
    public boolean delete(Grade object) {
        return object != null && object.getId() != null && deleteById(object.getId());
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM NOTES WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int del = ps.executeUpdate();
            cn.commit();
            if (del > 0) {
                removeFromCache(id);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("deleteById({}) - {}", id, ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException e) { logger.error("rollback - {}", e.getMessage(), e); }
        }
        return false;
    }

    // -------------------- Mapping helper --------------------
    /** Mappe une ligne de NOTES. Ne charge pas l'évaluation (évite le cycle). */
    private Grade mapRow(ResultSet rs, CompleteEvaluation parent) throws SQLException {
        int id = rs.getInt("NUMERO");
        int note = rs.getInt("NOTE");
        int fkCrit = rs.getInt("FK_CRIT");

        EvaluationCriteria crit = criteriaMapper.findById(fkCrit);

        Grade g = new Grade(id, note, parent, crit);
        if (parent != null) g.setEvaluation(parent);
        return g;
    }

    // -------------------- Cache --------------------
    @Override protected boolean isCacheEmpty() { return cache.isEmpty(); }
    @Override protected void resetCache() { cache.clear(); }
    @Override protected void addToCache(Grade objet) {
        if (objet != null && objet.getId() != null && !cache.containsKey(objet.getId())) {
            cache.put(objet.getId(), objet);
        }
    }
    @Override protected void removeFromCache(Integer id) { if (id != null) cache.remove(id); }
}
