package foodassistant.agent;

import foodassistant.model.FoodWords;
import foodassistant.taste.ChatPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the words the person actually used. Atmosphere is a mood, not a price,
 * and a short follow-up keeps the kind of place and the area from earlier.
 */
final class RequestSense {
    private static final List<String> ATMOSPHERE = List.of(
            "beautiful interior",
            "fancy looking",
            "good looking",
            "nice interior",
            "instagrammable",
            "fine looking",
            "sophisticated",
            "luxurious",
            "aesthetic",
            "aesthetics",
            "high end",
            "elegant",
            "upscale",
            "stylish",
            "gorgeous",
            "beautiful",
            "ambiance",
            "ambience",
            "polished",
            "luxury",
            "classy",
            "trendy",
            "pretty",
            "fancy",
            "cozy",
            "chic",
            "cute",
            "vibes",
            "vibe");

    private static final List<String> AREAS = List.of(
            "downtown",
            "midtown",
            "uptown",
            "chinatown",
            "koreatown",
            "little italy",
            "yorkville",
            "distillery",
            "harbourfront",
            "waterfront");

    private static final Pattern FILLER = Pattern.compile(
            "\\b(any|some|a|an|the|restaurants?|places?|that|have|has|with|find|me|show|want|i|im|i'm|something|somewhere|looking|for|near|nearby|you|it|of|to|do|dont|don't|not|no|skip|except|can|could|please|just|food|eat|hungry|today|what|should|good|really|very|super|kind|bit|little|get|like|and|or|in|at|around|on|my|your|this|these|those|one)\\b");

    private static final Pattern LEFTOVER = Pattern.compile("\\b(shop|shops|spot|spots|joint|bar|bars|house|room|interior|looking)\\b");

    private RequestSense() {
    }

    static Intent ground(Intent parsed, String asked, List<FoodAgent.Turn> history) {
        String latest = asked == null ? "" : asked.trim();
        String latestNormal = plain(latest);
        String source = latest;
        if (reuseEarlier(latest)) {
            String earlier = earlier(history);
            if (!earlier.isBlank()) {
                source = earlier;
            }
        }
        List<String> declined = ChatPreference.rejected(ChatPreference.read(source));
        String cuisine = withoutFoods(String.join(" ", FoodWords.mentioned(source)), declined);
        String location = locationOf(source);
        String budget = budgetFrom(source);
        String style = atmosphereLabel(source);
        String dish = withoutFoods(specificFood(source), declined);
        if (FoodWords.mentioned(dish).isEmpty()) {
            dish = "";
        }
        if (covers(cuisine, dish)) {
            dish = "";
        }
        if (dish.isBlank() && parsed != null) {
            String hinted = withoutFoods(specificFood(parsed.dish()), declined);
            if (FoodWords.mentioned(hinted).isEmpty()) {
                hinted = "";
            }
            if (!hinted.isBlank() && sourceContains(source, hinted) && !covers(cuisine, hinted)) {
                dish = hinted;
            }
        }
        boolean named = !dish.isBlank() || !cuisine.isBlank();
        boolean skipping = !declined.isEmpty();
        String intent = "search";
        if (vague(latestNormal) && !named && !skipping && budget.isBlank() && location.isBlank()) {
            intent = "ask";
        } else if (!named && !skipping && location.isBlank() && budget.isBlank()) {
            intent = "ask";
        }
        if (latestNormal.contains("surprise") && dish.isBlank() && cuisine.isBlank() && location.isBlank() && budget.isBlank()) {
            location = "near me";
            intent = "search";
        }
        return new Intent(dish, cuisine, budget, location, intent, style);
    }

    static String focus(String asked, List<FoodAgent.Turn> history) {
        String latest = asked == null ? "" : asked.trim();
        if (!reuseEarlier(latest)) {
            return latest;
        }
        String earlier = earlier(history);
        return earlier.isBlank() ? latest : earlier;
    }

    static String earlier(List<FoodAgent.Turn> history) {
        if (history == null) {
            return "";
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            FoodAgent.Turn turn = history.get(i);
            if (!"user".equals(turn.role()) || turn.text() == null) {
                continue;
            }
            String text = turn.text().trim();
            if (text.isEmpty() || skip(text)) {
                continue;
            }
            return text;
        }
        return "";
    }

    static boolean reuseEarlier(String asked) {
        if (asked == null || asked.isBlank() || !FoodWords.mentioned(asked).isEmpty()) {
            return false;
        }
        return broadens(asked) || continues(asked) || affirmation(asked);
    }

    static boolean broadens(String text) {
        String normal = plain(text);
        return normal.contains("what can you find")
                || normal.contains("what can you get")
                || normal.contains("what do you have")
                || normal.contains("what else")
                || normal.contains("anything is fine")
                || normal.contains("anything's fine")
                || normal.contains("anythings fine")
                || normal.contains("anything works")
                || normal.contains("anything is ok")
                || normal.contains("anything is okay")
                || normal.contains("show me alternatives")
                || normal.contains("show alternatives")
                || normal.contains("some alternatives")
                || normal.contains("other options")
                || normal.contains("any options")
                || normal.contains("whatever you")
                || normal.contains("whatever is")
                || normal.equals("whatever")
                || normal.contains("surprise me")
                || normal.contains("just show me")
                || normal.contains("show me what you")
                || normal.contains("broaden");
    }

