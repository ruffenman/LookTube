package com.looktube.app

/** Public written excerpts checked against the original articles on 2026-10-15.
 * See docs/content/splash-quotes.md. Preserve wording and punctuation; explain excerpts in context.
 */
internal data class LaunchIntroQuoteSource(val title: String, val url: String, val publishedOn: String)
internal data class LaunchIntroSpeaker(val name: String, val role: String, val evidenceUrl: String)
internal data class LaunchIntroQuote(
    val id: String,
    val text: String,
    val speaker: LaunchIntroSpeaker,
    val source: LaunchIntroQuoteSource,
    val context: String,
)

private fun article(slug: String) = "https://giantbomb.com/articles/$slug"
private fun source(title: String, slug: String, date: String) = LaunchIntroQuoteSource(title, article(slug), date)
private val janIntroduction = source("Welcome The Newest Member of Giant Bomb: Jan Ochoa", "welcome-the-newest-member-of-giant-bomb-jan-ochoa", "2017-11-14")
private val jan2023 = source("Jan Ochoa's Top 10 Games of 2023", "jan-ochoas-top-10-games-of-2023", "2023-12-29")
private val grubb2024 = source("Jeff Grubb's Top 10 Games of 2024", "jeff-grubbs-top-10-games-of-2024", "2024-12-24")
private val mike2023 = source("Mike Minotti's Top 10 Games of 2023", "mike-minottis-top-10-games-of-2023", "2023-12-29")
private val dan2019 = source("Dan Ryckert's Top 10 Games of 2019", "dan-ryckerts-top-10-games-of-2019", "2019-12-23")
private val abby2019 = source("Abby Russell's Top 10 Games of 2019", "abby-russells-top-10-games-of-2019", "2019-12-25")
private val jeff2021 = source("Jeff Gerstmann's Top 10 Games of 2021", "jeff-gerstmanns-top-10-games-of-2021", "2022-01-26")
private val vinny2020 = source("Vinny Caravella's Top 10 Games of 2020", "vinny-caravellas-top-10-games-of-2020", "2021-01-22")
private val bakalar2021 = source("Jeff Bakalar’s Top 10 games and games-adjacent things of 2021", "jeff-bakalars-top-10-games-and-games-adjacent-things-of-2021", "2022-01-26")
private val niki2023 = source("Niki Grayson's Top 11 Things of 2023", "niki-graysons-top-11-things-of-2023", "2024-01-15")
private val brad2020 = source("Brad Shoemaker's Top 10 Games of 2020", "brad-shoemakers-top-10-games-of-2020", "2021-01-22")

private val currentTeamEvidence = article("209-days-to-build-a-bomb")
private val jan = LaunchIntroSpeaker("Jan Ochoa", "Giant Bomb video producer and regular cast member", janIntroduction.url)
private val grubb = LaunchIntroSpeaker("Jeff Grubb", "Giant Bomb host and regular cast member", currentTeamEvidence)
private val mike = LaunchIntroSpeaker("Mike Minotti", "Giant Bomb co-owner and regular cast member", currentTeamEvidence)
private val dan = LaunchIntroSpeaker("Dan Ryckert", "Giant Bomb senior content producer and regular cast member", dan2019.url)
private val abby = LaunchIntroSpeaker("Abby Russell", "Former Giant Bomb content producer and regular cast member", abby2019.url)
private val jeff = LaunchIntroSpeaker("Jeff Gerstmann", "Former Giant Bomb editor-in-chief and regular cast member", article("jeff-gerstmanns-top-10-games-of-2015"))
private val vinny = LaunchIntroSpeaker("Vinny Caravella", "Former Giant Bomb producer and regular cast member", vinny2020.url)
private val bakalar = LaunchIntroSpeaker("Jeff Bakalar", "Giant Bomb co-owner and regular cast member", currentTeamEvidence)
private val niki = LaunchIntroSpeaker("Niki Grayson", "Former Giant Bomb on-screen personality and regular cast member", article("the-community-spotlight-2023-07-22"))
private val brad = LaunchIntroSpeaker("Brad Shoemaker", "Former Giant Bomb senior editor and regular cast member", article("brad-shoemakers-top-10-games-of-2015"))

