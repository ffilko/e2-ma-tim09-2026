package com.example.slagalica.data.model;

public class ChatMessage {
    public String senderId;
    public String senderName;
    public String text;
    public long timestamp;

    public ChatMessage() {}

    public ChatMessage(String senderId, String senderName, String text, long timestamp) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = timestamp;
    }
}