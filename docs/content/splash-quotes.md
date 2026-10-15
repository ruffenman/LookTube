# Splash quote editorial ledger

Reviewed: 2026-10-15. The bundled catalogue contains 27 short written excerpts from 11 public Giant Bomb articles, attributed to 10 current or former regular cast members. It replaces the deleted 20-entry deck rather than trusting its old wording and eligibility assumptions.

## Editorial rules

- Read the original public article and the surrounding section before accepting an entry. Public guest authorship alone does not qualify a speaker as a regular cast member.
- Preserve spelling, capitalization, apostrophes, and punctuation. Display quotation marks are typography added around the excerpt; the stored text stays verbatim. Contiguous sentence excerpts are allowed, with context retained. Do not invent, paraphrase, stitch together, or silently correct a quote.
- Store the speaker, context, original title, original publication date, public URL, and role-evidence URL with each entry. Article bylines sometimes name the staff member who uploaded a guest list; attribute the list's actual author instead.
- Keep selections short and contextual. Do not turn comments about a game into praise for LookTube, or suggest endorsement, consent, or a position on AI.
- No synthetic voices, generated portraits, or generated quotations are part of this feature.
- Remove an entry individually if its provenance becomes uncertain or its speaker objects. No presumed permission is recorded.
- The catalogue is local app data. Opening a source is an explicit user action; app startup never downloads quote content.

## Source checks

The text and context for every ID below were compared with the original public article rendered through web browsing. These are written quotes, not transcriptions or claims about how a line was spoken.

| Source | Entry IDs | Publication date |
| --- | --- | --- |
| [Jan introduction](https://giantbomb.com/articles/welcome-the-newest-member-of-giant-bomb-jan-ochoa) | jan-yay-area, jan-splits, jan-grill | 2017-11-14 |
| [Jan 2023 list](https://giantbomb.com/articles/jan-ochoas-top-10-games-of-2023) | jan-freaks, jan-love-tiles, jan-coffee | 2023-12-29 |
| [Grubb 2024 list](https://giantbomb.com/articles/jeff-grubbs-top-10-games-of-2024) | grubb-connection, grubb-double-jump, grubb-ascending | 2024-12-24 |
| [Mike 2023 list](https://giantbomb.com/articles/mike-minottis-top-10-games-of-2023) | mike-daisy, mike-rusty | 2023-12-29 |
| [Dan 2019 list](https://giantbomb.com/articles/dan-ryckerts-top-10-games-of-2019) | dan-bonkers, dan-where-to-start | 2019-12-23 |
| [Abby 2019 list](https://giantbomb.com/articles/abby-russells-top-10-games-of-2019) | abby-anything-goes, abby-farmed, abby-dicey | 2019-12-25 |
| [Gerstmann 2021 list](https://giantbomb.com/articles/jeff-gerstmanns-top-10-games-of-2021) | jeff-sludge, jeff-rock, jeff-sarah | 2022-01-26 |
| [Vinny 2020 list](https://giantbomb.com/articles/vinny-caravellas-top-10-games-of-2020) | vinny-glowy, vinny-choices, vinny-os | 2021-01-22 |
| [Bakalar 2021 list](https://giantbomb.com/articles/jeff-bakalars-top-10-games-and-games-adjacent-things-of-2021) | bakalar-solar-ash, bakalar-godzilla | 2022-01-26 |
| [Niki 2023 list](https://giantbomb.com/articles/niki-graysons-top-11-things-of-2023) | niki-delight-confuse, niki-couch | 2024-01-15 |
| [Brad 2020 list](https://giantbomb.com/articles/brad-shoemakers-top-10-games-of-2020) | brad-nostalgia | 2021-01-22 |

## Regular cast eligibility

- Jan: the official introduction identifies his video-production role; his later staff list establishes continuing involvement.
- Dan and Abby: their original 2019 list introductions explicitly identify their staff production roles and regular site work.
- Vinny: his 2020 list identifies his work at Giant Bomb; the [2021 departure spotlight](https://giantbomb.com/articles/the-community-spotlight-2021-05-08) also documents his and Brad's staff history.
- Jeff Gerstmann: his [2015 list introduction](https://giantbomb.com/articles/jeff-gerstmanns-top-10-games-of-2015) explicitly identifies him as editor-in-chief.
- Brad: his [2015 list introduction](https://giantbomb.com/articles/brad-shoemakers-top-10-games-of-2015) explicitly identifies him as senior editor.
- Jeff Grubb, Mike, and Bakalar: the site's [209 Days To Build a Bomb](https://giantbomb.com/articles/209-days-to-build-a-bomb) documents the continuing core team and ownership. Bakalar's 2021 list introduction also explicitly identifies his Beastcast co-host role and move to full-time staff.
- Niki: the [2023-07-22 official spotlight](https://giantbomb.com/articles/the-community-spotlight-2023-07-22) announces their hire as an on-screen personality and social lead; their year-end list confirms sustained work with the team.

## Corrections and exclusions

- Removed the extra terminal period from the old jan-freaks entry.
- Restored the original curly apostrophe in bakalar-solar-ash.
- Did not restore the old Niki GB @ Nite excerpt: the original continued after a comma, while the old deck ended it as a sentence. The new couch excerpt contains two intact adjacent sentences instead.
- Left other old entries out unless both exact wording and regular-cast eligibility were established for this catalogue. Absence is not a claim that a contributor never qualified.
- Publication dates refer to the article's displayed date, not the year of the games list.

## Rechecking

`python scripts/verify-splash-quotes.py` is an optional, dependency-free text-presence check. It normalizes whitespace only and emits hashes and match status, without saving full articles. Exit 0 means every exact excerpt was found; exit 1 means a mismatch; exit 2 means a source was unavailable. It cannot verify editorial context or cast eligibility.

The direct HTTP check was blocked with HTTP 403 during this review. The web-rendered primary pages were readable and were used for the editorial comparison above. An unavailable automated check must never be reported as a pass. Future changes require another original-source review, including context and cast evidence.
