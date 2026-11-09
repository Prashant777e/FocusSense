package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// MODERN INDIGO-SLATE & ELECTRIC AMETHYST PALETTE - FOCUSENSE
// A clean, high-contrast, eye-friendly design system replacing harsh greens
// with rich Deep Navy/Slate backgrounds, Vibrant Royal Indigo primary tokens,
// Soft Electric Violet accents, and neutral crisp light backgrounds.
// =========================================================================

// Deep Slate & Navy Night Tokens
val Navy900 = Color(0xFF0F172A)         // Deep Slate Darkest
val Navy800 = Color(0xFF1E293B)         // Night Slate / Top Bar Header
val Navy700 = Color(0xFF334155)         // Muted Slate Card Border

// Primary Royal Indigo & Cobalt Spectrum
val IndigoPrimary = Color(0xFF4F46E5)    // Vibrant Indigo Primary
val IndigoDark = Color(0xFF3730A3)       // Deep Indigo Tone
val IndigoLight = Color(0xFF818CF8)      // Light Periwinkle Blue

// Violet & Amethyst Accent Tokens
val PurpleAccent = Color(0xFF8B5CF6)     // Electric Amethyst / Violet
val PurpleAccentBg = Color(0xFFF3E8FF)   // Soft Lavender Tint

// Safe Status & Positive Accents (Clean Emerald Mint, subtle and balanced)
val EmeraldSafe = Color(0xFF10B981)      // Balanced Emerald Mint
val EmeraldSafeBg = Color(0xFFD1FAE5)    // Ice Mint Soft Tint

// Clean Backgrounds & Surfaces (Zero Eye Strain)
val SurfaceLight = Color(0xFFF8FAFC)     // Crisp Cool Slate-White (Light Mode)
val SurfaceDark = Color(0xFF0F172A)      // Deep Slate Night (Dark Mode)
val CardLight = Color(0xFFFFFFFF)        // Pure White Card
val CardDark = Color(0xFF1E293B)         // Deep Slate Card

// Backward-compatibility Aliases mapping legacy names to modern Indigo-Slate palette
val ForestDarkest = Navy900
val ForestDark = Navy800
val ForestSlate = Navy700
val ForestCard = CardDark

val EmeraldPrimary = IndigoPrimary
val EmeraldVibrant = IndigoPrimary
val EmeraldDark = IndigoDark
val EmeraldLight = IndigoLight
val EmeraldDeep = IndigoDark
val EmeraldUltraDeep = Navy900

val SageAccent = PurpleAccent
val SageAccentBg = PurpleAccentBg
val PineAccent = IndigoPrimary
val PineAccentBg = Color(0xFFEEF2FF)

// Functional Alert Tokens
val AmberWarning = Color(0xFFF59E0B)     // Warm Amber Honey
val AmberWarningBg = Color(0xFFFEF3C7)   // Soft Amber Tint
val CoralDanger = Color(0xFFEF4444)      // Crisp Rose Coral
val CoralDangerBg = Color(0xFFFEE2E2)    // Soft Rose Tint

// Typography & Text Ink Colors
val TextPrimaryLight = Color(0xFF0F172A)   // Deep Slate Ink
val TextSecondaryLight = Color(0xFF475569) // Muted Slate Body
val TextPrimaryDark = Color(0xFFF8FAFC)    // Crisp White Text
val TextSecondaryDark = Color(0xFF94A3B8)  // Soft Slate Text
