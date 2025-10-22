package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.BasicEvaluation;
import ch.hearc.ig.guideresto.business.Restaurant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Data Mapper pour BasicEvaluation ↔ table LIKES.
 * - PK : NUMERO
 * - Colonnes : APPRECIATION (CHAR(1): 'T'/'F'), DATE_EVAL (DATE), ADRESSE_IP, FK_REST
 * - FK : FK_REST → RESTAURANTS.NUMERO
 * - Séquence/trigger : SEQ_EVAL + TR_BIF_LIKES
 *
 * Conversions :
 * - likeRestaurant(Boolean) <-> APPRECIATION ('T' pour true, 'F' pour false)
 * - visitDate(java.util.Date) <-> DATE_EVAL(Oracle DATE via Timestamp)
 *
 * Transactions : create/update/delete -> commit() si OK, rollback() en cas d'erreur.
 *
 * Cache : Identity Map (Map<Integer, BasicEvaluation>)
 */
public class BasicEvaluationMapper extends AbstractMapper<BasicEvaluation> {

    // -------------------------------
    // Identity Map
    // -------------------------------
    private final Map<Integer, BasicEvaluation> cache = new HashMap<>();

    // -------------------------------
    // Dépendances (FK)
    // -------------------------------
    private final RestaurantMapper restaurantMapper = new RestaurantMapper();

    // -------------------------------
    // Requêtes "génériques" (Abstract)
    // -------------------------------
    @Override
    protected String getSequenceQuery() {
        // Même séquence que COMMENTAIRES côté schéma
        return "SELECT SEQ_EVAL.CURRVAL FROM dual";
    }

    @Override
    protected String getExistsQuery() {
        return "SELECT 1 FROM LIKES WHERE NUMERO = ?";
    }

    @Override
    protected String getCountQuery() {
        return "SELECT COUNT(*) FROM LIKES";
    }

