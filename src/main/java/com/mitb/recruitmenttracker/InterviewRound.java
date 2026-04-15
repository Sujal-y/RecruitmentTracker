package com.mitb.recruitmenttracker;

/** Interview stage for combo boxes and updates (backed by {@code INTERVIEW_ROUND.round_id}). */
public record InterviewRound(int roundId, String roundName) {
    @Override
    public String toString() {
        return roundName;
    }
}
