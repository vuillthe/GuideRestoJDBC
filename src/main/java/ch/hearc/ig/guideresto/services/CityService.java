package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.business.City;
import java.util.Set;

public interface CityService {
    Set<City> listAll();
    City create(City c);
}
