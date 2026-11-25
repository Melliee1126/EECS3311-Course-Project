package foodassistant.taste;

import foodassistant.model.FoodWords;
import foodassistant.model.UserPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a lasting food taste out of a chat message.
 * A request for tonight, or a plain "I want ramen", is not stored.
 */
public final class ChatPreference {
    public record Signal(String food, int weight) {
    }

    public record Reading(List<Signal> stated, List<String> requested, List<String> cleared, List<String> declined) {
        public Reading {
            stated = stated == null ? List.of() : List.copyOf(stated);
            requested = requested == null ? List.of() : List.copyOf(requested);
            cleared = cleared == null ? List.of() : List.copyOf(cleared);
            declined = declined == null ? List.of() : List.copyOf(declined);
        }

        public Reading(List<Signal> stated, List<String> requested, List<String> cleared) {
            this(stated, requested, cleared, List.of());
        }
    }

    private record Rule(Pattern pattern, int weight) {
    }

    private record Cue(int start, int end, int weight) {
    }

    private record Hit(int start, String food) {
    }

    private record ClauseResult(
            List<Signal> stated,
            List<String> requested,
            List<String> declined,
            List<String> foods,
            int pronounWeight) {
    }

    /** This meal only. A negative weight would be saved on the taste profile. */
    private static final int SKIP_NOW = Integer.MIN_VALUE + 1;

    private static final List<Rule> RULES = List.of(
            rule("(?:do not|dont) want", SKIP_NOW),
            rule("not in the mood(?: for)?", SKIP_NOW),
            rule("anything but", SKIP_NOW),
            rule("other than", SKIP_NOW),
            rule("except(?: for)?", SKIP_NOW),
            rule("rather not", SKIP_NOW),
            rule("skip", SKIP_NOW),
            rule("(?:i would like|id like|would like)", 0),
            rule("not sure", Integer.MIN_VALUE),
            rule("(?:feel like|in the mood(?: for)?|looking for)", 0),
            rule("(?:craving|crave)", 0),
            rule("(?:always|usually) (?:eat|eats|order|get|go for|have|want)", 2),
            rule("(?:im|i am) not (?:a )?(?:vegetarian|vegan|halal)", -4),
            rule("(?:im|i am) (?:a )?(?:vegetarian|vegan|halal)", 4),
            rule("(?:kind of|kinda|sort of|somewhat|mildly|a bit|a little) (?:like|enjoy)", 1),
            rule("(?:kind of|kinda|sort of|somewhat|mildly) (?:dislike|hate)", -1),
            rule("not (?:really |that )?into", -1),
            rule("not a (?:big |huge )?fan(?: of)?", -2),
            rule("(?:do not|dont) (?:really )?(?:like|enjoy|eat|love)", -2),
            rule("prefer not to", -2),
            rule("allergic(?: to)?", -4),
            rule("(?:cant|cannot) stand", -4),
            rule("never (?:eat|eats|order|get|touch|have)", -4),
            rule("(?:absolutely|really|truly) (?:love|adore)", 4),
            rule("(?:really|truly) (?:like|enjoy|into)", 3),
            rule("(?:huge|big) fan(?: of)?", 4),
            rule("favou?rites?", 4),
            rule("obsessed(?: with)?", 4),
            rule("(?:cant|cannot) get enough of", 4),
            rule("dislike", -2),
            rule("avoid(?:ing)?", -2),
            rule("hate", -4),
            rule("despise", -4),
            rule("love", 4),
            rule("adore", 4),
            rule("(?:fine|okay|ok) with", 1),
            rule("fan of", 2),
            rule("enjoy", 2),
            rule("prefer", 2),
            rule("like", 2),
            rule("into", 2),
            rule("only eat", 2),
            rule("(?:want|wanna|need)", 0),
            rule("(?:find|show|recommend|suggest)", 0),
            rule("(?:tonight|today|right now|this evening)", 0),
            rule("for (?:lunch|dinner|breakfast|brunch)", 0),
            rule("(?:give me|get me)", 0),
            rule("lets", 0),
            rule("hungry for", 0),
            rule("not", -2),
            rule("no", -2));

