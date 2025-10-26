package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.business.*;
import ch.hearc.ig.guideresto.persistence.*;
import java.util.*;

public class EvaluationServiceImpl implements EvaluationService {
    private final RestaurantMapper restaurantMapper = new RestaurantMapper();
    private final BasicEvaluationMapper basicMapper = new BasicEvaluationMapper();
    private final CompleteEvaluationMapper completeMapper = new CompleteEvaluationMapper();
    private final GradeMapper gradeMapper = new GradeMapper();

    @Override
    public BasicEvaluation addLike(int restaurantId, boolean like, String ip) {
        TransactionManager.begin();
        try {
            Restaurant r = restaurantMapper.findById(restaurantId);
            if (r == null) throw new ServiceException("Restaurant not found: " + restaurantId);
            BasicEvaluation be = new BasicEvaluation(null, new Date(), r, like, ip);
            BasicEvaluation out = basicMapper.create(be);
            TransactionManager.commit();
            return out;
        } catch (Exception e) {
            TransactionManager.rollback();
            throw new ServiceException("add like", e);
        }
    }

    @Override public Set<BasicEvaluation> listBasicByRestaurant(int restaurantId) {
        return basicMapper.findByRestaurantId(restaurantId);
    }

    @Override
    public CompleteEvaluation addCompleteEvaluation(int restaurantId, String username, String comment, Set<Grade> grades) {
        TransactionManager.begin();
        try {
            Restaurant r = restaurantMapper.findById(restaurantId);
            if (r == null) throw new ServiceException("Restaurant not found: " + restaurantId);

            CompleteEvaluation ce = new CompleteEvaluation(null, new Date(), r, comment, username);
            ce = completeMapper.create(ce); // PK ici

            if (grades != null) {
                for (Grade g : grades) {
                    g.setEvaluation(ce);
                    gradeMapper.create(g);
                }
            }

            TransactionManager.commit();
            return ce;
        } catch (Exception e) {
            TransactionManager.rollback();
            throw new ServiceException("add complete evaluation", e);
        }
    }

    @Override public Set<CompleteEvaluation> listCompleteByRestaurant(int restaurantId) {
        return completeMapper.findByRestaurantId(restaurantId);
    }

    @Override
    public CompleteEvaluation getCompleteWithGrades(int evaluationId) {
        return completeMapper.findWithGradesById(evaluationId);
    }

    @Override
    public Set<Evaluation> listAllByRestaurant(int restaurantId) {
        Set<Evaluation> out = new LinkedHashSet<>();
        out.addAll(basicMapper.findByRestaurantId(restaurantId));
        Set<CompleteEvaluation> comments = completeMapper.findByRestaurantId(restaurantId);
        for (CompleteEvaluation ce : comments) {
            completeMapper.loadGrades(ce); // lazy -> chargé ici
        }
        out.addAll(comments);
        return out;
    }

}
