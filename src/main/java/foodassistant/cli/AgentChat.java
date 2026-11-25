package foodassistant.cli;

import foodassistant.account.AccountBook;
import foodassistant.account.SupabaseDatabase;
import foodassistant.agent.FoodAgent;
import foodassistant.llm.LLMFactory;
import foodassistant.model.Account;
import foodassistant.model.Recommendation;
import foodassistant.model.Restaurant;
import foodassistant.places.PlacesSearch;
import foodassistant.taste.MemoryService;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Terminal session for {@link FoodAgent}. Each line comes from standard input or from a file.
 */
public final class AgentChat {
    private static final double TORONTO_LAT = 43.70;
    private static final double TORONTO_LNG = -79.38;
    private static final int HISTORY_LIMIT = 16;

    private AgentChat() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException | IllegalStateException | IOException exception) {
            System.err.println(exception.getMessage());
        }
    }

    public static void run(String[] args) throws IOException {
        String email = "";
        String password = "";
        double latitude = TORONTO_LAT;
        double longitude = TORONTO_LNG;
        Path file = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--chat" -> {
                }
                case "--email" -> email = next(args, ++i);
                case "--password" -> password = next(args, ++i);
                case "--lat" -> latitude = coordinate(next(args, ++i), "--lat");
                case "--lng" -> longitude = coordinate(next(args, ++i), "--lng");
                case "--file" -> file = Path.of(next(args, ++i));
                case "--help" -> {
                    printHelp();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (password.isBlank() && !email.isBlank()) {
            password = readPassword();
        }
        if (!password.isBlank() && email.isBlank()) {
            throw new IllegalArgumentException("Pass --email with --password.");
        }

        Path settings = Path.of("supabase.local");
        SupabaseDatabase database = SupabaseDatabase.open(settings);
        String accountId = "";
        if (!email.isBlank()) {
            AccountBook.SignedIn signedIn = AccountBook.load(database).signIn(email, password);
            Account account = signedIn.account();
            accountId = account.getId();
            System.out.println("Signed in as " + account.getName() + ".");
        } else {
            System.out.println("Not signed in. Pass --email and --password to use the taste saved on the website.");
        }
        FoodAgent agent = new FoodAgent(
                PlacesSearch.fromFile(settings),
                new MemoryService(database),
                LLMFactory.fromFile(settings));
        System.out.printf("Searching near %.2f, %.2f. Type a request, \"for you\", or quit.%n", latitude, longitude);

        List<FoodAgent.Turn> history = new ArrayList<>();
        try (BufferedReader reader = open(file)) {
            boolean prompt = file == null;
            if (prompt) {
                System.out.print("you> ");
                System.out.flush();
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty()) {
                    if (prompt) {
                        System.out.print("you> ");
                        System.out.flush();
                    }
                    continue;
                }
                if (isQuit(text)) {
                    return;
                }
                boolean forYou = isForYou(text);
                try {
                    FoodAgent.Answer answer = agent.processRequest(
                            accountId,
                            forYou ? "" : text,
                            List.of(),
                            List.copyOf(history),
                            latitude,
                            longitude,
                            forYou);
                    printAnswer(answer);
                    remember(history, forYou ? "For you" : text, answer.reply());
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    System.err.println(exception.getMessage());
                } catch (RuntimeException exception) {
                    String message = exception.getMessage();
                    System.err.println(message == null || message.isBlank()
                            ? "Could not get a recommendation."
                            : message);
                }
                if (prompt) {
                    System.out.print("you> ");
                    System.out.flush();
                }
            }
        }
    }

    private static BufferedReader open(Path file) throws IOException {
        if (file == null) {
            return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("No such file: " + file);
        }
        return Files.newBufferedReader(file, StandardCharsets.UTF_8);
    }

    private static void printAnswer(FoodAgent.Answer answer) {
        String reply = answer.reply() == null ? "" : answer.reply().strip();
        String[] lines = reply.isEmpty() ? new String[] {""} : reply.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            System.out.println((i == 0 ? "agent> " : "       ") + lines[i]);
        }
        List<Recommendation> recommendations = answer.recommendations();
        if (recommendations.isEmpty()) {
            System.out.println();
            return;
        }
        System.out.println();
        int number = 1;
        for (Recommendation recommendation : recommendations) {
            printPlace(number, recommendation.getRestaurant());
            number++;
        }
        System.out.println();
    }

    private static void printPlace(int number, Restaurant restaurant) {
        String name = restaurant.getName();
        String status = restaurant.getStatus();
        if ("saved".equals(status) || "visited".equals(status)) {
            name = name + " (" + status + ")";
        }
        System.out.println("  " + number + ". " + name);
        String facts = facts(restaurant);
        if (!facts.isEmpty()) {
            System.out.println("     " + facts);
        }
        String address = restaurant.getAddress();
        if (address != null && !address.isBlank()) {
            System.out.println("     " + address);
        }
    }

    private static String facts(Restaurant restaurant) {
        StringBuilder line = new StringBuilder();
        appendFact(line, restaurant.getCuisine());
        appendFact(line, restaurant.getPriceRange());
        if (restaurant.getRating() > 0) {
            appendFact(line, String.valueOf(restaurant.getRating()));
        }
        return line.toString();
    }

    private static void appendFact(StringBuilder line, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!line.isEmpty()) {
            line.append(" | ");
        }
        line.append(part.trim());
    }

    private static void remember(List<FoodAgent.Turn> history, String asked, String reply) {
        history.add(new FoodAgent.Turn("user", asked));
        if (reply != null && !reply.isBlank()) {
            history.add(new FoodAgent.Turn("assistant", reply.strip()));
        }
        int extra = history.size() - HISTORY_LIMIT;
        if (extra > 0) {
            history.subList(0, extra).clear();
        }
    }

    private static boolean isQuit(String text) {
        return "quit".equalsIgnoreCase(text) || "exit".equalsIgnoreCase(text);
    }

    private static boolean isForYou(String text) {
        return "for you".equalsIgnoreCase(text) || "foryou".equalsIgnoreCase(text);
    }

    private static String readPassword() throws IOException {
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword("Password: ");
            if (chars == null || chars.length == 0) {
                throw new IllegalArgumentException("Type the password for --email.");
            }
            return new String(chars);
        }
        System.out.print("Password: ");
        String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Type the password for --email.");
        }
        return line;
    }

    private static double coordinate(String value, String flag) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(flag + " needs a number.");
        }
    }

    private static String next(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }

    private static void printHelp() {
        System.out.println("""
                Food agent
                  java -cp out foodassistant.cli.FoodAssistantCli --chat [--email you@example.com --password secret] [--lat 43.70 --lng -79.38] [--file requests.txt]
                Each line is one request. "for you" uses your taste with no sentence. quit ends the chat.
                With no --file, lines come from standard input. The Java server does not need to be running.
                """);
    }
}
