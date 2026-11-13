package com.example.studyapp.auth;

import com.example.studyapp.user.Profile;
import com.example.studyapp.user.User;

public record AuthResult(IssuedTokens tokens, User user, Profile profile) {
}
