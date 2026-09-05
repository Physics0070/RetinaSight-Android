package com.retinasight.ai.core.model

/**
 * Where the referral line sits.
 *
 * The grader decides a *grade* by rounding the softmax-weighted mean grade, so
 * it refers (grade >= 2) only once the expected grade reaches 1.5. That is a
 * balanced operating point, and balance is the wrong objective for screening:
 * a missed referral can end in blindness, a false alarm costs one clinic visit.
 * Those errors are not symmetric, so the threshold should not sit in the middle.
 *
 * Measured on the held-out APTOS validation split of the shipped checkpoint
 * (efficientnet_b0-20260823-124225, n=546, 221 referable), sweeping the
 * referral threshold over the expected grade:
 *
 *   threshold   sensitivity   specificity   missed   false alarms
 *   1.50 (round)     91.4%         94.8%        19         17
 *   1.15 (here)      98.2%         92.9%         4         23
 *   1.00             100.0%        84.3%         0         51
 *
 * 1.15 buys 15 fewer missed referable patients for 6 more false alarms. Below
 * 1.15 specificity falls away quickly for no further gain in recall, because
 * 1.05..1.15 is a flat region at 98.2% sensitivity and 1.15 is its cheapest end.
 *
 * These are development numbers on a single dataset, not clinical validation.
 */
object ReferralPolicy {

    /** Refer when the expected grade reaches this. See the table above. */
    const val REFERRAL_THRESHOLD: Float = 1.15f

    /** The threshold implied by rounding, i.e. the grader's own balanced point. */
    const val ROUNDING_THRESHOLD: Float = 1.5f

    /** True when this scan should be sent to an ophthalmologist. */
    fun isReferable(expectedGrade: Float): Boolean = expectedGrade >= REFERRAL_THRESHOLD

    /**
     * True when the referral is owed to the screening threshold rather than to
     * the grade itself - the scan sits between 1.15 and 1.5, so the grade reads
     * "mild" while the referral says "go". The result screen says so out loud;
     * a technician must never be left guessing why the two disagree.
     */
    fun isBorderline(expectedGrade: Float, grade: DrGrade): Boolean =
        isReferable(expectedGrade) && grade.grade < 2
}
