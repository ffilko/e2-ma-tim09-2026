package com.example.slagalica.data.model;

public class LeaderboardEntry {
    public String uid;
    public String username;
    public int league;
    public long stars;
    public long games;
    public int rank;

    public LeaderboardEntry() {}

    public LeaderboardEntry(String uid, String username, int league, long stars, long games) {
        this.uid = uid;
        this.username = username;
        this.league = league;
        this.stars = stars;
        this.games = games;
    }
}
