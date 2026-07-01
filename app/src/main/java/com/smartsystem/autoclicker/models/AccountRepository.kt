package com.smartsystem.autoclicker.models

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AccountRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): MutableList<Account> {
        val json = prefs.getString(KEY, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Account>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    fun save(list: List<Account>) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }

    fun addAll(accounts: List<Account>) {
        val all = getAll()
        all.addAll(accounts)
        save(all)
    }

    fun remove(id: String) {
        save(getAll().filter { it.id != id })
    }

    fun setStatus(id: String, status: AccountStatus, note: String = "") {
        val all = getAll()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(status = status, note = note)
            save(all)
        }
    }

    /** Returns the first PENDING account, or null if none left. */
    fun getNextPending(): Account? = getAll().firstOrNull { it.status == AccountStatus.PENDING }

    /**
     * Resets all IN_PROGRESS accounts back to PENDING.
     * Call this when the service starts to recover from crashes/stops.
     */
    fun resetInProgress() {
        val all = getAll()
        var changed = false
        all.forEachIndexed { i, acc ->
            if (acc.status == AccountStatus.IN_PROGRESS) {
                all[i] = acc.copy(status = AccountStatus.PENDING)
                changed = true
            }
        }
        if (changed) save(all)
    }

    /** Parse "user:pass" lines — each line is one account. Skips duplicates. */
    fun parseAndAdd(raw: String) {
        val existing = getAll().map { it.username.lowercase() }.toSet()
        val accounts = raw.lines()
            .map { it.trim() }
            .filter { it.contains(':') && it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(':', limit = 2)
                if (parts.size < 2) null
                else Account(username = parts[0].trim(), password = parts[1].trim())
            }
            .filter { it.username.isNotBlank() && it.password.isNotBlank() }
            .filter { it.username.lowercase() !in existing }  // skip duplicates
        addAll(accounts)
    }

    fun getByStatus(vararg statuses: AccountStatus): List<Account> =
        getAll().filter { it.status in statuses }

    fun clearByStatus(status: AccountStatus) {
        save(getAll().filter { it.status != status })
    }

    fun countPending() = getAll().count { it.status == AccountStatus.PENDING }

    companion object {
        private const val PREFS = "account_checker_prefs"
        private const val KEY = "accounts"
    }
}
