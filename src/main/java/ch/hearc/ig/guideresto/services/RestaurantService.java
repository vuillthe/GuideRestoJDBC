package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.business.Restaurant;
import java.util.Set;

public interface RestaurantService {
    Set<Restaurant> listAll();
    Set<Restaurant> searchByName(String nameLike);
    Set<Restaurant> searchByCity(String cityNameLike);
    Set<Restaurant> searchByTypeLabel(String typeLabel);
    Restaurant getById(int id);
    Restaurant create(Restaurant r);
    boolean update(Restaurant r);
    boolean delete(int id);
    boolean deleteCascade(int restaurantId);
}
