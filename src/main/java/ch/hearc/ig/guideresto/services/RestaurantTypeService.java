package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.business.RestaurantType;
import java.util.Set;

public interface RestaurantTypeService {
    Set<RestaurantType> listAll();
    RestaurantType getByLabel(String label);
}
