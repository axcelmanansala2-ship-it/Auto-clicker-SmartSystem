package com.smartsystem.autoclicker.models

import java.util.UUID

enum class AccountStatus {
    PENDING,
    IN_PROGRESS,
    BANNED,
    NEW_ACCOUNT,
    GOOD,
    INVALID  // wrong credentials / login failed
}

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val password: String,
    val status: AccountStatus = AccountStatus.PENDING,
    val note: String = ""
)
