package com.looktube.app

import com.looktube.model.FeedConfiguration
import kotlin.random.Random

internal data class LaunchIntroQuote(
    val id: String,
    val text: String,
    val speaker: String,
    val sourceTitle: String,
    val sourceUrl: String,
)

internal val LaunchIntroQuotes = listOf(
    LaunchIntroQuote(
        id = "jan-yay-area",
        text = "Born and raised in the YAY AREAAAAAAA",
        speaker = "Jan Ochoa",
        sourceTitle = "Welcome The Newest Member of Giant Bomb: Jan Ochoa",
        sourceUrl = "https://giantbomb.com/articles/welcome-the-newest-member-of-giant-bomb-jan-ochoa",
    ),
    LaunchIntroQuote(
        id = "jan-splits",
        text = "I can do the splits.",
        speaker = "Jan Ochoa",
        sourceTitle = "Welcome The Newest Member of Giant Bomb: Jan Ochoa",
        sourceUrl = "https://giantbomb.com/articles/welcome-the-newest-member-of-giant-bomb-jan-ochoa",
    ),
    LaunchIntroQuote(
        id = "jan-freaks",
        text = "These little freaks are fun.",
        speaker = "Jan Ochoa",
        sourceTitle = "Jan Ochoa's Top 10 Games of 2023",
        sourceUrl = "https://giantbomb.com/articles/jan-ochoas-top-10-games-of-2023",
    ),
    LaunchIntroQuote(
        id = "jan-love-tiles",
        text = "I LOVE TILES. I LOVE PERSONA. VERY GOOD GAME. CHECK IT OUT.",
        speaker = "Jan Ochoa",
        sourceTitle = "Jan Ochoa's Top 10 Games of 2023",
        sourceUrl = "https://giantbomb.com/articles/jan-ochoas-top-10-games-of-2023",
    ),
    LaunchIntroQuote(
        id = "grubb-normal-dudes",
        text = "Three normal dudes debating video games.",
        speaker = "Jeff Grubb",
        sourceTitle = "Jeff Grubb's Top 10 Games of 2024",
        sourceUrl = "https://giantbomb.com/articles/jeff-grubbs-top-10-games-of-2024",
    ),
    LaunchIntroQuote(
        id = "grubb-connection",
        text = "It's connection that I'm after.",
        speaker = "Jeff Grubb",
        sourceTitle = "Jeff Grubb's Top 10 Games of 2024",
        sourceUrl = "https://giantbomb.com/articles/jeff-grubbs-top-10-games-of-2024",
    ),
    LaunchIntroQuote(
        id = "grubb-best-year",
        text = "This is easily one of the best games of the year.",
        speaker = "Jeff Grubb",
        sourceTitle = "Jeff Grubb's Top 10 Games of 2024",
        sourceUrl = "https://giantbomb.com/articles/jeff-grubbs-top-10-games-of-2024",
    ),
    LaunchIntroQuote(
        id = "grubb-double-jump",
        text = "I nearly cried when I got the double jump.",
        speaker = "Jeff Grubb",
        sourceTitle = "Jeff Grubb's Top 10 Games of 2024",
        sourceUrl = "https://giantbomb.com/articles/jeff-grubbs-top-10-games-of-2024",
    ),
    LaunchIntroQuote(
        id = "mike-daisy",
        text = "Also, you can play as Daisy the whole time.",
        speaker = "Mike Minotti",
        sourceTitle = "Mike Minotti's Top 10 Games of 2023",
        sourceUrl = "https://giantbomb.com/articles/mike-minottis-top-10-games-of-2023",
    ),
    LaunchIntroQuote(
        id = "mike-rusty",
        text = "And shoutouts to Rusty! I love you!",
        speaker = "Mike Minotti",
        sourceTitle = "Mike Minotti's Top 10 Games of 2023",
        sourceUrl = "https://giantbomb.com/articles/mike-minottis-top-10-games-of-2023",
    ),
    LaunchIntroQuote(
        id = "mike-not-afraid",
        text = "Now, just like Kevin McCallister, I'm not afraid anymore.",
        speaker = "Mike Minotti",
        sourceTitle = "Mike Minotti's Top 10 Games of 2023",
        sourceUrl = "https://giantbomb.com/articles/mike-minottis-top-10-games-of-2023",
    ),
    LaunchIntroQuote(
        id = "shawn-do-now",
        text = "\"That is something I do now.\"",
        speaker = "TurboShawn McDowell",
        sourceTitle = "TurboShawn's Top 10 Games of 2023",
        sourceUrl = "https://giantbomb.com/posts/turboshawns-top-10-games-of-2023",
    ),
    LaunchIntroQuote(
        id = "shawn-love-yall",
        text = "Love y'all. Now, let's talk about some games.",
        speaker = "TurboShawn McDowell",
        sourceTitle = "TurboShawn's Top 10 Games of 2023",
        sourceUrl = "https://giantbomb.com/posts/turboshawns-top-10-games-of-2023",
    ),
    LaunchIntroQuote(
        id = "shawn-comfort-food",
        text = "2D platformers continue to be my comfort food in gaming.",
        speaker = "TurboShawn McDowell",
        sourceTitle = "TurboShawn's Top 10 Games of 2023",
        sourceUrl = "https://giantbomb.com/posts/turboshawns-top-10-games-of-2023",
    ),
    LaunchIntroQuote(
        id = "niki-delight-confuse",
        text = "It is my pleasure to delight and/or confuse you.",
        speaker = "Niki Grayson",
        sourceTitle = "Niki Grayson's Top 11 Things of 2023",
        sourceUrl = "https://www.giantbomb.com/articles/niki-graysons-top-11-things-of-2023/1100-6383/",
    ),
    LaunchIntroQuote(
        id = "niki-gb-nite",
        text = "GB @ Nite is the only reason you're reading this list.",
        speaker = "Niki Grayson",
        sourceTitle = "Niki Grayson's Top 11 Things of 2023",
        sourceUrl = "https://www.giantbomb.com/articles/niki-graysons-top-11-things-of-2023/1100-6383/",
    ),
    LaunchIntroQuote(
        id = "bakalar-solar-ash",
        text = "Oh man I love Solar Ash's skating-on-jelly-clouds vibe.",
        speaker = "Jeff Bakalar",
        sourceTitle = "Jeff Bakalar’s Top 10 games and games-adjacent things of 2021",
        sourceUrl = "https://www.giantbomb.com/articles/jeff-bakalars-top-10-games-and-games-adjacent-things-of-2021/1100-6187/",
    ),
    LaunchIntroQuote(
        id = "bakalar-give-me-keys",
        text = "Give me keys, weird horror, spooky basements, puzzles inside impossible rooms, a few memorable enemies and I'm set.",
        speaker = "Jeff Bakalar",
        sourceTitle = "Jeff Bakalar’s Top 10 games and games-adjacent things of 2021",
        sourceUrl = "https://www.giantbomb.com/articles/jeff-bakalars-top-10-games-and-games-adjacent-things-of-2021/1100-6187/",
    ),
    LaunchIntroQuote(
        id = "bailey-jp-shotput",
        text = "I'd hurl JP into the sun like a shot-put.",
        speaker = "Bailey Meyers",
        sourceTitle = "Bailey Meyers' Top 10 Games of 2023",
        sourceUrl = "https://www.giantbomb.com/articles/bailey-meyers-top-10-games-of-2023/1100-6343/",
    ),
    LaunchIntroQuote(
        id = "bailey-garl",
        text = "Garl is the best boy in the whole world.",
        speaker = "Bailey Meyers",
        sourceTitle = "Bailey Meyers' Top 10 Games of 2023",
        sourceUrl = "https://www.giantbomb.com/articles/bailey-meyers-top-10-games-of-2023/1100-6343/",
    ),
)

private val FallbackLaunchIntroQuote = LaunchIntroQuote(
    id = "looktube-fallback",
    text = "Premium videos, ready when you are.",
    speaker = "LookTube",
    sourceTitle = "LookTube launch intro",
    sourceUrl = "https://github.com/",
)

internal val LaunchIntroQuoteDeckSize: Int
    get() = LaunchIntroQuotes.size

internal fun currentLaunchIntroQuote(
    feedConfiguration: FeedConfiguration,
): LaunchIntroQuote {
    if (LaunchIntroQuotes.isEmpty()) {
        return FallbackLaunchIntroQuote
    }
    val deck = LaunchIntroQuotes.toMutableList().apply {
        shuffle(Random(feedConfiguration.launchIntroQuoteDeckSeed))
    }
    val normalizedIndex = ((feedConfiguration.launchIntroQuoteDeckIndex % deck.size) + deck.size) % deck.size
    return deck[normalizedIndex]
}
