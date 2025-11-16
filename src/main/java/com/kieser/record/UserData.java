package com.kieser.record;

// This data is the response to the UserController
public record UserData (String user_name,
                        String display_name,
                        String avatar,
                        String geo_location,
                        String email,
                        String url,
                        String created_at,
                        GitRepoData[] repos) {
}
