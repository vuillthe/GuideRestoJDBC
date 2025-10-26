package ch.hearc.ig.guideresto.persistence;

import ch.hearc.ig.guideresto.business.City;
import ch.hearc.ig.guideresto.business.Localisation;
import ch.hearc.ig.guideresto.business.Restaurant;
import ch.hearc.ig.guideresto.business.RestaurantType;

import java.sql.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Data Mapper pour Restaurant ↔ table RESTAURANTS.
 * - Transactions : AUCUN commit/rollback ici → gérés par la couche service.
 * - Cache : Identity Map générique (AbstractMapper).
 */
public class RestaurantMapper extends AbstractMapper<Restaurant> {

    // Dépendances (FK)
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
        Restaurant cached = getFromCache(id);
        if (cached != null) return cached;

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
                    Restaurant r = mapRow(rs, true);
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
                Restaurant r = mapRow(rs, true);
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

            // FK_TYPE
            if (object.getType() != null && object.getType().getId() != null) {
                ps.setInt(5, object.getType().getId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            // FK_VILL (city)
            Integer cityId = (object.getAddress() != null && object.getAddress().getCity() != null)
                    ? object.getAddress().getCity().getId()
                    : null;
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

            addToCache(object);
            return object;
        } catch (SQLException ex) {
            logger.error("create(Restaurant) - SQLException: {}", ex.getMessage(), ex);
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

            // FK_TYPE
            if (object.getType() != null && object.getType().getId() != null) {
                ps.setInt(5, object.getType().getId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            // FK_VILL
            Integer cityId = (object.getAddress() != null && object.getAddress().getCity() != null)
                    ? object.getAddress().getCity().getId()
                    : null;
            if (cityId != null) {
                ps.setInt(6, cityId);
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            ps.setInt(7, object.getId());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                addToCache(object); // refresh dans l’Identity Map
                return true;
            }
        } catch (SQLException ex) {
            logger.error("update(Restaurant) - SQLException: {}", ex.getMessage(), ex);
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
            if (deleted > 0) {
                removeFromCache(id);
                return true;
            }
        } catch (SQLException ex) {
            logger.error("deleteById({}) - SQLException: {}", id, ex.getMessage(), ex);
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

        Integer fkType = getNullableInt(rs, "FK_TYPE");
        Integer fkCity = getNullableInt(rs, "FK_VILL");

        RestaurantType type = null;
        City city = null;

        if (eagerFK) {
            if (fkType != null) type = typeMapper.findById(fkType);
            if (fkCity != null) city = cityMapper.findById(fkCity);
        }

        Localisation loc = new Localisation(street, city);
        return new Restaurant(id, name, description, website, loc, type);
    }

    /** Lecture d’un entier nullable (getInt + wasNull). */
    private Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
