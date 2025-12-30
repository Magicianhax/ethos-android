package com.ethos.led.model

data class Tier(
    val threshold: Int,
    val name: String,
    val color: String
)

object TierData {
    val SCORE_TIERS = listOf(
        Tier(800, "Untrusted", "b72b38"),
        Tier(1200, "Questionable", "C29010"),
        Tier(1400, "Neutral", "c1c0b6"),
        Tier(1600, "Known", "7C8DA8"),
        Tier(1800, "Established", "4E86B9"),
        Tier(2000, "Reputable", "2E7BC3"),
        Tier(2200, "Exemplary", "427B56"),
        Tier(2400, "Distinguished", "127f31"),
        Tier(2600, "Revered", "836DA6"),
        Tier(2800, "Renowned", "7A5EA0")
    )

    fun getTier(score: Int): Tier {
        for (tier in SCORE_TIERS) {
            if (score < tier.threshold) {
                return tier
            }
        }
        return Tier(Int.MAX_VALUE, "Renowned", "7A5EA0")
    }
}


