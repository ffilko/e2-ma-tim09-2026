package com.example.slagalica.data.model;

public class ChatContact {
    public String uid;
    public String username;
    public String region;
    public String lastMessage;
    public long lastMessageTime;

    public ChatContact() {}

    public ChatContact(String uid, String username, String region) {
        this.uid = uid;
        this.username = username;
        this.region = region;
    }
}