    static boolean continues(String text) {
        String normal = plain(text);
        return normal.contains("can't find")
                || normal.contains("cant find")
                || normal.contains("cannot find")
                || normal.contains("try again")
                || normal.contains("look again")
                || normal.contains("search again");
    }

    static boolean affirmation(String text) {
        String normal = plain(text);
        return normal.equals("yes")
                || normal.equals("yeah")
                || normal.equals("yep")
                || normal.equals("sure")
                || normal.equals("ok")
                || normal.equals("okay")
                || normal.equals("please")
                || normal.equals("go ahead");
    }

    static boolean vague(String text) {
        String normal = plain(text);
        return normal.isBlank()
                || normal.contains("im hungry")
                || normal.contains("i am hungry")
                || normal.contains("what should i eat")
                || normal.contains("somewhere nice")
                || normal.contains("something nice")
                || normal.contains("not sure");
    }

    private static boolean skip(String normal) {
        return vague(normal) || continues(normal) || affirmation(normal) || broadens(normal);
    }

    private static String plain(String text) {
        return FoodWords.normalize(text).replaceAll("[\\u2018\\u2019\\u02BC']", "");
    }

    private static String locationOf(String source) {
        String normal = plain(source);
        if (normal.contains("near me") || normal.contains("nearby") || normal.contains("close to me") || normal.contains("around me")) {
            return "near me";
        }
        for (String area : AREAS) {
            if (containsPhrase(normal, area)) {
                return area;
            }
        }
        return "";
    }

    private static String budgetFrom(String source) {
        String normal = plain(source);
        if (!explicitBudget(normal)) {
            return "";
        }
        java.util.regex.Matcher money = Pattern.compile("\\$\\s*(\\d+)|(\\d+)\\s*(dollars|bucks)").matcher(normal);
        if (money.find()) {
            String digits = money.group(1) != null ? money.group(1) : money.group(2);
            int dollars = Integer.parseInt(digits);
            if (dollars <= 15) {
                return "$";
            }
            if (dollars <= 30) {
                return "$$";
            }
            if (dollars <= 60) {
                return "$$$";
            }
            return "$$$$";
        }
        if (normal.contains("cheap") || normal.contains("inexpensive") || normal.contains("affordable") || normal.contains("budget")) {
            return "$";
        }
        if (normal.contains("fine dining") || normal.contains("expensive")) {
            return "$$$";
        }
        return "";
    }

    private static boolean explicitBudget(String normal) {
        return normal.contains("$")
                || normal.matches(".*\\d+\\s*(dollars|bucks).*")
                || normal.contains("cheap")
                || normal.contains("inexpensive")
                || normal.contains("affordable")
                || normal.contains("budget")
                || normal.contains("expensive")
                || normal.contains("fine dining")
                || normal.contains("under ")
                || normal.contains("less than");
    }

    private static String atmosphereLabel(String source) {
        String remaining = " " + plain(source) + " ";
        List<String> found = new ArrayList<>();
        for (String phrase : ATMOSPHERE) {
            String needle = " " + phrase + " ";
            if (remaining.contains(needle)) {
                found.add(phrase);
                remaining = remaining.replace(needle, " ");
            }
        }
        return String.join(", ", found);
    }

    private static String stripAtmosphere(String text) {
        String remaining = " " + plain(text) + " ";
        for (String phrase : ATMOSPHERE) {
            remaining = remaining.replace(" " + phrase + " ", " ");
        }
        return remaining.replaceAll("\\s+", " ").trim();
    }

    private static String specificFood(String text) {
        String cleaned = stripAtmosphere(text);
        cleaned = removeAreas(cleaned);
        cleaned = FILLER.matcher(" " + cleaned + " ").replaceAll(" ");
        cleaned = LEFTOVER.matcher(" " + cleaned + " ").replaceAll(" ");
        cleaned = cleaned.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() < 3 ? "" : cleaned;
    }

    private static String removeAreas(String text) {
        String remaining = " " + text + " ";
        for (String area : AREAS) {
            remaining = remaining.replace(" " + area + " ", " ");
        }
        return remaining.replaceAll("\\s+", " ").trim();
    }

    private static String withoutFoods(String text, List<String> declined) {
        if (text == null || text.isBlank() || declined == null || declined.isEmpty()) {
            return text == null ? "" : text;
        }
        String remaining = " " + FoodWords.normalize(text) + " ";
        for (String food : declined) {
            remaining = remaining.replace(" " + FoodWords.normalize(food) + " ", " ");
        }
        return remaining.replaceAll("\\s+", " ").trim();
    }

    private static boolean covers(String cuisine, String dish) {
        if (dish.isBlank()) {
            return true;
        }
        String haystack = " " + FoodWords.normalize(cuisine) + " ";
        for (String word : dish.split(" ")) {
            if (!word.isBlank() && !haystack.contains(" " + word + " ")) {
                return false;
            }
        }
        return true;
    }

    private static boolean sourceContains(String source, String phrase) {
        return containsPhrase(plain(source), plain(phrase));
    }

    private static boolean containsPhrase(String normal, String phrase) {
        return (" " + normal + " ").contains(" " + phrase + " ");
    }

    static String placeType(Intent intent) {
        String blob = FoodWords.normalize(intent.cuisine() + " " + intent.dish());
        if (blob.contains("cafe") || blob.contains("coffee")) {
            return "cafe";
        }
        return "restaurant";
    }
}
