package com.example.slagalica.data.model;

import java.util.HashMap;
import java.util.Map;

public class Challenge {
    public String id;
    public String creatorUid;
    public String creatorName;
    public String region;
    public int starsBet;
    public int tokensBet;
    public long timestamp;
    public String status;
    public Map<String, Boolean> participants = new HashMap<>();
    public Map<String, Integer> scores = new HashMap<>();
    public Map<String, String> names = new HashMap<>();
    public String roundSeed;

    public Challenge() {}

    public Challenge(String id, String creatorUid, String creatorName,
                     String region, int stars, int tokens) {
        this.id = id;
        this.creatorUid = creatorUid;
        this.creatorName = creatorName;
        this.region = region;
        this.starsBet = stars;
        this.tokensBet = tokens;
        this.timestamp = System.currentTimeMillis();
        this.status = "open";
        this.participants.put(creatorUid, true);
        this.names.put(creatorUid, creatorName);
        this.roundSeed = java.util.UUID.randomUUID().toString();
    }
}