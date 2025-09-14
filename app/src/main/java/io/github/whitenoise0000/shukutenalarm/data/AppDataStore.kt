package io.github.whitenoise0000.shukutenalarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

