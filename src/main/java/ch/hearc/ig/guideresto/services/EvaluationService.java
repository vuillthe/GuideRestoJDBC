package ch.hearc.ig.guideresto.services;

import ch.hearc.ig.guideresto.business.*;
import java.util.Set;

public interface EvaluationService {
    // Likes
    BasicEvaluation addLike(int restaurantId, boolean like, String ip);
    Set<BasicEvaluation> listBasicByRestaurant(int restaurantId);
    Set<Evaluation> listAllByRestaurant(int restaurantId); // likes + commentaires(+notes)


    // Commentaires + notes (transactionnel)
    CompleteEvaluation addCompleteEvaluation(int restaurantId, String username, String comment, Set<Grade> grades);
    Set<CompleteEvaluation> listCompleteByRestaurant(int restaurantId);
    CompleteEvaluation getCompleteWithGrades(int evaluationId);
}
