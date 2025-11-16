package com.kieser.record;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitUserData (String name,
                           String avatar_url,
                           String location,
                           String email,
                           String url,
                           String created_at) {
}
