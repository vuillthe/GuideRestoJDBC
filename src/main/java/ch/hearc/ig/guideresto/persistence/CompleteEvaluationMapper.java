package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.CompleteEvaluation;
import ch.hearc.ig.guideresto.business.Grade;
import ch.hearc.ig.guideresto.business.Restaurant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Data Mapper pour CompleteEvaluation ↔ table COMMENTAIRES.
 * - PK : NUMERO
 * - Colonnes : DATE_EVAL (DATE), COMMENTAIRE (CLOB), NOM_UTILISATEUR, FK_REST
 * - FK : FK_REST → RESTAURANTS.NUMERO
 * - Séquence/trigger : SEQ_EVAL + TR_BIF_COMMENTAIRES
 *
 * Chargement :
 * - Restaurant (FK_REST) en EAGER.
 * - grades (1-*) en LAZY par défaut (méthode utilitaire loadGrades).
 *
 * Transactions : create/update/delete -> commit() si OK, rollback() en cas d'erreur.
 *
 * Cache : Identity Map (Map<Integer, CompleteEvaluation>)
 */
public class CompleteEvaluationMapper extends AbstractMapper<CompleteEvaluation> {

    // -------------------------------
    // Identity Map
    // -------------------------------
    private final Map<Integer, CompleteEvaluation> cache = new HashMap<>();

    // -------------------------------
    // Dépendances (FK)
    // -------------------------------
    private final RestaurantMapper restaurantMapper = new RestaurantMapper();
    private final GradeMapper gradeMapper = new GradeMapper();

    // -------------------------------
    // Requêtes "génériques" (Abstract)
    // -------------------------------
    @Override
    protected String getSequenceQuery() {
        // Même séquence que LIKES selon le schéma
        return "SELECT SEQ_EVAL.CURRVAL FROM dual";
    }

    @Override
    protected String getExistsQuery() {
        return "SELECT 1 FROM COMMENTAIRES WHERE NUMERO = ?";
    }

    @Override
    protected String getCountQuery() {
        return "SELECT COUNT(*) FROM COMMENTAIRES";
    }

