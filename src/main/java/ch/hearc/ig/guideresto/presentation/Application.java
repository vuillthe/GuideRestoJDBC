package ch.hearc.ig.guideresto.presentation;

import ch.hearc.ig.guideresto.business.*;
import ch.hearc.ig.guideresto.persistence.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Application console GuideResto — version JDBC (Oracle) avec Data Mappers.
 */
public class Application {

    private static Scanner scanner;
    private static final Logger logger = LogManager.getLogger(Application.class);

    // ---------- MAPPERS ----------
    private static final CityMapper cityMapper = new CityMapper();
    private static final RestaurantTypeMapper typeMapper = new RestaurantTypeMapper();
    private static final RestaurantMapper restaurantMapper = new RestaurantMapper();
    private static final EvaluationCriteriaMapper criteriaMapper = new EvaluationCriteriaMapper();
    private static final BasicEvaluationMapper likeMapper = new BasicEvaluationMapper();
    private static final CompleteEvaluationMapper commentMapper = new CompleteEvaluationMapper();
    private static final GradeMapper gradeMapper = new GradeMapper();

    public static void main(String[] args) {
        scanner = new Scanner(System.in);

        System.out.println("Bienvenue dans GuideResto ! Que souhaitez-vous faire ?");
        int choice;
        do {
            printMainMenu();
            choice = readInt();
            proceedMainMenu(choice);
        } while (choice != 0);

        // Fermer proprement la connexion JDBC
        ConnectionUtils.closeConnection();
        System.out.println("Au revoir !");
    }

    // =======================
    // Menu principal
    // =======================
    private static void printMainMenu() {
        System.out.println("======================================================");
        System.out.println("Que voulez-vous faire ?");
        System.out.println("1. Afficher la liste de tous les restaurants");
        System.out.println("2. Rechercher un restaurant par son nom");
        System.out.println("3. Rechercher un restaurant par ville");
        System.out.println("4. Rechercher un restaurant par son type de cuisine");
        System.out.println("5. Saisir un nouveau restaurant");
        System.out.println("0. Quitter l'application");
    }

    private static void proceedMainMenu(int choice) {
        switch (choice) {
            case 1 -> showRestaurantsList();
            case 2 -> searchRestaurantByName();
            case 3 -> searchRestaurantByCity();
            case 4 -> searchRestaurantByType();
            case 5 -> addNewRestaurant();
            case 0 -> { /* handled in main() */ }
            default -> System.out.println("Erreur : saisie incorrecte. Veuillez réessayer");
        }
    }

    // =======================
    // Sélection d’objets
    // =======================
    private static Restaurant pickRestaurant(Set<Restaurant> restaurants) {
        if (restaurants == null || restaurants.isEmpty()) {
            System.out.println("Aucun restaurant n'a été trouvé !");
            return null;
        }
        for (Restaurant currentRest : restaurants) {
            String result = "\"" + currentRest.getName() + "\" - "
                    + (currentRest.getAddress() != null ? currentRest.getAddress().getStreet() : "?") + " - "
                    + (currentRest.getAddress() != null && currentRest.getAddress().getCity() != null
                    ? currentRest.getAddress().getCity().getZipCode() + " " + currentRest.getAddress().getCity().getCityName()
                    : "?");
            System.out.println(result);
        }
        System.out.println("Veuillez saisir le nom exact du restaurant dont vous voulez voir le détail, ou appuyez sur Enter pour revenir en arrière");
        String choice = readString();
        return searchRestaurantByName(restaurants, choice);
    }

    private static City pickCity(Set<City> cities) {
        System.out.println("Voici la liste des villes possibles, veuillez entrer le NPA de la ville désirée : ");
        for (City currentCity : cities) {
            System.out.println(currentCity.getZipCode() + " " + currentCity.getCityName());
        }
        System.out.println("Entrez \"NEW\" pour créer une nouvelle ville");
        String choice = readString();

        if (choice.equalsIgnoreCase("NEW")) {
            City city = new City();
            System.out.println("Veuillez entrer le NPA de la nouvelle ville : ");
            city.setZipCode(readString());
            System.out.println("Veuillez entrer le nom de la nouvelle ville : ");
            city.setCityName(readString());
            // Persist en DB
            City created = cityMapper.create(city);
            if (created == null) {
                System.out.println("Erreur lors de la création de la ville.");
                return null;
            }
            return created;
        }
        return searchCityByZipCode(cities, choice);
    }

