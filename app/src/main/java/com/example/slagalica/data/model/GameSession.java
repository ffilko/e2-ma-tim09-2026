package com.example.slagalica.data.model;

import java.util.Map;

public class GameSession {
    public String player1Id;
    public String player2Id;
    public String player1Name;
    public String player2Name;
    public int currentGameIndex;
    public String phase;    // waiting, playing, finished
    public boolean player1Ready;
    public boolean player2Ready;
    public Map<String, Object> gamesState;
    public Map<String, Integer> scores;
}
