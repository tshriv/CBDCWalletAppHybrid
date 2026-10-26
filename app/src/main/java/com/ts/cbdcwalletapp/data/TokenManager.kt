package com.ts.cbdcwalletapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit { putString("jwt_token", token) }
    }

    fun getToken(): String? {
        return prefs.getString("jwt_token", null)
    }

    fun saveUser(user: User) {
        prefs.edit {
            putString("user_id", user.userId)
                .putString("user_name", user.name)
                .putString("user_phone", user.phoneNumber)
        }
    }

    fun getUser(): User? {
        val id = prefs.getString("user_id", null)
        val name = prefs.getString("user_name", null)
        val phone = prefs.getString("user_phone", null)

        return if (id != null && name != null && phone != null) {
            User(id = id, name = name, phoneNumber = phone)
        } else {
            null
        }
    }

    fun clearToken() {
        prefs.edit { clear() }
    }

    // ---------- Offline balance cache ----------
    // Seeds StateFlows so the UI never flashes ₹0 while Room loads.

    fun saveOfflineCache(balance: Int, count: Int) {
        prefs.edit {
            putInt("offline_balance_cache", balance)
                .putInt("offline_count_cache", count)
        }
    }

    fun getCachedOfflineBalance(): Int = prefs.getInt("offline_balance_cache", 0)
    fun getCachedOfflineCount(): Int = prefs.getInt("offline_count_cache", 0)
}
