# Theme and language guide

`ui/Theme.kt` defines the palettes without any server dependency. Each palette has background, surface, ink, decorative accent, accessible button color and corner radius. Lavender is soft and rounded; Jade uses terminal-style headings and tighter corners; Solar uses angular outlined cards; Mint uses rounded electronic-instrument cues. System sans/monospace fonts provide Latin, accented pinyin and Android's Chinese fallback; there are no bundled font assets requiring redistribution.

| Theme | Background | Surface | Ink | Accent |
| --- | --- | --- | --- | --- |
| Lavender Original | #F2E8FA | #FBF8FE | #252033 | #A98AC9 |
| Jade Terminal | #E6ECDD | #F5F7EF | #153B2E | #8BB69C |
| Solar Archive | #F1E8D8 | #FAF5E9 | #274B37 | #C9AA7D |
| Mint Circuit | #E1EFEB | #F5FAF7 | #184738 | #93BFAE |

Light accents are decorative; buttons use darker colors for readable white text. Tests assert WCAG AA normal-text contrast for primary ink on surface/background and button labels. Settings provides immediate theme selection and swatches. All selected preferences persist in DataStore.

`ui/Strings.kt` stores independent English, Simplified Chinese and correctly accented pinyin entries. `Words.localized` builds the selected presentation. Chinese and pinyin wrap in normal text layouts. Compact navigation uses English visible labels and trilingual content descriptions; screen titles retain the selected language layers. Pinyin appears only when Chinese is enabled; its stored toggle remains independent. Dynamic server content is never translated.

Compose responds to font scaling. Touch controls use standard Material targets. Navigation transitions are disabled; loading indicators honor reduced-motion preference and the system animator scale. No ornamental loops, glow or intense neon are included. Test proposed themes with TalkBack, narrow widths and large fonts before release.
