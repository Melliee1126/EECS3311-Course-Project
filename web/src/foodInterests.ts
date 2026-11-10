const FOOD_INTERESTS = [
  'Bakery',
  'BBQ',
  'Banh mi',
  'Breakfast',
  'Brunch',
  'Burgers',
  'Burritos',
  'Cafe',
  'Canadian',
  'Chinese',
  'Coffee',
  'Comfort food',
  'Curry',
  'Dessert',
  'Dim sum',
  'Dumplings',
  'Falafel',
  'French',
  'Fried chicken',
  'Gluten-free',
  'Greek',
  'Halal',
  'Hot pot',
  'Ice cream',
  'Indian',
  'Italian',
  'Japanese',
  'Kebab',
  'Korean',
  'Mediterranean',
  'Mexican',
  'Noodles',
  'Pad thai',
  'Pasta',
  'Pho',
  'Pizza',
  'Poke',
  'Ramen',
  'Salad',
  'Sandwiches',
  'Sashimi',
  'Seafood',
  'Shawarma',
  'Soba',
  'Soup',
  'Spanish',
  'Spicy',
  'Steak',
  'Sushi',
  'Tacos',
  'Tapas',
  'Tea',
  'Tempura',
  'Thai',
  'Udon',
  'Vegan',
  'Vegetarian',
  'Vietnamese',
  'Wings',
]

function normalize(value: string) {
  return value.trim().toLowerCase().replace(/\s+/g, ' ')
}

function levenshtein(left: string, right: string) {
  const previous = Array.from({ length: right.length + 1 }, (_, index) => index)
  for (let i = 1; i <= left.length; i++) {
    let diagonal = previous[0]
    previous[0] = i
    for (let j = 1; j <= right.length; j++) {
      const next = left[i - 1] === right[j - 1] ? diagonal : Math.min(diagonal, previous[j - 1], previous[j]) + 1
      diagonal = previous[j]
      previous[j] = next
    }
  }
  return previous[right.length]
}

function closeEnough(query: string, distance: number) {
  if (distance === 0) return true
  if (query.length < 4) return distance <= 1
  return distance <= 2 && distance / query.length <= 0.4
}

export function suggestFoodInterests(input: string) {
  const query = normalize(input)
  if (!query) return []
  return FOOD_INTERESTS.map((item) => {
    const name = normalize(item)
    return {
      item,
      prefix: name.startsWith(query),
      includes: name.includes(query),
      score: levenshtein(query, name),
    }
  })
    .filter((row) => row.prefix || row.includes || closeEnough(query, row.score))
    .sort((a, b) => Number(b.prefix) - Number(a.prefix) || Number(b.includes) - Number(a.includes) || a.score - b.score)
    .slice(0, 5)
    .map((row) => row.item)
}

export function correctFoodInterest(input: string) {
  const query = normalize(input)
  if (query.length < 2) return null
  const exact = FOOD_INTERESTS.find((item) => normalize(item) === query)
  if (exact) return exact
  const prefixes = FOOD_INTERESTS.filter((item) => normalize(item).startsWith(query))
  if (prefixes.length === 1 && query.length >= 3) return prefixes[0]
  const scored = FOOD_INTERESTS.map((item) => ({ item, score: levenshtein(query, normalize(item)) }))
    .filter((row) => closeEnough(query, row.score))
    .sort((a, b) => a.score - b.score)
  if (scored.length === 0) return null
  if (scored.length === 1 || scored[0].score < scored[1].score) return scored[0].item
  return null
}
