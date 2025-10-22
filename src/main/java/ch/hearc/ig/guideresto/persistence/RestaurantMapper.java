package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.City;
import ch.hearc.ig.guideresto.business.Localisation;
import ch.hearc.ig.guideresto.business.Restaurant;
import ch.hearc.ig.guideresto.business.RestaurantType;

import java.sql.*;
import java.util.*;

/**
 * Data Mapper pour Restaurant ↔ table RESTAURANTS.
 * - PK : NUMERO (NUMBER)
 * - Colonnes : NOM, ADRESSE, DESCRIPTION (CLOB), SITE_WEB, FK_TYPE, FK_VILL
 * - Séquence/trigger : SEQ_RESTAURANTS + TR_BIF_RESTAURANTS
 *
 * Transactions :
 * - create/update/delete -> commit() si OK, rollback() en cas d'erreur.
 *
 * Chargement :
 * - City (FK_VILL) et RestaurantType (FK_TYPE) chargés en EAGER (findById) via leurs mappers.
 * - evaluations (1-*) laissées en LAZY (non chargées ici).
 *
 * Cache (Identity Map) :
 * - Map<Integer, Restaurant> cache : évite les doublons en mémoire.
 */
public class RestaurantMapper extends AbstractMapper<Restaurant> {

    // -------------------------------
    // Identity Map
    // -------------------------------
    private final Map<Integer, Restaurant> cache = new HashMap<>();

    // -------------------------------
    // Dépendances (FK)
    // -------------------------------
    private final CityMapper cityMapper = new CityMapper();
    private final RestaurantTypeMapper typeMapper = new RestaurantTypeMapper();

    // -------------------------------
    // Requêtes "génériques" (Abstract)
    // -------------------------------
    @Override
    protected String getSequenceQuery() {
        return "SELECT SEQ_RESTAURANTS.CURRVAL FROM dual";
    }

    @Override
    protected String getExistsQuery() {
        return "SELECT 1 FROM RESTAURANTS WHERE NUMERO = ?";
    }

    @Override
    protected String getCountQuery() {
        return "SELECT COUNT(*) FROM RESTAURANTS";
    }

