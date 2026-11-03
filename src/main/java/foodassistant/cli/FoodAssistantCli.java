package foodassistant.cli;

import foodassistant.map.ExternalMapClient;
import foodassistant.map.MapProviderAdapter;
import foodassistant.map.MapService;
import foodassistant.repository.RestaurantRepository;

import java.io.IOException;
import java.nio.file.Path;

public final class FoodAssistantCli {
    private static final double TORONTO_LAT = 43.70;
    private static final double TORONTO_LNG = -79.38;
    private static final double RADIUS_KM = 20;

    private FoodAssistantCli() {
    }

    public static void main(String[] args) throws IOException {
        String query = "";
        String cuisine = "";
        boolean mapAvailable = true;
        Path data = Path.of("data", "restaurants.json");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--query" -> query = next(args, ++i);
                case "--cuisine" -> cuisine = next(args, ++i);
                case "--unavailable" -> mapAvailable = false;
                case "--data" -> data = Path.of(next(args, ++i));
                case "--help" -> {
                    printHelp();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        RestaurantRepository repository = RestaurantRepository.load(data);
        ExternalMapClient client = new ExternalMapClient(mapAvailable, System.out);
        MapService mapService = new MapService(repository, new MapProviderAdapter(client), System.out);
        mapService.loadMap(query, cuisine, TORONTO_LAT, TORONTO_LNG, RADIUS_KM);
    }

    private static String next(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }

    private static void printHelp() {
        System.out.println("""
                Food map (F01)
                  java -cp out foodassistant.cli.FoodAssistantCli [--query text] [--cuisine text] [--unavailable]
                --unavailable prints a list instead of map pins.
                """);
    }
}
