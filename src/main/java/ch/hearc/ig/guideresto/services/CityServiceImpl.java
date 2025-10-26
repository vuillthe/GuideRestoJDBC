package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.business.City;
import ch.hearc.ig.guideresto.persistence.CityMapper;
import java.util.Set;

public class CityServiceImpl implements CityService {
    private final CityMapper cityMapper = new CityMapper();
    @Override public Set<City> listAll() { return cityMapper.findAll(); }

    @Override
    public City create(City c) {
        TransactionManager.begin();
        try { City out = cityMapper.create(c); TransactionManager.commit(); return out; }
        catch (Exception e) { TransactionManager.rollback(); throw new ServiceException("create city", e); }
    }
}
