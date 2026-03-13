package com.looktube.model

data class AccountSession(
    val isSignedIn: Boolean,
    val accountLabel: String?,
    val authMode: AuthMode?,
    val notes: String,
)