    private static RestaurantType pickRestaurantType(Set<RestaurantType> types) {
        System.out.println("Voici la liste des types possibles, veuillez entrer le libellé exact du type désiré : ");
        for (RestaurantType currentType : types) {
            System.out.println("\"" + currentType.getLabel() + "\" : " + currentType.getDescription());
        }
        String choice = readString();
        return searchTypeByLabel(types, choice);
    }

    // =======================
    // Use cases
    // =======================
    private static void showRestaurantsList() {
        System.out.println("Liste des restaurants : ");
        Set<Restaurant> all = restaurantMapper.findAll();
        Restaurant restaurant = pickRestaurant(all);
        if (restaurant != null) {
            loadEvaluations(restaurant);
            showRestaurant(restaurant);
        }
    }

    private static void searchRestaurantByName() {
        System.out.println("Veuillez entrer une partie du nom recherché : ");
        String research = readString();
        Set<Restaurant> list = restaurantMapper.findByNameLike(research);
        Restaurant restaurant = pickRestaurant(list);
        if (restaurant != null) {
            loadEvaluations(restaurant);
            showRestaurant(restaurant);
        }
    }

    private static void searchRestaurantByCity() {
        System.out.println("Veuillez entrer une partie du nom de la ville désirée : ");
        String research = readString();

        // Trouver les villes qui matchent
        Set<City> allCities = cityMapper.findAll();
        Set<Integer> cityIds = new HashSet<>();
        for (City c : allCities) {
            if (c.getCityName().toUpperCase().contains(research.toUpperCase())) {
                cityIds.add(c.getId());
            }
        }
        // Cumuler les restaurants de ces villes
        Set<Restaurant> filtered = new LinkedHashSet<>();
        for (Integer cityId : cityIds) {
            filtered.addAll(restaurantMapper.findByCityId(cityId));
        }

        Restaurant restaurant = pickRestaurant(filtered);
        if (restaurant != null) {
            loadEvaluations(restaurant);
            showRestaurant(restaurant);
        }
    }

    private static void searchRestaurantByType() {
        RestaurantType chosenType = pickRestaurantType(typeMapper.findAll());
        if (chosenType == null) {
            System.out.println("Aucun type sélectionné.");
            return;
        }
        Set<Restaurant> filtered = restaurantMapper.findByTypeId(chosenType.getId());
        Restaurant restaurant = pickRestaurant(filtered);
        if (restaurant != null) {
            loadEvaluations(restaurant);
            showRestaurant(restaurant);
        }
    }

    private static void addNewRestaurant() {
        System.out.println("Vous allez ajouter un nouveau restaurant !");
        System.out.println("Quel est son nom ?");
        String name = readString();
        System.out.println("Veuillez entrer une courte description : ");
        String description = readString();
        System.out.println("Veuillez entrer l'adresse de son site internet : ");
        String website = readString();
        System.out.println("Rue : ");
        String street = readString();

        City city;
        do {
            city = pickCity(cityMapper.findAll());
        } while (city == null);

        RestaurantType restaurantType;
        do {
            restaurantType = pickRestaurantType(typeMapper.findAll());
        } while (restaurantType == null);

        Restaurant restaurant = new Restaurant(
                null, name, description, website,
                new Localisation(street, city),
                restaurantType
        );
        restaurant = restaurantMapper.create(restaurant);
        if (restaurant == null) {
            System.out.println("Erreur lors de la création du restaurant.");
            return;
        }
        loadEvaluations(restaurant);
        showRestaurant(restaurant);
    }

    private static void showRestaurant(Restaurant restaurant) {
        // (Re)charger les évaluations (likes & commentaires + notes)
        loadEvaluations(restaurant);

        System.out.println("Affichage d'un restaurant : ");
        StringBuilder sb = new StringBuilder();
        sb.append(restaurant.getName()).append("\n");
        sb.append(restaurant.getDescription()).append("\n");
        sb.append(restaurant.getType() != null ? restaurant.getType().getLabel() : "(type inconnu)").append("\n");
        sb.append(restaurant.getWebsite()).append("\n");
        if (restaurant.getAddress() != null && restaurant.getAddress().getCity() != null) {
            sb.append(restaurant.getAddress().getStreet()).append(", ");
            sb.append(restaurant.getAddress().getCity().getZipCode()).append(" ")
                    .append(restaurant.getAddress().getCity().getCityName()).append("\n");
        }
        sb.append("Nombre de likes : ").append(countLikes(restaurant.getEvaluations(), true)).append("\n");
        sb.append("Nombre de dislikes : ").append(countLikes(restaurant.getEvaluations(), false)).append("\n");
        sb.append("\nEvaluations reçues : ").append("\n");

        for (Evaluation currentEval : restaurant.getEvaluations()) {
            String text = getCompleteEvaluationDescription(currentEval);
            if (text != null) {
                sb.append(text).append("\n");
            }
        }
        System.out.println(sb);

        int choice;
        do {
            showRestaurantMenu();
            choice = readInt();
            proceedRestaurantMenu(choice, restaurant);
        } while (choice != 0 && choice != 6);
    }

