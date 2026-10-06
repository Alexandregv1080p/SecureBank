package com.securebank.mobile.core.network

import kotlinx.serialization.Serializable

/** Espelho do contrato da API (docs/api/openapi.yaml). Valores monetários são String; tipos/estados também, para tolerar valores novos. */

@Serializable data class Money(val amount: String, val currency: String = "BRL")

/** Entradas e saídas de uma categoria (CASH, TRANSFERS, PAYMENTS, PIX, SAVINGS) no mês. */
@Serializable
data class CategoryTotal(val category: String, val income: Money, val expenses: Money)

/** Resumo do mês de uma conta: só lançamentos concluídos. */
@Serializable
data class StatementSummary(val month: String, val income: Money, val expenses: Money, val net: Money, val byCategory: List<CategoryTotal>)

@Serializable
data class Account(
    val id: String,
    val branch: String,
    val accountNumber: String,
    val type: String,
    val status: String,
    val balance: Money,
    val createdAt: String,
)

@Serializable
data class Transaction(
    val id: String,
    val type: String,
    val direction: String,
    val amount: Money,
    val balanceAfter: Money,
    val status: String,
    val reference: String? = null,
    val createdAt: String,
)

@Serializable
data class Page<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long)

@Serializable
data class LimitUsage(
    val type: String,
    val perOperation: Money,
    val daily: Money,
    val usedToday: Money,
    val remainingToday: Money,
)

@Serializable
data class Transfer(
    val id: String,
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amount: Money,
    val description: String? = null,
    val status: String,
    val createdAt: String,
)

@Serializable
data class Payment(
    val id: String,
    val accountId: String,
    val amount: Money,
    val barcode: String,
    val description: String? = null,
    val status: String,
    val createdAt: String,
)

@Serializable
data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val read: Boolean,
)

@Serializable
data class SessionInfo(
    val id: String,
    val createdAt: String,
    val lastUsedAt: String,
    val expiresAt: String,
    val ip: String? = null,
    val userAgent: String? = null,
    val mfaVerified: Boolean = false,
    val current: Boolean = false,
)

@Serializable
data class Customer(val id: String, val name: String, val document: String, val email: String, val phone: String)

// ---- autenticação
@Serializable data class RegisterRequest(val name: String, val document: String, val email: String, val phone: String, val password: String)
@Serializable data class RegisterResponse(val userId: String, val customerId: String, val email: String? = null)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class MfaVerifyRequest(val mfaToken: String, val code: String)
@Serializable data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenResponse(
    val mfaRequired: Boolean = false,
    val mfaToken: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

// ---- operações
@Serializable data class OpenAccountRequest(val type: String)
@Serializable data class AmountRequest(val amount: String)

@Serializable
data class TransferRequest(
    val sourceAccountId: String,
    val destinationBranch: String,
    val destinationAccountNumber: String,
    val amount: String,
    val description: String? = null,
)

@Serializable
data class PaymentRequest(val accountId: String, val amount: String, val barcode: String, val description: String? = null)

// ---- segurança
@Serializable data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
@Serializable data class CodeRequest(val code: String)
@Serializable data class MfaSetup(val secret: String, val otpauthUri: String)
@Serializable data class MfaStatus(val enabled: Boolean)
