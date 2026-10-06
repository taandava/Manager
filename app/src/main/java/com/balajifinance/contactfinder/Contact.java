package com.balajifinance.contactfinder;

import java.util.UUID;

public class Contact {
    public String id;
    public String name;
    public String phone;
    public String details;
    public boolean saved;
    public boolean edited;
    public boolean deleted;
    public long createdAt;
    public long updatedAt;
    public String source;
    public String cloudId;
    public String syncStatus;

    public Contact() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.source = "LOCAL";
        this.syncStatus = "PENDING";
    }

    public Contact(String id, String name, String phone, String details) {
        this.id = (id == null || id.isEmpty()) ? UUID.randomUUID().toString() : id;
        this.name = name == null ? "" : name;
        this.phone = phone == null ? "" : phone;
        this.details = details == null ? "" : details;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.source = "CSV";
        this.syncStatus = "SYNCED";
    }

    public String getSearchable() {
        return PhoneUtils.normalizeSearch(name + " " + phone + " " + details);
    }
}