    /** Affiche dans la console un ensemble d'actions réalisables sur le restaurant sélectionné. */
    private static void showRestaurantMenu() {
        System.out.println("======================================================");
        System.out.println("Que souhaitez-vous faire ?");
        System.out.println("1. J'aime ce restaurant !");
        System.out.println("2. Je n'aime pas ce restaurant !");
        System.out.println("3. Faire une évaluation complète de ce restaurant !");
        System.out.println("4. Editer ce restaurant");
        System.out.println("5. Editer l'adresse du restaurant");
        System.out.println("6. Supprimer ce restaurant");
        System.out.println("0. Revenir au menu principal");
    }

    private static void proceedRestaurantMenu(int choice, Restaurant restaurant) {
        switch (choice) {
            case 1 -> addBasicEvaluation(restaurant, true);
            case 2 -> addBasicEvaluation(restaurant, false);
            case 3 -> evaluateRestaurant(restaurant);
            case 4 -> editRestaurant(restaurant);
            case 5 -> editRestaurantAddress(restaurant);
            case 6 -> deleteRestaurant(restaurant);
            case 0 -> { /* retour */ }
            default -> { /* ignore */ }
        }
    }

    // =======================
    // Évaluations
    // =======================
    private static void addBasicEvaluation(Restaurant restaurant, Boolean like) {
        String ipAddress;
        try {
            ipAddress = Inet4Address.getLocalHost().toString();
        } catch (UnknownHostException ex) {
            logger.error("Error - Couldn't retrieve host IP address");
            ipAddress = "Indisponible";
        }
        BasicEvaluation eval = new BasicEvaluation(new Date(), restaurant, like, ipAddress);
        BasicEvaluation persisted = likeMapper.create(eval);
        if (persisted != null) {
            restaurant.getEvaluations().add(persisted);
            System.out.println("Votre vote a été pris en compte !");
        } else {
            System.out.println("Erreur lors de l'enregistrement du vote.");
        }
    }

    private static void evaluateRestaurant(Restaurant restaurant) {
        System.out.println("Merci d'évaluer ce restaurant !");
        System.out.println("Quel est votre nom d'utilisateur ? ");
        String username = readString();
        System.out.println("Quel commentaire aimeriez-vous publier ?");
        String comment = readString();

        CompleteEvaluation eval = new CompleteEvaluation(new Date(), restaurant, comment, username);
        eval = commentMapper.create(eval);
        if (eval == null) {
            System.out.println("Erreur lors de la création de l'évaluation.");
            return;
        }

        System.out.println("Veuillez svp donner une note entre 1 et 5 pour chacun de ces critères : ");
        for (EvaluationCriteria currentCriteria : criteriaMapper.findAll()) {
            System.out.println(currentCriteria.getName() + " : " + currentCriteria.getDescription());
            Integer note = readInt();
            Grade grade = new Grade(note, eval, currentCriteria);
            gradeMapper.create(grade);
            eval.getGrades().add(grade);
        }

        restaurant.getEvaluations().add(eval);
        System.out.println("Votre évaluation a bien été enregistrée, merci !");
    }

    private static void loadEvaluations(Restaurant r) {
        if (r == null || r.getId() == null) return;
        r.getEvaluations().clear();

        // Likes
        r.getEvaluations().addAll(likeMapper.findByRestaurantId(r.getId()));

        // Commentaires + notes
        Set<CompleteEvaluation> comments = commentMapper.findByRestaurantId(r.getId());
        // charger les notes pour chaque commentaire
        for (CompleteEvaluation ce : comments) {
            commentMapper.loadGrades(ce);
        }
        r.getEvaluations().addAll(comments);
    }

    // =======================
    // Edition / suppression
    // =======================
    private static void editRestaurant(Restaurant restaurant) {
        System.out.println("Edition d'un restaurant !");
        System.out.println("Nouveau nom : ");
        restaurant.setName(readString());
        System.out.println("Nouvelle description : ");
        restaurant.setDescription(readString());
        System.out.println("Nouveau site web : ");
        restaurant.setWebsite(readString());
        System.out.println("Nouveau type de restaurant : ");
        RestaurantType newType = pickRestaurantType(typeMapper.findAll());
        if (newType != null) {
            restaurant.setType(newType);
        }
        if (restaurantMapper.update(restaurant)) {
            System.out.println("Merci, le restaurant a bien été modifié !");
        } else {
            System.out.println("Erreur lors de la mise à jour du restaurant.");
        }
    }

