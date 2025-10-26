package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.business.*;
import ch.hearc.ig.guideresto.persistence.*;
import java.util.*;

public class RestaurantServiceImpl implements RestaurantService {
    private final RestaurantMapper restaurantMapper = new RestaurantMapper();
    private final CityMapper cityMapper = new CityMapper();
    private final RestaurantTypeMapper typeMapper = new RestaurantTypeMapper();

    // pour la suppression en cascade
    private final BasicEvaluationMapper basicMapper = new BasicEvaluationMapper();
    private final CompleteEvaluationMapper completeMapper = new CompleteEvaluationMapper();
    private final GradeMapper gradeMapper = new GradeMapper();

    @Override public Set<Restaurant> listAll() { return restaurantMapper.findAll(); }
    @Override public Set<Restaurant> searchByName(String nameLike) { return restaurantMapper.findByNameLike(nameLike); }

    @Override
    public Set<Restaurant> searchByCity(String cityNameLike) {
        Set<Restaurant> out = new LinkedHashSet<>();
        for (City c : cityMapper.findAll()) {
            String n = c.getCityName();
            if (n != null && n.toUpperCase().contains(cityNameLike.toUpperCase())) {
                out.addAll(restaurantMapper.findByCityId(c.getId()));
            }
        }
        return out;
    }

    @Override
    public Set<Restaurant> searchByTypeLabel(String typeLabel) {
        RestaurantType t = typeMapper.findByLabel(typeLabel);
        return (t == null) ? Collections.emptySet() : restaurantMapper.findByTypeId(t.getId());
    }

    @Override public Restaurant getById(int id) { return restaurantMapper.findById(id); }

    @Override
    public Restaurant create(Restaurant r) {
        TransactionManager.begin();
        try { Restaurant out = restaurantMapper.create(r); TransactionManager.commit(); return out; }
        catch (Exception e) { TransactionManager.rollback(); throw new ServiceException("create restaurant", e); }
    }

    @Override
    public boolean update(Restaurant r) {
        TransactionManager.begin();
        try { boolean ok = restaurantMapper.update(r); TransactionManager.commit(); return ok; }
        catch (Exception e) { TransactionManager.rollback(); throw new ServiceException("update restaurant", e); }
    }

    @Override
    public boolean delete(int id) {
        TransactionManager.begin();
        try { boolean ok = restaurantMapper.deleteById(id); TransactionManager.commit(); return ok; }
        catch (Exception e) { TransactionManager.rollback(); throw new ServiceException("delete restaurant", e); }
    }

    @Override
    public boolean deleteCascade(int restaurantId) {
        TransactionManager.begin();
        try {
            // 1) LIKES
            for (BasicEvaluation be : basicMapper.findByRestaurantId(restaurantId)) {
                basicMapper.delete(be);
            }
            // 2) COMMENTAIRES + leurs NOTES
            for (CompleteEvaluation ce : completeMapper.findByRestaurantId(restaurantId)) {
                for (Grade g : gradeMapper.findByEvaluationId(ce.getId())) {
                    gradeMapper.delete(g);
                }
                completeMapper.delete(ce);
            }
            // 3) RESTAURANT
            boolean ok = restaurantMapper.deleteById(restaurantId);

            TransactionManager.commit();
            return ok;
        } catch (Exception e) {
            TransactionManager.rollback();
            throw new ServiceException("deleteCascade restaurant " + restaurantId, e);
        }
    }
}
