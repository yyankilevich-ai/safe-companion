package com.safecompanion

/**
 * ============================================================
 *  FILL THESE IN — the only file you need to edit.
 *
 *  From your Supabase project: Dashboard → Project Settings →
 *  Data API:  copy the "Project URL"
 *  API Keys:  copy the "anon / public" key
 *
 *  The anon key is designed to be shipped in client apps;
 *  Row-Level Security in schema.sql is what protects the data.
 * ============================================================
 */
object BackendConfig {
    const val SUPABASE_URL = "https://genrbobnoeyohghlduqy.supabase.co" // e.g. https://abcd1234.supabase.co
    const val SUPABASE_ANON_KEY = "sb_publishable_bhxaHvfXzjbFlyi5xhiTdQ_WK1_ExZE"

    val isConfigured: Boolean
        get() = SUPABASE_URL.startsWith("https://") && SUPABASE_ANON_KEY.length > 40
}
