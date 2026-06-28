package com.example.slagalica.data.model;

public class ChallengeParticipant {
    public String uid;
    public String name;
    public int score;
    public ChallengeParticipant(String uid, String name, int score) {
        this.uid = uid;
        this.name = name;
        this.score = score;
    }
}