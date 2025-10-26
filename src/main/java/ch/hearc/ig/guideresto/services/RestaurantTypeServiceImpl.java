package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.business.RestaurantType;
import ch.hearc.ig.guideresto.persistence.RestaurantTypeMapper;
import java.util.Set;

public class RestaurantTypeServiceImpl implements RestaurantTypeService {
    private final RestaurantTypeMapper typeMapper = new RestaurantTypeMapper();
    @Override public Set<RestaurantType> listAll() { return typeMapper.findAll(); }
    @Override public RestaurantType getByLabel(String label) { return typeMapper.findByLabel(label); }
}