    // -------------------------------
    // CRUD & Finders
    // -------------------------------
    @Override
    public BasicEvaluation findById(int id) {
        if (cache.containsKey(id)) return cache.get(id);

        String sql = "SELECT NUMERO, APPRECIATION, DATE_EVAL, ADRESSE_IP, FK_REST FROM LIKES WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BasicEvaluation be = mapRow(rs, /*eagerFK=*/true);
                    addToCache(be);
                    return be;
                }
            }
        } catch (SQLException ex) {
            logger.error("findById({}) - SQLException: {}", id, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public Set<BasicEvaluation> findAll() {
        String sql = "SELECT NUMERO, APPRECIATION, DATE_EVAL, ADRESSE_IP, FK_REST FROM LIKES ORDER BY DATE_EVAL DESC, NUMERO DESC";
        Set<BasicEvaluation> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BasicEvaluation be = mapRow(rs, true);
                addToCache(be);
                out.add(be);
            }
        } catch (SQLException ex) {
            logger.error("findAll() - SQLException: {}", ex.getMessage(), ex);
        }
        return out;
    }

    /** Toutes les likes d'un restaurant. */
    public Set<BasicEvaluation> findByRestaurantId(int restaurantId) {
        String sql = "SELECT NUMERO, APPRECIATION, DATE_EVAL, ADRESSE_IP, FK_REST FROM LIKES WHERE FK_REST = ? ORDER BY DATE_EVAL DESC, NUMERO DESC";
        Set<BasicEvaluation> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BasicEvaluation be = mapRow(rs, true);
                    addToCache(be);
                    out.add(be);
                }
            }
        } catch (SQLException ex) {
            logger.error("findByRestaurantId({}) - SQLException: {}", restaurantId, ex.getMessage(), ex);
        }
        return out;
    }

    /** Recherche par adresse IP. */
    public Set<BasicEvaluation> findByIp(String ip) {
        String sql = "SELECT NUMERO, APPRECIATION, DATE_EVAL, ADRESSE_IP, FK_REST FROM LIKES WHERE ADRESSE_IP = ? ORDER BY DATE_EVAL DESC, NUMERO DESC";
        Set<BasicEvaluation> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BasicEvaluation be = mapRow(rs, true);
                    addToCache(be);
                    out.add(be);
                }
            }
        } catch (SQLException ex) {
            logger.error("findByIp({}) - SQLException: {}", ip, ex.getMessage(), ex);
        }
        return out;
    }

    @Override
    public BasicEvaluation create(BasicEvaluation object) {
        // FK_REST requis
        if (object.getRestaurant() == null || object.getRestaurant().getId() == null) {
            logger.warn("create(BasicEvaluation) : restaurant (FK_REST) manquant.");
            return null;
        }

        String sql = "INSERT INTO LIKES (APPRECIATION, DATE_EVAL, ADRESSE_IP, FK_REST) VALUES (?, ?, ?, ?)";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, toAppreciationChar(object.getLikeRestaurant()));
            Timestamp ts = new Timestamp(
                    (object.getVisitDate() != null ? object.getVisitDate().getTime() : System.currentTimeMillis())
            );
            ps.setTimestamp(2, ts);
            ps.setString(3, object.getIpAddress());
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
            logger.error("create(BasicEvaluation) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("create(BasicEvaluation) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
        }
        return null;
    }

    @Override
    public boolean update(BasicEvaluation object) {
        if (object.getId() == null) {
            logger.warn("update(BasicEvaluation) appelé avec un ID null.");
            return false;
        }
        if (object.getRestaurant() == null || object.getRestaurant().getId() == null) {
            logger.warn("update(BasicEvaluation) : restaurant (FK_REST) manquant.");
            return false;
        }

        String sql = "UPDATE LIKES SET APPRECIATION = ?, DATE_EVAL = ?, ADRESSE_IP = ?, FK_REST = ? WHERE NUMERO = ?";
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, toAppreciationChar(object.getLikeRestaurant()));
            Timestamp ts = new Timestamp(
                    (object.getVisitDate() != null ? object.getVisitDate().getTime() : System.currentTimeMillis())
            );
            ps.setTimestamp(2, ts);
            ps.setString(3, object.getIpAddress());
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
            logger.error("update(BasicEvaluation) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("update(BasicEvaluation) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
        }
        return false;
    }

    @Override
    public boolean delete(BasicEvaluation object) {
        if (object == null || object.getId() == null) {
            logger.warn("delete(BasicEvaluation) appelé avec un objet/ID null.");
            return false;
        }
        return deleteById(object.getId());
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM LIKES WHERE NUMERO = ?";
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
    private BasicEvaluation mapRow(ResultSet rs, boolean eagerFK) throws SQLException {
        int id = rs.getInt("NUMERO");
        String appr = rs.getString("APPRECIATION");
        Timestamp ts = rs.getTimestamp("DATE_EVAL");
        String ip = rs.getString("ADRESSE_IP");
        int fkRest = rs.getInt("FK_REST");

        Boolean like = fromAppreciationChar(appr);
        java.util.Date visitDate = (ts != null) ? new java.util.Date(ts.getTime()) : null;

        Restaurant rest = null;
        if (eagerFK && fkRest > 0) {
            rest = restaurantMapper.findById(fkRest);
        } else if (fkRest > 0) {
            // Variante light si besoin :
            // rest = new Restaurant(fkRest, null, null, null, (Localisation) null, (RestaurantType) null);
        }

        return new BasicEvaluation(id, visitDate, rest, like, ip);
    }

    private static String toAppreciationChar(Boolean like) {
        if (like == null) return "F"; // par défaut false
        return like ? "T" : "F";
    }

    private static Boolean fromAppreciationChar(String s) {
        if (s == null) return null;
        return "T".equalsIgnoreCase(s);
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
    protected void addToCache(BasicEvaluation objet) {
        if (objet != null && objet.getId() != null && !cache.containsKey(objet.getId())) {
            cache.put(objet.getId(), objet);
        }
    }

    @Override
    protected void removeFromCache(Integer id) {
        if (id != null) cache.remove(id);
    }
}