internal val LaunchIntroQuotes = listOf(
    LaunchIntroQuote("jan-yay-area", "Born and raised in the YAY AREAAAAAAA", jan, janIntroduction, "Introducing himself to Giant Bomb"),
    LaunchIntroQuote("jan-splits", "I can do the splits.", jan, janIntroduction, "Introducing himself to Giant Bomb"),
    LaunchIntroQuote("jan-grill", "I have a gold grill.", jan, janIntroduction, "Introducing himself to Giant Bomb"),
    LaunchIntroQuote("jan-freaks", "These little freaks are fun", jan, jan2023, "On Pikmin 4"),
    LaunchIntroQuote("jan-love-tiles", "I LOVE TILES. I LOVE PERSONA. VERY GOOD GAME. CHECK IT OUT.", jan, jan2023, "Excerpt on Persona 5 Tactica"),
    LaunchIntroQuote("jan-coffee", "COFFEE. WORLD.", jan, jan2023, "Excerpt on Alan Wake II"),
    LaunchIntroQuote("grubb-connection", "It's connection that I'm after.", grubb, grubb2024, "On sharing what games mean to him"),
    LaunchIntroQuote("grubb-double-jump", "I nearly cried when I got the double jump.", grubb, grubb2024, "Excerpt on Prince of Persia: The Lost Crown"),
    LaunchIntroQuote("grubb-ascending", "It felt like ascending.", grubb, grubb2024, "On unlocking the double jump in Prince of Persia: The Lost Crown"),
    LaunchIntroQuote("mike-daisy", "Also, you can play as Daisy the whole time.", mike, mike2023, "On Super Mario Bros. Wonder"),
    LaunchIntroQuote("mike-rusty", "And shoutouts to Rusty! I love you!", mike, mike2023, "On Armored Core VI: Fires of Rubicon"),
    LaunchIntroQuote("dan-bonkers", "It’s bonkers and broken in the absolute best ways.", dan, dan2019, "On Bloodstained: Ritual of the Night"),
    LaunchIntroQuote("dan-where-to-start", "Oh man. I don’t even know where to start with this one.", dan, dan2019, "On Bloodstained: Ritual of the Night"),
    LaunchIntroQuote("abby-anything-goes", "This is my list and anything goes!", abby, abby2019, "Including Stardew Valley in her 2019 list"),
    LaunchIntroQuote("abby-farmed", "I didn’t sleep, I didn’t eat, I only farmed.", abby, abby2019, "On Stardew Valley"),
    LaunchIntroQuote("abby-dicey", "Dicey Dungeons rules!", abby, abby2019, "On Dicey Dungeons"),
    LaunchIntroQuote("jeff-sludge", "The sludge begins to feel like home.", jeff, jeff2021, "On Cruelty Squad"),
    LaunchIntroQuote("jeff-rock", "Me dumb in head, hit rock.", jeff, jeff2021, "Admiring the design of Babble Royale"),
    LaunchIntroQuote("jeff-sarah", "Sarah is that main and I will never waver!", jeff, jeff2021, "On his Virtua Fighter main"),
    LaunchIntroQuote("vinny-glowy", "Though Ori is possibly too glowy. I stand by it.", vinny, vinny2020, "On Ori and the Will of the Wisps"),
    LaunchIntroQuote("vinny-choices", "Make better choices you silly characters!", vinny, vinny2020, "On The Last of Us Part II"),
    LaunchIntroQuote("vinny-os", "It even has a fake operating system!", vinny, vinny2020, "On There Is No Game: Wrong Dimension"),
    LaunchIntroQuote("bakalar-solar-ash", "Oh man I love Solar Ash’s skating-on-jelly-clouds vibe.", bakalar, bakalar2021, "On Solar Ash"),
    LaunchIntroQuote("bakalar-godzilla", "A must-play if you see it out there.", bakalar, bakalar2021, "On Stern's Godzilla pinball machine"),
    LaunchIntroQuote("niki-delight-confuse", "It is my pleasure to delight and/or confuse you.", niki, niki2023, "Thanking the Giant Bomb audience"),
    LaunchIntroQuote("niki-couch", "I was on the couch. My dog was on the couch.", niki, niki2023, "On appearing on GB @ Nite"),
    LaunchIntroQuote("brad-nostalgia", "If you need me, I’ll be in the PlayStation Labo, wallowing in nostalgia just a little bit longer.", brad, brad2020, "On Astro’s Playroom"),
)