    // -------------------------------
    // CRUD & finders
    // -------------------------------
    @Override
    public Restaurant findById(int id) {
        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        String sql = """
                SELECT NUMERO, NOM, ADRESSE, DESCRIPTION, SITE_WEB, FK_TYPE, FK_VILL
                FROM RESTAURANTS
                WHERE NUMERO = ?
                """;
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Restaurant r = mapRow(rs, /*eagerFK=*/true);
                    addToCache(r);
                    return r;
                }
            }
        } catch (SQLException ex) {
            logger.error("findById({}) - SQLException: {}", id, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public Set<Restaurant> findAll() {
        String sql = """
                SELECT NUMERO, NOM, ADRESSE, DESCRIPTION, SITE_WEB, FK_TYPE, FK_VILL
                FROM RESTAURANTS
                ORDER BY NOM
                """;
        Set<Restaurant> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                // EAGER pour les FK (type + ville) : simple et cohérent avec notre stratégie
                Restaurant r = mapRow(rs, /*eagerFK=*/true);
                addToCache(r);
                out.add(r);
            }
        } catch (SQLException ex) {
            logger.error("findAll() - SQLException: {}", ex.getMessage(), ex);
        }
        return out;
    }

    /** Recherche par libellé (LIKE, insensible à la casse) */
    public Set<Restaurant> findByNameLike(String nameLike) {
        String sql = """
                SELECT NUMERO, NOM, ADRESSE, DESCRIPTION, SITE_WEB, FK_TYPE, FK_VILL
                FROM RESTAURANTS
                WHERE UPPER(NOM) LIKE UPPER(?)
                ORDER BY NOM
                """;
        Set<Restaurant> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, "%" + nameLike + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Restaurant r = mapRow(rs, true);
                    addToCache(r);
                    out.add(r);
                }
            }
        } catch (SQLException ex) {
            logger.error("findByNameLike({}) - SQLException: {}", nameLike, ex.getMessage(), ex);
        }
        return out;
    }

    /** Recherche par type (FK_TYPE) */
    public Set<Restaurant> findByTypeId(int typeId) {
        String sql = """
                SELECT NUMERO, NOM, ADRESSE, DESCRIPTION, SITE_WEB, FK_TYPE, FK_VILL
                FROM RESTAURANTS
                WHERE FK_TYPE = ?
                ORDER BY NOM
                """;
        Set<Restaurant> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Restaurant r = mapRow(rs, true);
                    addToCache(r);
                    out.add(r);
                }
            }
        } catch (SQLException ex) {
            logger.error("findByTypeId({}) - SQLException: {}", typeId, ex.getMessage(), ex);
        }
        return out;
    }

    /** Recherche par ville (FK_VILL) */
    public Set<Restaurant> findByCityId(int cityId) {
        String sql = """
                SELECT NUMERO, NOM, ADRESSE, DESCRIPTION, SITE_WEB, FK_TYPE, FK_VILL
                FROM RESTAURANTS
                WHERE FK_VILL = ?
                ORDER BY NOM
                """;
        Set<Restaurant> out = new LinkedHashSet<>();
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Restaurant r = mapRow(rs, true);
                    addToCache(r);
                    out.add(r);
                }
            }
        } catch (SQLException ex) {
            logger.error("findByCityId({}) - SQLException: {}", cityId, ex.getMessage(), ex);
        }
        return out;
    }

    @Override
    public Restaurant create(Restaurant object) {
        String sql = """
                INSERT INTO RESTAURANTS (NOM, ADRESSE, DESCRIPTION, SITE_WEB, FK_TYPE, FK_VILL)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, object.getName());
            ps.setString(2, object.getAddress() != null ? object.getAddress().getStreet() : null);
            ps.setString(3, object.getDescription());
            ps.setString(4, object.getWebsite());
            ps.setInt(5, object.getType() != null ? object.getType().getId() : Types.NULL);
            // city id (dans Localisation)
            Integer cityId = null;
            if (object.getAddress() != null && object.getAddress().getCity() != null) {
                cityId = object.getAddress().getCity().getId();
            }
            if (cityId != null) {
                ps.setInt(6, cityId);
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            ps.executeUpdate();

            // ID généré par trigger/séquence
            try (PreparedStatement psSeq = cn.prepareStatement(getSequenceQuery());
                 ResultSet rs = psSeq.executeQuery()) {
                if (rs.next()) {
                    object.setId(rs.getInt(1));
                }
            }

            cn.commit();
            addToCache(object);
            return object;
        } catch (SQLException ex) {
            logger.error("create(Restaurant) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("create(Restaurant) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
        }
        return null;
    }

    @Override
    public boolean update(Restaurant object) {
        if (object.getId() == null) {
            logger.warn("update(Restaurant) appelé avec un ID null.");
            return false;
        }

        String sql = """
                UPDATE RESTAURANTS
                SET NOM = ?, ADRESSE = ?, DESCRIPTION = ?, SITE_WEB = ?, FK_TYPE = ?, FK_VILL = ?
                WHERE NUMERO = ?
                """;
        Connection cn = ConnectionUtils.getConnection();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, object.getName());
            ps.setString(2, object.getAddress() != null ? object.getAddress().getStreet() : null);
            ps.setString(3, object.getDescription());
            ps.setString(4, object.getWebsite());
            if (object.getType() != null && object.getType().getId() != null) {
                ps.setInt(5, object.getType().getId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            Integer cityId = null;
            if (object.getAddress() != null && object.getAddress().getCity() != null) {
                cityId = object.getAddress().getCity().getId();
            }
            if (cityId != null) {
                ps.setInt(6, cityId);
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.setInt(7, object.getId());

            int updated = ps.executeUpdate();
            cn.commit();

            if (updated > 0) {
                removeFromCache(object.getId());
                addToCache(object);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("update(Restaurant) - SQLException: {}", ex.getMessage(), ex);
            try { cn.rollback(); } catch (SQLException rollEx) {
                logger.error("update(Restaurant) - rollback error: {}", rollEx.getMessage(), rollEx);
            }
        }
        return false;
    }

    @Override
    public boolean delete(Restaurant object) {
        if (object == null || object.getId() == null) {
            logger.warn("delete(Restaurant) appelé avec un objet/ID null.");
            return false;
        }
        return deleteById(object.getId());
    }

    @Override
    public boolean deleteById(int id) {
        String sql = "DELETE FROM RESTAURANTS WHERE NUMERO = ?";
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
    private Restaurant mapRow(ResultSet rs, boolean eagerFK) throws SQLException {
        int id = rs.getInt("NUMERO");
        String name = rs.getString("NOM");
        String street = rs.getString("ADRESSE");
        String description = rs.getString("DESCRIPTION");
        String website = rs.getString("SITE_WEB");
        int fkType = rs.getInt("FK_TYPE");
        int fkCity = rs.getInt("FK_VILL");

        RestaurantType type = null;
        City city = null;

        if (eagerFK) {
            if (fkType > 0) {
                type = typeMapper.findById(fkType);
            }
            if (fkCity > 0) {
                city = cityMapper.findById(fkCity);
            }
        } else {
            // Variante "light" si tu veux éviter un aller/retour DB de plus :
            // type = new RestaurantType(fkType, null, null);
            // city = new City(fkCity, null, null);
        }

        Localisation loc = new Localisation(street, city);
        return new Restaurant(id, name, description, website, loc, type);
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
    protected void addToCache(Restaurant objet) {
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