    private ChatPreference() {
    }

    public static Reading none() {
        return new Reading(List.of(), List.of(), List.of());
    }

    public static Reading read(String message) {
        if (message == null || message.isBlank()) {
            return none();
        }
        String normal = FoodWords.normalize(message)
                .replaceAll("[\\u2018\\u2019\\u02bc']", "")
                .replaceAll("[?!]", ".")
                .replaceAll("\\s+", " ")
                .replaceAll("\\banything but\\b", "except")
                .trim();
        Map<String, Integer> scores = new LinkedHashMap<>();
        Set<String> requested = new LinkedHashSet<>();
        Set<String> cleared = new LinkedHashSet<>();
        Set<String> declined = new LinkedHashSet<>();
        List<String> carry = List.of();
        for (String clause : clauses(normal)) {
            ClauseResult result = interpret(clause);
            for (Signal signal : result.stated) {
                scores.merge(signal.food, signal.weight, Integer::sum);
            }
            requested.addAll(result.requested);
            declined.addAll(result.declined);
            if (result.stated.isEmpty() && result.requested.isEmpty() && result.declined.isEmpty()
                    && result.pronounWeight != 0 && !carry.isEmpty()) {
                if (result.pronounWeight == SKIP_NOW) {
                    declined.addAll(carry);
                } else {
                    for (String food : carry) {
                        scores.merge(food, result.pronounWeight, Integer::sum);
                    }
                }
            }
            if (!result.foods.isEmpty()) {
                carry = result.foods;
            }
        }
        applyDiets(normal, scores, cleared);
        for (String food : cleared) {
            scores.remove(food);
        }
        List<Signal> stated = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            int cap = FoodWords.isDiet(entry.getKey()) && entry.getValue() > 4 ? 8 : 4;
            int weight = Math.max(-4, Math.min(cap, entry.getValue()));
            if (weight != 0) {
                stated.add(new Signal(entry.getKey(), weight));
            }
        }
        stated.sort((left, right) -> {
            int byWeight = Integer.compare(Math.abs(right.weight), Math.abs(left.weight));
            return byWeight != 0 ? byWeight : left.food.compareToIgnoreCase(right.food);
        });
        declined.removeAll(requested);
        declined.removeAll(cleared);
        return new Reading(stated, List.copyOf(requested), List.copyOf(cleared), List.copyOf(declined));
    }

    public static String note(Reading reading) {
        if (reading == null || (reading.stated.isEmpty() && reading.cleared.isEmpty())) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Signal signal : reading.stated) {
            parts.add(phrase(signal));
        }
        for (String food : reading.cleared) {
            parts.add("no longer " + food);
        }
        return String.join(", ", parts);
    }

    private static String phrase(Signal signal) {
        if (FoodWords.isDiet(signal.food()) && signal.weight() >= 8) {
            return signal.food();
        }
        if (FoodWords.isRestriction(signal.food()) && signal.weight() <= -4) {
            return "no " + signal.food();
        }
        return signal.food() + " (" + UserPreferences.strengthWord(signal.weight()) + ")";
    }

    public static List<String> rejected(Reading reading) {
        if (reading == null) {
            return List.of();
        }
        List<String> foods = new ArrayList<>();
        for (Signal signal : reading.stated) {
            if (signal.weight < 0 && !reading.requested.contains(signal.food)) {
                foods.add(signal.food);
            }
        }
        for (String food : reading.declined()) {
            if (!reading.requested.contains(food) && !foods.contains(food)) {
                foods.add(food);
            }
        }
        return foods;
    }

    private static ClauseResult interpret(String clause) {
        String text = clause.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return new ClauseResult(List.of(), List.of(), List.of(), List.of(), 0);
        }
        List<Cue> cues = cues(text);
        if (text.contains("used to")) {
            cues.removeIf(cue -> cue.weight != 0);
        }
        if (aboutAssistant(text)) {
            cues.removeIf(cue -> cue.weight != 0);
        }
        List<Hit> hits = foodsIn(text);
        List<Signal> stated = new ArrayList<>();
        List<String> requested = new ArrayList<>();
        List<String> declined = new ArrayList<>();
        List<String> foods = new ArrayList<>();
        for (Hit hit : hits) {
            foods.add(hit.food);
            Cue cue = nearest(cues, hit.start);
            if (cue != null && cue.weight == SKIP_NOW) {
                add(declined, hit.food);
                continue;
            }
            if (cue == null || cue.weight == 0 || cue.weight == Integer.MIN_VALUE) {
                if (cue != null && cue.weight == 0) {
                    add(requested, hit.food);
                }
                continue;
            }
            stated.add(new Signal(hit.food, cue.weight));
        }
        int pronounWeight = 0;
        if (hits.isEmpty() && pronoun(text)) {
            boolean skip = false;
            for (Cue cue : cues) {
                if (cue.weight == SKIP_NOW) {
                    skip = true;
                    break;
                }
            }
            pronounWeight = skip ? SKIP_NOW : strongest(cues);
        }
        return new ClauseResult(stated, requested, declined, foods, pronounWeight);
    }

    private static boolean aboutAssistant(String text) {
        return text.matches(".*\\b(?:do|did|would|could|can|will) you\\b.*") && !text.matches(".*\\bi\\b.*");
    }

    private static boolean pronoun(String text) {
        return text.matches(".*\\b(?:it|that|them|those)\\b.*");
    }

    private static int strongest(List<Cue> cues) {
        int weight = 0;
        for (Cue cue : cues) {
            if (cue.weight == Integer.MIN_VALUE || cue.weight == SKIP_NOW) {
                continue;
            }
            if (Math.abs(cue.weight) > Math.abs(weight)) {
                weight = cue.weight;
            }
        }
        return weight;
    }

    private static List<String> clauses(String normal) {
        String[] chunks = normal.split("(?<=[.!?])|[;]|\\b(?:but|although|though|however|because|since)\\b");
        List<String> clauses = new ArrayList<>();
        for (String chunk : chunks) {
            for (String piece : splitJoints(chunk)) {
                String trimmed = piece.trim();
                if (!trimmed.isEmpty()) {
                    clauses.add(trimmed);
                }
            }
        }
        return clauses;
    }

    private static List<String> splitJoints(String chunk) {
        List<String> commaParts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : chunk.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!current.isEmpty() && (trimmed.matches("i\\b.*") || hasCue(trimmed))) {
                commaParts.add(current.toString());
                current = new StringBuilder(trimmed);
            } else {
                if (!current.isEmpty()) {
                    current.append(", ");
                }
                current.append(trimmed);
            }
        }
        if (!current.isEmpty()) {
            commaParts.add(current.toString());
        }
        List<String> done = new ArrayList<>();
        for (String piece : commaParts) {
            done.addAll(splitAnd(piece));
        }
        return done;
    }

    private static List<String> splitAnd(String chunk) {
        String[] parts = chunk.split("\\band\\b");
        List<String> groups = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!current.isEmpty() && (trimmed.matches("i\\b.*") || hasCue(trimmed))) {
                groups.add(current.toString());
                current = new StringBuilder(trimmed);
            } else {
                if (!current.isEmpty()) {
                    current.append(" and ");
                }
                current.append(trimmed);
            }
        }
        if (!current.isEmpty()) {
            groups.add(current.toString());
        }
        return groups;
    }

    private static boolean hasCue(String text) {
        for (Rule rule : RULES) {
            if (rule.pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Cue> cues(String text) {
        boolean[] used = new boolean[text.length()];
        List<Cue> cues = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(text);
            while (matcher.find()) {
                if (overlaps(used, matcher.start(), matcher.end())) {
                    continue;
                }
                mark(used, matcher.start(), matcher.end());
                cues.add(new Cue(matcher.start(), matcher.end(), rule.weight));
            }
        }
        cues.sort(Comparator.comparingInt(cue -> cue.start));
        return cues;
    }

    private static Cue nearest(List<Cue> cues, int foodAt) {
        Cue best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean bestLeft = false;
        for (Cue cue : cues) {
            boolean left = cue.end <= foodAt;
            int distance = left ? foodAt - cue.end : cue.start - foodAt;
            if (distance < bestDistance || (distance == bestDistance && left && !bestLeft)) {
                best = cue;
                bestDistance = distance;
                bestLeft = left;
            }
        }
        return best;
    }

    private static List<Hit> foodsIn(String text) {
        boolean[] taken = new boolean[text.length()];
        List<Hit> hits = new ArrayList<>();
        for (FoodWords.Term term : FoodWords.terms()) {
            int from = 0;
            while (from < text.length()) {
                int at = indexOfForm(text, term.form(), from);
                if (at < 0) {
                    break;
                }
                int end = at + term.form().length();
                if (!overlaps(taken, at, end)) {
                    mark(taken, at, end);
                    hits.add(new Hit(at, term.canonical()));
                }
                from = end;
            }
        }
        hits.sort(Comparator.comparingInt(hit -> hit.start));
        return hits;
    }

    private static int indexOfForm(String text, String form, int from) {
        int at = text.indexOf(form, from);
        while (at >= 0) {
            boolean left = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            int end = at + form.length();
            boolean right = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (left && right) {
                return at;
            }
            at = text.indexOf(form, at + 1);
        }
        return -1;
    }

    private static void add(List<String> foods, String food) {
        for (String existing : foods) {
            if (existing.equalsIgnoreCase(food)) {
                return;
            }
        }
        foods.add(food);
    }

    private static boolean overlaps(boolean[] used, int start, int end) {
        for (int i = start; i < end && i < used.length; i++) {
            if (used[i]) {
                return true;
            }
        }
        return false;
    }

    private static void mark(boolean[] used, int start, int end) {
        for (int i = start; i < end && i < used.length; i++) {
            used[i] = true;
        }
    }

    private static final String DIET_WORDS = "gluten free|dairy free|nut free|pescatarian|vegetarian|vegan|halal|kosher";

    private static final Pattern NOT_DIET = Pattern.compile(
            "\\b(?:(?:im|i am) (?:not|no longer)|used to be) (?:a |an )?(" + DIET_WORDS + ")\\b");

    private static final Pattern AM_DIET = Pattern.compile(
            "\\b(?:im|i am) (?:a |an )?(" + DIET_WORDS + ")\\b");

    private static final Pattern KEEP_DIET = Pattern.compile("\\b(?:i only eat|i eat|i keep) (halal|kosher)\\b");

    private static final Pattern NOT_CELIAC = Pattern.compile("\\bnot (?:celiac|coeliac)\\b");

    private static final Pattern CELIAC = Pattern.compile("\\b(?:celiac|coeliac)\\b");

    private static final Pattern NOT_LACTOSE = Pattern.compile("\\bnot lactose\\b");

    private static final Pattern LACTOSE = Pattern.compile("\\blactose intoleran(?:t|ce)\\b");

    private static final Pattern EATS_MEAT = Pattern.compile("\\bi (?:do )?eat meat\\b");

    private static final Pattern NO_MEAT = Pattern.compile("\\b(?:(?:dont|do not|cant|cannot|never) eat meat|no meat|without meat)\\b");

    private static final Pattern NOT_ALLERGIC = Pattern.compile("\\bnot allergic to ([a-z ]{3,40})");

    private static final Pattern ALLERGIC = Pattern.compile(
            "\\b(?:allergic to|allergy to|cant eat|cannot eat|cant have|cannot have|intolerant to) ([a-z ]{3,40})");

    private static final Pattern ALLERGY_NOUN = Pattern.compile(
            "\\b(tree nut|peanut|shellfish|shrimp|prawn|soya|soy|sesame|pork|beef|lamb|fish|dairy|lactose|milk|gluten|wheat|egg|nut) allerg");

    private static final Pattern AVOID_FOOD = Pattern.compile(
            "\\b(?:dont|do not|cant|cannot|never) (?:eat|have) ([a-z ]{3,40})"
                    + "|\\bno (tree nuts|peanuts|peanut|shellfish|shrimp|prawns|prawn|pork|dairy|gluten|wheat|sesame|soya|soy|eggs|egg|lamb|fish|beef|nuts)(?! with)\\b");

    private static void applyDiets(String text, Map<String, Integer> scores, Set<String> cleared) {
        boolean[] taken = new boolean[text.length()];
        Matcher notDiet = NOT_DIET.matcher(text);
        while (notDiet.find()) {
            clearDiet(notDiet.group(1), cleared, scores);
            mark(taken, notDiet.start(), notDiet.end());
        }
        if (NOT_CELIAC.matcher(text).find()) {
            clearDiet("gluten free", cleared, scores);
        }
        if (NOT_LACTOSE.matcher(text).find()) {
            clearDiet("dairy free", cleared, scores);
        }
        if (EATS_MEAT.matcher(text).find()) {
            clearDiet("vegetarian", cleared, scores);
            clearDiet("vegan", cleared, scores);
        }
        Matcher notAllergic = NOT_ALLERGIC.matcher(text);
        while (notAllergic.find()) {
            for (String food : targets(notAllergic.group(1))) {
                clearFood(food, cleared, scores);
            }
            mark(taken, notAllergic.start(), notAllergic.end());
        }
        Matcher claim = AM_DIET.matcher(text);
        while (claim.find()) {
            if (overlaps(taken, claim.start(), claim.end())) {
                continue;
            }
            keepDiet(claim.group(1), scores, cleared);
        }
        Matcher keep = KEEP_DIET.matcher(text);
        while (keep.find()) {
            keepDiet(keep.group(1), scores, cleared);
        }
        if (CELIAC.matcher(text).find() && !NOT_CELIAC.matcher(text).find()) {
            keepDiet("gluten free", scores, cleared);
        }
        if (LACTOSE.matcher(text).find() && !NOT_LACTOSE.matcher(text).find()) {
            keepDiet("dairy free", scores, cleared);
        }
        if (NO_MEAT.matcher(text).find() && !EATS_MEAT.matcher(text).find()) {
            keepDiet("vegetarian", scores, cleared);
        }
        rememberAvoidance(ALLERGIC.matcher(text), 1, taken, scores, cleared);
        rememberAvoidance(ALLERGY_NOUN.matcher(text), 1, taken, scores, cleared);
        Matcher avoid = AVOID_FOOD.matcher(text);
        while (avoid.find()) {
            if (overlaps(taken, avoid.start(), avoid.end())) {
                continue;
            }
            String tail = avoid.group(1) != null ? avoid.group(1) : avoid.group(2);
            rememberTargets(tail, scores, cleared);
        }
    }

    private static void rememberAvoidance(
            Matcher matcher,
            int group,
            boolean[] taken,
            Map<String, Integer> scores,
            Set<String> cleared) {
        while (matcher.find()) {
            if (overlaps(taken, matcher.start(), matcher.end())) {
                continue;
            }
            mark(taken, matcher.start(), matcher.end());
            rememberTargets(matcher.group(group), scores, cleared);
        }
    }

    private static void rememberTargets(String tail, Map<String, Integer> scores, Set<String> cleared) {
        for (String food : targets(tail)) {
            if (cleared.contains(food)) {
                continue;
            }
            if (FoodWords.isDiet(food)) {
                scores.put(food, 8);
            } else {
                scores.put(food, -4);
            }
        }
    }

    private static void keepDiet(String raw, Map<String, Integer> scores, Set<String> cleared) {
        String diet = dietName(raw);
        if (diet.isEmpty() || cleared.contains(diet)) {
            return;
        }
        scores.put(diet, 8);
    }

    private static void clearDiet(String raw, Set<String> cleared, Map<String, Integer> scores) {
        clearFood(dietName(raw), cleared, scores);
    }

    private static void clearFood(String food, Set<String> cleared, Map<String, Integer> scores) {
        if (food == null || food.isEmpty()) {
            return;
        }
        cleared.add(food);
        scores.remove(food);
    }

    private static String dietName(String raw) {
        if (raw == null) {
            return "";
        }
        return switch (raw.trim()) {
            case "vegetarian" -> "Vegetarian";
            case "vegan" -> "Vegan";
            case "pescatarian" -> "Pescatarian";
            case "halal" -> "Halal";
            case "kosher" -> "Kosher";
            case "gluten free" -> "Gluten-free";
            case "dairy free" -> "Dairy-free";
            case "nut free" -> "Nut-free";
            default -> "";
        };
    }

    private static List<String> targets(String tail) {
        String window = clip(tail);
        String padded = " " + window + " ";
        List<String> found = new ArrayList<>();
        if (hasWord(padded, "gluten") || hasWord(padded, "wheat")) {
            addFood(found, "Gluten-free");
        }
        if (hasWord(padded, "dairy") || hasWord(padded, "lactose") || hasWord(padded, "milk")) {
            addFood(found, "Dairy-free");
        }
        if (hasWord(padded, "tree nut") || hasWord(padded, "tree nuts")) {
            addFood(found, "Tree nut");
        } else if (hasWord(padded, "nut") || hasWord(padded, "nuts")) {
            addFood(found, "Tree nut");
            addFood(found, "Peanut");
        }
        if (hasWord(padded, "peanut") || hasWord(padded, "peanuts")) {
            addFood(found, "Peanut");
        }
        if (hasWord(padded, "shellfish") || hasWord(padded, "shrimp") || hasWord(padded, "prawn") || hasWord(padded, "prawns")) {
            addFood(found, "Shellfish");
        }
        if (hasWord(padded, "soy") || hasWord(padded, "soya")) {
            addFood(found, "Soy");
        }
        if (hasWord(padded, "egg") || hasWord(padded, "eggs")) {
            addFood(found, "Egg");
        }
        if (hasWord(padded, "sesame")) {
            addFood(found, "Sesame");
        }
        if (hasWord(padded, "pork")) {
            addFood(found, "Pork");
        }
        if (hasWord(padded, "beef")) {
            addFood(found, "Beef");
        }
        if (hasWord(padded, "lamb")) {
            addFood(found, "Lamb");
        }
        if (hasWord(padded, "fish") || hasWord(padded, "fishes")) {
            addFood(found, "Fish");
        }
        if (hasWord(padded, "meat")) {
            addFood(found, "Vegetarian");
        }
        return found;
    }

    private static String clip(String tail) {
        if (tail == null) {
            return "";
        }
        String lower = tail.toLowerCase(java.util.Locale.ROOT).trim();
        int end = lower.length();
        for (String stop : List.of(" but ", " because ", " although ", " though ", " however ", " since ", " and i ")) {
            int at = lower.indexOf(stop);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        String[] words = lower.substring(0, end).trim().split("\\s+");
        StringBuilder window = new StringBuilder();
        int count = 0;
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (count == 6) {
                break;
            }
            if (!window.isEmpty()) {
                window.append(' ');
            }
            window.append(word);
            count++;
        }
        return window.toString();
    }

    private static boolean hasWord(String padded, String word) {
        return padded.contains(" " + word + " ");
    }

    private static void addFood(List<String> foods, String food) {
        if (!foods.contains(food)) {
            foods.add(food);
        }
    }

    private static Rule rule(String body, int weight) {
        return new Rule(Pattern.compile("\\b(?:" + body + ")\\b"), weight);
    }
}