    private static void editRestaurantAddress(Restaurant restaurant) {
        System.out.println("Edition de l'adresse d'un restaurant !");
        System.out.println("Nouvelle rue : ");
        String newStreet = readString();
        City newCity = pickCity(cityMapper.findAll());
        if (restaurant.getAddress() == null) {
            restaurant.setAddress(new Localisation(newStreet, newCity));
        } else {
            restaurant.getAddress().setStreet(newStreet);
            restaurant.getAddress().setCity(newCity);
        }
        if (restaurantMapper.update(restaurant)) {
            System.out.println("L'adresse a bien été modifiée ! Merci !");
        } else {
            System.out.println("Erreur lors de la mise à jour de l'adresse.");
        }
    }

    private static void deleteRestaurant(Restaurant restaurant) {
        System.out.println("Etes-vous sûr de vouloir supprimer ce restaurant ? (O/n)");
        String choice = readString();
        if (!(choice.equalsIgnoreCase("o"))) return;

        // ⚠️ Contraintes FK : supprimer d'abord LIKES, COMMENTAIRES, puis NOTES des commentaires, puis le restaurant
        // 1) Likes
        for (BasicEvaluation be : likeMapper.findByRestaurantId(restaurant.getId())) {
            likeMapper.delete(be);
        }
        // 2) Commentaires + leurs notes
        for (CompleteEvaluation ce : commentMapper.findByRestaurantId(restaurant.getId())) {
            for (Grade g : gradeMapper.findByEvaluationId(ce.getId())) {
                gradeMapper.delete(g);
            }
            commentMapper.delete(ce);
        }
        // 3) Restaurant
        boolean ok = restaurantMapper.deleteById(restaurant.getId());
        if (ok) {
            System.out.println("Le restaurant a bien été supprimé !");
        } else {
            System.out.println("Erreur lors de la suppression (vérifiez les contraintes).");
        }
    }

    // =======================
    // Helpers d’affichage
    // =======================
    private static int countLikes(Set<Evaluation> evaluations, Boolean likeRestaurant) {
        int count = 0;
        for (Evaluation currentEval : evaluations) {
            if (currentEval instanceof BasicEvaluation && Objects.equals(((BasicEvaluation) currentEval).getLikeRestaurant(), likeRestaurant)) {
                count++;
            }
        }
        return count;
    }

    private static String getCompleteEvaluationDescription(Evaluation eval) {
        if (!(eval instanceof CompleteEvaluation)) return null;
        CompleteEvaluation ce = (CompleteEvaluation) eval;
        StringBuilder result = new StringBuilder();
        result.append("Evaluation de : ").append(ce.getUsername()).append("\n");
        result.append("Commentaire : ").append(ce.getComment()).append("\n");
        for (Grade currentGrade : ce.getGrades()) {
            result.append(currentGrade.getCriteria().getName())
                    .append(" : ").append(currentGrade.getGrade()).append("/5").append("\n");
        }
        return result.toString();
    }

    // =======================
    // Recherche en mémoire (utilisées pour les prompts)
    // =======================
    private static Restaurant searchRestaurantByName(Set<Restaurant> restaurants, String name) {
        if (name == null || name.isBlank()) return null;
        for (Restaurant current : restaurants) {
            if (current.getName().equalsIgnoreCase(name)) {
                return current;
            }
        }
        return null;
    }

    private static City searchCityByZipCode(Set<City> cities, String zipCode) {
        for (City current : cities) {
            if (current.getZipCode().equalsIgnoreCase(zipCode)) {
                return current;
            }
        }
        return null;
    }

    private static RestaurantType searchTypeByLabel(Set<RestaurantType> types, String label) {
        for (RestaurantType current : types) {
            if (current.getLabel().equalsIgnoreCase(label)) {
                return current;
            }
        }
        return null;
    }

    // =======================
    // IO utils
    // =======================
    private static int readInt() {
        int i = 0;
        boolean success = false;
        do {
            try {
                i = scanner.nextInt();
                success = true;
            } catch (InputMismatchException e) {
                System.out.println("Erreur ! Veuillez entrer un nombre entier s'il vous plaît !");
            } finally {
                scanner.nextLine();
            }
        } while (!success);
        return i;
    }

    private static String readString() {
        return scanner.nextLine();
    }
}
