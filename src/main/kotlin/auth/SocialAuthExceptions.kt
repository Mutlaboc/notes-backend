package com.example.mutlabocnotes.auth

open class SocialAuthException(message: String) : RuntimeException(message)

class SocialAuthNotReadyException(message: String) : SocialAuthException(message)

class SocialTokenValidationException(message: String) : SocialAuthException(message)

class SocialIdentityResolutionException(message: String) : SocialAuthException(message)