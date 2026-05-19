package hr.kronos.backend.auth;

public record SocialIdentity(String providerSubject, String email, String name) {}
