package com.example.mutlabocnotes.auth

// Класс с основной логикой данного модуля.
open class SocialAuthException(message: String) : RuntimeException(message)

// Класс с основной логикой данного модуля.
class SocialAuthNotReadyException(message: String) : SocialAuthException(message)

// Класс с основной логикой данного модуля.
class SocialTokenValidationException(message: String) : SocialAuthException(message)

// Класс с основной логикой данного модуля.
class SocialIdentityResolutionException(message: String) : SocialAuthException(message)