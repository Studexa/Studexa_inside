package com.studexa.auth;

import com.studexa.user.Profile;
import com.studexa.user.User;

public record AuthResult(IssuedTokens tokens, User user, Profile profile) {
}