    // -------------------------------
    // CRUD & Finders
    // -------------------------------
    @Override
    public CompleteEvaluation findById(int id) {
        if (cache.containsKey(id)) return cache.get(id);

        String sql = "SELECT NUMERO, DATE_EVAL, COMMENTAIRE, NOM_UTILISATEUR, FK_REST FROM COMMENTAIRES WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CompleteEvaluation ce = mapRow(rs, /*eagerFK=*/true);
                    addToCache(ce);
                    return ce;
                }
            }
        } catch (SQLException ex) {
            logger.error("findById({}) - SQLException: {}", id, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public Set<CompleteEvaluation> findAll() {
        String sql = "SELECT NUMERO, DATE_EVAL, COMMENTAIRE, NOM_UTILISATEUR, FK_REST FROM COMMENTAIRES ORDER BY DATE_EVAL DESC, NUMERO DESC";
        Set<CompleteEvaluation> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CompleteEvaluation ce = mapRow(rs, true);
                addToCache(ce);
                out.add(ce);
            }
        } catch (SQLException ex) {
            logger.error("findAll() - SQLException: {}", ex.getMessage(), ex);
        }
        return out;
    }

    /** Toutes les évaluations complètes d’un restaurant. */
    public Set<CompleteEvaluation> findByRestaurantId(int restaurantId) {
        String sql = "SELECT NUMERO, DATE_EVAL, COMMENTAIRE, NOM_UTILISATEUR, FK_REST FROM COMMENTAIRES WHERE FK_REST = ? ORDER BY DATE_EVAL DESC, NUMERO DESC";
        Set<CompleteEvaluation> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CompleteEvaluation ce = mapRow(rs, true);
                    addToCache(ce);
                    out.add(ce);
                }
            }
        } catch (SQLException ex) {
            logger.error("findByRestaurantId({}) - SQLException: {}", restaurantId, ex.getMessage(), ex);
        }
        return out;
    }

    /** Recherche par nom d'utilisateur exact. */
    public Set<CompleteEvaluation> findByUsername(String username) {
        String sql = "SELECT NUMERO, DATE_EVAL, COMMENTAIRE, NOM_UTILISATEUR, FK_REST FROM COMMENTAIRES WHERE NOM_UTILISATEUR = ? ORDER BY DATE_EVAL DESC, NUMERO DESC";
        Set<CompleteEvaluation> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CompleteEvaluation ce = mapRow(rs, true);
                    addToCache(ce);
                    out.add(ce);
                }
            }
        } catch (SQLException ex) {
            logger.error("findByUsername({}) - SQLException: {}", username, ex.getMessage(), ex);
        }
        return out;
    }

    /** Variante pratique : charge l'évaluation ET ses notes (grades) en une fois. */
    public CompleteEvaluation findWithGradesById(int id) {
        CompleteEvaluation ce = findById(id);
        if (ce != null) loadGrades(ce);
        return ce;
    }

    /** Charge les notes (grades) LAZY pour une évaluation donnée, et rattache le parent. */
    public void loadGrades(CompleteEvaluation evaluation) {
        if (evaluation == null || evaluation.getId() == null) return;
        Set<Grade> grades = gradeMapper.findByEvaluationId(evaluation.getId());
        // rattacher le parent à chaque grade (évite les références nulles)
        for (Grade g : grades) {
            g.setEvaluation(evaluation);
        }
        evaluation.getGrades().clear();
        evaluation.getGrades().addAll(grades);
    }


    @Override
    public CompleteEvaluation create(CompleteEvaluation object) {
        // FK_REST requis
        if (object.getRestaurant() == null || object.getRestaurant().getId() == null) {
            logger.warn("create(CompleteEvaluation) : restaurant (FK_REST) manquant.");
            return null;
        }

        String sql = "INSERT INTO COMMENTAIRES (DATE_EVAL, COMMENTAIRE, NOM_UTILISATEUR, FK_REST) VALUES (?, ?, ?, ?)";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            Timestamp ts = new Timestamp(
                    (object.getVisitDate() != null ? object.getVisitDate().getTime() : System.currentTimeMillis())
            );
            ps.setTimestamp(1, ts);
            ps.setString(2, object.getComment());
            ps.setString(3, object.getUsername());
            ps.setInt(4, object.getRestaurant().getId());

            ps.executeUpdate();

            // PK générée (trigger + SEQ_EVAL)
            try (PreparedStatement psSeq = cn.prepareStatement(getSequenceQuery());
                 ResultSet rs = psSeq.executeQuery()) {
                if (rs.next()) object.setId(rs.getInt(1));
            }

            cn.commit();
            addToCache(object);
            return object;
        } catch (SQLException ex) {
            logger.error("create(CompleteEvaluation) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("create(CompleteEvaluation) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
        }
        return null;
    }

    @Override
    public boolean update(CompleteEvaluation object) {
        if (object.getId() == null) {
            logger.warn("update(CompleteEvaluation) appelé avec un ID null.");
            return false;
        }
        if (object.getRestaurant() == null || object.getRestaurant().getId() == null) {
            logger.warn("update(CompleteEvaluation) : restaurant (FK_REST) manquant.");
            return false;
        }

        String sql = "UPDATE COMMENTAIRES SET DATE_EVAL = ?, COMMENTAIRE = ?, NOM_UTILISATEUR = ?, FK_REST = ? WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            Timestamp ts = new Timestamp(
                    (object.getVisitDate() != null ? object.getVisitDate().getTime() : System.currentTimeMillis())
            );
            ps.setTimestamp(1, ts);
            ps.setString(2, object.getComment());
            ps.setString(3, object.getUsername());
            ps.setInt(4, object.getRestaurant().getId());
            ps.setInt(5, object.getId());

            int updated = ps.executeUpdate();
            cn.commit();

            if (updated > 0) {
                removeFromCache(object.getId());
                addToCache(object);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("update(CompleteEvaluation) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("update(CompleteEvaluation) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
        }
        return false;
    }

    @Override
    public boolean delete(CompleteEvaluation object) {
        if (object == null || object.getId() == null) {
            logger.warn("delete(CompleteEvaluation) appelé avec un objet/ID null.");
            return false;
        }
        return deleteById(object.getId());
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM COMMENTAIRES WHERE NUMERO = ?";
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
    private CompleteEvaluation mapRow(ResultSet rs, boolean eagerFK) throws SQLException {
        int id = rs.getInt("NUMERO");
        Timestamp ts = rs.getTimestamp("DATE_EVAL");
        String comment = rs.getString("COMMENTAIRE");
        String username = rs.getString("NOM_UTILISATEUR");
        int fkRest = rs.getInt("FK_REST");

        Date visitDate = (ts != null) ? new Date(ts.getTime()) : null;

        Restaurant rest = null;
        if (eagerFK && fkRest > 0) {
            rest = restaurantMapper.findById(fkRest);
        } else if (fkRest > 0) {
            // Variante light si besoin :
            // rest = new Restaurant(fkRest, null, null, null, (Localisation) null, (RestaurantType) null);
        }

        return new CompleteEvaluation(id, visitDate, rest, comment, username);
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
    protected void addToCache(CompleteEvaluation objet) {
        if (objet != null && objet.getId() != null && !cache.containsKey(objet.getId())) {
            cache.put(objet.getId(), objet);
        }
    }

    @Override
    protected void removeFromCache(Integer id) {
        if (id != null) cache.remove(id);
    }
}
